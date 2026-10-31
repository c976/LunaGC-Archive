package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardRspOuterClass.AvatarExpeditionGetRewardRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.AvatarExpeditionBasicInfo._AvatarExpeditionBasicInfo;
import emu.grasscutter.net.proto.AvatarExpeditionRewardInfo._AvatarExpeditionRewardInfo;
import java.util.*;

public class PacketAvatarExpeditionGetRewardRsp extends BasePacket {
    public PacketAvatarExpeditionGetRewardRsp(
            Map<Long, ExpeditionInfo> expeditionInfo,
            Collection<_AvatarExpeditionRewardInfo> rewardInfos) {
        super(PacketOpcodes.AvatarExpeditionGetRewardRsp);

        AvatarExpeditionGetRewardRsp.Builder proto = AvatarExpeditionGetRewardRsp.newBuilder();
        expeditionInfo.forEach((key, e) -> proto.putExpeditionInfoMap(key, e.toProto()));
        if (rewardInfos != null) {
            proto.addAllExpeditionRewardList(rewardInfos);
        }
        this.setData(proto.build());
    }

    public static _AvatarExpeditionRewardInfo toRewardInfo(
            long avatarGuid, ExpeditionInfo expInfo, Collection<GameItem> items) {
        _AvatarExpeditionRewardInfo.Builder b = _AvatarExpeditionRewardInfo.newBuilder();
        if (expInfo != null) {
            b.setBasicInfo(
                    _AvatarExpeditionBasicInfo.newBuilder()
                            .setAvatarGuid(avatarGuid)
                            .setExpId(expInfo.getExpId())
                            .setHourTime(expInfo.getHourTime())
                            .build());
        } else {
            b.setBasicInfo(_AvatarExpeditionBasicInfo.newBuilder().setAvatarGuid(avatarGuid).build());
        }
        if (items != null) {
            for (GameItem item : items) {
                b.addItemList(
                        ItemParam.newBuilder()
                                .setItemId(item.getItemId())
                                .setCount(item.getCount())
                                .build());
            }
        }
        return b.build();
    }
}
