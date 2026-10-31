package emu.grasscutter.server.packet.send;

import com.google.protobuf.MessageLite;
import emu.grasscutter.net.packet.BasePacket;

/** Any Ice Creature response or notify; each one is a single prebuilt message. */
public class PacketIceCreature extends BasePacket {
    public PacketIceCreature(int opcode, MessageLite message) {
        super(opcode);
        this.setData(message);
    }
}
