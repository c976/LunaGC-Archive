package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.MoveTeamRsp._MoveTeamRsp;
import java.util.List;

/** MoveTeamRsp. 7.1's response carries no order list, so the order is not echoed any more. */
public class PacketMoveTeamRsp extends BasePacket {
    public PacketMoveTeamRsp() {
        this(0);
    }

    public PacketMoveTeamRsp(List<Integer> order) {
        this(0);
    }

    public PacketMoveTeamRsp(int retcode) {
        super(PacketOpcodes._MoveTeamRsp);
        this.setData(_MoveTeamRsp.newBuilder().setRetcode(retcode).build());
    }
}
