package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.achievement.AchievementWatchers;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WatcherEventStageNotifyOuterClass.WatcherEventStageNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.WatcherEventStageNotify)
public class HandlerWatcherEventStageNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var notify = WatcherEventStageNotify.parseFrom(payload);
        emu.grasscutter.Grasscutter.getLogger()
                .debug(
                        "[Watcher] uid={} stage event watcher={} stage={} add={}",
                        session.getPlayer().getUid(),
                        notify.getWatcherId(),
                        notify.getStage(),
                        notify.getAddProgress());
        AchievementWatchers.onWatcherEvent(
                session.getPlayer(), notify.getWatcherId(), notify.getAddProgress());
    }
}
