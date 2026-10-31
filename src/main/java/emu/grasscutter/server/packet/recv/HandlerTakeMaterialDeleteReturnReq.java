package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeMaterialDeleteReturnRsp;

/**
 * TakeMaterialDeleteReturnReq.
 * Client sends this on login / bag open and waits for Rsp; leaving it unhandled
 * can soft-lock inventory (X/Esc ineffective).
 */
@Opcodes(PacketOpcodes.TakeMaterialDeleteReturnReq)
public class HandlerTakeMaterialDeleteReturnReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new PacketTakeMaterialDeleteReturnRsp());
    }
}
