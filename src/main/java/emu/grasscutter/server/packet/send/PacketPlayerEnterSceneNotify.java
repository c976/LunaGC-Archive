package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.PlayerEnterSceneNotifyOuterClass.PlayerEnterSceneNotify;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.utils.Utils;

public class PacketPlayerEnterSceneNotify extends BasePacket {

    public PacketPlayerEnterSceneNotify(Player player) {
        super(PacketOpcodes.PlayerEnterSceneNotify);

        // Fresh-born 7.1 emits the login scene-entry at the native intro boundary, before the
        // expensive tail of Player.onLogin. When onLogin reaches its normal notify later, keep the
        // original token/state intact and drop that duplicate packet.
        if (BornIntroGate.shouldSuppressLoginSceneEntry(player.getSession())) {
            this.setOpcode(-1);
            return;
        }

        player.setSceneLoadState(SceneLoadState.LOADING);
        player.setEnterSceneToken(Utils.randomRange(1000, 99999));

        long currentTime = System.currentTimeMillis();
        int clientSceneId = DomainDungeonHelper.notifySceneId(player);

        var proto =
                PlayerEnterSceneNotify.newBuilder()
                        .setSceneId((clientSceneId - 49379) ^ 11523)
                        .setPos(player.getPosition().toProto())
                        .setSceneBeginTime((currentTime ^ 27843L) + 16749L)
                        .setType(EnterType.EnterType_ENTER_SELF)
                        .setTargetUid((player.getUid() - 30259) ^ 4145)
                        .setEnterSceneToken((player.getEnterSceneToken() ^ 57361) - 22665)
                        .setWorldLevel((player.getWorldLevel() ^ 31579) + 19873)
                        .setEnterReason((EnterReason.Login.getValue() ^ 43962) + 40350)
                        .setSceneTransaction(
                                clientSceneId
                                        + "-"
                                        + player.getUid()
                                        + "-"
                                        + (int) (System.currentTimeMillis() / 1000)
                                        + "-"
                                        + 18402);

        addSceneTags(proto, player, clientSceneId);
        this.setData(proto);
    }

    public PacketPlayerEnterSceneNotify(
            Player player, EnterType type, EnterReason reason, int newScene, Position newPos) {
        this(player, player, type, reason, newScene, newPos);
    }

    public PacketPlayerEnterSceneNotify(Player player, TeleportProperties teleportProperties) {
        this(player, player, teleportProperties);
    }

    public PacketPlayerEnterSceneNotify(
            Player player,
            Player target,
            EnterType type,
            EnterReason reason,
            int newScene,
            Position newPos) {
        this(
                player,
                target,
                TeleportProperties.builder()
                        .enterType(type)
                        .enterReason(reason)
                        .sceneId(newScene)
                        .teleportTo(newPos)
                        .build());
    }

    public PacketPlayerEnterSceneNotify(
            Player player, Player target, TeleportProperties teleportProperties) {
        super(PacketOpcodes.PlayerEnterSceneNotify);

        player.setSceneLoadState(SceneLoadState.LOADING);
        player.setEnterSceneToken(Utils.randomRange(1000, 99999));

        long currentTime = System.currentTimeMillis();
        int clientSceneId = DomainDungeonHelper.notifySceneId(teleportProperties.getSceneId());

        var proto =
                PlayerEnterSceneNotify.newBuilder()
                        .setSceneId((clientSceneId - 49379) ^ 11523)
                        .setPos(teleportProperties.getTeleportTo().toProto())
                        .setSceneBeginTime((currentTime ^ 27843L) + 16749L)
                        .setType(teleportProperties.getEnterType())
                        .setTargetUid((target.getUid() - 30259) ^ 4145)
                        .setEnterSceneToken((player.getEnterSceneToken() ^ 57361) - 22665)
                        .setWorldLevel((target.getWorld().getWorldLevel() ^ 31579) + 19873)
                        .setSceneTransaction(
                                clientSceneId
                                        + "-"
                                        + target.getUid()
                                        + "-"
                                        + (int) (System.currentTimeMillis() / 1000)
                                        + "-"
                                        + 18402);

        if (teleportProperties.getEnterReason() != null) {
            proto.setEnterReason((teleportProperties.getEnterReason().getValue() ^ 43962) + 40350);
        }

        if (teleportProperties.getDungeonId() != 0) {
            proto.setDungeonId((teleportProperties.getDungeonId() ^ 27544) - 17829);
        }

        addSceneTags(proto, target, clientSceneId);
        this.setData(proto);
    }

    public PacketPlayerEnterSceneNotify(
            Player player, int targetUid, TeleportProperties teleportProperties, boolean other) {
        super(PacketOpcodes.PlayerEnterSceneNotify);

        player.setSceneLoadState(SceneLoadState.LOADING);
        player.setEnterSceneToken(Utils.randomRange(1000, 99999));

        long currentTime = System.currentTimeMillis();
        int clientSceneId = DomainDungeonHelper.notifySceneId(teleportProperties.getSceneId());

        var proto =
                PlayerEnterSceneNotify.newBuilder()
                        .setSceneId((clientSceneId - 49379) ^ 11523)
                        .setPos(teleportProperties.getTeleportTo().toProto())
                        .setSceneBeginTime((currentTime ^ 27843L) + 16749L)
                        .setType(
                                other
                                        ? EnterType.EnterType_ENTER_OTHER_HOME
                                        : EnterType.EnterType_ENTER_SELF_HOME)
                        .setTargetUid((targetUid - 30259) ^ 4145)
                        .setEnterSceneToken((player.getEnterSceneToken() ^ 57361) - 22665)
                        .setSceneTransaction(
                                clientSceneId
                                        + "-"
                                        + targetUid
                                        + "-"
                                        + (int) (System.currentTimeMillis() / 1000)
                                        + "-"
                                        + 105092);

        addSceneTags(proto, player, clientSceneId);
        this.setData(proto);
    }

    private static void addSceneTags(
            PlayerEnterSceneNotify.Builder proto, Player owner, int clientSceneId) {
        var world = owner.getWorld();
        var host = world != null ? world.getHost() : null;
        var tagOwner = host != null ? host : owner;
        var tags = tagOwner.getSceneTags().getOrDefault(clientSceneId, java.util.Set.of());
        proto.addAllSceneTagIdList(tags);
    }
}
