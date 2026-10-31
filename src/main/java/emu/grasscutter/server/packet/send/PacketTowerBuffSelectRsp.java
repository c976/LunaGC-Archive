package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerBuffSelectRspOuterClass.TowerBuffSelectRsp;

public class PacketTowerBuffSelectRsp extends BasePacket {

    public PacketTowerBuffSelectRsp(int towerBuffId) {
        super(PacketOpcodes.TowerBuffSelectRsp);

        TowerBuffSelectRsp proto =
                TowerBuffSelectRsp.newBuilder().setTowerBuffId(towerBuffId).setRetcode(0).build();

        this.setData(proto);
    }
}
