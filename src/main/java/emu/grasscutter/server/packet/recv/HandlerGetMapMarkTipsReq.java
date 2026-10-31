package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetMapMarkTipsRsp;

@Opcodes(PacketOpcodes.GetMapMarkTipsReq)
public class HandlerGetMapMarkTipsReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        // Opening the map: re-push resin so it stacks with the abyss floor/chamber widget in the top-right.
        if (player != null && player.getResinManager() != null) {
            player.getResinManager().refreshClientResinUi();
        }
        session.send(new PacketGetMapMarkTipsRsp());
    }
}
