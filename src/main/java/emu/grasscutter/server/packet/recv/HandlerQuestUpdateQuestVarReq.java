package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestUpdateQuestVarReqOuterClass.QuestUpdateQuestVarReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketQuestUpdateQuestVarRsp;

@Opcodes(PacketOpcodes.QuestUpdateQuestVarReq)
public class HandlerQuestUpdateQuestVarReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Client sends packets. One with the value, and one with the index and the new value to
        // set/inc/dec
        var req = QuestUpdateQuestVarReq.parseFrom(payload);
        var questManager = session.getPlayer().getQuestManager();
        var subQuest = questManager.getQuestById(req.getQuestId());
        var mainQuest = questManager.getMainQuestById(req.getParentQuestId());
        if (mainQuest == null && subQuest != null) {
            mainQuest = subQuest.getMainQuest();
        }

        if (mainQuest == null) {
            session.send(new PacketQuestUpdateQuestVarRsp(req, Retcode.RET_QUEST_NOT_EXIST));
            Grasscutter.getLogger()
                    .debug(
                            "trying to update QuestVar for non existing quest s{} m{}",
                            req.getQuestId(),
                            req.getParentQuestId());
            return;
        }

        // 7.0 proto only carries parent quest ids; acknowledge sync without mutating vars here.
        session.send(new PacketQuestUpdateQuestVarRsp(req));
    }
}
