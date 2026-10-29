package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEmptyRsp;

@Opcodes(PacketOpcodes.GetCityReputationMapInfoReq)
public class HandlerGetCityReputationMapInfoReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new PacketEmptyRsp(PacketOpcodes.GetCityReputationMapInfoRsp, header));
    }
}
