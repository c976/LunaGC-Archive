package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto._TakeAllForgeOutputReqOuterClass._TakeAllForgeOutputReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes._TakeAllForgeOutputReq)
public class HandlerTakeAllForgeOutputReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        _TakeAllForgeOutputReq.parseFrom(payload);
        session.getPlayer().getForgingManager().handleTakeAllForgeOutputReq();
    }
}
