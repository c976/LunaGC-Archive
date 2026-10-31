package emu.grasscutter.game.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMonster;
import emu.grasscutter.server.packet.send.PacketGetInvestigationMonsterRsp;
import emu.grasscutter.server.packet.send.PacketMonsterForceAlertNotify;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Force-load Common/Elite investigation groups near players.
 * Elite (e.g. ToothTrap hunter) respawns 12h after kill; persists across restarts.
 */
public final class InvestigationSpawnHelper {
    private static final double NEAR_DIST = 140.0;
    private static final double LEAVE_DIST = 220.0;
    private static final long CHECK_INTERVAL_MS = 3000L;
    /** Anti flicker right after a successful create. */
    private static final long ANTI_FLICKER_MS = 45000L;
    /** Elite investigation respawn after kill. */
    private static final long ELITE_RESPAWN_MS = 12L * 60L * 60L * 1000L;
    private static final Path KILL_STORE = Path.of("data", "investigation_elite_respawn.json");

    private static final ConcurrentHashMap<Integer, Long> lastCheckMs = new ConcurrentHashMap<Integer, Long>();
    private static final ConcurrentHashMap<Integer, Long> lastSpawnOkMs = new ConcurrentHashMap<Integer, Long>();
    /** groupId -> kill epoch ms */
    private static final ConcurrentHashMap<Integer, Long> groupKilledAtMs = new ConcurrentHashMap<Integer, Long>();
    private static final Set<Integer> heldGroups = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> indexedGroupIds = ConcurrentHashMap.newKeySet();
    /** groupId -> investigationId */
    private static final ConcurrentHashMap<Integer, Integer> groupToInvestigationId = new ConcurrentHashMap<Integer, Integer>();
    /** investigationId -> cityId */
    private static final ConcurrentHashMap<Integer, Integer> investigationCityId = new ConcurrentHashMap<Integer, Integer>();
    private static volatile List<SpawnEntry> index;
    private static volatile boolean killStoreLoaded;

    private InvestigationSpawnHelper() {}

    public static final class SpawnEntry {
        public final int investigationId;
        public final int groupId;
        public final int blockId;
        public final int monsterId;
        public final Position position;
        public final Set<Integer> allowedMonsterIds;
        public final boolean elite;

        public SpawnEntry(
                int investigationId,
                int groupId,
                int blockId,
                int monsterId,
                Position position,
                Set<Integer> allowedMonsterIds,
                boolean elite) {
            this.investigationId = investigationId;
            this.groupId = groupId;
            this.blockId = blockId;
            this.monsterId = monsterId;
            this.position = position;
            this.allowedMonsterIds = allowedMonsterIds == null ? Set.of() : Set.copyOf(allowedMonsterIds);
            this.elite = elite;
        }
    }

