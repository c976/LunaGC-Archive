package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.utils.Utils;
import java.util.List;

public class PacketServerAnnounceNotify extends BasePacket {

    public PacketServerAnnounceNotify(List<AnnounceDataOuterClass.AnnounceData> data) {
        super(PacketOpcodes.ServerAnnounceNotify);

        var proto = ServerAnnounceNotifyOuterClass.ServerAnnounceNotify.newBuilder();

        proto.addAllAnnounceDataList(data);

        this.setData(proto);
    }

    public PacketServerAnnounceNotify(String msg, int configId) {
        super(PacketOpcodes.ServerAnnounceNotify);

        var proto = ServerAnnounceNotifyOuterClass.ServerAnnounceNotify.newBuilder();

        // msg used to be accepted and thrown away, so this overload announced nothing at all.
        // See AnnouncementSystem.toProto for why all three string fields carry the same text.
        var text = msg == null ? "" : msg;

        proto.addAnnounceDataList(
                AnnounceDataOuterClass.AnnounceData.newBuilder()
                        .setConfigId(configId)
                        .setBeginTime(Utils.getCurrentSeconds() + 1)
                        .setEndTime(Utils.getCurrentSeconds() + 2)
                        .setDungeonConfirmText(text)
                        .setCountDownText(text)
                        .setCenterSystemText(text)
                        .build());

        this.setData(proto);
    }
}
