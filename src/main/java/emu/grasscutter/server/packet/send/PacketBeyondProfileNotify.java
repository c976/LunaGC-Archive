package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.game.player.BeyondProfilePictureWire;
import java.io.ByteArrayOutputStream;

/**
 * 7.1 个人资料推送（无 retcode，客户端不需请求）：
 *  7302 HBMHJBKBDIP { repeated LNFKGCNALBO MPGELOFKHOJ = 6; ... }
 *  25001 GOONLKPEFFL { repeated LNFKGCNALBO GPAEGCCMJKH = 9; ... }
 */
public class PacketBeyondProfileNotify extends BasePacket {
    public static final int OPCODE_7302 = 7302;
    public static final int OPCODE_25001 = 25001;

    public PacketBeyondProfileNotify(Player player, int opcode) {
        super(opcode);
        byte[] data;
        try {
            byte[] detail = PacketBeyondPlayerDetailRsp.buildDetail(player);
            ByteArrayOutputStream out = new ByteArrayOutputStream(512);
            BeyondProfilePictureWire.bytesField(out, opcode == OPCODE_7302 ? 6 : 9, detail);
            data = out.toByteArray();
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("PacketBeyondProfileNotify build failed: {}", t.toString());
            data = new byte[0];
        }
        this.setData(data);
        Grasscutter.getLogger().debug("BeyondProfileNotify opcode={} bytes={}", opcode, data.length);
    }
}
