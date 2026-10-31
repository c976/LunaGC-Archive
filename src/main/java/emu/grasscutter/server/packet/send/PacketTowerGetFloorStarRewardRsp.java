package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerGetFloorStarRewardRspOuterClass.TowerGetFloorStarRewardRsp;

public class PacketTowerGetFloorStarRewardRsp extends BasePacket {

    public PacketTowerGetFloorStarRewardRsp(int floorId, int retcode) {
        super(PacketOpcodes.TowerGetFloorStarRewardRsp);

        this.setData(
                TowerGetFloorStarRewardRsp.newBuilder()
                        .setFloorId(floorId)
                        .setRetcode(retcode)
                        .build());
    }
}
