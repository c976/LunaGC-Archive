package emu.grasscutter.server.threading;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-pool task counters, written from the pool's own threads and read by status readouts.
 *
 * <p>Every counter is atomic rather than guarded by a lock: these are written on the hot path of
 * every submit and every completion, and a status readout that sees a slightly stale count is
 * harmless where contention on a shared lock would not be.
 */
public final class ThreadPoolStats {
    private static final int SLOW_TASK_LIMIT = 10;

    private final AtomicLong taskIds = new AtomicLong();
    private final AtomicLong submittedTasks = new AtomicLong();
    private final AtomicLong startedTasks = new AtomicLong();
    private final AtomicLong completedTasks = new AtomicLong();
    private final AtomicLong failedTasks = new AtomicLong();
    private final AtomicLong rejectedTasks = new AtomicLong();
    private final AtomicLong totalExecutionNanos = new AtomicLong();
    private final AtomicLong maxExecutionNanos = new AtomicLong();
    private final ConcurrentHashMap<Long, TaskTiming> activeTasks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SlowTask> slowTasks = new ConcurrentLinkedQueue<>();

    long recordSubmitted() {
        long id = this.taskIds.incrementAndGet();
        this.submittedTasks.incrementAndGet();
        this.activeTasks.put(id, new TaskTiming(System.currentTimeMillis()));
        return id;
    }

    void recordStarted(long id) {
        this.startedTasks.incrementAndGet();
        TaskTiming timing = this.activeTasks.get(id);
        if (timing != null) timing.startedAtMillis = System.currentTimeMillis();
    }

    void recordRejected(long id) {
        this.rejectedTasks.incrementAndGet();
        this.activeTasks.remove(id);
    }

    void recordCompleted(long id, long nanos, boolean failed) {
        TaskTiming timing = this.activeTasks.remove(id);
        if (timing != null) timing.completedAtMillis = System.currentTimeMillis();
        this.completedTasks.incrementAndGet();
        if (failed) this.failedTasks.incrementAndGet();
        this.totalExecutionNanos.addAndGet(nanos);
        this.maxExecutionNanos.accumulateAndGet(nanos, Math::max);
        this.addSlowTask(nanos, failed);
    }

    private void addSlowTask(long nanos, boolean failed) {
        this.slowTasks.add(new SlowTask(System.currentTimeMillis(), nanos, failed));
        // Kept at twice the reported limit so slowestTasks() still has a pool to sort from.
        while (this.slowTasks.size() > SLOW_TASK_LIMIT * 2) this.slowTasks.poll();
    }

    public long submittedTasks() {
        return this.submittedTasks.get();
    }

    public long startedTasks() {
        return this.startedTasks.get();
    }

    public long completedTasks() {
        return this.completedTasks.get();
    }

    public long failedTasks() {
        return this.failedTasks.get();
    }

    public long rejectedTasks() {
        return this.rejectedTasks.get();
    }

    public long averageExecutionMillis() {
        long completed = this.completedTasks.get();
        return completed == 0 ? 0 : this.totalExecutionNanos.get() / completed / 1_000_000L;
    }

    public long maxExecutionMillis() {
        return this.maxExecutionNanos.get() / 1_000_000L;
    }

    public List<SlowTask> slowestTasks() {
        return this.slowTasks.stream()
                .sorted(Comparator.comparingLong(SlowTask::executionNanos).reversed())
                .limit(SLOW_TASK_LIMIT)
                .toList();
    }

    private static final class TaskTiming {
        private final long submittedAtMillis;
        private volatile long startedAtMillis;
        private volatile long completedAtMillis;

        private TaskTiming(long submittedAtMillis) {
            this.submittedAtMillis = submittedAtMillis;
        }
    }

    public record SlowTask(long completedAtMillis, long executionNanos, boolean failed) {
        public long executionMillis() {
            return this.executionNanos / 1_000_000L;
        }
    }
}
