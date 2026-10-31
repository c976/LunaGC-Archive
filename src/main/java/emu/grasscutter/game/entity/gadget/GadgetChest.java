package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityBaseGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.gadget.chest.BossChestInteractHandler;
import emu.grasscutter.game.entity.gadget.chest.ChestInteractHandler;
import emu.grasscutter.game.entity.gadget.chest.ChestTypeResolver;
import emu.grasscutter.game.entity.gadget.chest.WorldChestLootHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.world.NodKraiExploreSpawnHelper;
import emu.grasscutter.game.world.WorldBossSpawnHelper;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.BossChestInfoOuterClass;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass;
import emu.grasscutter.net.proto.InterOpTypeOuterClass;
import emu.grasscutter.net.proto.InteractTypeOuterClass;
import emu.grasscutter.net.proto.ResinCostTypeOuterClass;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass;
import emu.grasscutter.net.proto.VisionTypeOuterClass;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneBossChest;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.packet.send.PacketGadgetAutoPickDropInfoNotify;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import emu.grasscutter.server.packet.send.PacketWorldChestOpenNotify;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World chests and world-boss trounce blossoms. Boss rewards must come from
 * an authoritative drop table; reward previews are never grant data.
 */
public class GadgetChest extends GadgetContent {
    private static final int CHEST_OPENED_STATE = 102;
    private boolean ordinaryClaimed;
    private final String ordinarySpawnId = UUID.randomUUID().toString();
    // Boss flowers: every player near the flower when it first appears claims once.
    private volatile List<Integer> bossUids;
    /**
     * Who counts as having fought for a boss flower. The whole scene is the whole open world, and a
     * player elsewhere on the map who never claims would keep the flower open and the boss from
     * respawning.
     */
    private static final double BOSS_CLAIM_RANGE = 100;
    private final Set<Integer> claimedBossUids = ConcurrentHashMap.newKeySet();
    // One durable receipt identity per flower spawn; rotate only after proven unpaid attempts.
    private String bossClaimOperationId = UUID.randomUUID().toString();

    public GadgetChest(EntityGadget gadget) {
        super(gadget);
    }

