package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ExecuteGroupTriggerReqOuterClass.ExecuteGroupTriggerReq;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketExecuteGroupTriggerRsp;

/**
 * The client firing a group trigger itself: a gadget it was told to watch, a puzzle step or a quest
 * beat played out on its side. The group's Lua listens for it as EVENT_CLIENT_EXECUTE, matched on
 * source_name; with nobody handling the request those triggers never ran.
 */
@Opcodes(PacketOpcodes.ExecuteGroupTriggerReq)
public class HandlerExecuteGroupTriggerReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = ExecuteGroupTriggerReq.parseFrom(payload);
        var player = session.getPlayer();
        var scene = player == null ? null : player.getScene();

        if (scene != null && scene.getScriptManager() != null) {
            // The trigger lives in the source entity's group; 0 reaches every loaded group.
            var source = scene.getEntityById(req.getSourceEntityId());
            int groupId = source == null ? 0 : source.getGroupId();

            scene.getScriptManager()
                    .callEvent(
                            new ScriptArgs(groupId, EventType.EVENT_CLIENT_EXECUTE)
                                    .setSourceEntityId(req.getSourceEntityId())
                                    .setTargetEntityId(req.getTargetEntityId())
                                    .setParam1(req.getParam1())
                                    .setParam2(req.getParam2())
                                    .setParam3(req.getParam3())
                                    .setEventSource(req.getSourceName()));
        }

        session.send(new PacketExecuteGroupTriggerRsp(req.getSourceName()));
    }
}
