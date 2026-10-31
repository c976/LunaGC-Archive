package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.gadget.OfferingHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import com.google.protobuf.CodedInputStream;
import emu.grasscutter.net.proto.TakeOfferingLevelRewardReqOuterClass.TakeOfferingLevelRewardReq;

/** Claim from the offering reward page; field numbers from the generated TakeOfferingLevelRewardReq. */
@Opcodes(PacketOpcodes.TakeOfferingLevelRewardReq)
public class HandlerTakeOfferingLevelRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (player == null) {
            return;
        }
        int offeringId = OfferingHelper.OFFERING_ORAIONOKAMI;
        int takeLevel = 0;
        if (payload != null && payload.length > 0) {
            CodedInputStream in = CodedInputStream.newInstance(payload);
            while (!in.isAtEnd()) {
                int tag = in.readTag();
                int field = tag >>> 3;
                int wt = tag & 7;
                if (wt != 0) {
                    in.skipField(tag);
                    continue;
                }
                int v = in.readUInt32();
                if (field == TakeOfferingLevelRewardReq.OFFERING_ID_FIELD_NUMBER) {
                    offeringId = v;
                } else if (field == TakeOfferingLevelRewardReq.LEVEL_FIELD_NUMBER) {
                    takeLevel = v;
                }
            }
        }
        if (takeLevel <= 0) {
            takeLevel = 1;
        }
        OfferingHelper.tryTakeLevelReward(player, offeringId, takeLevel, header);
    }
}
