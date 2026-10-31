package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ScenePointUnlockNotifyOuterClass.ScenePointUnlockNotify;

public class PacketScenePointUnlockNotify extends BasePacket {
    public PacketScenePointUnlockNotify(int sceneId, int pointId) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        // Official global 7.1 semantics: scene_id = field 2, locked_point_list = field 4,
        // hide_point_list = field 6, unhide_point_list = field 8, point_list = field 15.
        // The generated API misnames wire field 4 as unhidePointList, so normal unlocks must only
        // populate scene_id + point_list or the client receives an unlock+lock contradiction.
        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addPointList(pointId);

        this.setData(p);
    }

    /**
     * Relock points and hide them again.
     *
     * <p>The generated {@code unhidePointList} accessor is wire field 4, which the official 7.1
     * client treats as {@code locked_point_list}. Field 6 is {@code hide_point_list}.
     */
    public static PacketScenePointUnlockNotify lock(int sceneId, int pointId) {
        return lock(sceneId, java.util.List.of(pointId));
    }

    public static PacketScenePointUnlockNotify lock(int sceneId, Iterable<Integer> pointIds) {
        PacketScenePointUnlockNotify packet = new PacketScenePointUnlockNotify();

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addAllUnhidePointList(pointIds)
                        .addAllHidePointList(pointIds);

        packet.setData(p);
        return packet;
    }

    private PacketScenePointUnlockNotify() {
        super(PacketOpcodes.ScenePointUnlockNotify);
    }

    public PacketScenePointUnlockNotify(int sceneId, Iterable<Integer> pointIds) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addAllPointList(pointIds);

        this.setData(p);
    }
}
