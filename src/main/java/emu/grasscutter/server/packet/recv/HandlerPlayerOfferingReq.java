package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.gadget.OfferingHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import com.google.protobuf.CodedInputStream;

/** Offering submit for the Sacred Sakura and similar; live 7.0 opcode is
     * {@link PacketOpcodes#PlayerOfferingReq} (1423). */
@Opcodes(PacketOpcodes.PlayerOfferingReq)
public class HandlerPlayerOfferingReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (player == null) {
            return;
        }
        int offeringId = OfferingHelper.OFFERING_ORAIONOKAMI;
        if (payload != null && payload.length > 0) {
            CodedInputStream in = CodedInputStream.newInstance(payload);
            while (!in.isAtEnd()) {
                int tag = in.readTag();
                if ((tag & 7) == 0) {
                    int v = in.readUInt32();
                    if (v >= 1 && v <= 22) {
                        offeringId = v;
                        break;
                    }
                } else {
                    in.skipField(tag);
                }
            }
        }
        OfferingHelper.tryLevelUp(player, offeringId, header);
    }
}
