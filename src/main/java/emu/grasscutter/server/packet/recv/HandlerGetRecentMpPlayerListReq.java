package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEmptyRsp;

@Opcodes(PacketOpcodes.GetRecentMpPlayerListReq)
public class HandlerGetRecentMpPlayerListReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new PacketEmptyRsp(PacketOpcodes.GetRecentMpPlayerListRsp, header));
    }
}
