package emu.grasscutter.server.threading;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** JVM lock-cycle detection without taking any game-server locks or collecting thread stacks. */
public record JvmDeadlockSnapshot(
        Status status,
        String detectionMode,
        int threadCount,
        List<String> threadNames,
        String reason) {
    private static final int MAX_THREAD_NAMES = 16;

    public enum Status {
        CLEAR,
        DEADLOCK,
        UNKNOWN
    }

    public JvmDeadlockSnapshot {
        threadNames = List.copyOf(threadNames);
    }

    public static JvmDeadlockSnapshot collect() {
        try {
            return collect(ManagementFactory.getThreadMXBean());
        } catch (RuntimeException e) {
            return unavailable(e);
        }
    }

    static JvmDeadlockSnapshot collect(ThreadMXBean threads) {
        String mode = "MONITOR_AND_SYNCHRONIZER";
        long[] ids;
        try {
            try {
                ids = threads.findDeadlockedThreads();
            } catch (UnsupportedOperationException e) {
                mode = "MONITOR_ONLY";
                ids = threads.findMonitorDeadlockedThreads();
            }
        } catch (RuntimeException e) {
            return unavailable(e);
        }

        if (ids == null || ids.length == 0) {
            // Monitor-only detection cannot rule out cycles involving ownable synchronizers.
            boolean complete = mode.equals("MONITOR_AND_SYNCHRONIZER");
            return new JvmDeadlockSnapshot(
                    complete ? Status.CLEAR : Status.UNKNOWN,
                    mode,
                    0,
                    List.of(),
                    complete ? "none" : "OWNABLE_SYNCHRONIZERS_UNCHECKED");
        }

        var names = new ArrayList<String>();
        String reason = "none";
        try {
            var details =
                    threads.getThreadInfo(
                            Arrays.copyOf(ids, Math.min(ids.length, MAX_THREAD_NAMES)), 0);
            for (var detail : details) {
                if (detail != null) names.add(detail.getThreadName());
            }
        } catch (RuntimeException e) {
            // A failed optional name lookup must not hide a detected lock cycle.
            reason = "THREAD_NAMES_UNAVAILABLE";
        }
        return new JvmDeadlockSnapshot(Status.DEADLOCK, mode, ids.length, names, reason);
    }

    private static JvmDeadlockSnapshot unavailable(RuntimeException failure) {
        String reason =
                failure instanceof SecurityException
                        ? "ACCESS_DENIED"
                        : failure instanceof UnsupportedOperationException
                                ? "UNSUPPORTED"
                                : "MXBEAN_ERROR";
        return new JvmDeadlockSnapshot(Status.UNKNOWN, "UNAVAILABLE", -1, List.of(), reason);
    }
}
