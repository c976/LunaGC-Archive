package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.BeyondProfilePictureWire;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;

/**
 * 7.1 BeyondCreateHallRsp (2767 = FHPLFJOBNOE)
 * { bool GDEIBJIPBNF = 3; int32 retcode = 4; uint64 KMBCCOHJHDH = 2; }
 * 超限大厅(影域入口)创建应答。
 */
public class PacketBeyondCreateHallRsp extends BasePacket {
    public static final int OPCODE = 2767;

    public PacketBeyondCreateHallRsp(long hallId, int retcode, int clientSequence) {
        super(OPCODE, clientSequence);
        byte[] data;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64);
            if (hallId != 0L) BeyondProfilePictureWire.varintField(out, 2, hallId);
            BeyondProfilePictureWire.varintField(out, 3, retcode == 0 ? 1 : 0);
            BeyondProfilePictureWire.varintField(out, 4, retcode);
            data = out.toByteArray();
        } catch (Throwable t) {
            data = new byte[0];
        }
        this.setData(data);
        Grasscutter.getLogger().debug("[MIAO HALL] BeyondCreateHallRsp hallId={} retcode={} bytes={}", hallId, retcode, data.length);
    }
}
