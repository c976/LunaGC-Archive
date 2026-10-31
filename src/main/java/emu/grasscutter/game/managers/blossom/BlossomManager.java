package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.game.reward.RewardClaimFlow;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.data.excels.CityData;
import emu.grasscutter.game.inventory.InventoryAddPolicy;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.reward.RewardConfiguration;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.managers.ResinManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.PacketBlossomBriefInfoNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;

public class BlossomManager {
    private static final Set<BlossomType> MISSING_TYPES_REPORTED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final Scene scene;
    private final BlossomNativeCamps nativeCamps = new BlossomNativeCamps();
    private final List<BlossomActivity> blossomActivities = new ArrayList<>();
    private final List<BlossomActivity> activeChests = new ArrayList<>();
    private final List<EntityGadget> createdEntity = new ArrayList<>();

    private final List<SpawnDataEntry> blossomConsumed = new ArrayList<>();
    private List<SpawnDataEntry> scriptedSpawnCandidates;
    private final Set<SpawnDataEntry> scriptedSpawned = new HashSet<>();
    private List<SpawnDataEntry> activeSpawnCache;
    private int cachedAdventureRank = -1;
    private int cachedConsumedCount = -1;
    private Set<Integer> cachedUnlockedAreas = Set.of();
    private int activeSetRevision;
    private boolean mapMarkersDirty;
    private static final double SCRIPTED_SPAWN_RANGE = 400;
    private static final float OVERLAP_RANGE = 2.5f;

    /** The regular spawn grid is bypassed while Lua scene groups are active.
     * Populate the legacy ley-line outcrops from their authored spawn records in both modes.
     */
    public void ensureScriptedSpawns() {
        removeInactiveAuthoredOutcrops();
        for (SpawnDataEntry spawn : activeScriptedSpawns()) {
            if (scriptedSpawned.contains(spawn)) continue;
            boolean nearby = scene.getPlayers().stream()
                    .anyMatch(player -> player.getPosition().computeDistance(spawn.getPos())
                            <= SCRIPTED_SPAWN_RANGE);
            if (!nearby) continue;

            EntityGadget existing = scene.getEntities().values().stream()
                    .filter(entity -> entity instanceof EntityGadget)
                    .map(entity -> (EntityGadget) entity)
                    .filter(entity -> BlossomType.valueOf(entity.getGadgetId()) != null)
                    .filter(entity -> entity.getPosition().computeDistance(spawn.getPos())
                            <= OVERLAP_RANGE)
                    .findFirst().orElse(null);
            if (existing != null) {
                // An already-loaded script gadget at the same position owns this outcrop.
                // Only adopt metadata when it identifies the exact authored source.
                if (existing.getGroupId() != spawn.getGroup().getGroupId()
                        || existing.getConfigId() != spawn.getConfigId()
                        || existing.getGadgetId() != spawn.getGadgetId()) {
                    // A different gadget occupies this location; it does not own this spawn.
                    continue;
                }
                existing.setSpawnEntry(spawn);
                initBlossom(existing);
                scene.broadcastPacket(new emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify(
                        existing, VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH, 0));
            } else {
                var gadget = new EntityGadget(
                        scene, spawn.getGadgetId(), spawn.getPos(), spawn.getRot());
                gadget.setGroupId(spawn.getGroup().getGroupId());
                gadget.setConfigId(spawn.getConfigId());
                gadget.setSpawnEntry(spawn);
                if (spawn.getGadgetState() > 0) gadget.setState(spawn.getGadgetState());
                initBlossom(gadget);
                scene.addEntity(gadget);
            }
            scriptedSpawned.add(spawn);
            scene.getSpawnedEntities().add(spawn);
        }
    }

