package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

/**
 * City reputation is not implemented, and CityReputationRewardInfo is still obfuscated, so there is
 * nothing meaningful to fill the reward list with. An empty response is wire-identical to a message
 * carrying only defaults (retcode 0, no rewards): the client opens the reputation map empty instead
 * of re-sending the request and filling the console with "Unhandled packet".
 */
@Opcodes(PacketOpcodes.GetCityReputationMapInfoReq)
public class HandlerGetCityReputationMapInfoReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var rsp = new BasePacket(PacketOpcodes.GetCityReputationMapInfoRsp);
        rsp.setData(new byte[0]);

        session.send(rsp);
    }
}
