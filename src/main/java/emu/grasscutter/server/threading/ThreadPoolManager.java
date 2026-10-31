package emu.grasscutter.server.threading;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** The registry every managed pool adds itself to, and the one place that judges their numbers. */
public final class ThreadPoolManager {
    /** A pool whose average task takes this long is treated as a bottleneck. */
    private static final long SLOW_TASK_MILLIS = 5_000L;

    private static final ThreadPoolManager INSTANCE = new ThreadPoolManager();

    private final Map<String, ManagedThreadPool> pools = new ConcurrentHashMap<>();

    private ThreadPoolManager() {}

    public static ThreadPoolManager getInstance() {
        return INSTANCE;
    }

    void register(ManagedThreadPool executor) {
        this.pools.put(executor.getConfig().name(), executor);
    }

    public Optional<ManagedThreadPool> get(String name) {
        return Optional.ofNullable(this.pools.get(name));
    }

    public Collection<ManagedThreadPool> getAll() {
        return this.pools.values();
    }

    public void shutdown(String name) {
        this.get(name).ifPresent(pool -> pool.asThreadPoolExecutor().shutdown());
    }

    public void shutdownNow(String name) {
        this.get(name).ifPresent(pool -> pool.asThreadPoolExecutor().shutdownNow());
    }

    public void shutdownAll() {
        this.pools.values().forEach(pool -> pool.asThreadPoolExecutor().shutdown());
    }

    public void shutdownAllNow() {
        this.pools.values().forEach(pool -> pool.asThreadPoolExecutor().shutdownNow());
    }

    /** Waits for every pool to terminate, with {@code timeout} covering all of them together. */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (ManagedThreadPool pool : this.pools.values()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0
                    || !pool.asThreadPoolExecutor()
                            .awaitTermination(remaining, TimeUnit.NANOSECONDS)) return false;
        }
        return true;
    }

    public void resize(String name, int corePoolSize, int maximumPoolSize) {
        this.get(name)
                .ifPresent(
                        pool -> {
                            if (maximumPoolSize < corePoolSize)
                                throw new IllegalArgumentException(
                                        "maximumPoolSize must be >= corePoolSize");
                            ThreadPoolExecutor executor = pool.asThreadPoolExecutor();
                            // Maximum first: setCorePoolSize rejects a core above the current
                            // maximum, so raising both in the other order would throw.
                            executor.setMaximumPoolSize(maximumPoolSize);
                            executor.setCorePoolSize(corePoolSize);
                        });
    }

    public ThreadPoolSnapshot snapshot(ManagedThreadPool pool) {
        ThreadPoolExecutor executor = pool.asThreadPoolExecutor();
        var stats = pool.getStats();
        int remainingCapacity = executor.getQueue().remainingCapacity();
        int queueSize = executor.getQueue().size();
        int queueCapacity =
                remainingCapacity == Integer.MAX_VALUE ? -1 : queueSize + remainingCapacity;
        ThreadPoolDiagnosis diagnosis = diagnose(executor, queueCapacity, queueSize, stats);
        return new ThreadPoolSnapshot(
                pool.getConfig().name(),
                pool.getConfig().type(),
                pool.getCreatedAtMillis(),
                lifecycle(executor),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                queueSize,
                queueCapacity,
                executor.getCompletedTaskCount(),
                stats.submittedTasks(),
                stats.failedTasks(),
                stats.rejectedTasks(),
                stats.averageExecutionMillis(),
                stats.maxExecutionMillis(),
                health(executor, queueCapacity, queueSize, stats),
                diagnosis);
    }

    private static String lifecycle(ThreadPoolExecutor executor) {
        if (executor.isTerminated()) return "TERMINATED";
        if (executor.isTerminating()) return "TERMINATING";
        if (executor.isShutdown()) return "SHUTDOWN";
        return "RUNNING";
    }

    /** An unbounded queue has no ratio to report, so it never contributes to the verdict. */
    private static double queueRatio(int capacity, int size) {
        return capacity <= 0 ? 0D : (double) size / capacity;
    }

    private static ThreadPoolHealth health(
            ThreadPoolExecutor executor, int capacity, int size, ThreadPoolStats stats) {
        double ratio = queueRatio(capacity, size);
        boolean saturated =
                executor.getMaximumPoolSize() > 0
                        && executor.getActiveCount() >= executor.getMaximumPoolSize();
        if (stats.rejectedTasks() > 0
                || ratio >= 0.9D
                || stats.averageExecutionMillis() >= SLOW_TASK_MILLIS) return ThreadPoolHealth.DANGER;
        if (stats.failedTasks() > 0 || ratio >= 0.7D || saturated) return ThreadPoolHealth.WARNING;
        if (ratio >= 0.4D || executor.getActiveCount() >= executor.getCorePoolSize())
            return ThreadPoolHealth.BUSY;
        return ThreadPoolHealth.NORMAL;
    }

    private static ThreadPoolDiagnosis diagnose(
            ThreadPoolExecutor executor, int capacity, int size, ThreadPoolStats stats) {
        double ratio = queueRatio(capacity, size);
        if (stats.rejectedTasks() > 0) return ThreadPoolDiagnosis.REJECTED_TASKS;
        if (stats.averageExecutionMillis() >= SLOW_TASK_MILLIS) return ThreadPoolDiagnosis.SLOW_TASKS;
        if (ratio >= 0.7D) return ThreadPoolDiagnosis.QUEUE_BACKLOG;
        if (executor.getActiveCount() >= executor.getMaximumPoolSize())
            return ThreadPoolDiagnosis.THREADS_SATURATED;
        if (stats.failedTasks() > 0) return ThreadPoolDiagnosis.FAILING_TASKS;
        return health(executor, capacity, size, stats) == ThreadPoolHealth.NORMAL
                ? ThreadPoolDiagnosis.NONE
                : ThreadPoolDiagnosis.ELEVATED_LOAD;
    }
}
