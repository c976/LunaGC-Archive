package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.BeyondProfilePictureTable;
import emu.grasscutter.game.player.BeyondProfilePictureWire;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 用真数据向指定 CmdId 发资料。
 * 7.0 号：20816 { rep@11 + rep@6 + retcode@10 } / 21088 { rep槽@6 + 头像@15 + @7 + bool@9 }
 * 7.1 候选：5247 / 7475
 */
public class PacketBeyondFull extends BasePacket {
    public static final int[] FRAME_IDS = {100000, 100011, 100012, 100013, 100014};


    /** 7.0 真实数据源：玩家角色 id 列表 */
    public static java.util.List<Integer> avatarIds(Player player) {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        try {
            for (emu.grasscutter.game.avatar.Avatar a : player.getAvatars()) {
                l.add(a.getAvatarId());
            }
        } catch (Throwable ignored) {
        }
        if (l.isEmpty()) {
            l.add(10000007);
            l.add(10000005);
        }
        return l;
    }

    /** 名片 id 范围（7.0 原版：210001~210049） */
    public static void writeNameCards(BeyondProfilePictureWire w, ByteArrayOutputStream out) {
        for (int id = 210001; id <= 210049; id++) {
            BeyondProfilePictureWire.varintField(out, 11, id);
        }
    }
    public static int fieldOf(int opcode) {
        switch (opcode) {
            case 584: return 2;
            case 4721: return 4;
            case 3347: return 7;
            case 7302: return 6;
            case 25001: return 9;
            default: return -1;
        }
    }

    public PacketBeyondFull(Player player, int opcode) {
        super(opcode);
        byte[] data;
        try {
            if (opcode == 4269) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                for (Integer aid : avatarIds(player)) {
                    BeyondProfilePictureWire.varintField(out, 3, aid);
                }
                for (int id = 210001; id <= 210049; id++) {
                    BeyondProfilePictureWire.varintField(out, 4, id);
                }
                BeyondProfilePictureWire.varintField(out, 7, 0);
                data = out.toByteArray();
            } else             if (opcode == 20816 || opcode == 27037 + 1 || opcode == 27038) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                BeyondProfilePictureWire.varintField(out, 5, 0);
                for (Integer aid : avatarIds(player)) {
                    BeyondProfilePictureWire.varintField(out, 9, aid);
                }
                for (int id = 210001; id <= 210049; id++) {
                    BeyondProfilePictureWire.varintField(out, 11, id);
                }
                data = out.toByteArray();
            } else if (opcode == 21088) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
                List<BeyondProfilePictureTable.Entry> list = BeyondProfilePictureTable.all();
                for (BeyondProfilePictureTable.Entry e : list) {
                    BeyondProfilePictureWire.bytesField(out, 6,
                            BeyondProfilePictureWire.cnmppapjddi(e.id, e.iconPath, e.unlockParam));
                }
                BeyondProfilePictureTable.Entry cur = BeyondProfilePictureTable.find(player.getHeadImage());
                if (cur == null && !list.isEmpty()) {
                    cur = list.get(0);
                }
                if (cur != null) {
                    BeyondProfilePictureWire.bytesField(out, 15,
                            BeyondProfilePictureWire.ofnjdnhlgji(cur.id, cur.iconPath, cur.unlockParam));
                    BeyondProfilePictureWire.varintField(out, 7, cur.id);
                }
                BeyondProfilePictureWire.varintField(out, 9, 1);
                data = out.toByteArray();
            } else if (opcode == 5247 || opcode == 7475) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                for (Integer aid : avatarIds(player)) {
                    BeyondProfilePictureWire.varintField(out, 6, aid);
                }
                for (int id = 210001; id <= 210049; id++) {
                    BeyondProfilePictureWire.varintField(out, 11, id);
                }
                if (opcode == 5247) {
                    BeyondProfilePictureWire.varintField(out, 7, 0);
                    BeyondProfilePictureWire.varintField(out, 4, 0);
                } else {
                    BeyondProfilePictureWire.varintField(out, 15, 0);
                }
                data = out.toByteArray();
            } else {
                byte[] detail = PacketBeyondPlayerDetailRsp.buildDetail(player);
                ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
                int f = fieldOf(opcode);
                if (f > 0) {
                    BeyondProfilePictureWire.bytesField(out, f, detail);
                }
                switch (opcode) {
                    case 584:
                    case 4721:
                        BeyondProfilePictureWire.varintField(out, 15, 0);
                        break;
                    default:
                        break;
                }
                data = out.toByteArray();
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("PacketBeyondFull build failed: {}", t.toString());
            data = new byte[0];
        }
        this.setData(data);
        Grasscutter.getLogger().debug("BeyondFull opcode={} bytes={}", opcode, data.length);
    }
}
