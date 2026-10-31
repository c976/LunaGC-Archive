package emu.grasscutter.server.threading;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CPU, memory and GC at one instant.
 *
 * <p>The CPU and system-memory figures come from a JDK-specific MX bean, so they are reported as
 * -1 where it is not available rather than guessed at.
 */
public record ServerRuntimeSnapshot(
        long sampledAtMillis,
        long startedAtMillis,
        long uptimeMillis,
        double processCpuLoad,
        double systemCpuLoad,
        long usedJvmMemory,
        long maxJvmMemory,
        long totalJvmMemory,
        long freeSystemMemory,
        long totalSystemMemory,
        long gcCount,
        long gcTimeMillis) {

    public static ServerRuntimeSnapshot collect() {
        var runtime = Runtime.getRuntime();
        var os = ManagementFactory.getOperatingSystemMXBean();
        double processCpu = -1D;
        double systemCpu = -1D;
        long freeSystem = -1L;
        long totalSystem = -1L;
        if (os instanceof OperatingSystemMXBean sunOs) {
            processCpu = sunOs.getProcessCpuLoad();
            systemCpu = sunOs.getCpuLoad();
            freeSystem = sunOs.getFreeMemorySize();
            totalSystem = sunOs.getTotalMemorySize();
        }

        long gcCount = 0L;
        long gcTime = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            // Both return -1 where the collector does not track them.
            if (bean.getCollectionCount() > 0) gcCount += bean.getCollectionCount();
            if (bean.getCollectionTime() > 0) gcTime += bean.getCollectionTime();
        }

        var runtimeBean = ManagementFactory.getRuntimeMXBean();
        return new ServerRuntimeSnapshot(
                System.currentTimeMillis(),
                runtimeBean.getStartTime(),
                runtimeBean.getUptime(),
                processCpu,
                systemCpu,
                runtime.totalMemory() - runtime.freeMemory(),
                runtime.maxMemory(),
                runtime.totalMemory(),
                freeSystem,
                totalSystem,
                gcCount,
                gcTime);
    }

    public String uptimeText() {
        return Duration.between(
                        Instant.ofEpochMilli(this.startedAtMillis),
                        Instant.ofEpochMilli(this.sampledAtMillis))
                .toString();
    }

    public String startedAtText() {
        return formatMillis(this.startedAtMillis);
    }

    public String sampledAtText() {
        return formatMillis(this.sampledAtMillis);
    }

    private static String formatMillis(long millis) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
}
