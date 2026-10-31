package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.PingReqOuterClass.PingReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.ProtoRead;
import emu.grasscutter.server.packet.send.PacketPingRsp;

@Opcodes(PacketOpcodes.PingReq)
public class HandlerPingReq extends PacketHandler {

    // Read with ProtoRead: fields protobuf does not recognise would read as 0, leaving the last-ping
    // time at 0. The numbers come from the generated class so they follow the protocol (they used
    // to be pinned to a 7.0 capture, 1 and 7; in 7.1 client_time is 7 and seq is 12).
    private static final int F_CLIENT_TIME = PingReq.CLIENT_TIME_FIELD_NUMBER;
    private static final int F_SEQ = PingReq.SEQ_FIELD_NUMBER;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PacketHead head = PacketHead.parseFrom(header);
        var clientTime = (int) ProtoRead.varint(payload, F_CLIENT_TIME);
        var seq = (int) ProtoRead.varint(payload, F_SEQ);

        session.updateLastPingTime(clientTime);

        session.send(new PacketPingRsp(head.getClientSequenceId(), clientTime, seq));
    }
}
