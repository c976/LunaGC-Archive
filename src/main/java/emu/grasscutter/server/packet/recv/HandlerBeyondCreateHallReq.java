package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBeyondCreateHallRsp;

/**
 * 7.1 BeyondCreateHallReq (26704) 客户端请求创建"超限大厅"(影域 / BeyondHall 体系入口)。 先打印原始 payload 确认字段，再回
 * retcode=0。
 */
@Opcodes(PacketOpcodes._BeyondCreateHallReq)
public class HandlerBeyondCreateHallReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int seq = 0;
        try {
            if (header != null && header.length > 0) {
                seq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Exception ignored) {
        }

        StringBuilder hex = new StringBuilder();
        if (payload != null) {
            for (int k = 0; k < payload.length && k < 256; k++) {
                hex.append(String.format("%02X ", payload[k] & 0xFF));
            }
        }
        Grasscutter.getLogger().debug("[MIAO HALL] >>> BeyondCreateHallReq len={} hex={}",
                payload == null ? 0 : payload.length, hex);

        session.send(new PacketBeyondCreateHallRsp(1L, 0, seq));
    }
}
