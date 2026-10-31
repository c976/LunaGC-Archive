package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.systems.ReliquaryDustSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;

@Opcodes(ReliquaryDustSystem.OPCODE_DUST_COMPANION_REQ)
public class HandlerReliquaryDustCompanionReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        ReliquaryDustSystem.handleCompanionReq(session.getPlayer(), payload);
    }
}
