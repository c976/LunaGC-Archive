package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import java.util.List;

public class PacketTakeAchievementRewardRsp extends BasePacket {
    public PacketTakeAchievementRewardRsp() {
        this(RetcodeOuterClass.Retcode.RET_REWARD_HAS_TAKEN_VALUE);
    }

    public PacketTakeAchievementRewardRsp(int retcode) {
        super(PacketOpcodes.TakeAchievementRewardRsp);
        this.setData(
                TakeAchievementRewardRspOuterClass.TakeAchievementRewardRsp.newBuilder()
                        .setRetcode(retcode)
                        .build());
    }

    public PacketTakeAchievementRewardRsp(
            List<Integer> idList, List<ItemParamOuterClass.ItemParam> items) {
        super(PacketOpcodes.TakeAchievementRewardRsp);

        var rsp =
                TakeAchievementRewardRspOuterClass.TakeAchievementRewardRsp.newBuilder()
                        .addAllIdList(idList)
                        .addAllItemList(items)
                        .build();

        this.setData(rsp);
    }
}
