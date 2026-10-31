package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

/** The other 7.1 {personal_line_id = 13} request; see HandlerUnlockPersonalLineReq. */
@Opcodes(PacketOpcodes._UnlockPersonalLineReqAlt)
public class HandlerUnlockPersonalLineReqAlt extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        HandlerUnlockPersonalLineReq.handleRequest(
                session, PacketOpcodes._UnlockPersonalLineReqAlt, payload);
    }
}
