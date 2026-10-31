/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BeyondBattlePassWireEncoder;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

public class PacketBeyondBattlePassCurScheduleUpdateNotify
extends BasePacket {
    public PacketBeyondBattlePassCurScheduleUpdateNotify(Player player) {
        super(PacketOpcodes._BeyondBattlePassCurScheduleUpdateNotify);
        BattlePassManager battlePassManager = player != null ? player.getBattlePassManager() : null;
        this.setData(BeyondBattlePassWireEncoder.encodeCurScheduleUpdateNotify(battlePassManager));
        BattlePassCompatHelper.logBuilt(player, "BeyondCurScheduleUpdate");
    }
}
