package emu.grasscutter.game.icecreature;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.SnezhnayaFrigid;
import emu.grasscutter.game.props.LifeState;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.WorldAreaIndex;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.BPAEPJHBOFMOuterClass.BPAEPJHBOFM;
import emu.grasscutter.net.proto.IceCreatureAllGadgetInfoNotifyOuterClass.IceCreatureAllGadgetInfoNotify;
import emu.grasscutter.net.proto.IceCreatureBuildReqOuterClass.IceCreatureBuildReq;
import emu.grasscutter.net.proto.IceCreatureBuildRspOuterClass.IceCreatureBuildRsp;
import emu.grasscutter.net.proto.IceCreatureDataNotifyOuterClass.IceCreatureDataNotify;
import emu.grasscutter.net.proto.IceCreatureDataNotifyOuterClass.IceCreatureTypeGadget;
import emu.grasscutter.net.proto.IceCreatureSetTypeGadgetReqOuterClass.IceCreatureSetTypeGadgetReq;
import emu.grasscutter.net.proto.IceCreatureGadgetDisappearNotifyOuterClass.IceCreatureGadgetDisappearNotify;
import emu.grasscutter.net.proto.IceCreatureGadgetInfoOuterClass.IceCreatureGadgetDetail;
import emu.grasscutter.net.proto.IceCreatureGadgetInfoOuterClass.IceCreatureGadgetInfo;
import emu.grasscutter.net.proto.IceCreatureGadgetInfoOuterClass.IceCreatureLimitType;
import emu.grasscutter.net.proto.IceCreatureGadgetRemoveReqOuterClass.IceCreatureGadgetRemoveReq;
import emu.grasscutter.net.proto.IceCreatureGadgetRemoveRspOuterClass.IceCreatureGadgetRemoveRsp;
import emu.grasscutter.net.proto.IceCreatureLikeReqOuterClass.IceCreatureLikeReq;
import emu.grasscutter.net.proto.IceCreatureLikeRspOuterClass.IceCreatureLikeRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.PacketIceCreature;
import emu.grasscutter.server.packet.send.PacketLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketSceneEntityDisappearNotify;
import emu.grasscutter.utils.Utils;
import java.util.ArrayList;
import java.util.List;

/**
 * Snezhnaya Ice Creatures (冰造物): what a player builds with the Icemaking skill.
 *
 * <p>The client places the creature and asks for it with IceCreatureBuildReq; the server checks the
 * per-area limit and the build material, spawns the gadget and answers with its info. Creatures are
 * saved on the player and stand again whenever the player enters their scene, announced with
 * IceCreatureAllGadgetInfoNotify. Removing one refunds its material (see {@link #materialLeft}).
 */
public final class IceCreatureSystem {
    private static final int OK = Retcode.RET_SUCC_VALUE;
    private static final int FAIL = Retcode.RET_FAIL_VALUE;

    /** Low half of creature GUIDs starts here, clear of the session GUIDs items use. */
    private static final long GUID_BASE = 0x40000000L;

    private IceCreatureSystem() {}

    public static void onBuild(Player player, IceCreatureBuildReq req) {
        var rsp = IceCreatureBuildRsp.newBuilder();
        synchronized (player) {
            build(player, req, rsp);
        }
        player.sendPacket(new PacketIceCreature(PacketOpcodes.IceCreatureBuildRsp, rsp.build()));
        sendData(player);
    }

