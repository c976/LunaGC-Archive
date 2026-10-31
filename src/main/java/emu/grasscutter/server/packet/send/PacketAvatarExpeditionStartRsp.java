package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionStartRspOuterClass.AvatarExpeditionStartRsp;
import emu.grasscutter.net.proto.AvatarExpeditionBasicInfo._AvatarExpeditionBasicInfo;
import java.util.Collection;
import java.util.Map;

public class PacketAvatarExpeditionStartRsp extends BasePacket {
    public PacketAvatarExpeditionStartRsp(
            Map<Long, ExpeditionInfo> expeditionInfo,
            Collection<_AvatarExpeditionBasicInfo> startedBasicInfo) {
        super(PacketOpcodes.AvatarExpeditionStartRsp);

        AvatarExpeditionStartRsp.Builder proto = AvatarExpeditionStartRsp.newBuilder();
        expeditionInfo.forEach((key, e) -> proto.putExpeditionInfoMap(key, e.toProto()));
        // 7.0 client refreshes the open dispatch UI from this list, not only the map.
        if (startedBasicInfo != null) {
            proto.addAllExpeditionBasicInfoList(startedBasicInfo);
        }

        this.setData(proto.build());
    }
}
