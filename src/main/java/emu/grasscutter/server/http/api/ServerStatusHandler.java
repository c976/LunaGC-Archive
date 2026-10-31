package emu.grasscutter.server.http.api;

import static emu.grasscutter.config.Configuration.ACCOUNT;
import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.server.http.api.ApiHandler.ERROR_RET_CODE;
import static emu.grasscutter.server.http.api.ApiHandler.SUCCESS_RET_CODE;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.game.GameSessionManager;
import emu.grasscutter.server.threading.JvmDeadlockSnapshot;
import emu.grasscutter.server.threading.ServerHealthSnapshot;
import emu.grasscutter.server.threading.ServerRuntimeSnapshot;
import emu.grasscutter.server.threading.ThreadPoolHealth;
import emu.grasscutter.server.threading.ThreadPoolManager;
import emu.grasscutter.server.threading.ThreadPoolSnapshot;
import emu.grasscutter.utils.JsonUtils;
import io.javalin.http.Context;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the same figures the periodic status log prints.
 *
 * <p>Shares runtime and pool figures with the periodic log, and additionally checks JVM lock
 * cycles and completed ticks so an idle but stalled game server is not reported as healthy.
 */
public final class ServerStatusHandler {
    private ServerStatusHandler() {}

    /** Player count from this process. A dispatch-only node has no game server and reports 0. */
    private static int onlinePlayers() {
        var gameServer = Grasscutter.getGameServer();
        return gameServer == null || gameServer.getPlayers() == null
                ? 0
                : gameServer.getPlayers().size();
    }

    public static void serverStatus(Context ctx) {
        ctx.contentType("application/json; charset=UTF-8");
        try {
            var runtime = ServerRuntimeSnapshot.collect();
            var pools =
                    ThreadPoolManager.getInstance().getAll().stream()
                            .map(ThreadPoolManager.getInstance()::snapshot)
                            .sorted(Comparator.comparing(ThreadPoolSnapshot::name))
                            .toList();
            var health = ServerHealthSnapshot.from(runtime, pools);
            var deadlock = JvmDeadlockSnapshot.collect();
            var gameServer = Grasscutter.getGameServer();
            boolean hasGameServer = gameServer != null && gameServer.getPlayers() != null;
            long completedTicks = hasGameServer ? gameServer.getCompletedTickCount() : 0L;
            long lastTickNanos = hasGameServer ? gameServer.getLastTickCompletedNanos() : 0L;
            long tickAge = tickAgeMillis(completedTicks, lastTickNanos, System.nanoTime());

            var tick = new LinkedHashMap<String, Object>();
            tick.put("completedCount", completedTicks);
            tick.put(
                    "lastCompletedAtMillis",
                    hasGameServer ? gameServer.getLastTickCompletedAtMillis() : 0L);
            tick.put("ageMillis", tickAge);
            tick.put("lastDurationMillis", hasGameServer ? gameServer.getLastTickDurationMillis() : -1L);
            tick.put("maxDurationMillis", hasGameServer ? gameServer.getMaxTickDurationMillis() : -1L);

            var game = new LinkedHashMap<String, Object>();
            game.put("players", onlinePlayers());
            game.put("maxPlayers", ACCOUNT.maxPlayer);
            game.put("gameVersion", GameConstants.VERSION);
            game.put("uptime", runtime.uptimeText());
            game.put("startedAt", runtime.startedAtText());
            game.put("tick", tick);
            game.put(
                    "logicPendingTasks",
                    hasGameServer ? GameSessionManager.getLogicThread().pendingTasks() : -1);

            var jvm = new LinkedHashMap<String, Object>();
            jvm.put("processCpuLoad", runtime.processCpuLoad());
            jvm.put("usedMemoryBytes", runtime.usedJvmMemory());
            jvm.put("maxMemoryBytes", runtime.maxJvmMemory());
            jvm.put("memoryUsage", health.jvmMemoryUsage());
            jvm.put("gcCount", runtime.gcCount());
            jvm.put("gcTimeMillis", runtime.gcTimeMillis());
            jvm.put("deadlock", deadlockJson(deadlock));

            var system = new LinkedHashMap<String, Object>();
            system.put("arch", System.getProperty("os.arch"));
            system.put("name", System.getProperty("os.name"));
            system.put("systemCpuLoad", runtime.systemCpuLoad());
            system.put("totalMemoryBytes", runtime.totalSystemMemory());
            system.put("freeMemoryBytes", runtime.freeSystemMemory());
            system.put("memoryUsage", health.systemMemoryUsage());

            var response = new LinkedHashMap<String, Object>();
            response.put("retcode", SUCCESS_RET_CODE);
            response.put("health", health.health().name());
            response.put("bottleneck", health.bottleneck());
            response.put("diagnosis", health.diagnosisText());
            // Stable key for clients that show the diagnosis in another language.
            response.put("diagnosisCode", health.diagnosis().name());
            response.put("suggestion", health.suggestion());
            response.put("game", game);
            response.put("jvm", jvm);
            response.put("system", system);
            response.put("threadPools", poolsJson(pools));
            applyTickHealth(
                    response,
                    hasGameServer,
                    completedTicks,
                    tickAge,
                    runtime.uptimeMillis(),
                    GAME_INFO.tickRateMs);
            applyDeadlockHealth(response, deadlock);

            ctx.result(JsonUtils.encode(response));
        } catch (Throwable t) {
            // Never let a monitoring endpoint take a thread down with it.
            Grasscutter.getLogger().warn("Failed to build the status response.", t);
            ctx.result("{\"retcode\":" + ERROR_RET_CODE + ",\"message\":\"internal error\"}");
        }
    }

