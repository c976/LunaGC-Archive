package emu.grasscutter.server.threading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public final class JvmDeadlockSnapshotTest {
    private static ThreadMXBean bean(InvocationHandler handler) {
        return (ThreadMXBean)
                Proxy.newProxyInstance(
                        ThreadMXBean.class.getClassLoader(), new Class<?>[] {ThreadMXBean.class}, handler);
    }

    @Test
    public void completeCheckReportsClearWithoutCollectingStacks() {
        var snapshot =
                JvmDeadlockSnapshot.collect(
                        bean(
                                (proxy, method, args) -> {
                                    assertEquals("findDeadlockedThreads", method.getName());
                                    return null;
                                }));

        assertEquals(JvmDeadlockSnapshot.Status.CLEAR, snapshot.status());
        assertEquals("MONITOR_AND_SYNCHRONIZER", snapshot.detectionMode());
        assertEquals(0, snapshot.threadCount());
        assertTrue(snapshot.threadNames().isEmpty());
    }

    @Test
    public void detectedCycleContainsNamesButNoStacks() {
        var thread =
                ManagementFactory.getThreadMXBean().getThreadInfo(Thread.currentThread().threadId());
        var snapshot =
                JvmDeadlockSnapshot.collect(
                        bean(
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "findDeadlockedThreads" -> new long[] {123L, 456L};
                                            case "getThreadInfo" -> {
                                                assertEquals(0, args[1]);
                                                yield new ThreadInfo[] {thread, null};
                                            }
                                            default -> throw new AssertionError(method.getName());
                                        }));

        assertEquals(JvmDeadlockSnapshot.Status.DEADLOCK, snapshot.status());
        assertEquals(2, snapshot.threadCount());
        assertEquals(List.of(thread.getThreadName()), snapshot.threadNames());
    }

    @Test
    public void nameSamplingIsBoundedWhileThreadCountRemainsComplete() {
        var thread =
                ManagementFactory.getThreadMXBean().getThreadInfo(Thread.currentThread().threadId());
        var snapshot =
                JvmDeadlockSnapshot.collect(
                        bean(
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "findDeadlockedThreads" -> new long[32];
                                            case "getThreadInfo" -> {
                                                assertEquals(16, ((long[]) args[0]).length);
                                                assertEquals(0, args[1]);
                                                var details = new ThreadInfo[16];
                                                Arrays.fill(details, thread);
                                                yield details;
                                            }
                                            default -> throw new AssertionError(method.getName());
                                        }));

        assertEquals(32, snapshot.threadCount());
        assertEquals(16, snapshot.threadNames().size());
    }

    @Test
    public void monitorFallbackDoesNotClaimCompleteClearance() {
        var monitorCalls = new AtomicInteger();
        var snapshot =
                JvmDeadlockSnapshot.collect(
                        bean(
                                (proxy, method, args) -> {
                                    if (method.getName().equals("findDeadlockedThreads"))
                                        throw new UnsupportedOperationException();
                                    assertEquals("findMonitorDeadlockedThreads", method.getName());
                                    monitorCalls.incrementAndGet();
                                    return null;
                                }));

        assertEquals(1, monitorCalls.get());
        assertEquals(JvmDeadlockSnapshot.Status.UNKNOWN, snapshot.status());
        assertEquals("MONITOR_ONLY", snapshot.detectionMode());
        assertEquals("OWNABLE_SYNCHRONIZERS_UNCHECKED", snapshot.reason());
    }

    @Test
    public void monitorFallbackStillReportsDetectedCycle() {
        var snapshot =
                JvmDeadlockSnapshot.collect(
                        bean(
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "findDeadlockedThreads" ->
                                                    throw new UnsupportedOperationException();
                                            case "findMonitorDeadlockedThreads" -> new long[] {123L};
                                            case "getThreadInfo" -> new ThreadInfo[] {null};
                                            default -> throw new AssertionError(method.getName());
                                        }));

        assertEquals(JvmDeadlockSnapshot.Status.DEADLOCK, snapshot.status());
        assertEquals("MONITOR_ONLY", snapshot.detectionMode());
        assertEquals(1, snapshot.threadCount());
    }

    @Test
    public void deniedDetectionIsUnknownAndDoesNotExposeExceptionMessage() {
        var snapshot =
                JvmDeadlockSnapshot.collect(
                        bean(
                                (proxy, method, args) -> {
                                    throw new SecurityException("private details");
                                }));

        assertEquals(JvmDeadlockSnapshot.Status.UNKNOWN, snapshot.status());
        assertEquals(-1, snapshot.threadCount());
        assertEquals("ACCESS_DENIED", snapshot.reason());
        assertTrue(snapshot.threadNames().isEmpty());
    }

    @Test
    public void failedNameLookupCannotHideDetectedCycle() {
        var snapshot =
                JvmDeadlockSnapshot.collect(
                        bean(
                                (proxy, method, args) -> {
                                    if (method.getName().equals("findDeadlockedThreads"))
                                        return new long[] {123L};
                                    throw new SecurityException("private details");
                                }));

        assertEquals(JvmDeadlockSnapshot.Status.DEADLOCK, snapshot.status());
        assertEquals(1, snapshot.threadCount());
        assertEquals("THREAD_NAMES_UNAVAILABLE", snapshot.reason());
    }

    @Test
    public void unsupportedFallbackAndRuntimeFailureAreUnknown() {
        for (var failure :
                new RuntimeException[] {
                    new UnsupportedOperationException(), new IllegalStateException()
                }) {
            var snapshot =
                    JvmDeadlockSnapshot.collect(
                            bean(
                                    (proxy, method, args) -> {
                                        throw failure;
                                    }));
            assertEquals(JvmDeadlockSnapshot.Status.UNKNOWN, snapshot.status());
            assertEquals(-1, snapshot.threadCount());
        }
    }
}
