package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetBeyondPlayerInfoRsp;

/**
 * 7.1 _GetBeyondPlayerInfoReq (5960 = KPGOJJPCEME { repeated uint32 HEBFBENKILL = 14; uint32 ECKDAAHALPA = 10; }).
 * 客户端拿它要 Beyond 玩家信息（个人资料页数据）。
 */
@Opcodes(PacketOpcodes._GetBeyondPlayerInfoReq)
public class HandlerGetBeyondPlayerInfoReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {

        int seq = 0;
        try {
            if (header != null && header.length > 0) {
                seq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Exception ignored) {
        }
        int type = 0;
        try {
            int i = 0;
            while (i < payload.length) {
                int tag = payload[i++] & 0xFF;
                int field = tag >> 3;
                int wire = tag & 7;
                if (wire == 0) {
                    long v = 0;
                    int sh = 0;
                    while (i < payload.length) {
                        int b = payload[i++] & 0xFF;
                        v |= (long) (b & 0x7F) << sh;
                        if ((b & 0x80) == 0) break;
                        sh += 7;
                    }
                    if (field == 10) type = (int) v;
                } else if (wire == 2) {
                    long len = 0;
                    int sh = 0;
                    while (i < payload.length) {
                        int b = payload[i++] & 0xFF;
                        len |= (long) (b & 0x7F) << sh;
                        if ((b & 0x80) == 0) break;
                        sh += 7;
                    }
                    i += (int) len;
                } else if (wire == 5) {
                    i += 4;
                } else if (wire == 1) {
                    i += 8;
                } else {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        session.send(new PacketGetBeyondPlayerInfoRsp(type, seq));
    }
}