    /** Remove script-owned outcrops excluded by the city's current native refresh set. */
    private void removeInactiveAuthoredOutcrops() {
        var loaded = scene.getEntities().values().stream()
                .filter(entity -> entity instanceof EntityGadget)
                .map(entity -> (EntityGadget) entity)
                // A /spawn gadget has group 0 and must remain under operator control.
                .filter(entity -> entity.getGroupId() != 0)
                .filter(entity -> BlossomType.valueOf(entity.getGadgetId()) != null)
                .sorted(java.util.Comparator
                        .comparingInt((EntityGadget entity) ->
                                entity.getGadgetId() == BlossomType.BLUE.getGadgetId() ? 0 : 1)
                        .thenComparingInt(EntityGadget::getGroupId)
                        .thenComparingInt(EntityGadget::getConfigId))
                .toList();
        var kept = new ArrayList<EntityGadget>();
        var removed = new ArrayList<GameEntity>();
        for (var gadget : loaded) {
            boolean inactive = !isActiveAuthoredGadget(
                    gadget.getGroupId(), gadget.getConfigId(), gadget.getGadgetId());
            boolean duplicate = kept.stream().anyMatch(other ->
                    other.getPosition().computeDistance(gadget.getPosition()) <= OVERLAP_RANGE);
            if (inactive || duplicate) {
                Grasscutter.getLogger().debug(
                        "Ley-line authored outcrop suppressed group={} config={} gadget={} reason={}",
                        gadget.getGroupId(), gadget.getConfigId(), gadget.getGadgetId(),
                        inactive ? "inactive" : "overlap");
                scene.removeEntity(gadget,
                        VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                removed.add(gadget);
            } else {
                kept.add(gadget);
            }
        }
        recycleGadgetEntity(removed);
    }

    /** Use existing spawn coordinates and gadget IDs, never synthetic ley-line positions. */
    static List<SpawnDataEntry> scriptedCandidates(
            Map<SpawnDataEntry.GridBlockId, ? extends List<SpawnDataEntry>> spawnLists,
            int sceneId) {
        var source = spawnLists.entrySet().stream()
                .filter(entry -> entry.getKey().getSceneId() == sceneId)
                .flatMap(entry -> entry.getValue().stream())
                .filter(entry -> BlossomType.valueOf(entry.getGadgetId()) != null)
                .distinct()
                // Prefer the earlier-unlocked Revelation outcrop when two source
                // entries occupy the same physical location.
                .sorted(java.util.Comparator
                        .comparingInt((SpawnDataEntry entry) ->
                                entry.getGadgetId() == BlossomType.BLUE.getGadgetId() ? 0 : 1)
                        .thenComparingInt(entry -> entry.getGroup().getGroupId())
                        .thenComparingInt(SpawnDataEntry::getConfigId))
                .toList();
        var distinct = new ArrayList<SpawnDataEntry>();
        for (var entry : source) {
            if (distinct.stream().noneMatch(other ->
                    other.getGadgetId() == entry.getGadgetId()
                            && other.getPos().computeDistance(entry.getPos()) <= OVERLAP_RANGE)) {
                distinct.add(entry);
            }
        }
        return distinct;
    }


    /** The normal world grid and Lua group loader must obey the same selection. */
    public boolean isActiveSpawn(SpawnDataEntry entry) {
        return BlossomType.valueOf(entry.getGadgetId()) == null
                || activeScriptedSpawns().contains(entry);
    }

    /** Operator-created gadgets with no authored group are outside this policy. */
    /** Native camp instances are not part of the legacy Spawns.json grid. */
    public boolean isNativeCampSpawn(SpawnDataEntry entry) {
        return nativeCamps.owns(entry);
    }

    public boolean isActiveAuthoredGadget(int groupId, int configId, int gadgetId) {
        if (BlossomType.valueOf(gadgetId) == null || groupId == 0) return true;
        return activeScriptedSpawns().stream().anyMatch(entry ->
                entry.getGroup().getGroupId() == groupId
                        && entry.getConfigId() == configId
                        && entry.getGadgetId() == gadgetId);
    }

    private List<SpawnDataEntry> authoredCandidates() {
        if (scriptedSpawnCandidates == null) {
            scriptedSpawnCandidates = scriptedCandidates(GameDepot.getSpawnLists(), scene.getId());
            long gold = scriptedSpawnCandidates.stream()
                    .filter(entry -> BlossomType.valueOf(entry.getGadgetId()) == BlossomType.GOLD)
                    .count();
            Grasscutter.getLogger().info(
                    "Ley-line outcrop sources scene={} wealth={} revelation={}",
                    scene.getId(), gold, scriptedSpawnCandidates.size() - gold);
        }
        return scriptedSpawnCandidates;
    }

    private List<SpawnDataEntry> activeScriptedSpawns() {
        var host = scene.getWorld().getHost();
        int rank = host.getLevel();
        var unlocked = host.getUnlockedSceneAreas(scene.getId());
        if (activeSpawnCache != null && cachedAdventureRank == rank
                && cachedConsumedCount == blossomConsumed.size()
                && cachedUnlockedAreas.equals(unlocked)) {
            return activeSpawnCache;
        }

        var old = activeSpawnCache;
        var areaIndex = WorldAreaIndex.forScene(scene.getId());
        // Native BlossomGroups supplies many more locations than the imported grid:
        // its newGroupVec resolves directly to original Scene Lua gadgets.
        var nativeSelected = nativeCamps.select(scene, rank, unlocked);
        var selected = nativeSelected.isEmpty()
                ? (areaIndex.isEmpty() ? List.<SpawnDataEntry>of()
                    : selectActiveSpawns(authoredCandidates().stream()
                            .filter(entry -> !blossomConsumed.contains(entry)).toList(),
                            rank, unlocked,
                            GameData.getCityDataMap().values(),
                            GameData.getBlossomRefreshExcelConfigDataMap().values(),
                            entry -> areaIndex.level1AreaAt(entry.getPos().getX(), entry.getPos().getZ())))
                : nativeSelected;

        activeSpawnCache = selected;
        cachedAdventureRank = rank;
        cachedUnlockedAreas = Set.copyOf(unlocked);
        cachedConsumedCount = blossomConsumed.size();
        if (old == null || !old.equals(selected)) {
            activeSetRevision++;
            if (old != null) mapMarkersDirty = true;
            Grasscutter.getLogger().info(
                    "Ley-line active scene={} rank={} blue={} gold={} originalCamps={}",
                    scene.getId(), rank,
                    selected.stream().filter(e -> e.getGadgetId() == BlossomType.BLUE.getGadgetId()).count(),
                    selected.stream().filter(e -> e.getGadgetId() == BlossomType.GOLD.getGadgetId()).count(),
                    !nativeSelected.isEmpty());
        }
        return selected;
    }

    /** Used by the normal grid loader to notice eligibility changes without player movement. */
    public int getActiveSetRevision() {
        activeScriptedSpawns();
        return activeSetRevision;
    }

    /** Select up to refreshCount entries for each native (cityId, refreshType) pair. */
    static List<SpawnDataEntry> selectActiveSpawns(
            List<SpawnDataEntry> candidates, int adventureRank, Collection<Integer> unlockedAreas,
            Collection<CityData> cities, Collection<BlossomRefreshExcelConfigData> refreshes,
            java.util.function.ToIntFunction<SpawnDataEntry> areaAt) {
        record Key(int cityId, String type) {}
        var areaToCity = new HashMap<Integer, Integer>();
        var ambiguous = new HashSet<Integer>();
        for (var city : cities) {
            if (city.getAreaIdVec() == null) continue;
            for (int area : city.getAreaIdVec()) {
                var previous = areaToCity.putIfAbsent(area, city.getCityId());
                if (previous != null && previous != city.getCityId()) ambiguous.add(area);
            }
        }

        var refreshByKey = new HashMap<Key, BlossomRefreshExcelConfigData>();
        for (var config : refreshes) {
            if (config.getRefreshCount() <= 0) continue;
            if (!BlossomType.GOLD.refreshType().equals(config.getRefreshType())
                    && !BlossomType.BLUE.refreshType().equals(config.getRefreshType())) continue;
            refreshByKey.put(new Key(config.getCityId(), config.getRefreshType()), config);
        }

        var selected = new ArrayList<SpawnDataEntry>();
        var counts = new HashMap<Integer, Integer>();
        for (var entry : candidates) {
            var type = BlossomType.valueOf(entry.getGadgetId());
            if (type == null) continue;
            int area = areaAt.applyAsInt(entry);
            if (area == 0 || ambiguous.contains(area)) continue;
            Integer cityId = areaToCity.get(area);
            if (cityId == null) continue;
            var refresh = refreshByKey.get(new Key(cityId, type.refreshType()));
            if (refresh == null || !refreshEligible(refresh, adventureRank, unlockedAreas, cities)) continue;
            if (counts.getOrDefault(refresh.getId(), 0) >= refresh.getRefreshCount()) continue;
            if (selected.stream().anyMatch(other ->
                    other.getPos().computeDistance(entry.getPos()) <= OVERLAP_RANGE)) continue;
            selected.add(entry);
            counts.merge(refresh.getId(), 1, Integer::sum);
        }
        return selected;
    }

    static boolean refreshEligible(
            BlossomRefreshExcelConfigData refresh, int adventureRank,
            Collection<Integer> unlockedAreas, Collection<CityData> cities) {
        if (refresh.getRefreshCondVec() == null) return false;
        for (var condition : refresh.getRefreshCondVec()) {
            if (condition == null || condition.getType() == null) continue;
            var params = condition.getParam();
            if (params == null || params.isEmpty()) return false;
            switch (condition.getType()) {
                case "BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER" -> {
                    if (adventureRank < params.get(0)) return false;
                }
                case "BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY" -> {
                    if (params.get(0) != refresh.getCityId()) return false;
                    boolean anyUnlocked = cities.stream()
                            .filter(city -> city.getCityId() == refresh.getCityId())
                            .filter(city -> city.getAreaIdVec() != null)
                            .anyMatch(city -> city.getAreaIdVec().stream()
                                    .anyMatch(unlockedAreas::contains));
                    if (!anyUnlocked) return false;
                }
                default -> {
                    // Unknown conditions must be verified before enabling their entries.
                    return false;
                }
            }
        }
        return true;
    }

    public BlossomManager(Scene scene) {
        this.scene = scene;
    }

    /** Track ability summons at creation, including ones removed before the next world tick. */
    public void onSceneEntityCreated(GameEntity entity) {
        synchronized (blossomActivities) {
            for (var activity : blossomActivities) activity.onSceneEntityCreated(entity);
        }
    }

    public void onTick() {
        activeScriptedSpawns();
        // Scene.checkGroups() already loads nearby authored outcrops in script mode.
        // The normal spawn-grid path needs the same loader.
        if (!scene.getScriptManager().isInit()) ensureScriptedSpawns();
        if (mapMarkersDirty) notifyIcon();
        synchronized (blossomActivities) {
            var it = blossomActivities.iterator();
            while (it.hasNext()) {
                var active = it.next();
                active.onTick();
                if (active.getPass()) {
                    active.clearEncounterSummons();
                    EntityGadget chest = active.getChest();
                    scene.addEntity(chest);
                    Grasscutter.getLogger().info(
                            "Ley-line reward flower spawned scene={} gadget={} source={} kills={}/{}",
                            scene.getId(), chest.getGadgetId(),
                            active.getGadget().getGadgetId(),
                            active.getChallenge().getScore().get(),
                            active.getChallenge().getGoal());
                    scene.setChallenge(null);
                    activeChests.add(active);
                    it.remove();
                }
            }
        }
    }

    /** Entity unload is separate from activity completion and map-marker changes. */
    public void recycleGadgetEntity(List<GameEntity> entities) {
        for (var entity : entities) {
            if (!(entity instanceof EntityGadget gadget)
                    || BlossomType.valueOf(gadget.getGadgetId()) == null) continue;
            createdEntity.remove(gadget);
            var spawn = gadget.getSpawnEntry();
            if (spawn == null) continue;
            scriptedSpawned.remove(spawn);
            scene.getSpawnedEntities().remove(spawn);
        }
    }

    public void initBlossom(EntityGadget gadget) {
        if (createdEntity.contains(gadget)) {
            return;
        }
        if (blossomConsumed.contains(gadget.getSpawnEntry())) {
            return;
        }
        var id = gadget.getGadgetId();
        if (BlossomType.valueOf(id) == null) {
            return;
        }
        // Source gadget rows may carry a generic type under 7.1; a ley-line outcrop
        // always needs a worktop to start its combat challenge.
        GadgetWorktop gadgetWorktop = gadget.getContent() instanceof GadgetWorktop worktop
                ? worktop : new GadgetWorktop(gadget);
        gadget.replaceContent(gadgetWorktop);
        gadget.setState(204);
        int worldLevel = getWorldLevel();
        gadgetWorktop.addWorktopOptions(new int[] {187});
        gadgetWorktop.setOnSelectWorktopOptionEvent(
                (GadgetWorktop context, int option) -> {
                    BlossomActivity activity;
                    EntityGadget entityGadget = context.getGadget();
                    synchronized (blossomActivities) {
                        for (BlossomActivity i : this.blossomActivities) {
                            if (i.getGadget() == entityGadget) {
                                return false;
                            }
                        }

                        int volume = 0;
                        IntList monsters = new IntArrayList();
                        while (true) {
                            var remain = GameDepot.getBlossomConfig().getMonsterFightingVolume() - volume;
                            if (remain <= 0) {
                                break;
                            }
                            var rand = Utils.randomRange(1, 100);
                            if (rand > 85 && remain >= 50) { // 15% ,generate strong monster
                                monsters.addAll(getRandomMonstersID(2, 1));
                                volume += 50;
                            } else if (rand > 50 && remain >= 20) { // 35% ,generate normal monster
                                monsters.addAll(getRandomMonstersID(1, 1));
                                volume += 20;
                            } else { // 50% ,generate weak monster
                                monsters.addAll(getRandomMonstersID(0, 1));
                                volume += 10;
                            }
                        }

                        Grasscutter.getLogger().info("Blossom Monsters:" + monsters);

                        activity = new BlossomActivity(entityGadget, monsters, -1, worldLevel);
                        blossomActivities.add(activity);
                    }
                    entityGadget.updateState(201);
                    scene.setChallenge(activity.getChallenge());
                    scene.removeEntity(entityGadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                    activity.start();
                    return true;
                });
        createdEntity.add(gadget);
        notifyIcon();
    }

    /** Each player receives only the outcrops unlocked at their current Adventure Rank. */
    public void notifyIcon() {
        activeScriptedSpawns();
        mapMarkersDirty = false;
        scene.getPlayers().forEach(this::notifyIcon);
    }

    /** Sync map markers after EnterSceneDone, including when no outcrop is nearby. */
    public void notifyIcon(Player player) {
        player.sendPacket(new PacketBlossomBriefInfoNotify(buildBriefInfos(player)));
    }

    // These progression thresholds are gameplay observations; original-client
    // protobuf wire identities are verified separately in Genshin-Reverse.
    static boolean unlockedAtRank(BlossomType type, int adventureRank) {
        return type != null && adventureRank >= (type == BlossomType.BLUE ? 8 : 12);
    }

    private List<BlossomBriefInfoOuterClass.BlossomBriefInfo> buildBriefInfos(Player player) {
        final int wl = getWorldLevel();
        final int worldLevel = (wl < 0) ? 0 : ((wl > 9) ? 9 : wl);
        final var worldLevelData = GameData.getWorldLevelDataMap().get(worldLevel);
        final int monsterLevel = (worldLevelData != null) ? worldLevelData.getMonsterLevel() : 1;
        var blossoms = new ArrayList<BlossomBriefInfoOuterClass.BlossomBriefInfo>();
        for (var spawn : activeScriptedSpawns()) {
            if (blossomConsumed.contains(spawn)) continue;
            var type = BlossomType.valueOf(spawn.getGadgetId());
            if (!unlockedAtRank(type, player.getLevel())) continue;
            Integer previewReward = getPreviewReward(type, worldLevel);
            if (previewReward == null) continue;
            blossoms.add(BlossomBriefInfoOuterClass.BlossomBriefInfo.newBuilder()
                    .setSceneId(scene.getId())
                    .setPos(spawn.getPos().toProto())
                    .setResin(20)
                    .setMonsterLevel(monsterLevel)
                    .setRewardId(previewReward)
                    .setCircleCampId(nativeCamps.campId(spawn) == 0
                            ? type.getCircleCampId() : nativeCamps.campId(spawn))
                    .setRefreshId(nativeCamps.refreshId(spawn) == 0
                            ? type.getBlossomChestId() : nativeCamps.refreshId(spawn))
                    .build());
        }
        return blossoms;
    }

    public int getWorldLevel() {
        return scene.getWorld().getWorldLevel();
    }

    private static Integer getPreviewReward(BlossomType type, int worldLevel) {
        if (type == null || worldLevel < 0 || worldLevel > 9) {
            throw new IllegalArgumentException(
                    "Unsupported blossom type/world level: " + type + "/" + worldLevel);
        }
        // Both kinds have blossomChestId=1 in the native Excel rows. The actual
        // distinction is refreshType (SCOIN vs EXP), shared across cities.
        Integer previewId = null;
        for (var data : GameData.getBlossomRefreshExcelConfigDataMap().values()) {
            if (!type.refreshType().equals(data.getRefreshType())) continue;
            var drops = data.getDropVec();
            // Native Excel has 9 preview tiers (WL0..WL8); WL9 shares the capped WL8 preview.
            int index = worldLevel == 9 ? 8 : worldLevel;
            if (drops == null || drops.length <= index || drops[index] == null
                    || drops[index].getPreviewReward() <= 0) {
                throw new IllegalStateException(
                        "Missing native blossom preview: " + type + " WL" + worldLevel
                                + " refresh=" + data.getId());
            }
            int candidate = drops[index].getPreviewReward();
            if (previewId != null && previewId != candidate) {
                throw new IllegalStateException(
                        "Conflicting native blossom previews: " + type + " WL" + worldLevel);
            }
            previewId = candidate;
        }
        if (previewId == null && MISSING_TYPES_REPORTED.add(type)) {
            Grasscutter.getLogger().error(
                    "Missing native blossom refresh type {} for {}", type.refreshType(), type);
        }
        return previewId;
    }

    public boolean hasRewardChest(EntityGadget chest) {
        synchronized (activeChests) {
            return activeChests.stream().anyMatch(active -> active.getChest() == chest);
        }
    }

    /** Called once the reward chest is fully claimed; ignores chests this manager doesn't own. */
    public void retireChest(EntityGadget chest) {
        synchronized (activeChests) {
            var it = activeChests.iterator();
            while (it.hasNext()) {
                var active = it.next();
                if (active.getChest() == chest) {
                    it.remove();
                    // Debug-spawned outcrops have no authored SpawnDataEntry. Never
                    // mark null as consumed, or later /spawn outcrops all get skipped.
                    var source = active.getGadget().getSpawnEntry();
                    if (source != null) {
                        if (nativeCamps.advance(source, scene.getWorld().getHost().getLevel())) {
                            activeSpawnCache = null;
                            activeSetRevision++;
                        } else {
                            blossomConsumed.add(source);
                        }
                    }
                    recycleGadgetEntity(List.of(active.getGadget()));
                    ensureScriptedSpawns();
                    notifyIcon();
                    return;
                }
            }
        }
    }

    /**
     * Claim a completed ley-line flower. Roll the two rewards independently for
     * condensed resin, preflight the complete inventory batch, then charge once.
     * Admission/payment refusal permits retry. Ambiguous delivery failures retain a receipt.
     */
    record ClaimCost(int rolls, int itemId, int amount) {}

    /**
     * Global 7.1 GadgetInteractReq field 14 selects one (20 resin) or two (40 resin)
     * original-resin claims. Both use ResinCostType_NORMAL on the wire.
     */
    static ClaimCost claimCost(
            ResinCostTypeOuterClass.ResinCostType costType, int buyCount, int normalClaimCount) {
        return switch (costType) {
            case ResinCostType_NONE, ResinCostType_NORMAL ->
                    normalClaimCount == 1 || normalClaimCount == 2
                            ? new ClaimCost(normalClaimCount, 106, 20 * normalClaimCount)
                            : null;
            case ResinCostType_CONDENSE -> new ClaimCost(3, 220007, 1);
            case ResinCostType_FRAGILE -> new ClaimCost(3, 107009, 1);
            case ResinCostType_TIMING -> new ClaimCost(3, 107012, 1);
            case ResinCostType_HCOIN -> buyCount < ResinManager.MAX_RESIN_BUYING_COUNT
                    ? new ClaimCost(3, 201, ResinManager.HCOIN_NUM_TO_BUY_RESIN[buyCount]) : null;
            default -> null;
        };
    }

    /** Global 7.1 original-resin amount selector: GadgetInteractReq field 14. */
    static int normalClaimCount(GadgetInteractReqOuterClass.GadgetInteractReq req) {
        return (int) req.getField(req.getDescriptorForType().findFieldByNumber(14));
    }

    public List<GameItem> claimReward(Player player, EntityGadget chest,
            GadgetInteractReqOuterClass.GadgetInteractReq req, Runnable confirmed) {
        synchronized (activeChests) {
            BlossomActivity active = activeChests.stream()
                    .filter(candidate -> candidate.getChest() == chest)
                    .findFirst().orElse(null);
            if (active == null || active.rewardClaimed(player.getUid())) return null;

            BlossomType type = BlossomType.valueOf(active.getGadget().getGadgetId());
            if (type == null) throw new IllegalStateException("Unknown claimed blossom type");
            var costType = req.getResinCostType();
            int normalClaimCount = normalClaimCount(req);
            var cost = claimCost(costType, player.getResinBuyCount(), normalClaimCount);
            if (cost == null) {
                Grasscutter.getLogger().warn(
                        "Ley-line claim rejected uid={} resinCostType={} field14={}",
                        player.getUid(), costType, normalClaimCount);
                return null;
            }
            int resinBefore = player.getProperty(emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_RESIN);
            int worldLevel = getWorldLevel();
            Map<Integer, Integer> rolled = RewardConfiguration.current().rollBlossom(
                    type.rewardKind(), worldLevel, cost.rolls(),
                    java.util.concurrent.ThreadLocalRandom.current());
            var items = rolled.entrySet().stream()
                    .map(entry -> new GameItem(entry.getKey(), entry.getValue()))
                    .toList();
            var resin = player.getResinManager();
            var journal = new RewardGrantJournal();
            var key = new RewardGrantJournal.Key(RewardGrantJournal.Source.BLOSSOM, player.getUid(),
                    "flower." + scene.getId() + "." + active.getGadget().getGroupId() + "."
                            + active.getGadget().getConfigId() + "." + active.rewardOperation(player.getUid()));
            var snapshot = items.stream().map(item -> new RewardGrantJournal.Reward(
                    item.getItemId(), item.getCount(), item.getLevel())).toList();
            var debits = Configuration.GAME_OPTIONS.resinOptions.resinUsage
                    ? List.of(new RewardGrantJournal.Debit(cost.itemId(), cost.amount()))
                    : List.<RewardGrantJournal.Debit>of();
            boolean accepted = RewardClaimFlow.grant(
                    RewardClaimFlow.receipt(journal, key, snapshot, debits),
                    new RewardClaimFlow.Delivery() {
                        public boolean admit(java.util.function.BooleanSupplier authorize, Runnable claim) {
                            return player.getInventory().addItems(items, ActionReason.OpenWorldBossChest,
                                    InventoryAddPolicy.ALL_OR_NOTHING, authorize, claim).allAccepted();
                        }
                        public void publish() {}
                    }, () -> switch (costType) {
                        case ResinCostType_CONDENSE -> resin.useCondensedResin(1);
                        case ResinCostType_FRAGILE, ResinCostType_TIMING ->
                                !Configuration.GAME_OPTIONS.resinOptions.resinUsage
                                        || player.getInventory().payItem(cost.itemId(), cost.amount());
                        case ResinCostType_HCOIN -> resin.payHcoinRewardClaim();
                        default -> resin.useResin(cost.amount());
                    },
                    () -> {
                        active.confirmReward(player.getUid());
                        confirmed.run();
                    }, () -> active.resetUnpaidReward(player.getUid()));
            if (!accepted) {
                Grasscutter.getLogger().warn("Ley-line claim refused uid={} kind={} receipt={}",
                        player.getUid(), type, key.storageId());
                return null;
            }
            Grasscutter.getLogger().info(
                    "Ley-line claim settled uid={} kind={} resinCostType={} rolls={} debitItem={} debitAmount={} resinBefore={} resinAfter={} rewards={}",
                    player.getUid(), type, costType, cost.rolls(), cost.itemId(), cost.amount(),
                    resinBefore,
                    player.getProperty(emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_RESIN),
                    rolled);
            return items;
        }
    }

    public static IntList getRandomMonstersID(int difficulty, int count) {
        IntList result = new IntArrayList();
        List<Integer> monsters =
                GameDepot.getBlossomConfig().getMonsterIdsPerDifficulty().get(difficulty);
        for (int i = 0; i < count; i++) {
            result.add((int) monsters.get(Utils.randomRange(0, monsters.size() - 1)));
        }
        return result;
    }
}
