package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TakeInvestigationRewardRspOuterClass.TakeInvestigationRewardRsp;

public class PacketTakeInvestigationRewardRsp extends BasePacket {
    public PacketTakeInvestigationRewardRsp(int chapterId, int retcode) {
        super(PacketOpcodes.TakeInvestigationRewardRsp);
        this.setData(TakeInvestigationRewardRsp.newBuilder().setId(chapterId).setRetcode(retcode).build());
    }
}
