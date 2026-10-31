package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.game.player.InvestigationHandbookHelper;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeInvestigationTargetRewardRsp;
import java.io.IOException;

/** TakeInvestigationTargetRewardReq: quest_id = 9 */
@Opcodes(PacketOpcodes.TakeInvestigationTargetRewardReq)
public class HandlerTakeInvestigationTargetRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int questId = readUInt32Field(payload, 9);
        int retcode = InvestigationHandbookHelper.claimTargetReward(session.getPlayer(), questId);
        session.send(new PacketTakeInvestigationTargetRewardRsp(questId, retcode));
    }

    private static int readUInt32Field(byte[] payload, int fieldNum) {
        if (payload == null || payload.length == 0) {
            return 0;
        }
        try {
            CodedInputStream input = CodedInputStream.newInstance(payload);
            int value = 0;
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                int field = tag >>> 3;
                if (field == fieldNum) {
                    if ((tag & 7) != 0) {
                        return 0;
                    }
                    value = input.readUInt32();
                } else if (!input.skipField(tag)) {
                    return 0;
                }
            }
            return value;
        } catch (IOException ignored) {
        }
        return 0;
    }
}
