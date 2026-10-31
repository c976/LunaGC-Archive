package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AntiAddictNotifyOuterClass.AntiAddictNotify;

/**
 * The anti-addiction notice, used here as a plain message box.
 *
 * <p>The client renders {@code msg} verbatim in a dialog, which makes this the one reliable way to
 * put arbitrary server text in front of a player who is already in the world.
 */
public class PacketAntiAddictNotify extends BasePacket {
    public PacketAntiAddictNotify(int msgType, String msg) {
        super(PacketOpcodes.AntiAddictNotify);

        var proto = AntiAddictNotify.newBuilder().setMsgType(msgType).setMsg(msg).build();

        this.setData(proto);
    }
}
