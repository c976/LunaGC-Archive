package emu.grasscutter.game.dungeons;

import java.util.List;
import java.util.function.Consumer;

/** Take a stable snapshot before scene removal mutates the live player list. */
final class DungeonRestartParticipants {
    private DungeonRestartParticipants() {}

    static <T> List<T> detachAll(List<T> live, Consumer<? super T> detach) {
        var participants = List.copyOf(live);
        participants.forEach(detach);
        return participants;
    }
}
