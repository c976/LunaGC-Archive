/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.battlepass.BattlePassManager
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.server.packet.send.PacketBattlePassAllDataNotify
 *  emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketBeyondBattlePassAllDataNotify
 *  emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify
 */
package emu.grasscutter.bootstrap;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketBattlePassAllDataNotify;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassAllDataNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;

public final class BattlePassBootstrap {
    private static final int OPEN_STATE_BATTLE_PASS = 1300;
    private static final int OPEN_STATE_BATTLE_PASS_ENTRY = 1301;

    private BattlePassBootstrap() {
    }

    public static void onLogin(Player player) {
        if (player == null) {
            return;
        }
        BattlePassManager battlePassManager = player.getBattlePassManager();
        if (battlePassManager == null) {
            return;
        }
        try {
            BattlePassBootstrap.unlockBattlePassOpenStates(player);
            battlePassManager.save();
            player.sendPacket((BasePacket)new PacketBattlePassAllDataNotify(player));
            player.sendPacket((BasePacket)new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket((BasePacket)new PacketBeyondBattlePassAllDataNotify(player));
            player.sendPacket((BasePacket)new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("BattlePassBootstrap: login init failed uid={}", (Object)player.getUid(), (Object)throwable);
        }
    }

    private static void unlockBattlePassOpenStates(Player player) {
        player.getProgressManager().forceSetOpenState(1300, 1);
        player.getProgressManager().forceSetOpenState(1301, 1);
    }
}