    @Override
    public synchronized boolean onInteract(
            Player player, GadgetInteractReqOuterClass.GadgetInteractReq req) {
        var blossomManager = getGadget().getScene().getBlossomManager();
        if (blossomManager != null && blossomManager.hasRewardChest(getGadget())) {
            SceneGadget meta = resolveChestMeta();
            if (meta == null || meta.boss_chest == null
                    || finishIfAbandoned(player, meta) || !canClaimBossFlower(player)) {
                return false;
            }
            if (req.getOpType() == InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START) {
                player.sendPacket(
                        new PacketGadgetInteractRsp(
                                getGadget(),
                                InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                                InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START));
                return false;
            }
            var rewards = blossomManager.claimReward(player, getGadget(),
                    req, () -> claimedBossUids.add(player.getUid()));
            if (rewards != null) {
                finishBossClaim(player, meta);
                player.sendPacket(new PacketGadgetAutoPickDropInfoNotify(rewards));
            }
            return false;
        }
        // World-boss flowers have exactly one claim route, independent of script mode.
        // Weekly dungeon reward statues have their own native claim flow.
        SceneGadget bossMeta = resolveChestMeta();
        if (bossMeta != null && bossMeta.boss_chest != null
                && getGadget().getScene().getSceneType() != SceneType.SCENE_DUNGEON) {
            if (finishIfAbandoned(player, bossMeta) || !canClaimBossFlower(player)) {
                return false;
            }
            if (req.getOpType() == InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START) {
                player.sendPacket(
                        new PacketGadgetInteractRsp(
                                getGadget(),
                                InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                                InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START));
                return false;
            }
            int cost = bossMeta.boss_chest.resin;
            if (cost <= 0) {
                throw new IllegalStateException(
                        "World boss flower has no native resin cost: " + getGadget().getGroupId());
            }
            boolean granted;
            try {
                granted = grantWorldBossReward(player, bossMeta, cost);
            } catch (RuntimeException e) {
                Grasscutter.getLogger().error(
                        "World boss reward failed group={} uid={} tag={}",
                        getGadget().getGroupId(), player.getUid(), bossMeta.drop_tag, e);
                return false;
            }
            if (!granted) return false;
            // Battle-pass bookkeeping must not turn an already-paid claim into a retry.
            try {
                var battlePass = player.getBattlePassManager();
                if (battlePass != null) {
                    battlePass.triggerMission(
                            WatcherTriggerType.TRIGGER_WORLD_BOSS_REWARD,
                            bossMeta.boss_chest.monster_config_id, 1);
                }
            } catch (RuntimeException e) {
                Grasscutter.getLogger().warn(
                        "World boss battle-pass bookkeeping failed uid={}", player.getUid(), e);
            }
            finishBossClaim(player, bossMeta);
            return false;
        }
        if (Grasscutter.getConfig().server.game.enableScriptInBigWorld) {
            SceneGadget meta = resolveChestMeta();
            DropSystem dropSystem = player.getServer().getDropSystem();
            if (!canOpenOrdinary(player)) {
                return false;
            }
            if (req.getOpType() == InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START) {
                player.sendPacket(
                        new PacketGadgetInteractRsp(
                                getGadget(),
                                InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                                InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START));
                return false;
            }
            if (!grantOrdinaryChestLoot(player)) return false;
            finishOpen(player, meta);
            return false;
        }

        ChestInteractHandler handler =
                getGadget()
                        .getScene()
                        .getWorld()
                        .getServer()
                        .getWorldDataSystem()
                        .getChestInteractHandlerMap()
                        .get(getGadget().getGadgetData().getJsonName());
        if (handler == null) {
            if (!canOpenOrdinary(player)) {
                return false;
            }
            if (req.getOpType() == InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START) {
                player.sendPacket(
                        new PacketGadgetInteractRsp(
                                getGadget(),
                                InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                                InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START));
                return false;
            }
            if (!grantOrdinaryChestLoot(player)) return false;
            finishOpen(player, null);
            return false;
        }

        if (!(handler instanceof BossChestInteractHandler) && !canOpenOrdinary(player)) {
            return false;
        }
        if (req.getOpType() == InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START
                && handler.isTwoStep()) {
            player.sendPacket(
                    new PacketGadgetInteractRsp(
                            getGadget(),
                            InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                            InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_START));
            return false;
        }

        boolean success;
        if (handler instanceof BossChestInteractHandler bossHandler) {
            var gadgetMeta = getGadget().getMetaGadget();
            boolean bossChestPresent = gadgetMeta != null && gadgetMeta.boss_chest != null;
            if (bossChestPresent && finishIfAbandoned(player, gadgetMeta)) {
                return false;
            }
            if (bossChestPresent && !canClaimBossFlower(player)) {
                return false;
            }
            success =
                    bossHandler.onInteract(
                            this,
                            player,
                            req.getResinCostType()
                                    == ResinCostTypeOuterClass.ResinCostType.ResinCostType_CONDENSE);
        } else {
            success = grantOrdinaryChestLoot(player);
        }
        if (!success) {
            return false;
        }
        if (getGadget().getMetaGadget() != null && getGadget().getMetaGadget().boss_chest != null) {
            finishBossClaim(player, getGadget().getMetaGadget());
        } else {
            finishOpen(player, null);
        }
        return false;
    }

    /** Ordinary chests open once, for the world host. */
    private boolean canOpenOrdinary(Player player) {
        return player == player.getWorld().getHost()
                && !ordinaryClaimed
                && getGadget().getState() != CHEST_OPENED_STATE;
    }

    private boolean canClaimBossFlower(Player player) {
        List<Integer> uids = captureBossClaimants();
        return getGadget().getState() != CHEST_OPENED_STATE
                && uids != null
                && uids.contains(player.getUid())
                && !claimedBossUids.contains(player.getUid());
    }

    /** A player who already claimed re-checks whether the others have left, without new loot. */
    private boolean finishIfAbandoned(Player player, SceneGadget meta) {
        if (getGadget().getState() == CHEST_OPENED_STATE
                || bossUids == null
                || !bossUids.contains(player.getUid())
                || !claimedBossUids.contains(player.getUid())) {
            return false;
        }
        if (!finishIfAllClaimed(player, meta, false)) {
            // Others still have to claim; still answer, or the client's interaction never completes.
            player.sendPacket(
                    new PacketGadgetInteractRsp(
                            getGadget(),
                            InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                            InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_FINISH));
        }
        return true;
    }

