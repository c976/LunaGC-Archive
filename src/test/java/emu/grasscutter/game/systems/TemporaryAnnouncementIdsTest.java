package emu.grasscutter.game.systems;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public final class TemporaryAnnouncementIdsTest {
    @Test
    void uniqueIdsSkipConfiguredAndCanBeRevokedOnce() {
        var clock = new AtomicLong(1000);
        var ids = new TemporaryAnnouncementIds(clock::get);
        int first = ids.issue(Set.of(10000));
        int second = ids.issue(Set.of(10000));
        assertEquals(10001, first);
        assertEquals(10002, second);
        assertTrue(ids.revoke(first));
        assertFalse(ids.revoke(first));
        assertTrue(ids.revoke(second));
    }

    @Test
    void expirationMakesRevocationFail() {
        var clock = new AtomicLong();
        var ids = new TemporaryAnnouncementIds(clock::get);
        int id = ids.issue(Set.of());
        clock.addAndGet(TemporaryAnnouncementIds.LIFETIME_MILLIS);
        assertFalse(ids.revoke(id));
        assertFalse(ids.revoke(-1));
    }
}
