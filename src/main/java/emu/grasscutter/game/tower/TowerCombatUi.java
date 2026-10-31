package emu.grasscutter.game.tower;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketCanUseSkillNotify;

/** Re-enable attack/skill HUD after abyss buff-select / mid-half disables it. */
public final class TowerCombatUi {
    private TowerCombatUi() {}

    public static void allowSkills(Player player) {
        if (player == null) {
            return;
        }
        try {
            if (player.getSession() == null) {
                return;
            }
            player.sendPacket(new PacketCanUseSkillNotify(true));
        } catch (Throwable ignored) {
            // Best effort.
        }
    }
}
