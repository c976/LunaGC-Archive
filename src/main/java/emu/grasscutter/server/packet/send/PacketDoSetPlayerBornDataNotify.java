package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

public class PacketDoSetPlayerBornDataNotify extends BasePacket {
    public PacketDoSetPlayerBornDataNotify() {
        super(PacketOpcodes.DoSetPlayerBornDataNotify);
    }
}
