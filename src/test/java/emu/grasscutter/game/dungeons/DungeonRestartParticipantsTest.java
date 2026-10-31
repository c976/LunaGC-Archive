package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DungeonRestartParticipantsTest {
    @Test
    void singlePlayerDetachesWithoutConcurrentModification() {
        var live = new ArrayList<>(List.of(70632));
        var removed = DungeonRestartParticipants.detachAll(
                live, p -> live.remove((Object) p));
        assertEquals(List.of(70632), removed);
        assertTrue(live.isEmpty());
    }

    @Test
    void fullPartySnapshotSurvivesLiveListMutation() {
        var live = new ArrayList<>(List.of(1, 2, 3));
        var removed = DungeonRestartParticipants.detachAll(
                live, p -> live.remove((Object) p));
        assertEquals(List.of(1, 2, 3), removed);
        assertTrue(live.isEmpty());
    }
}
