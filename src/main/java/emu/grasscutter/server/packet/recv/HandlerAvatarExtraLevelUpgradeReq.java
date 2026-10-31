/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.avatar.AvatarExtraLevelHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;

@Opcodes(value=25098)
public class HandlerAvatarExtraLevelUpgradeReq
extends PacketHandler {
    @Override
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) throws Exception {
        Player player = gameSession.getPlayer();
        if (player == null || byArray2 == null || byArray2.length == 0) {
            return;
        }
        AvatarExtraLevelHelper.tryHandleUnregisteredPacket(player, 25098, byArray2);
    }
}
