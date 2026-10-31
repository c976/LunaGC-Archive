/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;

public final class SkirkNyxDisplay {
    private SkirkNyxDisplay() {
    }

    public static void applyGain(Player player, EntityAvatar entityAvatar, float f) {
        SkirkCunningBridge.applyDelta(player, entityAvatar, f);
    }

    public static void addDelta(Player player, EntityAvatar entityAvatar, float f) {
        SkirkCunningBridge.applyDelta(player, entityAvatar, f);
    }

    public static void syncBar(Player player, EntityAvatar entityAvatar, float f) {
        SkirkCunningBridge.syncBar(player, entityAvatar, f);
    }
}