    private static void build(Player player, IceCreatureBuildReq req, IceCreatureBuildRsp.Builder rsp) {
        var config = IceCreatureConfig.get();
        Scene scene = player.getScene();
        int type = config.typeOf(req.getGadgetId());
        log("build request gadget {} type {} pos {} replace {} values {}",
                req.getGadgetId(), type, req.getPos(), req.getReplaceGuidListList(),
                req.getGlobalValueListList().stream()
                        .map(v -> (v.getKey().hasStr() ? v.getKey().getStr() : String.valueOf(v.getKey().getHash()))
                                + "=" + v.getFloatValue())
                        .toList());
        if (scene == null || type == 0 || !config.isBuildScene(scene.getId())) {
            log("refused build of gadget {} in scene {}: not an Ice Creature or not a build scene",
                    req.getGadgetId(), scene == null ? 0 : scene.getId());
            rsp.setRetcode(FAIL);
            return;
        }

        var records = player.getIceCreatures();
        // The client names creatures it wants replaced when a limit would otherwise refuse this one.
        if (req.getReplaceGuidListCount() > 0) {
            removeRecords(player, records.stream().filter(r -> req.getReplaceGuidListList().contains(r.getGuid())).toList());
        }

        var pos = new Position(req.getPos());
        int area = WorldAreaIndex.forScene(scene.getId()).level1AreaAt(pos.getX(), pos.getZ());
        if (config.isBuildArea(area)) {
            int max = config.maxInArea(area, type);
            long built = records.stream()
                    .filter(r -> r.getSceneId() == scene.getId() && r.getAreaId() == area && r.getType() == type)
                    .count();
            if (built >= max) {
                log("refused build of type {} in area {}: {} of {} built", type, area, built, max);
                rsp.setRetcode(FAIL).setLimitType(IceCreatureLimitType.ICE_CREATURE_LIMIT_TYPE_AREA_LIMIT);
                return;
            }
        }

        int cost = config.costOf(type);
        float left = materialLeft(player);
        if (cost > left) {
            log("refused build of type {}: costs {}, {} material left", type, cost, left);
            rsp.setRetcode(FAIL).setLimitType(IceCreatureLimitType.ICE_CREATURE_LIMIT_TYPE_COST_LIMIT);
            return;
        }

        var record = new IceCreatureRecord();
        record.setGuid(nextGuid(player));
        record.setGadgetId(req.getGadgetId());
        record.setType(type);
        record.setSceneId(scene.getId());
        record.setAreaId(area);
        record.setMapLayerId(req.getMapLayerId());
        record.setPos(pos);
        record.setRot(new Position(req.getRot()));
        record.setCreateTime((int) (System.currentTimeMillis() / 1000));
        record.setCost(cost);
        record.setFlag(req.getUnk11());
        if (req.hasBuildDetail()) record.setBuildDetail(req.getBuildDetail().toByteArray());
        for (var value : req.getGlobalValueListList()) record.getGlobalValues().add(value.toByteArray());

        spawn(scene, record);
        records.add(record);
        SnezhnayaFrigid.sendBuildMaterial(player);
        log("built type {} gadget {} guid {} entity {} in area {} for {} material",
                type, record.getGadgetId(), record.getGuid(), record.getEntityId(), area, cost);
        rsp.setRetcode(OK).setIceCreatureGadgetInfo(info(player, record));
    }

    public static void onRemove(Player player, IceCreatureGadgetRemoveReq req) {
        int retcode;
        synchronized (player) {
            var doomed = player.getIceCreatures().stream()
                    .filter(r -> r.getEntityId() != 0 && req.getEntityIdListList().contains(r.getEntityId()))
                    .toList();
            retcode = doomed.isEmpty() ? FAIL : OK;
            log("remove request entities {} scene {} flag {}: matched {} of {} standing creatures {}",
                    req.getEntityIdListList(), req.getSceneId(), req.getUnk15(), doomed.size(),
                    player.getIceCreatures().size(),
                    player.getIceCreatures().stream().map(r -> r.getGuid() + "@" + r.getEntityId()).toList());
            removeRecords(player, doomed);
        }
        int entityId = req.getEntityIdListCount() == 0 ? 0 : req.getEntityIdList(0);
        player.sendPacket(
                new PacketIceCreature(
                        PacketOpcodes.IceCreatureGadgetRemoveRsp,
                        IceCreatureGadgetRemoveRsp.newBuilder().setRetcode(retcode).setEntityId(entityId).build()));
        sendData(player);
    }

    public static void onLike(Player player, IceCreatureLikeReq req) {
        IceCreatureRecord liked = null;
        var world = player.getWorld();
        var candidates = world == null ? List.of(player) : world.getPlayers();
        for (Player owner : candidates) {
            synchronized (owner) {
                for (var record : owner.getIceCreatures()) {
                    if (record.getGuid() != req.getGuid()) continue;
                    record.setLikeNum(record.getLikeNum() + 1);
                    liked = record;
                }
            }
            if (liked != null) break;
        }
        player.sendPacket(
                new PacketIceCreature(
                        PacketOpcodes.IceCreatureLikeRsp,
                        IceCreatureLikeRsp.newBuilder()
                                .setRetcode(liked == null ? FAIL : OK)
                                .setGuid(req.getGuid())
                                .setLikeNum(liked == null ? 0 : liked.getLikeNum())
                                .build()));
    }

