package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReadPrivateChatRspOuterClass.ReadPrivateChatRsp;
import emu.grasscutter.server.game.GameSession;

/**
 * Sent when a private chat window is opened. The server keeps no unread state, so it only
 * acknowledges; unanswered, the client re-sent it every time the chat was shown.
 */
@Opcodes(PacketOpcodes.ReadPrivateChatReq)
public class HandlerReadPrivateChatReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var rsp = new BasePacket(PacketOpcodes.ReadPrivateChatRsp);
        rsp.setData(ReadPrivateChatRsp.newBuilder().setRetcode(0).build());
        session.send(rsp);
    }
}
