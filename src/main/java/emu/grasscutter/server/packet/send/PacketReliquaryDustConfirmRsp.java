package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;

/** S2C confirm rsp — guid + retcode (EBAHKKBAKFO 4711 layout as safe default). */
public class PacketReliquaryDustConfirmRsp extends BasePacket {
    public static final int OPCODE = 4711;

    public PacketReliquaryDustConfirmRsp(long guid, int retcode) {
        super(OPCODE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (guid != 0L) {
            ProtoWire.writeUint64Force(out, 8, guid);
        }
        ProtoWire.writeUint32Force(out, 7, retcode);
        this.setData(out.toByteArray());
    }
}