    private void finishBossClaim(Player player, SceneGadget meta) {
        claimedBossUids.add(player.getUid());
        if (finishIfAllClaimed(player, meta, true)) {
            return;
        }
        player.sendPacket(
                new PacketGadgetInteractRsp(
                        getGadget(),
                        InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                        InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_FINISH));
        // Refresh the flower for the claimer: its remaining-claimant list no longer has them, and a
        // stale copy keeps offering a claim that would be refused.
        player.sendPacket(
                new PacketSceneEntityAppearNotify(
                        getGadget(), VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH, 0));
        creditChestOpen(player);
    }

    /** Opens the flower for good once every eligible player still in the scene has claimed. */
    private boolean finishIfAllClaimed(Player player, SceneGadget meta, boolean credit) {
        boolean allClaimed =
                getGadget().getScene().getPlayers().stream()
                        .map(Player::getUid)
                        .filter(bossUids::contains)
                        .allMatch(claimedBossUids::contains);
        if (allClaimed) {
            if (getGadget().getScene().getSceneType() == SceneType.SCENE_WORLD) {
                getGadget().getScene().getBlossomManager().retireChest(getGadget());
            }
            finishOpen(player, meta, credit);
            WorldBossSpawnHelper.onBossChestClaimed(getGadget());
        }
        return allClaimed;
    }

    /**
     * Whether the group's own chest_drop_id decides the loot. Configured chest rules cover only
     * the chest kinds ChestTypeResolver knows; anything else with a native drop table (investigation
     * points, "SearchPoint" 70210101/70210102) used to throw and leave the F prompt dead.
     */
    static boolean usesNativeDrop(
            SceneType sceneType, int groupId, boolean questItemDrop, String chestType) {
        return sceneType == SceneType.SCENE_DUNGEON
                || groupId == 0
                || questItemDrop
                || chestType == null;
    }

    private boolean grantOrdinaryChestLoot(Player player) {
        EntityGadget gadget = getGadget();
        SceneGadget meta = resolveChestMeta();
        if (meta != null && meta.chest_drop_id > 0) {
            DropSystem dropSystem = player.getServer().getDropSystem();
            // Quest-created chests have no Lua group; scripted quest chests are identified by loot.
            if (usesNativeDrop(
                            gadget.getScene().getSceneType(),
                            gadget.getGroupId(),
                            dropSystem.hasQuestItemDrop(meta.chest_drop_id),
                            ChestTypeResolver.resolve(gadget))
                    && dropSystem.handleChestDrop(
                            meta.chest_drop_id, Math.max(1, meta.drop_count), gadget)) {
                ordinaryClaimed = true;
                return true;
            }
        }
        boolean permanent = ChestTypeResolver.resolve(gadget) != null
                || (meta != null && meta.isOneoff);
        String identity = permanent && gadget.getGroupId() > 0 && gadget.getConfigId() > 0
                ? gadget.getScene().getId() + "." + gadget.getGroupId() + "." + gadget.getConfigId()
                : "spawn." + ordinarySpawnId;
        return WorldChestLootHelper.grant(player, gadget, "chest." + identity,
                () -> ordinaryClaimed = true);
    }

