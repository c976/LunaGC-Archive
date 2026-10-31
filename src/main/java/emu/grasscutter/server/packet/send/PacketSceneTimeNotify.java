package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneTimeNotifyOuterClass.SceneTimeNotify;

public class PacketSceneTimeNotify extends BasePacket {

    public PacketSceneTimeNotify(Player player) {
        this(player.getScene());
    }

    public PacketSceneTimeNotify(Scene scene) {
        super(PacketOpcodes.SceneTimeNotify);

        var proto =
                SceneTimeNotify.newBuilder()
                        .setSceneId(DomainDungeonHelper.notifySceneId(scene.getId()))
                        .setSceneTime(scene.getSceneTime())
                        .setIsPaused(scene.isPaused())
                        .build();

        this.setData(proto);
    }
}
