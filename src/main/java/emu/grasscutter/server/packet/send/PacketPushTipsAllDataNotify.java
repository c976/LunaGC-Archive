package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PushTipsAllDataNotifyOuterClass.PushTipsAllDataNotify;
import emu.grasscutter.net.proto.PushTipsDataOuterClass.PushTipsData;
import java.util.Map;

/** Every push tip the player has recorded, with its state (1 unread, 3 finished). */
public class PacketPushTipsAllDataNotify extends BasePacket {
    public PacketPushTipsAllDataNotify(Map<Integer, Integer> tipStates) {
        super(PacketOpcodes.PushTipsAllDataNotify);

        var proto = PushTipsAllDataNotify.newBuilder();
        tipStates.forEach(
                (tipId, state) ->
                        proto.addPushTipsList(
                                PushTipsData.newBuilder().setPushTipsId(tipId).setState(state).build()));
        this.setData(proto);
    }
}
