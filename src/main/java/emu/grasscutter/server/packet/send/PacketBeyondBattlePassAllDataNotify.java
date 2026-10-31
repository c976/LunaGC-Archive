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

public class PacketBeyondBattlePassAllDataNotify
extends BasePacket {
    public PacketBeyondBattlePassAllDataNotify(Player player) {
        super(PacketOpcodes._BeyondBattlePassAllDataNotify);
        BattlePassManager battlePassManager = player != null ? player.getBattlePassManager() : null;
        this.setData(BeyondBattlePassWireEncoder.encodeAllDataNotify(battlePassManager));
        BattlePassCompatHelper.logBuilt(player, "BeyondAllDataNotify");
    }
}
