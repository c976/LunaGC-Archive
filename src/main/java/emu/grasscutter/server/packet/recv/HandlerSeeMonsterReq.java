package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SeeMonsterReqOuterClass.SeeMonsterReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAddSeenMonsterNotify;
import emu.grasscutter.server.packet.send.PacketSeeMonsterRsp;

@Opcodes(PacketOpcodes.SeeMonsterReq)
public class HandlerSeeMonsterReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int monsterId = 0;
        try {
            if (payload != null && payload.length > 0) {
                monsterId = SeeMonsterReq.parseFrom(payload).getMonsterId();
            }
        } catch (Throwable ignored) {
            monsterId = readVarintField(payload, 1);
        }

        if (monsterId > 0) {
            session.send(new PacketAddSeenMonsterNotify(monsterId));
        }
        session.send(new PacketSeeMonsterRsp(0));
    }

    private static int readVarintField(byte[] data, int fieldNum) {
        if (data == null || data.length == 0) {
            return 0;
        }
        int i = 0;
        while (i < data.length) {
            int tag = data[i] & 0xFF;
            i++;
            int fn = tag >>> 3;
            int wt = tag & 0x7;
            if (wt != 0) {
                break;
            }
            long val = 0;
            int shift = 0;
            while (i < data.length) {
                int b = data[i++] & 0xFF;
                val |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }
            if (fn == fieldNum) {
                return (int) val;
            }
        }
        return 0;
    }
}
