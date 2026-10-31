package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerApplyEnterMpReqOuterClass.PlayerApplyEnterMpReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerApplyEnterMpRsp;

@Opcodes(PacketOpcodes.PlayerApplyEnterMpReq)
public class HandlerPlayerApplyEnterMpReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PlayerApplyEnterMpReq req = PlayerApplyEnterMpReq.parseFrom(payload);
        int targetUid = req.getTargetUid();

        // 7.1 客户端把 target_uid 换成 field3 了，服务端 proto 仍写 field8 -> 手工补解析
        if (targetUid == 0 && payload != null && payload.length > 0) {
            try {
                com.google.protobuf.CodedInputStream cis =
                        com.google.protobuf.CodedInputStream.newInstance(payload);
                while (!cis.isAtEnd()) {
                    int tag = cis.readTag();
                    if ((tag >>> 3) == 3) {
                        targetUid = cis.readUInt32();
                    } else {
                        cis.skipField(tag);
                    }
                }
            } catch (Throwable t) {
            }
        }

        StringBuilder hex = new StringBuilder();
        if (payload != null) {
            for (byte b : payload) {
                hex.append(String.format("%02x", b));
            }
        }

        if (targetUid == 0) {
            session.send(new PacketPlayerApplyEnterMpRsp(0));
            return;
        }

        session.getServer().getMultiplayerSystem().applyEnterMp(session.getPlayer(), targetUid);
        session.send(new PacketPlayerApplyEnterMpRsp(targetUid));
    }
}
