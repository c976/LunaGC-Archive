package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WidgetGadgetAllDataNotifyOuterClass.WidgetGadgetAllDataNotify;

public class PacketWidgetGadgetAllDataNotify extends BasePacket {

    public PacketWidgetGadgetAllDataNotify() {
        super(PacketOpcodes.WidgetGadgetAllDataNotify);

        WidgetGadgetAllDataNotify proto = WidgetGadgetAllDataNotify.newBuilder().build();

        this.setData(proto);
    }
}
