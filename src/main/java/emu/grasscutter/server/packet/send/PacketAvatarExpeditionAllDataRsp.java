package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionHelper;
import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionAllDataRspOuterClass.AvatarExpeditionAllDataRsp;
import java.util.Map;
import java.util.stream.Collectors;

public class PacketAvatarExpeditionAllDataRsp extends BasePacket {
    public PacketAvatarExpeditionAllDataRsp(
            Map<Long, ExpeditionInfo> expeditionInfo, int expeditionCountLimit) {
        super(PacketOpcodes.AvatarExpeditionAllDataRsp);

        this.setData(
                AvatarExpeditionAllDataRsp.newBuilder()
                        .addAllOpenExpeditionList(ExpeditionHelper.allOpenExpeditionIds())
                        .setExpeditionCountLimit(expeditionCountLimit)
                        .putAllExpeditionInfoMap(
                                expeditionInfo.entrySet().stream()
                                        .collect(
                                                Collectors.toMap(
                                                        Map.Entry::getKey, e -> e.getValue().toProto())))
                        .build());
    }
}