    /** Raises the player's creatures in the scene they just finished entering and lists them. */
    public static void onEnterSceneDone(Player player) {
        Scene scene = player.getScene();
        if (scene == null) return;
        sendData(player);
        var notify = IceCreatureAllGadgetInfoNotify.newBuilder().setUid(player.getUid());
        synchronized (player) {
            for (var record : player.getIceCreatures()) {
                if (record.getSceneId() != scene.getId()) continue;
                if (!(scene.getEntityById(record.getEntityId()) instanceof EntityGadget gadget)
                        || gadget.getGadgetId() != record.getGadgetId()) {
                    spawn(scene, record);
                }
                notify.addIceCreatureGadgetInfoList(info(player, record));
            }
        }
        if (notify.getIceCreatureGadgetInfoListCount() == 0) return;
        log("restored {} creatures in scene {} for {}",
                notify.getIceCreatureGadgetInfoListCount(), scene.getId(), player.getUid());
        player.sendPacket(new PacketIceCreature(PacketOpcodes.IceCreatureAllGadgetInfoNotify, notify.build()));
    }

    /**
     * The player's Ice Creature state (cmd 25312): which creatures the build wheel unlocks, the look
     * each type uses, the creatures they have built and their total likes. Without it the wheel only
     * offers the default creature. Every wheel type is unlocked here.
     */
    public static void sendData(Player player) {
        var config = IceCreatureConfig.get();
        var notify = IceCreatureDataNotify.newBuilder();
        var unlocked = new java.util.TreeSet<Integer>();
        var chosen = player.getIceCreatureTypeGadgets();
        config.getBuildableGadgetsByType().forEach((type, gadgets) -> {
            unlocked.addAll(gadgets);
            int look = chosen.getOrDefault(type, gadgets.get(0));
            notify.addTypeGadgetList(IceCreatureTypeGadget.newBuilder()
                    .setType(type)
                    .setGadgetId(gadgets.contains(look) ? look : gadgets.get(0)));
        });
        notify.addAllUnlockedGadgetIdList(unlocked);
        synchronized (player) {
            for (var record : player.getIceCreatures()) notify.addBuiltGuidList(record.getGuid());
            notify.setTotalLikeNum(totalLikes(player));
        }
        player.sendPacket(new PacketIceCreature(PacketOpcodes.IceCreatureDataNotify, notify.build()));
    }

    /** cmd 29885: the player picked another look for one creature type on the build wheel. */
    public static void onSetTypeGadget(Player player, IceCreatureSetTypeGadgetReq req) {
        var gadgets = IceCreatureConfig.get().getBuildableGadgetsByType().get(req.getType());
        if (gadgets == null || !gadgets.contains(req.getGadgetId())) {
            log("refused look {} for type {}: not one of its gadgets", req.getGadgetId(), req.getType());
        } else {
            synchronized (player) {
                player.getIceCreatureTypeGadgets().put(req.getType(), req.getGadgetId());
            }
            log("type {} now builds as gadget {}", req.getType(), req.getGadgetId());
        }
        sendData(player);
    }

    static int totalLikes(Player player) {
        int likes = 0;
        for (var record : player.getIceCreatures()) likes += record.getLikeNum();
        return likes;
    }

    /**
     * Build material the player has left. Every standing creature holds its cost, so removing one gives
     * the cost back, and the value survives relogs because the creatures do.
     */
    public static float materialLeft(Player player) {
        return materialLeft(IceCreatureConfig.get().getMaterialLimit(), player.getIceCreatures());
    }

    static float materialLeft(float limit, List<IceCreatureRecord> records) {
        float held = 0;
        for (var record : records) held += record.getCost();
        return Math.max(0f, limit - held);
    }

    private static void removeRecords(Player player, List<IceCreatureRecord> doomed) {
        if (doomed.isEmpty()) return;
        var notify = IceCreatureGadgetDisappearNotify.newBuilder()
                .setReason(IceCreatureGadgetDisappearNotify.Reason.REASON_REMOVE);
        for (var record : new ArrayList<>(doomed)) {
            notify.addIceCreatureGadgetInfoList(info(player, record));
            despawn(player, record);
            player.getIceCreatures().remove(record);
            log("removed guid {} (type {}), refunding {}", record.getGuid(), record.getType(), record.getCost());
        }
        SnezhnayaFrigid.sendBuildMaterial(player);
        player.sendPacket(new PacketIceCreature(PacketOpcodes.IceCreatureGadgetDisappearNotify, notify.build()));
    }

