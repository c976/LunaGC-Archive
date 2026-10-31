package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.BeyondProfilePictureWire;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;

/** 通用探测包：f1 = 第一个列表，f2 = 第二个列表，rf = retcode 字段。
 *  v1/v2 为逗号分隔的自定义值列表；为空时用旧默认（角色id列表 / 210001~210049）。 */
public class PacketListProbe extends BasePacket {
    public PacketListProbe(Player player, int opcode, int f1, int f2, int rf) {
        this(player, opcode, f1, f2, rf, null, null);
    }

    public PacketListProbe(Player player, int opcode, int f1, int f2, int rf, String v1, String v2) {
        super(opcode);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        if (v1 != null && !v1.isEmpty()) {
            for (String s : v1.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) BeyondProfilePictureWire.varintField(out, f1, Integer.parseInt(t));
            }
        } else {
            for (Integer aid : PacketBeyondFull.avatarIds(player)) {
                BeyondProfilePictureWire.varintField(out, f1, aid);
            }
        }
        if (v2 != null && !v2.isEmpty()) {
            for (String s : v2.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) BeyondProfilePictureWire.varintField(out, f2, Integer.parseInt(t));
            }
        } else {
            for (int id = 210001; id <= 210049; id++) {
                BeyondProfilePictureWire.varintField(out, f2, id);
            }
        }
        if (rf > 0) BeyondProfilePictureWire.varintField(out, rf, 0);
        this.setData(out.toByteArray());
    }
}
