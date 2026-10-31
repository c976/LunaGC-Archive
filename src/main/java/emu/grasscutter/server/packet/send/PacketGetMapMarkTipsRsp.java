package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

/**
 * Empty success for GetMapMarkTipsReq (map tip query when opening map or the go-gather action).
 * Proto fields vary by version; retcode=0 with no tips is accepted by client.
 */
public class PacketGetMapMarkTipsRsp extends BasePacket {
    public PacketGetMapMarkTipsRsp() {
        super(PacketOpcodes.GetMapMarkTipsRsp);
        // protobuf: field 1 (retcode) = 0  => 08 00
        this.setData(new byte[] {0x08, 0x00});
    }
}
