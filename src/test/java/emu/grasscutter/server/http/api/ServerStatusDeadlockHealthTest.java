package emu.grasscutter.server.http.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.server.threading.JvmDeadlockSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class ServerStatusDeadlockHealthTest {
    private static Map<String, Object> response(String health) {
        var response = new LinkedHashMap<String, Object>();
        response.put("retcode", 0);
        response.put("health", health);
        response.put("bottleneck", "original");
        response.put("diagnosis", "original diagnosis");
        response.put("suggestion", "original suggestion");
        return response;
    }

    @Test
    public void detectedDeadlockOverridesEvenExistingDanger() {
        for (var health : List.of("NORMAL", "BUSY", "WARNING", "DANGER")) {
            var response = response(health);
            ServerStatusHandler.applyDeadlockHealth(
                    response,
                    new JvmDeadlockSnapshot(
                            JvmDeadlockSnapshot.Status.DEADLOCK,
                            "MONITOR_AND_SYNCHRONIZER",
                            2,
                            List.of("server-tick", "game-logic"),
                            "none"));

            assertEquals("DANGER", response.get("health"));
            assertEquals("JVM_DEADLOCK", response.get("bottleneck"));
            assertEquals(0, response.get("retcode"));
        }
    }

    @Test
    public void unavailableDetectionCannotClaimNormalOrBusy() {
        for (var health : List.of("NORMAL", "BUSY")) {
            var response = response(health);
            ServerStatusHandler.applyDeadlockHealth(
                    response,
                    new JvmDeadlockSnapshot(
                            JvmDeadlockSnapshot.Status.UNKNOWN,
                            "UNAVAILABLE",
                            -1,
                            List.of(),
                            "ACCESS_DENIED"));

            assertEquals("WARNING", response.get("health"));
            assertEquals("JVM_DEADLOCK_CHECK", response.get("bottleneck"));
        }
    }

    @Test
    public void unavailableDetectionDoesNotMaskAnotherKnownProblem() {
        for (var health : List.of("WARNING", "DANGER")) {
            var response = response(health);
            var before = new LinkedHashMap<>(response);
            ServerStatusHandler.applyDeadlockHealth(
                    response,
                    new JvmDeadlockSnapshot(
                            JvmDeadlockSnapshot.Status.UNKNOWN,
                            "UNAVAILABLE",
                            -1,
                            List.of(),
                            "ACCESS_DENIED"));

            assertEquals(before, response);
        }
    }

    @Test
    public void completeClearCheckPreservesExistingHealthFields() {
        var response = response("NORMAL");
        var before = new LinkedHashMap<>(response);
        ServerStatusHandler.applyDeadlockHealth(
                response,
                new JvmDeadlockSnapshot(
                        JvmDeadlockSnapshot.Status.CLEAR,
                        "MONITOR_AND_SYNCHRONIZER",
                        0,
                        List.of(),
                        "none"));

        assertEquals(before, response);
    }
}
