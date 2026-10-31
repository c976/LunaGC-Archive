package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.systems.ReliquaryDustSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;

@Opcodes(ReliquaryDustSystem.OPCODE_DUST_CONFIRM_REQ_B)
public class HandlerReliquaryDustConfirmReqB extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        ReliquaryDustSystem.handleConfirm(
                session.getPlayer(), payload, ReliquaryDustSystem.OPCODE_DUST_CONFIRM_REQ_B);
    }
}
