package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AddQuestContentProgressBatchReq._AddQuestContentProgressBatchReq;
import emu.grasscutter.server.game.GameSession;

/**
 * 7.1 reports several quest content steps in one request instead of one AddQuestContentProgressReq
 * each. Nothing handled it, so progress the client reported this way was dropped and the quests
 * waiting on it never moved. Each entry is queued the way the single request queues its one.
 *
 * <p>The matching response's CmdId is not known for 7.1, so none is sent; the single request's
 * response carries nothing the client acts on beyond its content type either.
 */
@Opcodes(PacketOpcodes._AddQuestContentProgressBatchReq)
public class HandlerAddQuestContentProgressBatchReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = _AddQuestContentProgressBatchReq.parseFrom(payload);
        var questManager = session.getPlayer().getQuestManager();

        for (var info : req.getProgressInfoListList()) {
            var type = QuestContent.getContentTriggerByValue(info.getContentType());
            if (type != null) {
                questManager.queueEvent(type, info.getParam());
            }
        }
    }
}
