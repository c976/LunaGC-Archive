package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;

/** S2C 26599 FLONFCCEGDJ — companion retcode for ReliquaryDust confirm click. */
public class PacketReliquaryDustCompanionRsp extends BasePacket {
    public static final int OPCODE = 26599;

    public PacketReliquaryDustCompanionRsp(int retcode) {
        super(OPCODE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtoWire.writeUint32Force(out, 10, retcode);
        this.setData(out.toByteArray());
    }
}
