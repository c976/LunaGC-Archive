package emu.grasscutter.server.threading;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/** A {@link ThreadPoolExecutor} that times its tasks and registers itself for status readouts. */
public final class ManagedThreadPoolExecutor extends ThreadPoolExecutor implements ManagedThreadPool {
    private final ThreadPoolConfig config;
    private final ThreadPoolStats stats;
    private final long createdAtMillis = System.currentTimeMillis();

    public ManagedThreadPoolExecutor(
            ThreadPoolConfig config,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        super(
                config.coreThreads(),
                config.maxThreads(),
                config.keepAliveTime(),
                config.keepAliveUnit(),
                workQueue,
                threadFactory,
                handler);
        this.config = config;
        this.stats = new ThreadPoolStats();
        ThreadPoolManager.getInstance().register(this);
    }

    @Override
    public void execute(Runnable command) {
        long taskId = this.stats.recordSubmitted();
        try {
            super.execute(this.wrap(command, taskId));
        } catch (RejectedExecutionException ex) {
            // Counted here rather than in a RejectedExecutionHandler: the caller may pass any
            // handler it likes, and a rejection has to show up in the stats either way.
            this.stats.recordRejected(taskId);
            throw ex;
        }
    }

    private Runnable wrap(Runnable command, long taskId) {
        return () -> {
            this.stats.recordStarted(taskId);
            long startedAt = System.nanoTime();
            boolean failed = false;
            try {
                command.run();
                failed = commandFailed(command);
            } catch (RuntimeException | Error ex) {
                failed = true;
                throw ex;
            } finally {
                this.stats.recordCompleted(taskId, System.nanoTime() - startedAt, failed);
            }
        };
    }

    /**
     * A task submitted through {@code submit} arrives here wrapped in a {@link Future}, which
     * swallows the exception rather than letting it out of {@code run}. Asking the future is the
     * only way such a failure is visible to the counters.
     */
    private static boolean commandFailed(Runnable command) {
        if (!(command instanceof Future<?> future) || !future.isDone()) return false;
        try {
            future.get();
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return true;
        } catch (ExecutionException ex) {
            return true;
        }
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return this.config;
    }

    @Override
    public ThreadPoolStats getStats() {
        return this.stats;
    }

    @Override
    public long getCreatedAtMillis() {
        return this.createdAtMillis;
    }

    @Override
    public ThreadPoolExecutor asThreadPoolExecutor() {
        return this;
    }
}
