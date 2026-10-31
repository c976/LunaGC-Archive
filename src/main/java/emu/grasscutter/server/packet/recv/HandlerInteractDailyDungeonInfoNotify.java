package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.DomainHandbookHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetDailyDungeonEntryInfoRsp;

@Opcodes(PacketOpcodes.InteractDailyDungeonInfoNotify)
public class HandlerInteractDailyDungeonInfoNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Player player = session.getPlayer();
        // Unlock points/areas; do not echo on 3913 (that notify is an empty message).
        DomainHandbookHelper.onInteractDailyDungeon(player);
        PacketGetDailyDungeonEntryInfoRsp.sendBothLayouts(player, 3);
    }
}
