/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.gacha;

public final class GachaConstellationIds {
    private GachaConstellationIds() {
    }

    public static int fromGachaItemId(int n) {
        if (n >= 4100 && n < 5000) {
            return n + 1000;
        }
        return n + 100;
    }
}
