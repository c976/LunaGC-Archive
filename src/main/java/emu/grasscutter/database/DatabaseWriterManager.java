package emu.grasscutter.database;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Owns database admission, per-entity write order, maintenance barriers and bounded shutdown. */
public final class DatabaseWriterManager {
    public record PoolShutdownStatus(
            String name, boolean terminated, int activeAtTimeout, int notStartedTasks) {}

    public record ShutdownResult(
            boolean completed, boolean interrupted, List<PoolShutdownStatus> pools) {
        public int activeAtTimeoutTaskCount() {
            return pools.stream().mapToInt(PoolShutdownStatus::activeAtTimeout).sum();
        }

        public int notStartedTaskCount() {
            return pools.stream().mapToInt(PoolShutdownStatus::notStartedTasks).sum();
        }
    }

    private final LinkedHashMap<String, ExecutorService> pools;
    private final ReentrantLock lifecycle = new ReentrantLock(true);
    private final Condition changed = lifecycle.newCondition();
    private final LinkedHashSet<WriteTask> outstanding = new LinkedHashSet<>();
    private final Map<Object, ArrayDeque<WriteTask>> ordered = new HashMap<>();
    private final int orderedCapacity;
    private final ThreadLocal<Integer> writeDepth = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<ArrayDeque<WriteTask>> callerRunQueue = new ThreadLocal<>();
    /** Only queued successors count; heads go straight to their pool, which bounds them. */
    private int orderedOutstanding;
    private int synchronousActive;
    private boolean accepting = true;
    private boolean forceStopping;
    private boolean barrierPending;
    private Thread barrierOwner;
    private int barrierDepth;
    private boolean shutdownInProgress;
    private volatile ShutdownResult shutdownResult;

    DatabaseWriterManager(Map<String, ? extends ExecutorService> pools) {
        this(pools, orderedCapacity(pools));
    }

    DatabaseWriterManager(Map<String, ? extends ExecutorService> pools, int orderedCapacity) {
        if (pools.isEmpty() || orderedCapacity <= 0) {
            throw new IllegalArgumentException("Database writers and a positive capacity are required");
        }
        this.pools = new LinkedHashMap<>(pools);
        this.orderedCapacity = orderedCapacity;
    }

    /** Saturation still runs on the caller; a closed admission gate rejects visibly. */
    void submit(ExecutorService executor, Runnable task) {
        submit(executor, null, false, task);
    }

    /**
     * Serializes equal, stable entity keys across pools. Only the head is dispatched, so workers
     * never wait for a predecessor. The pending queue is bounded and rejects without waiting while
     * the caller might hold an inventory or player monitor.
     */
    void submitOrdered(ExecutorService executor, Object entityKey, Runnable task) {
        submit(executor, Objects.requireNonNull(entityKey, "entityKey"), true, task);
    }

    /**
     * As {@link #submitOrdered}, never rejected for capacity. For callers that queue at most one
     * successor per key, so the queue stays bounded without a caller-run fallback that could
     * reorder writes.
     */
    void submitOrderedUncapped(ExecutorService executor, Object entityKey, Runnable task) {
        submit(executor, Objects.requireNonNull(entityKey, "entityKey"), false, task);
    }

    private void submit(
            ExecutorService executor, Object entityKey, boolean capped, Runnable runnable) {
        Objects.requireNonNull(runnable, "task");
        WriteTask task;
        boolean dispatch;
        lifecycle.lock();
        try {
            requireAdmission();
            if (!pools.containsValue(executor) || executor.isShutdown()) {
                throw new RejectedExecutionException("Database writer is unavailable");
            }
            if (capped && entityKey != null && ordered.containsKey(entityKey) && orderedOutstanding >= orderedCapacity) {
                throw new RejectedExecutionException("Ordered database write capacity is full");
            }
            task = new WriteTask(executor, entityKey, runnable);
            outstanding.add(task);
            if (entityKey == null) {
                dispatch = true;
            } else {
                var queue = ordered.computeIfAbsent(entityKey, ignored -> new ArrayDeque<>());
                dispatch = queue.isEmpty();
                queue.addLast(task);
                if (!dispatch) orderedOutstanding++;
            }
        } finally {
            lifecycle.unlock();
        }
        if (dispatch) dispatch(task);
    }

    private void requireAdmission() {
        if (!accepting) {
            throw new RejectedExecutionException("Database writers are shutting down");
        }
        if (barrierPending || barrierOwner != null) {
            throw new RejectedExecutionException("Database write maintenance barrier is active");
        }
    }

