package emu.grasscutter.game.systems;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** Short-lived server-issued announcement IDs, distinct from configured template IDs. */
final class TemporaryAnnouncementIds {
    static final int FIRST_ID = 10000;
    static final int LAST_ID = 99998;
    static final long LIFETIME_MILLIS = 3000;
    private final Map<Integer, Long> live = new HashMap<>();
    private final LongSupplier clock;
    private int next = FIRST_ID;

    TemporaryAnnouncementIds() {
        this(System::currentTimeMillis);
    }

    TemporaryAnnouncementIds(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized int issue(Set<Integer> configured) {
        long now = clock.getAsLong();
        live.entrySet().removeIf(entry -> entry.getValue() <= now);
        int count = LAST_ID - FIRST_ID + 1;
        for (int i = 0; i < count; i++) {
            int candidate = next;
            next = candidate == LAST_ID ? FIRST_ID : candidate + 1;
            if (!configured.contains(candidate) && !live.containsKey(candidate)) {
                live.put(candidate, now + LIFETIME_MILLIS);
                return candidate;
            }
        }
        throw new IllegalStateException("No available temporary announcement IDs");
    }

    synchronized boolean revoke(int id) {
        long now = clock.getAsLong();
        Long expiry = live.remove(id);
        return expiry != null && expiry > now;
    }
}
