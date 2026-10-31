package emu.grasscutter.database;

import java.time.Duration;

/**
 * Freezes external database write admission and drains accepted writes, including caller-runs and
 * direct writes. The owning maintenance operation may write synchronously until this is closed.
 */
final class DatabaseWriteBarrier implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final DatabaseWriterManager.Barrier barrier;

    private DatabaseWriteBarrier(DatabaseWriterManager.Barrier barrier) {
        this.barrier = barrier;
    }

    static DatabaseWriteBarrier acquire() {
        return new DatabaseWriteBarrier(DatabaseHelper.getWriterManager().acquireBarrier(TIMEOUT));
    }

    @Override
    public void close() {
        barrier.close();
    }
}
