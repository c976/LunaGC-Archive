package emu.grasscutter.server.game;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Tracks only GM-triggered cutscenes within one game session. Client finishes for these
 * packets must be acknowledged, but must not advance scripts or tower transitions.
 */
final class ManualCutsceneTracker {
    static final long EXPIRY_NANOS = TimeUnit.MINUTES.toNanos(5);

    private final Map<Integer, Long> pending = new HashMap<>();
    private final LongSupplier clock;

    ManualCutsceneTracker() {
        this(System::nanoTime);
    }

    ManualCutsceneTracker(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized boolean register(int id) {
        if (id <= 0) return false;
        long now = clock.getAsLong();
        expire(now);
        if (pending.containsKey(id)) return false;
        pending.put(id, now);
        return true;
    }

    synchronized void cancel(int id) {
        pending.remove(id);
    }

    synchronized boolean consume(int id) {
        long now = clock.getAsLong();
        Long since = pending.remove(id);
        expire(now);
        return since != null && now - since < EXPIRY_NANOS;
    }

    private void expire(long now) {
        pending.values().removeIf(since -> now - since >= EXPIRY_NANOS);
    }
}
