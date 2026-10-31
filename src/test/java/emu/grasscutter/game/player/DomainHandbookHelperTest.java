package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DomainHandbookHelperTest {
    @Test
    void handbookEntriesNeverUnlockFreshPlayers() {
        var unlocked = new HashSet<Integer>();
        var forceLocked = new HashSet<Integer>();
        assertEquals(
                List.of(),
                DomainHandbookHelper.alreadyUnlockedEntries(
                        List.of(48, 44, 131), unlocked, forceLocked));
        assertTrue(unlocked.isEmpty());
    }

    @Test
    void onlyAlreadyDiscoveredAndUnlockedEntriesAreResent() {
        var unlocked = new HashSet<>(Set.of(44, 100, 131));
        var forceLocked = new HashSet<>(Set.of(131));
        assertEquals(
                List.of(44, 100),
                DomainHandbookHelper.alreadyUnlockedEntries(
                        List.of(48, 44, 131, 100), unlocked, forceLocked));
        assertEquals(Set.of(44, 100, 131), unlocked);
        assertEquals(Set.of(131), forceLocked);
    }
}
