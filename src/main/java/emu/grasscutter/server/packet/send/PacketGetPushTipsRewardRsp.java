package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPushTipsRewardRspOuterClass.GetPushTipsRewardRsp;
import java.util.List;

public class PacketGetPushTipsRewardRsp extends BasePacket {
    public PacketGetPushTipsRewardRsp(int retcode, List<Integer> tipIds) {
        super(PacketOpcodes.GetPushTipsRewardRsp);

        this.setData(
                GetPushTipsRewardRsp.newBuilder().setRetcode(retcode).addAllPushTipsIdList(tipIds).build());
    }
}
