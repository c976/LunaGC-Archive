package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetBattlePassViewedReqOuterClass.SetBattlePassViewedReq;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetBattlePassViewedRsp;

@Opcodes(PacketOpcodes.SetBattlePassViewedReq)
public class HandlerSetBattlePassViewedReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int scheduleId = 6700;
        try {
            var req = SetBattlePassViewedReq.parseFrom(payload);
            if (req.getScheduleId() > 0) scheduleId = req.getScheduleId();
        } catch (Throwable ignored) {
        }
        if (payload != null && payload.length > 0 && scheduleId == 6700) {
            try {
                UnknownFieldSet fields = UnknownFieldSet.parseFrom(payload);
                for (int fieldNumber : new int[] {2, 1, 7, 12, 5}) {
                    UnknownFieldSet.Field field = fields.getField(fieldNumber);
                    if (field != null && !field.getVarintList().isEmpty()) {
                        long value = field.getVarintList().get(0);
                        if (value > 0 && value <= Integer.MAX_VALUE) {
                            scheduleId = (int) value;
                            break;
                        }
                    }
                }
            } catch (Throwable throwable) {
                Grasscutter.getLogger().warn("SetBattlePassViewed parse: {}", throwable.toString());
            }
        }
        if (session.getPlayer() != null && session.getPlayer().getBattlePassManager() != null) {
            session.getPlayer().getBattlePassManager().updateViewed();
        }
        session.send(new PacketSetBattlePassViewedRsp(scheduleId));
    }
}
