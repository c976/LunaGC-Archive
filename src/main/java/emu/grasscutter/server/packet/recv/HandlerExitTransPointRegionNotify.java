package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ExitTransPointRegionNotifyOuterClass.ExitTransPointRegionNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.ExitTransPointRegionNotify)
public class HandlerExitTransPointRegionNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        try {
            ExitTransPointRegionNotify notify = ExitTransPointRegionNotify.parseFrom(payload);
            if (player != null && notify.getSceneId() > 0 && notify.getPointId() > 0) {
                player.getProgressManager()
                        .clearStatueGoddessNotify(notify.getSceneId(), notify.getPointId());
            }
        } catch (Throwable ignored) {
        }
        if (player != null) {
            player.getSotsManager().handleExitTransPointRegionNotify();
        }
    }
}
