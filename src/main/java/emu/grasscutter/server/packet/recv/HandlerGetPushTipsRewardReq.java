package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.PushTipsManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPushTipsRewardReqOuterClass.GetPushTipsRewardReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.GetPushTipsRewardReq)
public class HandlerGetPushTipsRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = GetPushTipsRewardReq.parseFrom(payload);
        PushTipsManager.of(session.getPlayer()).claimRewards(req.getPushTipsIdListList());
    }
}
