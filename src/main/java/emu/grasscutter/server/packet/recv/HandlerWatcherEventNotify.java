package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.achievement.AchievementWatchers;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WatcherEventNotifyOuterClass.WatcherEventNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.WatcherEventNotify)
public class HandlerWatcherEventNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var notify = WatcherEventNotify.parseFrom(payload);
        AchievementWatchers.onWatcherEvent(
                session.getPlayer(), notify.getWatcherId(), notify.getAddProgress());
    }
}