    /** Roll first, validate the whole reward batch, then charge once and grant. */
    protected boolean grantWorldBossReward(Player player, SceneGadget meta, int resinCost) {
        // Some spawned boss flowers omit drop_tag even though their native group
        // has a verified mapping in boss-drop-tags.json.
        String tag = meta.drop_tag;
        if (tag == null || tag.isBlank()) {
            tag = emu.grasscutter.game.drop.BossChestDropTagResolver.resolve(
                    getGadget().getGroupId(),
                    meta.boss_chest == null ? 0 : meta.boss_chest.monster_config_id);
        }
        if (tag == null || tag.isBlank()) {
            throw new IllegalStateException(
                    "World boss chest has no source-backed drop identity: " + getGadget().getGroupId());
        }
        if (resinCost <= 0) {
            throw new IllegalArgumentException("World boss has no payable resin cost");
        }
        List<GameItem> items = player.getServer().getDropSystem()
                .rollBossChestDrop(tag, player.getWorldLevel());
        // Like dungeon claims, persist intent before charging. A paid but interrupted
        // grant is never retried blindly: the receipt remains for reconciliation.
        var journal = new RewardGrantJournal();
        var receipt = new RewardGrantJournal.Key(
                RewardGrantJournal.Source.WORLD_BOSS, player.getUid(),
                "flower." + getGadget().getScene().getId() + "." + getGadget().getGroupId()
                        + "." + bossClaimOperationId);
        var intended = items.stream().map(item -> new RewardGrantJournal.Reward(
                item.getItemId(), item.getCount(), item.getLevel())).toList();
        var debits = Configuration.GAME_OPTIONS.resinOptions.resinUsage
                ? List.of(new RewardGrantJournal.Debit(106, resinCost)) : List.<RewardGrantJournal.Debit>of();
        var result = player.getInventory().addItems(
                items, ActionReason.OpenWorldBossChest,
                emu.grasscutter.game.inventory.InventoryAddPolicy.ALL_OR_NOTHING,
                () -> {
                    if (!canClaimBossFlower(player)
                            || (Configuration.GAME_OPTIONS.resinOptions.resinUsage
                                    && player.getProperty(PlayerProperty.PROP_PLAYER_RESIN) < resinCost)
                            || !journal.prepare(receipt, intended, debits)) {
                        return false;
                    }
                    if (player.getResinManager().useResin(resinCost)) {
                        return true;
                    }
                    // useResin returning false proves that no charge took place.
                    journal.abortUnpaid(receipt);
                    bossClaimOperationId = UUID.randomUUID().toString();
                    return false;
                },
                () -> {
                    // This is before inventory writes, and cannot be undone safely.
                    claimedBossUids.add(player.getUid());
                    journal.confirm(receipt);
                });
        if (!result.allAccepted()) {
            Grasscutter.getLogger().warn(
                    "World boss reward refused group={} tag={} uid={} statuses={}",
                    getGadget().getGroupId(), meta.drop_tag, player.getUid(),
                    result.entries().stream().map(entry -> entry.status()).toList());
            return false;
        }
        try {
            journal.complete(receipt);
        } catch (RuntimeException e) {
            Grasscutter.getLogger().error(
                    "World boss delivered but receipt remains unresolved uid={} receipt={}",
                    player.getUid(), receipt.storageId(), e);
        }
        player.sendPacket(new PacketGadgetAutoPickDropInfoNotify(items));
        return true;
    }

    private void finishOpen(Player player, SceneGadget sceneGadget) {
        finishOpen(player, sceneGadget, true);
    }

    private void finishOpen(Player player, SceneGadget sceneGadget, boolean credit) {
        EntityGadget entityGadget = getGadget();
        // Boss flowers are repeatable encounters. Ordinary authored chests and pickups
        // are consumed independently of the Lua oneoff/persistent flag combination.
        if (entityGadget.getScene().getSceneType() == SceneType.SCENE_WORLD
                && (sceneGadget == null || sceneGadget.boss_chest == null)
                && (ChestTypeResolver.resolve(entityGadget) != null
                        || (sceneGadget != null && sceneGadget.isOneoff))
                && entityGadget.getGroupId() > 0 && entityGadget.getConfigId() > 0) {
            var instance = entityGadget.getScene().getScriptManager()
                    .getGroupInstanceById(entityGadget.getGroupId());
            if (instance != null) instance.markGadgetConsumed(entityGadget.getConfigId());
        }
        entityGadget.updateState(CHEST_OPENED_STATE);
        player.sendPacket(
                new PacketGadgetInteractRsp(
                        (EntityBaseGadget) entityGadget,
                        InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_CHEST,
                        InterOpTypeOuterClass.InterOpType.InterOpType_INTER_OP_FINISH));
        int configId = sceneGadget != null ? sceneGadget.config_id : entityGadget.getConfigId();
        try {
            player.sendPacket(
                    new PacketWorldChestOpenNotify(
                            entityGadget.getGroupId(), player.getSceneId(), configId));
        } catch (Throwable ignored) {
        }
        scheduleDespawn(entityGadget);
        try {
            NodKraiExploreSpawnHelper.markClaimed(getGadget());
        } catch (Throwable ignored) {
        }
        try {
            emu.grasscutter.game.world.SnezhnayaExploreSpawnHelper.markClaimed(getGadget());
        } catch (Throwable ignored) {
        }
        if (credit) {
            if (!ChestTypeResolver.isNativeInteract(entityGadget)) {
                creditChestOpen(player);
            }
        }
    }

