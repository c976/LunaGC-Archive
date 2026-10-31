package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.PushTipsManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetCodexPushtipsReadReqOuterClass.SetCodexPushtipsReadReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetCodexPushtipsReadRsp;

@Opcodes(PacketOpcodes.SetCodexPushtipsReadReq)
public class HandlerSetCodexPushtipsReadReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = SetCodexPushtipsReadReq.parseFrom(payload);
        if (req.getTypeId() == PushTipsManager.CODEX_TYPE_PUSHTIPS) {
            PushTipsManager.of(session.getPlayer()).setCodexViewed(req.getCodexId());
        }
        session.send(
                new PacketSetCodexPushtipsReadRsp(
                        Retcode.RET_SUCC_VALUE, req.getTypeId(), req.getCodexId()));
    }
}
