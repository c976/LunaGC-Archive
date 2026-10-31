package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPlayerSocialDetailReqOuterClass.GetPlayerSocialDetailReq;
import emu.grasscutter.net.proto.SocialDetailOuterClass.SocialDetail;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetPlayerSocialDetailRsp;

@Opcodes(PacketOpcodes.GetPlayerSocialDetailReq)
public class HandlerGetPlayerSocialDetailReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {

        int seq = 0;
        try {
            if (header != null && header.length > 0) {
                seq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Exception ignored) {
        }
        GetPlayerSocialDetailReq req = GetPlayerSocialDetailReq.parseFrom(payload);

        emu.grasscutter.Grasscutter.getLogger().debug("MIAO social982 seq={} reqUid={} selfUid={} isSelf={}", seq, req.getUid(), session.getPlayer().getUid(), req.getUid() == session.getPlayer().getUid());
        emu.grasscutter.Grasscutter.getLogger().debug("MIAO social982 param={} seq={}", req.getParam(), seq);
        if (req.getUid() == session.getPlayer().getUid()) {
            // 自查：多人/任务/好友/手册等界面渲染要用，必须回成功；
            // 先回空 detail 试探（既不让客户端弹资料卡，也不报服务器内部错误）。
            session.send(new PacketGetPlayerSocialDetailRsp(SocialDetail.newBuilder(), seq));
            return;
        }
        SocialDetail.Builder detail = session.getServer().getSocialDetailByUid(req.getUid());

        if (detail != null) {
            detail.setIsFriend(session.getPlayer().getFriendsList().isFriendsWith(req.getUid()));
        }

        session.send(new PacketGetPlayerSocialDetailRsp(detail, seq));
    }
}
