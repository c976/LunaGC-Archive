package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.UnlockAvatarTalentReqOuterClass.UnlockAvatarTalentReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUnlockAvatarTalentRsp;

@Opcodes(PacketOpcodes.UnlockAvatarTalentReq)
public class HandlerUnlockAvatarTalentReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UnlockAvatarTalentReq req = UnlockAvatarTalentReq.parseFrom(payload);

        // Unlock avatar const
        var avatar = session.getPlayer().getAvatars().getAvatarByGuid(req.getAvatarGuid());
        if (avatar == null || !avatar.unlockConstellation(req.getTalentId())) {
            session.send(
                    new PacketUnlockAvatarTalentRsp(
                            req.getAvatarGuid(), req.getTalentId(), Retcode.RET_FAIL));
        }
    }
}
