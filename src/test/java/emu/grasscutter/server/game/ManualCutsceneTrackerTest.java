package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public final class ManualCutsceneTrackerTest {
    @Test
    void manualAcknowledgementConsumesOnlyTheMatchingIdOnce() {
        var clock = new AtomicLong();
        var tracker = new ManualCutsceneTracker(clock::get);
        assertTrue(tracker.register(150));
        assertTrue(tracker.register(151));
        assertFalse(tracker.register(150));
        assertFalse(tracker.consume(152));
        assertTrue(tracker.consume(150));
        assertFalse(tracker.consume(150));
        assertTrue(tracker.consume(151));
    }

    @Test
    void expiredAndCancelledPlaybackCannotSuppressGameplay() {
        var clock = new AtomicLong();
        var tracker = new ManualCutsceneTracker(clock::get);
        assertFalse(tracker.register(0));
        assertTrue(tracker.register(150));
        tracker.cancel(150);
        assertFalse(tracker.consume(150));

        assertTrue(tracker.register(150));
        clock.addAndGet(ManualCutsceneTracker.EXPIRY_NANOS);
        assertFalse(tracker.consume(150));
        assertTrue(tracker.register(150));
        assertTrue(tracker.consume(150));
    }

    @Test
    void longRunningPlaybackDoesNotEvictOtherFreshIds() {
        var clock = new AtomicLong();
        var tracker = new ManualCutsceneTracker(clock::get);
        assertTrue(tracker.register(1));
        clock.addAndGet(ManualCutsceneTracker.EXPIRY_NANOS - 1);
        assertTrue(tracker.register(2));
        clock.incrementAndGet();
        assertFalse(tracker.consume(1));
        assertTrue(tracker.consume(2));
    }
}
