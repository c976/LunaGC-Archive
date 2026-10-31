package emu.grasscutter.server.threading;

import java.util.concurrent.ThreadPoolExecutor;

/** A pool that registers itself with {@link ThreadPoolManager} and tracks its own task timings. */
public interface ManagedThreadPool {
    ThreadPoolConfig getConfig();

    ThreadPoolStats getStats();

    long getCreatedAtMillis();

    ThreadPoolExecutor asThreadPoolExecutor();
}
