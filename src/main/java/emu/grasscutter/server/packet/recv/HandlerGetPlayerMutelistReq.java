package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEmptyRsp;

@Opcodes(PacketOpcodes.GetPlayerMutelistReq)
public class HandlerGetPlayerMutelistReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new PacketEmptyRsp(PacketOpcodes.GetPlayerMutelistRsp, header));
    }
}
