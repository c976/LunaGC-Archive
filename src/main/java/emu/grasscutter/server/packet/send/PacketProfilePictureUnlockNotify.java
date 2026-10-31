package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.BeyondProfilePictureTable;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 7.1 头像解锁列表（CmdId 27126）。
 * NLCLNLDGECL { repeated uint32 KPIDIHGBNKB = 7; }
 * 依据：同名字段 KPIDIHGBNKB 出现在 4400 GetAllUnlockNameCardRsp（名片解锁列表）。
 */
public class PacketProfilePictureUnlockNotify extends BasePacket {

    public static final int OPCODE = 27126;

    public PacketProfilePictureUnlockNotify(Player player) {
        super(OPCODE);
        byte[] data;
        int count = 0;
        try {
            List<BeyondProfilePictureTable.Entry> list = BeyondProfilePictureTable.all();
            ByteArrayOutputStream packed = new ByteArrayOutputStream(1024);
            int __countK = 0;
            for (BeyondProfilePictureTable.Entry e : list) {
                writeVarint(packed, e.id);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            writeBytesField(out, 7, packed.toByteArray());
            data = out.toByteArray();
            count = list.size();
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("PacketProfilePictureUnlockNotify build failed: {}", t.toString());
            data = new byte[0];
        }
        this.setData(data);
        Grasscutter.getLogger().debug("ProfilePictureUnlockNotify opcode=27126 ids={} bytes={}", count, data.length);
    }

    private static void writeVarint(ByteArrayOutputStream o, long v) {
        while ((v & ~0x7FL) != 0) {
            o.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        o.write((int) v);
    }

    private static void writeBytesField(ByteArrayOutputStream o, int field, byte[] b) {
        writeVarint(o, ((long) field << 3) | 2);
        writeVarint(o, b.length);
        o.write(b, 0, b.length);
    }
}
