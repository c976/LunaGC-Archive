package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerApplyEnterMpResultReqOuterClass.PlayerApplyEnterMpResultReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerApplyEnterMpResultRsp;

@Opcodes(PacketOpcodes.PlayerApplyEnterMpResultReq)
public class HandlerPlayerApplyEnterMpResultReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PlayerApplyEnterMpResultReq req = PlayerApplyEnterMpResultReq.parseFrom(payload);
        int applyUid = req.getApplyUid();
        boolean isAgreed = req.getIsAgreed();

        // 7.1 客户端字段号变了：is_agree = f9(bool)、apply_uid = f12(uint32)
        // 服务端 proto 仍是 4/8 -> 解析不出来时手工补一遍
        if (applyUid == 0 && payload != null && payload.length > 0) {
            try {
                com.google.protobuf.CodedInputStream cis =
                        com.google.protobuf.CodedInputStream.newInstance(payload);
                while (!cis.isAtEnd()) {
                    int tag = cis.readTag();
                    int fn = tag >>> 3;
                    int wt = tag & 7;
                    if (fn == 12 && wt == 0) {
                        applyUid = cis.readUInt32();
                    } else if (fn == 9 && wt == 0) {
                        isAgreed = cis.readUInt32() != 0;
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

        if (applyUid == 0) {
            return;
        }

        session.getServer().getMultiplayerSystem().applyEnterMpReply(session.getPlayer(), applyUid, isAgreed);

        // ResultRsp 的 7.1 CmdId 还没抓到：为 0 时先不发，避免发个 opcode=0 的包出去
        if (PacketOpcodes.PlayerApplyEnterMpResultRsp != 0) {
            try {
                session.send(new PacketPlayerApplyEnterMpResultRsp(applyUid, isAgreed));
            } catch (Throwable t) {
            }
        }
    }
}
