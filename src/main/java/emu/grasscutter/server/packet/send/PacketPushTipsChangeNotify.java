package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PushTipsChangeNotifyOuterClass.PushTipsChangeNotify;
import emu.grasscutter.net.proto.PushTipsDataOuterClass.PushTipsData;

public class PacketPushTipsChangeNotify extends BasePacket {
    public PacketPushTipsChangeNotify(int tipId, int state) {
        super(PacketOpcodes.PushTipsChangeNotify);

        this.setData(
                PushTipsChangeNotify.newBuilder()
                        .addPushTipsList(PushTipsData.newBuilder().setPushTipsId(tipId).setState(state))
                        .build());
    }
}
