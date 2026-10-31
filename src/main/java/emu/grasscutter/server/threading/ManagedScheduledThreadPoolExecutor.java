package emu.grasscutter.server.threading;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The scheduled counterpart of {@link ManagedThreadPoolExecutor}.
 *
 * <p>A scheduled pool re-runs the same task, so timing is recorded per run at the point the run
 * begins rather than at submission: a task scheduled an hour out would otherwise be counted as an
 * hour-long task.
 */
public final class ManagedScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor
        implements ManagedThreadPool {
    private final ThreadPoolConfig config;
    private final ThreadPoolStats stats = new ThreadPoolStats();
    private final long createdAtMillis = System.currentTimeMillis();

    public ManagedScheduledThreadPoolExecutor(ThreadPoolConfig config, ThreadFactory threadFactory) {
        super(config.coreThreads(), threadFactory, new ThreadPoolExecutor.AbortPolicy());
        this.config = config;
        this.setKeepAliveTime(config.keepAliveTime(), config.keepAliveUnit());
        this.setMaximumPoolSize(config.maxThreads());
        ThreadPoolManager.getInstance().register(this);
    }

    @Override
    public void execute(Runnable command) {
        super.execute(this.track(command));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return super.schedule(this.track(command), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable command, long initialDelay, long period, TimeUnit unit) {
        return super.scheduleAtFixedRate(this.track(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return super.scheduleWithFixedDelay(this.track(command), initialDelay, delay, unit);
    }

    private Runnable track(Runnable command) {
        return () -> {
            long taskId = this.stats.recordSubmitted();
            this.stats.recordStarted(taskId);
            long startedAt = System.nanoTime();
            boolean failed = false;
            try {
                command.run();
            } catch (RuntimeException | Error ex) {
                failed = true;
                throw ex;
            } finally {
                this.stats.recordCompleted(taskId, System.nanoTime() - startedAt, failed);
            }
        };
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
