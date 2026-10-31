package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.systems.ReliquaryDustSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

@Opcodes(ReliquaryDustSystem.OPCODE_DUST_SELECT_REQ)
public class HandlerReliquaryDustSelectReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        int seq = 0;
        try {
            if (header != null && header.length > 0) {
                seq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Exception ignored) {
        }
        // Pass raw header so SelectRsp can echo 7.0 PacketHead fields 7–10 intact.
        ReliquaryDustSystem.handleSelect(session.getPlayer(), payload, seq, header);
    }
}
