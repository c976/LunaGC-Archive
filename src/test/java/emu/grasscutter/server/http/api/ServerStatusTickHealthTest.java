package emu.grasscutter.server.http.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.server.threading.JvmDeadlockSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ServerStatusTickHealthTest {
    private static Map<String, Object> response() {
        var response = new LinkedHashMap<String, Object>();
        response.put("retcode", 0);
        response.put("health", "NORMAL");
        response.put("bottleneck", "none");
        return response;
    }

    @Test
    public void zeroCompletedTicksHaveStartupGrace() {
        assertFalse(ServerStatusHandler.isGameTickStalled(0L, -1L, 30_000L, 200));
        assertFalse(ServerStatusHandler.isGameTickStalled(0L, -1L, 120_000L, 200));
        assertTrue(ServerStatusHandler.isGameTickStalled(0L, -1L, 120_001L, 200));
    }

    @Test
    public void freshCompletedTickIsHealthyEvenAfterLongUptime() {
        assertFalse(ServerStatusHandler.isGameTickStalled(123L, 199L, 9_000_000L, 200));
    }

    @Test
    public void staleCompletedTickTriggersDanger() {
        assertFalse(ServerStatusHandler.isGameTickStalled(1L, 30_000L, 40_000L, 200));
        assertTrue(ServerStatusHandler.isGameTickStalled(1L, 30_001L, 40_000L, 200));
        var response = response();
        ServerStatusHandler.applyTickHealth(response, true, 1L, 30_001L, 40_000L, 200);

        assertEquals("DANGER", response.get("health"));
        assertEquals("GAME_TICK_STALLED", response.get("bottleneck"));
        assertEquals(0, response.get("retcode"));
    }

    @Test
    public void slowConfiguredIntervalRaisesThresholdWithoutIntegerOverflow() {
        assertFalse(ServerStatusHandler.isGameTickStalled(1L, 40_000L, 90_000L, 4000));
        assertTrue(ServerStatusHandler.isGameTickStalled(1L, 40_001L, 90_000L, 4000));
        assertFalse(
                ServerStatusHandler.isGameTickStalled(
                        1L, 30_001L, 90_000L, Integer.MAX_VALUE));
    }

    @Test
    public void ageUsesOnlyMonotonicTimeAndInitialAgeIsUnknown() {
        assertEquals(-1L, ServerStatusHandler.tickAgeMillis(0L, 0L, 9_000_000L));
        assertEquals(123L, ServerStatusHandler.tickAgeMillis(1L, 500_000_000L, 623_000_000L));
        assertEquals(0L, ServerStatusHandler.tickAgeMillis(1L, 500_000_000L, 499_000_000L));
        assertEquals(123L, ServerStatusHandler.tickAgeMillis(1L, -623_000_000L, -500_000_000L));
    }

    @Test
    public void dispatchOnlyNodeHasNoTickLivenessRequirement() {
        var response = response();
        var before = new LinkedHashMap<>(response);
        ServerStatusHandler.applyTickHealth(response, false, 0L, -1L, 9_000_000L, 200);

        assertEquals(before, response);
    }

    @Test
    public void deadlockRemainsHighestPriorityRegardlessOfApplicationOrder() {
        var deadlock =
                new JvmDeadlockSnapshot(
                        JvmDeadlockSnapshot.Status.DEADLOCK,
                        "MONITOR_AND_SYNCHRONIZER",
                        2,
                        List.of("server-tick", "game-logic"),
                        "none");
        var first = response();
        ServerStatusHandler.applyDeadlockHealth(first, deadlock);
        ServerStatusHandler.applyTickHealth(first, true, 1L, 30_001L, 50_000L, 200);
        var second = response();
        ServerStatusHandler.applyTickHealth(second, true, 1L, 30_001L, 50_000L, 200);
        ServerStatusHandler.applyDeadlockHealth(second, deadlock);

        assertEquals("DANGER", first.get("health"));
        assertEquals("JVM_DEADLOCK", first.get("bottleneck"));
        assertEquals(first, second);
    }

    @Test
    public void unknownDeadlockDetectionCannotMaskKnownTickStall() {
        var response = response();
        ServerStatusHandler.applyTickHealth(response, true, 1L, 30_001L, 50_000L, 200);
        ServerStatusHandler.applyDeadlockHealth(
                response,
                new JvmDeadlockSnapshot(
                        JvmDeadlockSnapshot.Status.UNKNOWN,
                        "UNAVAILABLE",
                        -1,
                        List.of(),
                        "ACCESS_DENIED"));

        assertEquals("DANGER", response.get("health"));
        assertEquals("GAME_TICK_STALLED", response.get("bottleneck"));
    }
}
