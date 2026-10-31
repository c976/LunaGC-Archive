package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionCallBackReqOuterClass.AvatarExpeditionCallBackReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionAllDataRsp;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionCallBackRsp;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionDataNotify;

@Opcodes(PacketOpcodes.AvatarExpeditionCallBackReq)
public class HandlerAvatarExpeditionCallBackReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        AvatarExpeditionCallBackReq req = AvatarExpeditionCallBackReq.parseFrom(payload);
        var player = session.getPlayer();

        int clientSeq = 0;
        try {
            if (header != null && header.length > 0) {
                clientSeq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Exception ignored) {
        }

        for (int i = 0; i < req.getAvatarGuidCount(); i++) {
            player.removeExpeditionInfo(req.getAvatarGuid(i));
        }

        player.save();
        var rsp = new PacketAvatarExpeditionCallBackRsp(player.getExpeditionInfo());
        if (clientSeq > 0) {
            rsp.buildHeader(clientSeq);
        }
        session.send(rsp);
        session.send(new PacketAvatarExpeditionDataNotify(player.getExpeditionInfo()));
        session.send(
                new PacketAvatarExpeditionAllDataRsp(
                        player.getExpeditionInfo(), player.getExpeditionLimit()));
    }
}
