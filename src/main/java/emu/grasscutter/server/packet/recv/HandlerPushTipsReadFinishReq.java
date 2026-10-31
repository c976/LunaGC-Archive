package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.PushTipsManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PushTipsReadFinishReqOuterClass.PushTipsReadFinishReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PushTipsReadFinishReq)
public class HandlerPushTipsReadFinishReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = PushTipsReadFinishReq.parseFrom(payload);
        PushTipsManager.of(session.getPlayer()).readFinish(req.getPushTipsId());
    }
}
