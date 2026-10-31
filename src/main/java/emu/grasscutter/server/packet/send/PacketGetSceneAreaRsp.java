package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetSceneAreaRspOuterClass.GetSceneAreaRsp;

public class PacketGetSceneAreaRsp extends BasePacket {
    public PacketGetSceneAreaRsp(Player player, int sceneId) {
        super(PacketOpcodes.GetSceneAreaRsp);

        this.buildHeader(0);

        // Map fog is a client presentation setting; never grant area unlocks while replying.
        GetSceneAreaRsp.Builder b =
                GetSceneAreaRsp.newBuilder()
                        .setSceneId(sceneId)
                        .addAllAreaIdList(SceneAreaVisibility.forPlayer(player, sceneId));

        // Cities 1..10 (Mondstadt..Nod-Krai era); SotS trees / statue levels.
        for (int cityId = 1; cityId <= 10; cityId++) {
            try {
                b.addCityInfoList(player.getSotsManager().getCityInfo(cityId).toProto());
            } catch (Throwable ignored) {
            }
        }

        this.setData(b.build());
    }
}
