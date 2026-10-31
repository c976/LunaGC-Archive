/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.avatar;

import emu.grasscutter.game.player.Player;

public final class AvatarGuidCodec {
    private static final long GUID_XOR = 1583L;
    private static final long GUID_ADD = 41090L;

    private AvatarGuidCodec() {
    }

    public static long decode(long l) {
        return (l ^ 0x62FL) - 41090L;
    }

    public static long resolve(Player player, long l) {
        if (player == null || player.getAvatars() == null) {
            return l;
        }
        if (l > 0L && player.getAvatars().getAvatarByGuid(l) != null) {
            return l;
        }
        long l2 = AvatarGuidCodec.decode(l);
        if (l2 > 0L && player.getAvatars().getAvatarByGuid(l2) != null) {
            return l2;
        }
        long l3 = l ^ 0x62FL;
        if (l3 > 0L && player.getAvatars().getAvatarByGuid(l3) != null) {
            return l3;
        }
        return l;
    }
}
