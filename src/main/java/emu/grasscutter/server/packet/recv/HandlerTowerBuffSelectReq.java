package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerBuffSelectReqOuterClass.TowerBuffSelectReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTowerBuffSelectRsp;

@Opcodes(PacketOpcodes.TowerBuffSelectReq)
public class HandlerTowerBuffSelectReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        TowerBuffSelectReq req = TowerBuffSelectReq.parseFrom(payload);
        var player = session.getPlayer();
        int buffId = req.getTowerBuffId();

        if (player.getTowerManager().selectTowerBuff(buffId)) {
            session.send(new PacketTowerBuffSelectRsp(buffId));
        }
    }
}
