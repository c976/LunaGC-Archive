package emu.grasscutter.server;

import static emu.grasscutter.config.Configuration.GAME_INFO;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.server.threading.ManagedScheduledThreadPoolExecutor;
import emu.grasscutter.server.threading.ThreadPoolConfigResolver;
import emu.grasscutter.server.threading.ThreadPoolType;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;

/**
 * Automated maintenance, configured under {@code server.game.watchdog}.
 *
 * <p>Two jobs. The database monitor pings MongoDB on an interval and holds the game tick while it
 * is unreachable; the timed restart saves everyone and exits so a supervisor can bring the server
 * back.
 *
 * <p>AstaPS also has a component health check that calls {@code GameServer.start()} when the server
 * looks unhealthy. That is not ported: {@code start()} builds a fresh game-loop Timer and opens a
 * fresh dispatch connection every time it runs, so "restarting" a live server that way leaves two
 * game loops ticking the same worlds.
 */
public final class ServerWatchdog {
    private static final AtomicBoolean DATABASE_DOWN = new AtomicBoolean(false);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static ScheduledExecutorService scheduler;

    private ServerWatchdog() {}

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;

        var options = GAME_INFO.watchdog;
        if (!options.enableDatabaseMonitor && !options.enableAutoRestart) return;

        var config =
                ThreadPoolConfigResolver.resolve(
                        "SERVER_WATCHDOG", ThreadPoolType.SCHEDULER, 2, 2, 0, 60);
        scheduler =
                new ManagedScheduledThreadPoolExecutor(
                        config,
                        runnable -> {
                            var thread = new Thread(runnable, "watchdog");
                            thread.setDaemon(true);
                            return thread;
                        });

        if (options.enableDatabaseMonitor) {
            var interval = Math.max(1, options.databaseCheckIntervalSeconds);
            scheduler.scheduleAtFixedRate(
                    ServerWatchdog::checkDatabase, interval, interval, TimeUnit.SECONDS);
            Grasscutter.getLogger()
                    .info("[Watchdog] Database monitor started, every {}s.", interval);
        }

        if (options.enableAutoRestart) {
            var interval = Math.max(1, options.autoRestartIntervalHours);
            scheduler.scheduleAtFixedRate(
                    ServerWatchdog::restart, interval, interval, TimeUnit.HOURS);
            Grasscutter.getLogger()
                    .info(
                            "[Watchdog] Timed restart started, every {}h. The process will exit;"
                                + " something must be configured to start it again.",
                            interval);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdownNow));
    }

    /** Whether the database is currently unreachable. The game tick skips its body while it is. */
    public static boolean isDatabaseDown() {
        return DATABASE_DOWN.get();
    }

    private static void checkDatabase() {
        try {
            DatabaseManager.getGameDatastore().getDatabase().runCommand(new Document("ping", 1));

            if (DATABASE_DOWN.compareAndSet(true, false)) {
                Grasscutter.getLogger().info("[Watchdog] Database is back. Resuming.");
            }
        } catch (Throwable t) {
            if (DATABASE_DOWN.compareAndSet(false, true)) {
                Grasscutter.getLogger()
                        .error(
                                "[Watchdog] Database is unreachable. Holding the game tick until it"
                                    + " returns.",
                                t);
            } else {
                // Already known to be down: one line per check, without the stack trace again.
                Grasscutter.getLogger()
                        .error("[Watchdog] Database is still unreachable: {}", t.getMessage());
            }
        }
    }

    private static void restart() {
        Grasscutter.getLogger().info("[Watchdog] Timed restart: saving players.");

        var gameServer = Grasscutter.getGameServer();
        if (gameServer != null) {
            var saved = 0;
            for (var player : gameServer) {
                try {
                    player.save();
                    saved++;
                } catch (Throwable t) {
                    // One player failing to save must not cost everyone else theirs.
                    Grasscutter.getLogger()
                            .error("[Watchdog] Failed to save uid {}.", player.getUid(), t);
                }
            }
            Grasscutter.getLogger().info("[Watchdog] Saved {} player(s).", saved);

            try {
                gameServer.onServerShutdown();
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[Watchdog] Shutdown hook failed.", t);
            }
        }

        Grasscutter.getLogger().info("[Watchdog] Exiting for restart.");
        System.exit(0);
    }
}
