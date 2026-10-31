package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.expedition.ExpeditionHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionAllDataRsp;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionDataNotify;

@Opcodes(PacketOpcodes.AvatarExpeditionAllDataReq)
public class HandlerAvatarExpeditionAllDataReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        // Client shows claim-all based on state==FINISH_WAIT_REWARD in this map.
        if (ExpeditionHelper.refreshFinishedStates(player)) {
            player.save();
            session.send(new PacketAvatarExpeditionDataNotify(player.getExpeditionInfo()));
        }
        session.send(
                new PacketAvatarExpeditionAllDataRsp(
                        player.getExpeditionInfo(), player.getExpeditionLimit()));
    }
}
