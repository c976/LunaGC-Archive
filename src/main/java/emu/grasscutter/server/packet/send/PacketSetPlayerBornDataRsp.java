package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** SetPlayerBornDataRsp for the 7.1 protocol. */
public class PacketSetPlayerBornDataRsp extends BasePacket {
    /** Runtime- and client-static-confirmed 7.1 CmdId. */
    public static final int CMD_ID = 4385;

    /** The 7.1 client parser reads retcode from protobuf field 7. */
    private static final int RETCODE_FIELD_NUMBER = 7;

    /** Success is encoded as an empty payload because protobuf defaults retcode to zero. */
    public PacketSetPlayerBornDataRsp() {
        super(CMD_ID);
    }

    public PacketSetPlayerBornDataRsp(int retcode) {
        this();
        if (retcode != 0) {
            this.setData(encodeRetcode(retcode));
        }
    }

    private static byte[] encodeRetcode(int retcode) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = CodedOutputStream.newInstance(bytes);
            output.writeInt32(RETCODE_FIELD_NUMBER, retcode);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode SetPlayerBornDataRsp", e);
        }
    }
}
