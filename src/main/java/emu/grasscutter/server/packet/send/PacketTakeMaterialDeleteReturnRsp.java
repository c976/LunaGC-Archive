package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

/** TakeMaterialDeleteReturnRsp — official capture uses empty body. */
public class PacketTakeMaterialDeleteReturnRsp extends BasePacket {
    public PacketTakeMaterialDeleteReturnRsp() {
        super(PacketOpcodes.TakeMaterialDeleteReturnRsp);
        this.setData(new byte[0]);
    }
}
