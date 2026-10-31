package emu.grasscutter.server.threading;

import java.util.List;

/**
 * One verdict for the whole server, folded together from the runtime figures and every pool.
 *
 * <p>Host-level pressure is judged before any pool: a pool that looks saturated because the machine
 * is out of CPU or memory is a symptom, and naming it as the bottleneck would send someone tuning
 * the wrong thing.
 */
public record ServerHealthSnapshot(
        ThreadPoolHealth health,
        /** -1 where the figure was unavailable. */
        double jvmMemoryUsage,
        double systemMemoryUsage,
        double processCpuUsage,
        int busyPools,
        int warningPools,
        int dangerPools,
        String bottleneck,
        ThreadPoolDiagnosis diagnosis) {

    private static final String NO_BOTTLENECK = "none";
    private static final String WHOLE_SERVER = "server";
    private static final double CRITICAL = 0.9D;
    private static final double ELEVATED = 0.7D;

    public String diagnosisText() {
        return this.diagnosis.description();
    }

    public String suggestion() {
        return this.diagnosis.suggestion();
    }

    public static ServerHealthSnapshot from(
            ServerRuntimeSnapshot runtime, List<ThreadPoolSnapshot> pools) {
        double jvmMemoryUsage = ratio(runtime.usedJvmMemory(), runtime.maxJvmMemory());
        double systemMemoryUsage =
                ratio(
                        runtime.totalSystemMemory() - runtime.freeSystemMemory(),
                        runtime.totalSystemMemory());
        double processCpuUsage = runtime.processCpuLoad();

        int busy = 0;
        int warning = 0;
        int danger = 0;
        ThreadPoolSnapshot worstPool = null;
        for (ThreadPoolSnapshot pool : pools) {
            switch (pool.health()) {
                case BUSY -> busy++;
                case WARNING -> warning++;
                case DANGER -> danger++;
                default -> {}
            }
            // ThreadPoolHealth is declared worst-last, so ordinal ordering is severity ordering.
            if (worstPool == null || pool.health().ordinal() > worstPool.health().ordinal())
                worstPool = pool;
        }

        if (processCpuUsage >= CRITICAL) {
            return new ServerHealthSnapshot(
                    ThreadPoolHealth.DANGER,
                    jvmMemoryUsage,
                    systemMemoryUsage,
                    processCpuUsage,
                    busy,
                    warning,
                    danger,
                    "CPU",
                    ThreadPoolDiagnosis.CPU_LIMIT);
        }
        if (jvmMemoryUsage >= CRITICAL || systemMemoryUsage >= CRITICAL) {
            return new ServerHealthSnapshot(
                    ThreadPoolHealth.DANGER,
                    jvmMemoryUsage,
                    systemMemoryUsage,
                    processCpuUsage,
                    busy,
                    warning,
                    danger,
                    "memory",
                    ThreadPoolDiagnosis.MEMORY_PRESSURE);
        }
        if ((danger > 0 || warning > 0) && worstPool != null) {
            return new ServerHealthSnapshot(
                    danger > 0 ? ThreadPoolHealth.DANGER : ThreadPoolHealth.WARNING,
                    jvmMemoryUsage,
                    systemMemoryUsage,
                    processCpuUsage,
                    busy,
                    warning,
                    danger,
                    worstPool.name(),
                    worstPool.diagnosis());
        }
        if (busy > 0
                || processCpuUsage >= ELEVATED
                || jvmMemoryUsage >= ELEVATED
                || systemMemoryUsage >= ELEVATED) {
            return new ServerHealthSnapshot(
                    ThreadPoolHealth.BUSY,
                    jvmMemoryUsage,
                    systemMemoryUsage,
                    processCpuUsage,
                    busy,
                    warning,
                    danger,
                    worstPool == null ? WHOLE_SERVER : worstPool.name(),
                    ThreadPoolDiagnosis.ELEVATED_LOAD);
        }

        return new ServerHealthSnapshot(
                ThreadPoolHealth.NORMAL,
                jvmMemoryUsage,
                systemMemoryUsage,
                processCpuUsage,
                busy,
                warning,
                danger,
                NO_BOTTLENECK,
                ThreadPoolDiagnosis.NONE);
    }

    private static double ratio(long used, long max) {
        if (used < 0 || max <= 0) return -1D;
        return (double) used / max;
    }
}
