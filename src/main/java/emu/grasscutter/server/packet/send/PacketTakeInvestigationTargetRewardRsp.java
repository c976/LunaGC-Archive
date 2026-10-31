package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TakeInvestigationTargetRewardRspOuterClass.TakeInvestigationTargetRewardRsp;

public class PacketTakeInvestigationTargetRewardRsp extends BasePacket {
    public PacketTakeInvestigationTargetRewardRsp(int questId, int retcode) {
        super(PacketOpcodes.TakeInvestigationTargetRewardRsp);
        this.setData(
                TakeInvestigationTargetRewardRsp.newBuilder().setQuestId(questId).setRetcode(retcode).build());
    }
}
