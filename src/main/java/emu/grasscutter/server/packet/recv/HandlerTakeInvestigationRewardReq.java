package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.game.player.InvestigationHandbookHelper;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeInvestigationRewardRsp;
import java.io.IOException;

/** TakeInvestigationRewardReq (7.1 CmdId 8841): id = 6, the handbook chapter. */
@Opcodes(PacketOpcodes.TakeInvestigationRewardReq)
public class HandlerTakeInvestigationRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int chapterId = readUInt32Field(payload, 6);
        int retcode = InvestigationHandbookHelper.claimChapterReward(session.getPlayer(), chapterId);
        session.send(new PacketTakeInvestigationRewardRsp(chapterId, retcode));
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
