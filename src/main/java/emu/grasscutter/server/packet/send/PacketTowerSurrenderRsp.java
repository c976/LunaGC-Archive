package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerSurrenderRspOuterClass.TowerSurrenderRsp;

public class PacketTowerSurrenderRsp extends BasePacket {

    public PacketTowerSurrenderRsp() {
        this(0, 0);
    }

    public PacketTowerSurrenderRsp(int retcode) {
        this(retcode, 0);
    }

    public PacketTowerSurrenderRsp(int retcode, int clientSequence) {
        // Echo client_sequence_id so the confirm dialog matches this Rsp and can open team UI.
        super(PacketOpcodes.TowerSurrenderRsp, clientSequence);
        setData(TowerSurrenderRsp.newBuilder().setRetcode(retcode).build());
    }
}
