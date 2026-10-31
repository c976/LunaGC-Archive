package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.CommonPlayerTipsNotifyOuterClass.CommonPlayerTipsNotify;
import java.util.List;

public class PacketCommonPlayerTipsNotify extends BasePacket {
    public PacketCommonPlayerTipsNotify(String textMapId, int notifyType) {
        this(List.of(textMapId), notifyType);
    }

    public PacketCommonPlayerTipsNotify(List<String> textMapIds, int notifyType) {
        super(PacketOpcodes.CommonPlayerTipsNotify);
        var notify = CommonPlayerTipsNotify.newBuilder().setNotifyType(notifyType);
        if (textMapIds != null) {
            for (String id : textMapIds) {
                if (id != null && !id.isEmpty()) notify.addTextMapIdList(id);
            }
        }
        this.setData(notify.build());
    }
}
