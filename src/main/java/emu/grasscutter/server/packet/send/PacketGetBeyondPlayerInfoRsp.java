package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.BeyondProfilePictureWire;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;

/** 7.1 IOGBLFLPKIA (CmdId 9779) { repeated NADAPKEGMFJ = 2; uint32 ECKDAAHALPA = 3; int32 retcode = 15; } */
public class PacketGetBeyondPlayerInfoRsp extends BasePacket {
    public static final int OPCODE = 9779;

    public PacketGetBeyondPlayerInfoRsp(int queryType, int clientSequence) {
        super(OPCODE, clientSequence);
        byte[] data;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(16);
            BeyondProfilePictureWire.varintField(out, 3, queryType);
            BeyondProfilePictureWire.varintField(out, 15, 0);
            data = out.toByteArray();
        } catch (Throwable t) {
            data = new byte[0];
        }
        this.setData(data);
        Grasscutter.getLogger().debug("GetBeyondPlayerInfoRsp opcode=9779 type={} seq={} bytes={}", queryType, clientSequence, data.length);
    }
}
