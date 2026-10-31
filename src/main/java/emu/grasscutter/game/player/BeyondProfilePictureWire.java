package emu.grasscutter.game.player;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** 7.1 头像/头像框列表的 wire 编码（字段号/类型严格对齐真表）。 */
public final class BeyondProfilePictureWire {

    private BeyondProfilePictureWire() {}

    /** OFNJDNHLGJI { uint32 NNMPJPNDJMP=1; bool LBCKMAELOHL=2; string FICGJEKJFPL=3; string PAODDIHPDGL=4; uint32 CIJHCIPGFIK=5; } */
    public static byte[] ofnjdnhlgji(int id, String iconPath, int unlockParam) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(64);
        varintField(o, 1, id);
        varintField(o, 2, 1);
        if (iconPath != null && !iconPath.isEmpty()) {
            stringField(o, 3, iconPath);
            stringField(o, 4, iconPath);
        }
        varintField(o, 5, unlockParam);
        return o.toByteArray();
    }

    /** CNMPPAPJDDI { uint32 HKKLBNIPGPE=1; string FICGJEKJFPL=2; string PAODDIHPDGL=3; uint32 CIJHCIPGFIK=4; bool LPAGKMAGDJE=5; } */
    public static byte[] cnmppapjddi(int id, String iconPath, int unlockParam) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(64);
        varintField(o, 1, id);
        if (iconPath != null && !iconPath.isEmpty()) {
            stringField(o, 2, iconPath);
            stringField(o, 3, iconPath);
        }
        varintField(o, 4, 0); // [v31] unlockParam -> 0：客户端视为“无条件/默认解锁”，不再画锁
        varintField(o, 5, 1);
        return o.toByteArray();
    }

    /** map<uint32, ?> 的一项：MapEntry { key = 1; value = 2; } */
    public static byte[] mapEntry(int key, byte[] value) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(128);
        varintField(o, 1, key);
        bytesField(o, 2, value);
        return o.toByteArray();
    }

    public static void varint(ByteArrayOutputStream o, long v) {
        while ((v & ~0x7FL) != 0) {
            o.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        o.write((int) v);
    }

    public static void tag(ByteArrayOutputStream o, int field, int wire) {
        varint(o, ((long) field << 3) | wire);
    }

    public static void varintField(ByteArrayOutputStream o, int field, long v) {
        tag(o, field, 0);
        varint(o, v);
    }

    public static void bytesField(ByteArrayOutputStream o, int field, byte[] b) {
        tag(o, field, 2);
        varint(o, b.length);
        o.write(b, 0, b.length);
    }

    public static void stringField(ByteArrayOutputStream o, int field, String s) {
        bytesField(o, field, s.getBytes(StandardCharsets.UTF_8));
    }
}
