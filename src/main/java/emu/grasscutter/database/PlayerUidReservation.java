package emu.grasscutter.database;

/** Pure allocation policy; the store supplies the database-level atomic operations. */
final class PlayerUidReservation {
    static final int FIRST_UID = 1;
    private static final int MAX_PROBES = 10_000;

    interface Store {
        int highestUid();

        boolean playerExists(int uid);

        boolean tryReserve(int uid);

        int reserveNext(int floor);
    }

    private PlayerUidReservation() {}

    static int next(int requestedUid, Store store) {
        if (requestedUid > 0
                && !store.playerExists(requestedUid)
                && store.tryReserve(requestedUid)
                && !store.playerExists(requestedUid)) {
            return requestedUid;
        }

        int floor = Math.max(0, store.highestUid());
        for (int probe = 0; probe < MAX_PROBES; probe++) {
            int candidate = store.reserveNext(floor);
            floor = Math.max(floor, candidate);
            if (candidate < FIRST_UID) continue;
            if (store.playerExists(candidate)) continue;
            if (!store.tryReserve(candidate)) continue;
            if (!store.playerExists(candidate)) return candidate;
        }

        throw new IllegalStateException("Could not reserve a free player UID");
    }
}
