package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;

/**
 * Configurable ReliquaryDust SelectRsp probe. Default assumes 7281 / retcode field 9; the live
 * probe may override opcode + field while hunting the real 7273 pair.
 */
public class PacketReliquaryDustSelectRsp extends BasePacket {
    public static final int OPCODE = 7281;

    public PacketReliquaryDustSelectRsp(int retcode) {
        this(OPCODE, 9, retcode, null, 0L);
    }

    public PacketReliquaryDustSelectRsp(int retcode, byte[] requestHeader) {
        this(OPCODE, 9, retcode, requestHeader, 0L);
    }

    public PacketReliquaryDustSelectRsp(
            int opcode, int retcodeField, int retcode, byte[] requestHeader, long guid) {
        super(opcode);
        if (requestHeader != null && requestHeader.length > 0) {
            this.setHeader(requestHeader);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (guid != 0L) {
            // Common layouts: guid=4 (8036), guid=8 (4711), guid=14 (6541)
            if (opcode == 8036) {
                ProtoWire.writeUint64Force(out, 4, guid);
            } else if (opcode == 6541) {
                ProtoWire.writeUint64Force(out, 14, guid);
            } else if (opcode == 4711) {
                ProtoWire.writeUint64Force(out, 8, guid);
            }
        }
        ProtoWire.writeUint32Force(out, retcodeField, retcode);
        this.setData(out.toByteArray());
    }
}