    private static void spawn(Scene scene, IceCreatureRecord record) {
        var gadget = new EntityGadget(scene, record.getGadgetId(), record.getPos(), record.getRot());
        for (byte[] raw : record.getGlobalValues()) {
            try {
                var value = AbilityScalarValueEntry.parseFrom(raw);
                var key = value.getKey();
                int hash = key.hasStr() ? Utils.abilityHash(key.getStr()) : key.getHash();
                gadget.getExtraGlobalValues().put(hash, value);
            } catch (InvalidProtocolBufferException e) {
                log("skipped an unreadable global value on guid {}", record.getGuid());
            }
        }
        scene.addEntity(gadget);
        record.setEntityId(gadget.getId());
    }

    private static void despawn(Player player, IceCreatureRecord record) {
        Scene scene = player.getScene();
        if (scene != null && record.getSceneId() == scene.getId()) {
            var entity = scene.getEntityById(record.getEntityId());
            if (entity instanceof EntityGadget gadget) {
                // LIFE_DEAD runs the true-ice onKill dissolve. The client keeps these invincible gadgets
                // standing on a DIE disappear alone, so REMOVE follows once the die time has played.
                scene.broadcastPacket(new PacketLifeStateChangeNotify(gadget, LifeState.LIFE_DEAD));
                scene.removeEntity(gadget, VisionType.VisionType_VISION_DIE);
                int ticks = (int) Math.ceil(dieSeconds(gadget) * 1000d
                        / Math.max(1, Configuration.GAME_INFO.tickRateMs));
                scene.getScheduler().scheduleDelayedTask(
                        () -> scene.broadcastPacket(
                                new PacketSceneEntityDisappearNotify(gadget, VisionType.VisionType_VISION_REMOVE)),
                        Math.max(1, ticks));
            } else if (entity != null) {
                scene.removeEntity(entity, VisionType.VisionType_VISION_REMOVE);
            }
        }
        record.setEntityId(0);
    }

    /** The gadget's {@code combat.die.dieEndTime}, 1s for the 7.1 Ice Creatures. */
    private static double dieSeconds(EntityGadget gadget) {
        var config = gadget.getConfigGadget();
        var combat = config == null ? null : config.getCombat();
        var die = combat == null ? null : combat.getDie();
        return die == null || die.getDieEndTime() <= 0 ? 1d : die.getDieEndTime();
    }

    static IceCreatureGadgetInfo info(Player owner, IceCreatureRecord record) {
        var detail = IceCreatureGadgetDetail.newBuilder();
        if (record.getBuildDetail() != null) {
            try {
                detail.setBuildDetail(BPAEPJHBOFM.parseFrom(record.getBuildDetail()));
            } catch (InvalidProtocolBufferException e) {
                log("skipped an unreadable build detail on guid {}", record.getGuid());
            }
        }
        return IceCreatureGadgetInfo.newBuilder()
                .setGuid(record.getGuid())
                .setGadgetId(record.getGadgetId())
                .setEntityId(record.getEntityId())
                .setSceneId(record.getSceneId())
                .setAreaId(record.getAreaId())
                .setMapLayerId(record.getMapLayerId())
                .setPos(record.getPos().toProto())
                .setCreateTime(record.getCreateTime())
                .setLikeNum(record.getLikeNum())
                .setCreatorUid(owner.getUid())
                .setCreatorNickname(owner.getNickname() == null ? "" : owner.getNickname())
                .setUnk9(totalLikes(owner))
                .setUnk1401(record.isFlag())
                .setDetail(detail)
                .build();
    }

    /**
     * A GUID never handed out before. Taking the next one after the standing creatures reused a removed
     * creature's GUID once all were gone, and the client still tied that GUID to the removed gadget.
     * The clock keeps it moving forward across removals and restarts.
     */
    static long nextGuid(int uid, List<IceCreatureRecord> records, long nowSeconds) {
        long low = GUID_BASE + (nowSeconds & 0x3FFFFFFFL);
        for (var record : records) low = Math.max(low, (record.getGuid() & 0xFFFFFFFFL) + 1);
        return ((long) uid << 32) | (low & 0xFFFFFFFFL);
    }

    private static long nextGuid(Player player) {
        return nextGuid(player.getUid(), player.getIceCreatures(), System.currentTimeMillis() / 1000);
    }

    private static void log(String format, Object... args) {
        Grasscutter.getLogger().debug("[IceCreature] " + format, args);
    }
}
