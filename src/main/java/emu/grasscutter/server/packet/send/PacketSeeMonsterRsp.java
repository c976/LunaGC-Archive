package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;

/** SeeMonsterRsp (opcode 7491): retcode = field 2. */
public class PacketSeeMonsterRsp extends BasePacket {
    public PacketSeeMonsterRsp() {
        this(0);
    }

    public PacketSeeMonsterRsp(int retcode) {
        super(PacketOpcodes.SeeMonsterRsp);
        this.setData(build(retcode));
    }

    private static byte[] build(int retcode) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
            CodedOutputStream cos = CodedOutputStream.newInstance(baos);
            if (retcode != 0) {
                cos.writeInt32(2, retcode);
            }
            cos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