    /**
     * Direct writes share async admission. An admitted writer or barrier owner can call this
     * recursively without waiting for its own outer write to drain.
     */
    <T> T callSynchronous(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        boolean nested;
        lifecycle.lock();
        try {
            if (forceStopping) {
                throw new RejectedExecutionException("Database writers were forcibly stopped");
            }
            nested = writeDepth.get() > 0 || barrierOwner == Thread.currentThread();
            if (!nested) {
                requireAdmission();
                synchronousActive++;
            }
        } finally {
            lifecycle.unlock();
        }
        if (nested) return operation.get();

        int previousDepth = writeDepth.get();
        writeDepth.set(previousDepth + 1);
        try {
            return operation.get();
        } finally {
            restoreWriteDepth(previousDepth);
            lifecycle.lock();
            try {
                synchronousActive--;
                changed.signalAll();
            } finally {
                lifecycle.unlock();
            }
        }
    }

    void runSynchronous(Runnable operation) {
        callSynchronous(
                () -> {
                    operation.run();
                    return null;
                });
    }

    /** Freezes external admission and drains accepted writes against one deadline. */
    Barrier acquireBarrier(Duration timeout) {
        long deadline = deadline(timeout);
        if (writeDepth.get() > 0) {
            throw new IllegalStateException("An admitted database writer cannot wait for a barrier");
        }
        boolean pendingHere = false;
        try {
            if (!lifecycle.tryLock(remaining(deadline), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("Timed out acquiring the database admission lock");
            }
            try {
                if (barrierOwner == Thread.currentThread()) {
                    barrierDepth++;
                    return new Barrier(barrierOwner);
                }
                while (barrierPending || barrierOwner != null) {
                    if (!accepting) {
                        throw new RejectedExecutionException("Database writers are shutting down");
                    }
                    awaitChange(deadline, "Timed out waiting for the database write barrier");
                }
                if (!accepting) {
                    throw new RejectedExecutionException("Database writers are shutting down");
                }
                barrierPending = true;
                pendingHere = true;
                while (!outstanding.isEmpty() || synchronousActive > 0) {
                    awaitChange(deadline, "Timed out draining database writes");
                    if (!accepting) {
                        throw new RejectedExecutionException("Database writers are shutting down");
                    }
                }
                barrierOwner = Thread.currentThread();
                barrierDepth = 1;
                barrierPending = false;
                pendingHere = false;
                return new Barrier(barrierOwner);
            } finally {
                if (pendingHere) {
                    barrierPending = false;
                    changed.signalAll();
                }
                lifecycle.unlock();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while acquiring the database write barrier", interrupted);
        }
    }

    final class Barrier implements AutoCloseable {
        private final Thread owner;
        private boolean closed;

        private Barrier(Thread owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Only the database barrier owner can release it");
            }
            lifecycle.lock();
            try {
                if (closed) return;
                closed = true;
                if (--barrierDepth == 0) {
                    barrierOwner = null;
                    changed.signalAll();
                }
            } finally {
                lifecycle.unlock();
            }
        }
    }

    private void dispatch(WriteTask task) {
        Throwable failure = null;
        while (task != null) {
            RejectedExecutionException rejection;
            try {
                task.executor.execute(task);
                break;
            } catch (RejectedExecutionException rejected) {
                rejection = rejected;
            }
            boolean unavailable;
            WriteTask next = null;
            lifecycle.lock();
            try {
                // An inline executor may propagate the operation's exception after it finished.
                if (task.running || !outstanding.contains(task)) throw rejection;
                unavailable = forceStopping || task.executor.isShutdown();
                if (unavailable) next = cancelWaiting(task);
            } finally {
                lifecycle.unlock();
            }
            if (unavailable) {
                if (failure == null) failure = rejection;
                else if (failure != rejection) failure.addSuppressed(rejection);
                task = next;
                continue;
            }
            try {
                runOnCaller(task);
            } catch (RuntimeException | Error taskFailure) {
                if (failure == null) failure = taskFailure;
                else if (failure != taskFailure) failure.addSuppressed(taskFailure);
            }
            break;
        }
        rethrow(failure);
    }

    private void runOnCaller(WriteTask task) {
        // Avoid recursive stacks when ordered successors all encounter a saturated pool.
        var queue = callerRunQueue.get();
        if (queue != null) {
            queue.addLast(task);
            return;
        }
        queue = new ArrayDeque<>();
        callerRunQueue.set(queue);
        queue.addLast(task);
        Throwable failure = null;
        try {
            while (!queue.isEmpty()) {
                try {
                    queue.removeFirst().run();
                } catch (RuntimeException | Error taskFailure) {
                    if (failure == null) failure = taskFailure;
                    else if (failure != taskFailure) failure.addSuppressed(taskFailure);
                }
            }
        } finally {
            callerRunQueue.remove();
        }
        rethrow(failure);
    }

    private final class WriteTask implements Runnable {
        private final ExecutorService executor;
        private final Object entityKey;
        private final Runnable operation;
        private boolean running;
        private boolean cancelled;

        private WriteTask(ExecutorService executor, Object entityKey, Runnable operation) {
            this.executor = executor;
            this.entityKey = entityKey;
            this.operation = operation;
        }

        @Override
        public void run() {
            lifecycle.lock();
            try {
                if (cancelled) return;
                running = true;
            } finally {
                lifecycle.unlock();
            }
            int previousDepth = writeDepth.get();
            writeDepth.set(previousDepth + 1);
            Throwable failure = null;
            try {
                operation.run();
            } catch (RuntimeException | Error taskFailure) {
                failure = taskFailure;
            } finally {
                restoreWriteDepth(previousDepth);
                WriteTask next = null;
                lifecycle.lock();
                try {
                    outstanding.remove(this);
                    if (entityKey != null) {
                        var queue = ordered.get(entityKey);
                        if (queue.size() > 1) orderedOutstanding--;
                        queue.removeFirst();
                        if (queue.isEmpty()) ordered.remove(entityKey);
                        else next = queue.getFirst();
                    }
                    changed.signalAll();
                } finally {
                    lifecycle.unlock();
                }
                if (next != null) {
                    try {
                        dispatch(next);
                    } catch (RuntimeException | Error dispatchFailure) {
                        if (failure == null) failure = dispatchFailure;
                        else if (failure != dispatchFailure) failure.addSuppressed(dispatchFailure);
                    }
                }
            }
            rethrow(failure);
        }
    }

    private WriteTask cancelWaiting(WriteTask task) {
        if (task.running || task.cancelled) return null;
        WriteTask next = null;
        task.cancelled = true;
        outstanding.remove(task);
        if (task.entityKey != null) {
            var queue = ordered.get(task.entityKey);
            if (queue.size() > 1) orderedOutstanding--;
            boolean wasHead = queue.getFirst() == task;
            queue.remove(task);
            if (queue.isEmpty()) ordered.remove(task.entityKey);
            else if (wasHead && !forceStopping) next = queue.getFirst();
        }
        changed.signalAll();
        return next;
    }

    /** Stops admission and drains async, ordered, caller-run and synchronous writes. */
    public ShutdownResult shutdown(Duration timeout) {
        long deadline = deadline(timeout);
        // Clear an existing interrupt long enough to close admission, then restore it on return.
        boolean interrupted = Thread.interrupted();
        boolean timedOut = interrupted;
        try {
            while (true) {
                try {
                    if (!lifecycle.tryLock(remaining(deadline), TimeUnit.NANOSECONDS)) {
                        return unlockedShutdownFailure(interrupted);
                    }
                    break;
                } catch (InterruptedException lockInterrupted) {
                    interrupted = true;
                    timedOut = true;
                    if (remaining(deadline) == 0) return unlockedShutdownFailure(true);
                }
            }
            try {
                if (barrierOwner == Thread.currentThread() || writeDepth.get() > 0) {
                    throw new IllegalStateException("A database writer cannot drain its own operation");
                }
                while (shutdownInProgress && shutdownResult == null) {
                    long remaining = remaining(deadline);
                    if (interrupted || remaining == 0) return snapshot(false, interrupted);
                    try {
                        changed.awaitNanos(remaining);
                    } catch (InterruptedException stopInterrupted) {
                        interrupted = true;
                        return snapshot(false, true);
                    }
                }
                if (shutdownResult != null) {
                    return interrupted && !shutdownResult.interrupted()
                            ? new ShutdownResult(shutdownResult.completed(), true, shutdownResult.pools())
                            : shutdownResult;
                }
                shutdownInProgress = true;
                accepting = false;
                changed.signalAll();
                while (!timedOut
                        && (!outstanding.isEmpty()
                                || synchronousActive > 0
                                || barrierPending
                                || barrierOwner != null)) {
                    long remaining = remaining(deadline);
                    if (remaining == 0) {
                        timedOut = true;
                        break;
                    }
                    try {
                        changed.awaitNanos(remaining);
                    } catch (InterruptedException stopInterrupted) {
                        interrupted = true;
                        timedOut = true;
                        break;
                    }
                }

                var pendingAtStop = snapshot(false, interrupted).pools();
                pools.values().forEach(ExecutorService::shutdown);
                if (!timedOut) {
                    for (var executor : pools.values()) {
                        if (executor.isTerminated()) continue;
                        try {
                            if (!executor.awaitTermination(remaining(deadline), TimeUnit.NANOSECONDS)) {
                                timedOut = true;
                                break;
                            }
                        } catch (InterruptedException stopInterrupted) {
                            interrupted = true;
                            timedOut = true;
                            break;
                        }
                    }
                }
                if (timedOut) {
                    forceStopping = true;
                    for (var task : new ArrayList<>(outstanding)) cancelWaiting(task);
                    pools.values().forEach(ExecutorService::shutdownNow);
                    var statuses = new ArrayList<PoolShutdownStatus>();
                    for (var status : pendingAtStop) {
                        var executor = pools.get(status.name());
                        statuses.add(
                                new PoolShutdownStatus(
                                        status.name(),
                                        executor != null && executor.isTerminated(),
                                        status.activeAtTimeout(),
                                        status.notStartedTasks()));
                    }
                    shutdownResult = new ShutdownResult(false, interrupted, List.copyOf(statuses));
                } else {
                    shutdownResult = snapshot(true, false);
                }
                shutdownInProgress = false;
                changed.signalAll();
                return shutdownResult;
            } finally {
                lifecycle.unlock();
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private ShutdownResult snapshot(boolean completed, boolean interrupted) {
        var statuses = new ArrayList<PoolShutdownStatus>();
        for (var entry : pools.entrySet()) {
            int active = 0;
            int queued = 0;
            for (var task : outstanding) {
                if (task.executor != entry.getValue()) continue;
                if (task.running) active++;
                else queued++;
            }
            statuses.add(
                    new PoolShutdownStatus(entry.getKey(), entry.getValue().isTerminated(), active, queued));
        }
        if (synchronousActive > 0) {
            statuses.add(new PoolShutdownStatus("synchronous", false, synchronousActive, 0));
        }
        if (barrierOwner != null) {
            statuses.add(new PoolShutdownStatus("maintenance", false, 1, 0));
        }
        return new ShutdownResult(completed, interrupted, List.copyOf(statuses));
    }

    private ShutdownResult unlockedShutdownFailure(boolean interrupted) {
        var cached = shutdownResult;
        if (cached != null) return cached;
        var statuses = new ArrayList<PoolShutdownStatus>();
        for (var entry : pools.entrySet()) {
            var executor = entry.getValue();
            int active = executor instanceof ThreadPoolExecutor pool ? pool.getActiveCount() : 0;
            int queued = executor instanceof ThreadPoolExecutor pool ? pool.getQueue().size() : 0;
            statuses.add(new PoolShutdownStatus(entry.getKey(), executor.isTerminated(), active, queued));
        }
        return new ShutdownResult(false, interrupted, List.copyOf(statuses));
    }

    private void awaitChange(long deadline, String timeoutMessage) throws InterruptedException {
        long remaining = remaining(deadline);
        if (remaining == 0) throw new IllegalStateException(timeoutMessage);
        changed.awaitNanos(remaining);
    }

    private void restoreWriteDepth(int previousDepth) {
        if (previousDepth == 0) writeDepth.remove();
        else writeDepth.set(previousDepth);
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    private static int orderedCapacity(Map<String, ? extends ExecutorService> pools) {
        long capacity = 0;
        for (var executor : pools.values()) {
            if (executor instanceof ThreadPoolExecutor pool) {
                capacity +=
                        (long) pool.getQueue().remainingCapacity()
                                + pool.getQueue().size()
                                + pool.getMaximumPoolSize();
            } else {
                capacity += 1024;
            }
        }
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, capacity));
    }

    private static long deadline(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        long nanos = timeout.toNanos();
        long now = System.nanoTime();
        long sum = now + nanos;
        return ((now ^ sum) & (nanos ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    private static long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }
}
