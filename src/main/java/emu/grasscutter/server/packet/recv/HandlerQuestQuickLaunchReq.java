package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestQuickLaunchReq._QuestQuickLaunchReq;
import emu.grasscutter.net.proto.QuestQuickLaunchRsp._QuestQuickLaunchRsp;
import emu.grasscutter.server.game.GameSession;

/** Quest quick launch: had no handler, so the client got no reply. Accept it and echo the request. */
@Opcodes(PacketOpcodes._QuestQuickLaunchReq)
public class HandlerQuestQuickLaunchReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = _QuestQuickLaunchReq.parseFrom(payload);
        Grasscutter.getLogger()
                .info(
                        "QuestQuickLaunchReq uid={} quest={} focus={}",
                        session.getPlayer().getUid(),
                        req.getQuestId(),
                        req.getIsEnterFocusMode());

        // 原来这里只回一个空响应，任务永远不会进任务列表。
        // 客户端按“快速启动/接取”发出的就是这个包，所以这里要真把任务加进去。
        int miaoQuestId = req.getQuestId();
        if (miaoQuestId > 0) {
            try {
                var miaoQuest =
                        session.getPlayer().getQuestManager().addQuest(miaoQuestId);
            } catch (Throwable t) {
            }
        }

        var rsp =
                _QuestQuickLaunchRsp.newBuilder()
                        .setQuestId(req.getQuestId())
                        .setIsEnterFocusMode(req.getIsEnterFocusMode())
                        .build();
        var packet = new BasePacket(PacketOpcodes._QuestQuickLaunchRsp);
        packet.setData(rsp);
        session.send(packet);
    }
}
