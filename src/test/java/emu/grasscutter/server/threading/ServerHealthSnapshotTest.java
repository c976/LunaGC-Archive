package emu.grasscutter.server.threading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers how the server-wide verdict is reached.
 *
 * <p>This drives both the periodic status log and /api/status, and its whole job is to name the one
 * thing worth looking at. Naming the wrong one sends an operator tuning something that was only a
 * symptom, so the precedence between host pressure and pool pressure is pinned here.
 */
public final class ServerHealthSnapshotTest {
    private static ServerRuntimeSnapshot runtime(
            double processCpu, long usedJvm, long maxJvm, long freeSystem, long totalSystem) {
        return new ServerRuntimeSnapshot(
                0L, 0L, 0L, processCpu, 0.0D, usedJvm, maxJvm, maxJvm, freeSystem, totalSystem, 0L, 0L);
    }

    /** A calm host: 10% CPU, 10% of both memories used. */
    private static ServerRuntimeSnapshot calmHost() {
        return runtime(0.10D, 100L, 1000L, 900L, 1000L);
    }

    private static ThreadPoolSnapshot pool(
            String name, ThreadPoolHealth health, ThreadPoolDiagnosis diagnosis) {
        return new ThreadPoolSnapshot(
                name, ThreadPoolType.DATABASE, 0L, "RUNNING", 1, 1, 1, 0, 0, 10, 0L, 0L, 0L, 0L, 0L,
                0L, health, diagnosis);
    }

    @Test
    @DisplayName("a quiet server with quiet pools reports nothing to look at")
    public void quietServer() {
        var health =
                ServerHealthSnapshot.from(
                        calmHost(), List.of(pool("A", ThreadPoolHealth.NORMAL, ThreadPoolDiagnosis.NONE)));

        assertEquals(ThreadPoolHealth.NORMAL, health.health());
        assertEquals("none", health.bottleneck());
        assertEquals(ThreadPoolDiagnosis.NONE, health.diagnosis());
    }

    @Test
    @DisplayName("the worst pool is the one named, not whichever came first")
    public void worstPoolWins() {
        var health =
                ServerHealthSnapshot.from(
                        calmHost(),
                        List.of(
                                pool("BUSY_ONE", ThreadPoolHealth.BUSY, ThreadPoolDiagnosis.ELEVATED_LOAD),
                                pool("BAD_ONE", ThreadPoolHealth.DANGER, ThreadPoolDiagnosis.REJECTED_TASKS),
                                pool("OK_ONE", ThreadPoolHealth.NORMAL, ThreadPoolDiagnosis.NONE)));

        assertEquals(ThreadPoolHealth.DANGER, health.health());
        assertEquals("BAD_ONE", health.bottleneck());
        assertEquals(ThreadPoolDiagnosis.REJECTED_TASKS, health.diagnosis());
        assertEquals(1, health.dangerPools());
        assertEquals(1, health.busyPools());
    }

    @Test
    @DisplayName("CPU at its limit outranks a failing pool, because the pool is the symptom")
    public void hostPressureOutranksPools() {
        var health =
                ServerHealthSnapshot.from(
                        runtime(0.95D, 100L, 1000L, 900L, 1000L),
                        List.of(pool("BAD_ONE", ThreadPoolHealth.DANGER, ThreadPoolDiagnosis.REJECTED_TASKS)));

        assertEquals(ThreadPoolHealth.DANGER, health.health());
        assertEquals("CPU", health.bottleneck());
        assertEquals(ThreadPoolDiagnosis.CPU_LIMIT, health.diagnosis());
    }

    @Test
    @DisplayName("memory pressure is reported even when every pool is calm")
    public void memoryPressureWithCalmPools() {
        var health =
                ServerHealthSnapshot.from(
                        runtime(0.10D, 950L, 1000L, 900L, 1000L),
                        List.of(pool("A", ThreadPoolHealth.NORMAL, ThreadPoolDiagnosis.NONE)));

        assertEquals(ThreadPoolHealth.DANGER, health.health());
        assertEquals("memory", health.bottleneck());
        assertEquals(ThreadPoolDiagnosis.MEMORY_PRESSURE, health.diagnosis());
    }

    @Test
    @DisplayName("an unavailable CPU reading is not treated as an idle CPU")
    public void unavailableFiguresAreNotZero() {
        // The MX bean returns -1 where it cannot sample. Were that read as a number, -1 would
        // compare as "well under the threshold" and quietly pass for a healthy server.
        var health =
                ServerHealthSnapshot.from(
                        runtime(-1D, -1L, -1L, -1L, -1L),
                        List.of(pool("A", ThreadPoolHealth.NORMAL, ThreadPoolDiagnosis.NONE)));

        assertEquals(-1D, health.processCpuUsage());
        assertEquals(-1D, health.jvmMemoryUsage());
        assertEquals(ThreadPoolHealth.NORMAL, health.health());
    }

    @Test
    @DisplayName("no pools at all still produces a verdict")
    public void noPools() {
        var health = ServerHealthSnapshot.from(calmHost(), List.of());

        assertEquals(ThreadPoolHealth.NORMAL, health.health());
        assertEquals("none", health.bottleneck());
    }
}
