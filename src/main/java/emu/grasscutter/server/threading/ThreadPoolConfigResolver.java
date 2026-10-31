package emu.grasscutter.server.threading;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer;
import java.util.concurrent.TimeUnit;

/**
 * Turns a pool's built-in sizing into its effective sizing, letting config.json override it.
 *
 * <p>Pools are built in static initialisers, which can run before the config is loaded, so a
 * missing config is an ordinary case here rather than an error: the built-in sizing is used.
 */
public final class ThreadPoolConfigResolver {
    private ThreadPoolConfigResolver() {}

    public static ThreadPoolConfig resolve(
            String name,
            ThreadPoolType type,
            int coreThreads,
            int maxThreads,
            int queueCapacity,
            long keepAliveSeconds) {
        var config = Grasscutter.getConfig();
        var options = config == null ? null : config.server.threadPools;
        ConfigContainer.ThreadPoolDefinition configured =
                options == null || !options.enabled || options.pools == null
                        ? null
                        : options.pools.get(name);
        if (configured == null) {
            return new ThreadPoolConfig(
                    name,
                    type,
                    coreThreads,
                    maxThreads,
                    queueCapacity,
                    keepAliveSeconds,
                    TimeUnit.SECONDS);
        }

        // A negative value in config.json means "leave this one alone". Zero threads would give a
        // pool that never runs anything, so it is treated the same way.
        int resolvedCoreThreads = configured.coreThreads > 0 ? configured.coreThreads : coreThreads;
        int resolvedMaxThreads = configured.maxThreads > 0 ? configured.maxThreads : maxThreads;
        return new ThreadPoolConfig(
                name,
                type,
                resolvedCoreThreads,
                // ThreadPoolExecutor's constructor throws when max < core, and a config file is
                // exactly where that pairing gets mistyped.
                Math.max(resolvedCoreThreads, resolvedMaxThreads),
                configured.queueCapacity >= 0 ? configured.queueCapacity : queueCapacity,
                configured.keepAliveSeconds >= 0 ? configured.keepAliveSeconds : keepAliveSeconds,
                TimeUnit.SECONDS);
    }
}