    private void creditChestOpen(Player player) {
        try {
            int gadgetId = getGadget().getGadgetId();
            player.getPlayerProgress().addHandbookChestOpen(1);
            emu.grasscutter.game.player.InvestigationHandbookHelper.trigger(
                    player,
                    emu.grasscutter.game.props.WatcherTriggerType.TRIGGER_OPEN_CHEST_WITH_GADGET_ID,
                    gadgetId,
                    1);
        } catch (Throwable ignored) {
        }
    }

    private void scheduleDespawn(EntityGadget entityGadget) {
        try {
            if (entityGadget == null || entityGadget.getScene() == null) {
                return;
            }
            // Boss trounce flowers: remove immediately with VISION_REMOVE so they do not linger
            // as opened/smoke visuals while the world boss already respawned.
            boolean bossFlower =
                    entityGadget.getMetaGadget() != null
                            && entityGadget.getMetaGadget().boss_chest != null;
            // Scene scheduler ticks once per scene tick (~1s); 2 ≈ brief open animation then vanish.
            int delayTicks = bossFlower ? 0 : 2;
            int entityId = entityGadget.getId();
            Runnable despawn =
                    () -> {
                        try {
                            GameEntity entity = entityGadget.getScene().getEntityById(entityId);
                            if (entity instanceof EntityGadget) {
                                entityGadget
                                        .getScene()
                                        .removeEntity(
                                                entity,
                                                VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                            }
                        } catch (Throwable t) {
                            Grasscutter.getLogger().debug("Chest delayed despawn: {}", t.toString());
                        }
                    };
            if (delayTicks <= 0) {
                despawn.run();
            } else {
                entityGadget.getScene().getScheduler().scheduleDelayedTask(despawn, delayTicks);
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("Chest scheduleDespawn failed: {}", t.toString());
        }
    }

    @Override
    public void onBuildProto(SceneGadgetInfoOuterClass.SceneGadgetInfo.Builder builder) {
        SceneGadget sceneGadget = resolveChestMeta();
        if (sceneGadget == null) {
            return;
        }
        SceneBossChest bossChest = sceneGadget.boss_chest;
        if (bossChest != null) {
            List<Integer> uids = captureBossClaimants();
            builder.setBossChest(
                    BossChestInfoOuterClass.BossChestInfo.newBuilder()
                            .setMonsterConfigId(bossChest.monster_config_id)
                            .setResin(bossChest.resin)
                            .addAllQualifyUidList(uids)
                            .addAllRemainUidList(
                                    uids.stream().filter(uid -> !claimedBossUids.contains(uid)).toList())
                            .build());
        }
    }

    /** Capture the players near an unclaimed flower, including after an initially empty reveal. */
    private List<Integer> captureBossClaimants() {
        List<Integer> existing = bossUids;
        if (existing != null) {
            return existing;
        }
        var flower = getGadget().getPosition();
        List<Integer> present =
                getGadget().getScene().getPlayers().stream()
                        .filter(p -> flower == null
                                || (p.getPosition() != null
                                        && p.getPosition().computeDistance(flower) <= BOSS_CLAIM_RANGE))
                        .map(Player::getUid)
                        .toList();
        if (!present.isEmpty()) {
            bossUids = present;
        }
        return present;
    }

    private SceneGadget resolveChestMeta() {
        EntityGadget entityGadget = getGadget();
        SceneGadget meta = entityGadget.getMetaGadget();
        if (meta != null && meta.boss_chest != null && meta.drop_tag != null) {
            return meta;
        }
        if (entityGadget.getScene() == null) {
            return meta;
        }
        SceneScriptManager scriptManager = entityGadget.getScene().getScriptManager();
        if (scriptManager == null) {
            return meta;
        }
        SceneGroup group = scriptManager.getGroupById(entityGadget.getGroupId());
        if (group == null) {
            return meta;
        }
        if (group.gadgets == null || group.gadgets.isEmpty()) {
            try {
                scriptManager.loadGroupFromScript(group);
            } catch (Throwable ignored) {
            }
            group = scriptManager.getGroupById(entityGadget.getGroupId());
            if (group == null || group.gadgets == null) {
                return meta;
            }
        }
        SceneGadget fromGroup = group.gadgets.get(entityGadget.getConfigId());
        if (fromGroup == null) {
            return meta;
        }
        fromGroup.group = group;
        entityGadget.setMetaGadget(fromGroup);
        return fromGroup;
    }
}