    private static void ensureKillStoreLoaded() {
        if (killStoreLoaded) return;
        synchronized (InvestigationSpawnHelper.class) {
            if (killStoreLoaded) return;
            try {
                if (Files.isRegularFile(KILL_STORE)) {
                    String raw = Files.readString(KILL_STORE, StandardCharsets.UTF_8);
                    JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                        try {
                            groupKilledAtMs.put(Integer.parseInt(e.getKey()), e.getValue().getAsLong());
                        } catch (Exception ignored) {
                        }
                    }
                    Grasscutter.getLogger().info(
                        "InvestigationSpawnHelper loaded {} elite kill timers", groupKilledAtMs.size());
                }
            } catch (Exception e) {
                Grasscutter.getLogger().warn("InvestigationSpawnHelper kill-store load failed: {}", e.toString());
            }
            killStoreLoaded = true;
        }
    }

    private static void saveKillStore() {
        try {
            Files.createDirectories(KILL_STORE.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<Integer, Long> e : groupKilledAtMs.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append('"').append(':').append(e.getValue());
            }
            sb.append('}');
            Files.writeString(KILL_STORE, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Grasscutter.getLogger().warn("InvestigationSpawnHelper kill-store save failed: {}", e.toString());
        }
    }

    /** Called from EntityMonster.onDeath for investigation groups. */
    public static void markKilled(int groupId) {
        if (groupId <= 0) return;
        ensureIndex();
        ensureKillStoreLoaded();
        if (!indexedGroupIds.contains(groupId)) return;
        long now = System.currentTimeMillis();
        groupKilledAtMs.put(groupId, now);
        heldGroups.remove(groupId);
        saveKillStore();
        try {
            InvestigationTrackHelper.invalidateGroup(groupId);
        } catch (Throwable ignored) {
        }
        Grasscutter.getLogger().info(
            "InvestigationSpawnHelper group {} killed; elite respawn in {}h",
            groupId,
            ELITE_RESPAWN_MS / 3600000L);
    }

    public static void markKilled(EntityMonster monster) {
        if (monster == null) return;
        int groupId = monster.getGroupId();
        markKilled(groupId);
        try {
            pushTrackRefreshAfterKill(monster, groupId);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn(
                "InvestigationSpawnHelper track refresh after kill failed group {}: {}",
                groupId,
                t.toString());
        }
    }

    /**
     * After elite kill: push fresh handbook positions so the client tracking mark
     * jumps to the next available spawn without reopening the handbook.
     */
    private static void pushTrackRefreshAfterKill(EntityMonster monster, int groupId) {
        ensureIndex();
        Integer invIdObj = groupToInvestigationId.get(groupId);
        if (invIdObj == null) return;
        int invId = invIdObj.intValue();
        Integer cityObj = investigationCityId.get(invId);
        int cityId = cityObj != null ? cityObj.intValue() : 1;

        Scene scene = monster.getScene();
        if (scene == null || scene.getPlayers() == null || scene.getPlayers().isEmpty()) return;

        List<Integer> cities = List.of(
                Integer.valueOf(cityId),
                Integer.valueOf(1),
                Integer.valueOf(6));

        for (Player player : scene.getPlayers()) {
            if (player == null || player.getServer() == null) continue;
            try {
                var wds = player.getServer().getWorldDataSystem();
                player.sendPacket(new PacketGetInvestigationMonsterRsp(player, wds, cities));

                emu.grasscutter.net.proto.InvestigationMonsterOuterClass.InvestigationMonster updated = null;
                for (Integer cid : cities) {
                    List<emu.grasscutter.net.proto.InvestigationMonsterOuterClass.InvestigationMonster> list =
                            wds.getInvestigationMonstersByCityId(player, cid.intValue());
                    if (list == null) continue;
                    for (emu.grasscutter.net.proto.InvestigationMonsterOuterClass.InvestigationMonster m : list) {
                        if (m != null && m.getId() == invId) {
                            updated = m;
                            break;
                        }
                    }
                    if (updated != null) break;
                }
                if (updated != null) {
                    boolean allCd = areAllGroupsOnCooldown(invId);
                    Grasscutter.getLogger().info(
                        "InvestigationSpawnHelper pushed track refresh uid={} inv={} after group {} allCd={}",
                        player.getUid(),
                        invId,
                        groupId,
                        allCd);
                }
            } catch (Throwable t) {
                Grasscutter.getLogger().warn(
                    "InvestigationSpawnHelper push track uid={} failed: {}",
                    player.getUid(),
                    t.toString());
            }
        }
    }

    /** Nearest elite/common spawn for an investigation that is not on 12h cooldown. */
    public static SpawnEntry findNearestAvailable(int investigationId, Position from) {
        ensureIndex();
        if (index == null || index.isEmpty()) return null;
        SpawnEntry best = null;
        double bestDist = Double.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (SpawnEntry e : index) {
            if (e.investigationId != investigationId) continue;
            if (onKillCooldown(e, now)) continue;
            double d = (from != null && e.position != null) ? from.computeDistance(e.position) : 0.0;
            if (best == null || d < bestDist) {
                best = e;
                bestDist = d;
            }
        }
        return best;
    }

    /** Position of a group from the spawn index (ignores cooldown). */
    public static Position getIndexedPosition(int groupId) {
        ensureIndex();
        if (index == null) return null;
        for (SpawnEntry e : index) {
            if (e.groupId == groupId && e.position != null) return e.position.clone();
        }
        return null;
    }

    /** Earliest unix-seconds when any group of this investigation leaves 12h lock; 0 if none locked. */
    public static int getEarliestRespawnUnix(int investigationId) {
        ensureIndex();
        ensureKillStoreLoaded();
        long now = System.currentTimeMillis();
        long earliest = Long.MAX_VALUE;
        boolean any = false;
        for (SpawnEntry e : index) {
            if (e.investigationId != investigationId) continue;
            Long killedAt = groupKilledAtMs.get(e.groupId);
            if (killedAt == null) continue;
            long readyAt = killedAt + ELITE_RESPAWN_MS;
            if (readyAt <= now) {
                groupKilledAtMs.remove(e.groupId);
                continue;
            }
            any = true;
            if (readyAt < earliest) earliest = readyAt;
        }
        if (!any) return 0;
        return (int) (earliest / 1000L);
    }

    /** True when every indexed group for this investigation is still on the 12h kill lock. */
    public static boolean areAllGroupsOnCooldown(int investigationId) {
        ensureIndex();
        ensureKillStoreLoaded();
        boolean saw = false;
        long now = System.currentTimeMillis();
        for (SpawnEntry e : index) {
            if (e.investigationId != investigationId) continue;
            saw = true;
            Long killedAt = groupKilledAtMs.get(e.groupId);
            if (killedAt == null) return false;
            if (now - killedAt >= ELITE_RESPAWN_MS) {
                groupKilledAtMs.remove(e.groupId);
                return false;
            }
        }
        return saw;
    }

    public static int getEliteRespawnSeconds() {
        return (int) (ELITE_RESPAWN_MS / 1000L);
    }

    public static Integer getInvestigationIdForGroup(int groupId) {
        ensureIndex();
        return groupToInvestigationId.get(groupId);
    }

    /** True while this investigation group is within the 12h post-kill lock. */
    public static boolean isGroupOnKillCooldown(int groupId) {
        if (groupId <= 0) return false;
        ensureKillStoreLoaded();
        Long killedAt = groupKilledAtMs.get(groupId);
        if (killedAt == null) return false;
        long now = System.currentTimeMillis();
        if (now - killedAt >= ELITE_RESPAWN_MS) {
            groupKilledAtMs.remove(groupId);
            saveKillStore();
            return false;
        }
        return true;
    }

    private static boolean onKillCooldown(SpawnEntry e, long now) {
        if (e == null) return false;
        ensureKillStoreLoaded();
        Long killedAt = groupKilledAtMs.get(e.groupId);
        if (killedAt == null) return false;
        if (now - killedAt >= ELITE_RESPAWN_MS) {
            groupKilledAtMs.remove(e.groupId);
            saveKillStore();
            return false;
        }
        return true;
    }

    private static void ensureIndex() {
        if (index != null) return;
        synchronized (InvestigationSpawnHelper.class) {
            if (index != null) return;
            ensureKillStoreLoaded();
            ArrayList<SpawnEntry> list = new ArrayList<SpawnEntry>();
            Path cfg = Path.of("resources", "ExcelBinOutput", "InvestigationMonsterConfigData.json");
            Path sceneDir = Path.of("resources", "Scripts", "Scene", "3");
            if (!Files.isRegularFile(cfg)) {
                index = List.of();
                return;
            }
            try (BufferedReader br = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                JsonArray arr = JsonParser.parseReader(br).getAsJsonArray();
                Set<Integer> seenGroups = new HashSet<Integer>();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    String cat = o.has("monsterCategory") ? o.get("monsterCategory").getAsString() : "";
                    if (!"Common".equals(cat) && !"Elite".equals(cat)) continue;
                    boolean elite = "Elite".equals(cat);
                    if (!o.has("groupIdList") || !o.has("monsterIdList")) continue;
                    int invId = o.get("id").getAsInt();
                    int cityId = o.has("cityId") ? o.get("cityId").getAsInt() : 1;
                    investigationCityId.put(invId, cityId);
                    JsonArray groups = o.getAsJsonArray("groupIdList");
                    JsonArray monsters = o.getAsJsonArray("monsterIdList");
                    Set<Integer> mids = new HashSet<Integer>();
                    for (JsonElement me : monsters) mids.add(me.getAsInt());
                    for (JsonElement ge : groups) {
                        int gid = ge.getAsInt();
                        if (!seenGroups.add(gid)) continue;
                        Path gp = sceneDir.resolve("scene3_group" + gid + ".lua");
                        if (!Files.isRegularFile(gp)) continue;
                        ParsedMonster pm = parseFirstMonster(gp, mids);
                        if (pm == null) pm = parseFirstMonster(gp, null);
                        if (pm == null) continue;
                        int blockId = (gid / 1000) % 10000;
                        list.add(new SpawnEntry(invId, gid, blockId, pm.monsterId, pm.pos, mids, elite));
                        indexedGroupIds.add(gid);
                        groupToInvestigationId.put(gid, invId);
                    }
                }
            } catch (Exception e) {
                Grasscutter.getLogger().error("InvestigationSpawnHelper index failed", e);
            }
            index = List.copyOf(list);
            Grasscutter.getLogger().info(
                "InvestigationSpawnHelper indexed {} Common/Elite groups with scripts", index.size());
        }
    }

    private static final class ParsedMonster {
        final int monsterId;
        final Position pos;

        ParsedMonster(int monsterId, Position pos) {
            this.monsterId = monsterId;
            this.pos = pos;
        }
    }

    private static ParsedMonster parseFirstMonster(Path groupFile, Set<Integer> allow) {
        try {
            String t = Files.readString(groupFile, StandardCharsets.UTF_8);
            java.util.regex.Pattern p =
                    java.util.regex.Pattern.compile(
                            "monster_id\\s*=\\s*(\\d+)[\\s\\S]*?pos\\s*=\\s*\\{\\s*x\\s*=\\s*([-\\d.]+)\\s*,\\s*y\\s*=\\s*([-\\d.]+)\\s*,\\s*z\\s*=\\s*([-\\d.]+)",
                            java.util.regex.Pattern.MULTILINE);
            java.util.regex.Matcher m = p.matcher(t);
            while (m.find()) {
                int mid = Integer.parseInt(m.group(1));
                if (allow != null && !allow.isEmpty() && !allow.contains(mid)) continue;
                return new ParsedMonster(
                        mid,
                        new Position(
                                Float.parseFloat(m.group(2)),
                                Float.parseFloat(m.group(3)),
                                Float.parseFloat(m.group(4))));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void ensureNearby(Scene scene) {
        if (scene == null || scene.getId() != 3) return;
        if (scene.getPlayers() == null || scene.getPlayers().isEmpty()) return;
        SceneScriptManager sm = scene.getScriptManager();
        if (sm == null || !sm.isInit()) return;
        ensureIndex();
        if (index == null || index.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Player player : scene.getPlayers()) {
            if (player == null || player.getPosition() == null) continue;
            Long last = lastCheckMs.get(player.getUid());
            if (last != null && now - last < CHECK_INTERVAL_MS) continue;
            lastCheckMs.put(player.getUid(), now);

            Position ppos = player.getPosition();
            for (SpawnEntry e : index) {
                try {
                    double d = ppos.computeDistance(e.position);
                    if (d > LEAVE_DIST) continue;
                    if (d > NEAR_DIST) continue;

                    if (hasAliveMonster(scene, e)) {
                        heldGroups.add(e.groupId);
                        forceAlertGroupMonsters(scene, e, player);
                        continue;
                    }

                    // Was held, now dead → start 12h respawn timer (common + elite).
                    if (heldGroups.remove(e.groupId)) {
                        markKilled(e.groupId);
                    }

                    if (onKillCooldown(e, now)) {
                        continue;
                    }

                    Long okAt = lastSpawnOkMs.get(e.groupId);
                    if (okAt != null && now - okAt < ANTI_FLICKER_MS) {
                        continue;
                    }

                    spawnOne(scene, sm, e, player, d, now);
                } catch (Throwable t) {
                    Grasscutter.getLogger()
                            .debug("InvestigationSpawnHelper spawn fail group {}: {}", e.groupId, t.toString());
                }
            }
        }
    }

    private static boolean hasAliveMonster(Scene scene, SpawnEntry e) {
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster)) continue;
            EntityMonster em = (EntityMonster) ge;
            if (em.isAlive() == false) continue;
            if (em.getGroupId() == e.groupId) return true;
            if (em.getMonsterData() != null
                    && em.getMonsterData().getId() == e.monsterId
                    && em.getPosition() != null
                    && em.getPosition().computeDistance(e.position) < 35.0) {
                return true;
            }
        }
        return false;
    }

    private static void forceAlertGroupMonsters(Scene scene, SpawnEntry e, Player player) {
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster)) continue;
            EntityMonster em = (EntityMonster) ge;
            if (!em.isAlive()) continue;
            boolean match =
                    em.getGroupId() == e.groupId
                            || (em.getMonsterData() != null
                                    && em.getMonsterData().getId() == e.monsterId
                                    && em.getPosition() != null
                                    && em.getPosition().computeDistance(e.position) < 35.0);
            if (!match) continue;
            if (em.getPoseId() == 121 || em.getPoseId() == 111 || em.getPoseId() == 0) {
                em.setPoseId(201);
            }
            scene.broadcastPacket(new PacketMonsterForceAlertNotify(em.getId()));
            if (player != null
                    && em.getPlayerOnBattle() != null
                    && !em.getPlayerOnBattle().contains(player)) {
                em.getPlayerOnBattle().add(player);
            }
        }
    }

    private static SceneBlock resolveBlock(Map<Integer, SceneBlock> blocks, SpawnEntry e) {
        SceneBlock block = blocks.get(e.blockId);
        if (block != null) return block;
        for (SceneBlock b : blocks.values()) {
            if (b == null || b.min == null || b.max == null) continue;
            if (e.position.getX() >= b.min.getX()
                    && e.position.getX() <= b.max.getX()
                    && e.position.getZ() >= b.min.getZ()
                    && e.position.getZ() <= b.max.getZ()) {
                return b;
            }
        }
        return blocks.values().iterator().next();
    }

    private static SceneGroup ensureGroupStub(SceneBlock block, SpawnEntry e) {
        if (block.groups == null) {
            block.groups = new HashMap<Integer, SceneGroup>();
        }
        SceneGroup group = block.groups.get(e.groupId);
        if (group == null) {
            group = SceneGroup.of(e.groupId);
            group.block_id = block.id;
            group.pos = e.position.clone();
            group.refresh_id = 1001;
            block.groups.put(e.groupId, group);
        }
        group.dontUnload = true;
        group.dontUnload = true;
        return group;
    }

    private static void spawnOne(
            Scene scene, SceneScriptManager sm, SpawnEntry e, Player player, double dist, long now) {
        Map<Integer, SceneBlock> blocks = sm.getBlocks();
        if (blocks == null || blocks.isEmpty()) return;
        SceneBlock block = resolveBlock(blocks, e);
        if (block == null) return;
        SceneGroup group = ensureGroupStub(block, e);

        Grasscutter.getLogger()
                .info(
                        "InvestigationSpawnHelper uid={} spawn group {} monster {} ({}m)",
                        player.getUid(),
                        e.groupId,
                        e.monsterId,
                        (int) dist);

        try {
            // Clear persisted dead flags so createMonster can succeed after 12h.
            SceneGroupInstance gi = sm.getGroupInstanceById(e.groupId);
            if (gi != null && gi.getDeadEntities() != null) {
                gi.getDeadEntities().clear();
            }

            sm.loadGroupFromScript(group);
            group.dontUnload = true;
            group.dontUnload = true;
            if (group.monsters == null || group.monsters.isEmpty()) {
                Grasscutter.getLogger().warn("InvestigationSpawnHelper group {} empty after load", e.groupId);
                return;
            }
            int spawned = 0;
            for (SceneMonster smon : group.monsters.values()) {
                if (smon == null) continue;
                if (!e.allowedMonsterIds.isEmpty() && !e.allowedMonsterIds.contains(smon.monster_id)) {
                    continue;
                }
                GameEntity existing = scene.getEntityByConfigId(smon.config_id, e.groupId);
                if (existing != null) continue;
                if (smon.pose_id == 121 || smon.pose_id == 111 || smon.pose_id == 0) {
                    smon.pose_id = 201;
                }
                EntityMonster em = sm.createMonster(group.id, block.id, smon);
                if (em != null) {
                    if (em.getPoseId() == 121 || em.getPoseId() == 111 || em.getPoseId() == 0) {
                        em.setPoseId(201);
                    }
                    scene.addEntity(em);
                    scene.broadcastPacket(new PacketMonsterForceAlertNotify(em.getId()));
                    if (em.getPlayerOnBattle() != null && !em.getPlayerOnBattle().contains(player)) {
                        em.getPlayerOnBattle().add(player);
                    }
                    spawned++;
                }
            }
            if (spawned > 0 || hasAliveMonster(scene, e)) {
                lastSpawnOkMs.put(e.groupId, now);
                heldGroups.add(e.groupId);
                groupKilledAtMs.remove(e.groupId);
                saveKillStore();
                Grasscutter.getLogger()
                        .info("InvestigationSpawnHelper group {} ok spawned={} held", e.groupId, spawned);
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().error("InvestigationSpawnHelper direct-spawn failed group " + e.groupId, t);
        }
    }
}