    static long tickAgeMillis(long completedCount, long lastCompletedNanos, long sampledNanos) {
        return completedCount <= 0
                ? -1L
                : Math.max(0L, (sampledNanos - lastCompletedNanos) / 1_000_000L);
    }

    static boolean isGameTickStalled(
            long completedCount, long ageMillis, long uptimeMillis, int tickRateMs) {
        return completedCount <= 0
                ? uptimeMillis > 120_000L
                : ageMillis > Math.max(30_000L, tickRateMs * 10L);
    }

    static void applyTickHealth(
            Map<String, Object> response,
            boolean hasGameServer,
            long completedCount,
            long ageMillis,
            long uptimeMillis,
            int tickRateMs) {
        if (!hasGameServer
                || "JVM_DEADLOCK".equals(response.get("bottleneck"))
                || !isGameTickStalled(completedCount, ageMillis, uptimeMillis, tickRateMs)) return;

        response.put("health", ThreadPoolHealth.DANGER.name());
        response.put("bottleneck", "GAME_TICK_STALLED");
        response.put("diagnosis", "No game tick has completed within the liveness threshold.");
        response.put("diagnosisCode", "GAME_TICK_STALLED");
        response.put("suggestion", "Capture a thread dump and inspect the game tick and logic queue.");
    }

    static void applyDeadlockHealth(Map<String, Object> response, JvmDeadlockSnapshot deadlock) {
        if (deadlock.status() == JvmDeadlockSnapshot.Status.DEADLOCK) {
            response.put("health", ThreadPoolHealth.DANGER.name());
            response.put("bottleneck", "JVM_DEADLOCK");
            response.put("diagnosis", "JVM lock cycle detected; game processing may be stalled.");
            response.put("diagnosisCode", "JVM_DEADLOCK");
            response.put("suggestion", "Capture a thread dump and correct the conflicting lock order.");
        } else if (deadlock.status() == JvmDeadlockSnapshot.Status.UNKNOWN
                && (ThreadPoolHealth.NORMAL.name().equals(response.get("health"))
                        || ThreadPoolHealth.BUSY.name().equals(response.get("health")))) {
            response.put("health", ThreadPoolHealth.WARNING.name());
            response.put("bottleneck", "JVM_DEADLOCK_CHECK");
            response.put("diagnosis", "JVM deadlock detection is incomplete or unavailable.");
            response.put("diagnosisCode", "JVM_DEADLOCK_CHECK");
            response.put("suggestion", "Check the JVM monitoring capability and inspect a thread dump.");
        }
    }

    private static Map<String, Object> deadlockJson(JvmDeadlockSnapshot deadlock) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("status", deadlock.status().name());
        entry.put("detectionMode", deadlock.detectionMode());
        entry.put("threadCount", deadlock.threadCount());
        entry.put("threadNames", deadlock.threadNames());
        entry.put("reason", deadlock.reason());
        return entry;
    }

    private static List<Map<String, Object>> poolsJson(List<ThreadPoolSnapshot> pools) {
        return pools.stream()
                .map(
                        pool -> {
                            var entry = new LinkedHashMap<String, Object>();
                            entry.put("name", pool.name());
                            entry.put("type", pool.type().name());
                            entry.put("health", pool.health().name());
                            entry.put("state", pool.lifecycleState());
                            entry.put("activeThreads", pool.activeCount());
                            entry.put("poolSize", pool.currentPoolSize());
                            entry.put("maxThreads", pool.maximumPoolSize());
                            entry.put("queueSize", pool.queueSize());
                            // -1 means unbounded.
                            entry.put("queueCapacity", pool.queueCapacity());
                            entry.put("submitted", pool.submittedTaskCount());
                            entry.put("completed", pool.completedTaskCount());
                            entry.put("failed", pool.failedTaskCount());
                            entry.put("rejected", pool.rejectedTaskCount());
                            entry.put("averageMillis", pool.averageExecutionMillis());
                            entry.put("maxMillis", pool.maxExecutionMillis());
                            entry.put("diagnosis", pool.diagnosisText());
                            return (Map<String, Object>) entry;
                        })
                .toList();
    }

    public static void listRoutes(Context ctx) {
        ctx.contentType("text/plain; charset=UTF-8");
        ctx.result(
                """
                /api/help
                /api/status
                /status/server
                """);
    }
}
