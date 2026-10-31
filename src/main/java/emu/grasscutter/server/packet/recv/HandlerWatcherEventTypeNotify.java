package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.achievement.AchievementWatchers;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WatcherEventTypeNotifyOuterClass.WatcherEventTypeNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.WatcherEventTypeNotify)
public class HandlerWatcherEventTypeNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var notify = WatcherEventTypeNotify.parseFrom(payload);
        AchievementWatchers.onTypeEvent(
                session.getPlayer(),
                notify.getWatcherTriggerType(),
                notify.getParamListList(),
                notify.getAddProgress());
    }
}
