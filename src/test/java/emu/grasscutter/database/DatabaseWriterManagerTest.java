package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class DatabaseWriterManagerTest {
    @Test
    void shutdownDrainsAllFourWritersBeforeReportingSuccess() throws Exception {
        var fixture = new Fixture();
        var started = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        for (var pool : fixture.pools.values()) {
            fixture.manager.submit(
                    pool,
                    () -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var finished = new CountDownLatch(1);
            var shutdown =
                    executor.submit(
                            () -> {
                                try {
                                    return fixture.manager.shutdown(Duration.ofSeconds(2));
                                } finally {
                                    finished.countDown();
                                }
                            });

            assertFalse(finished.await(100, TimeUnit.MILLISECONDS));
            release.countDown();
            var result = shutdown.get(2, TimeUnit.SECONDS);
            assertTrue(result.completed());
            assertEquals(0, result.notStartedTaskCount());
            assertTrue(fixture.pools.values().stream().allMatch(ExecutorService::isTerminated));
        } finally {
            release.countDown();
            fixture.close();
        }
    }

    @Test
    void timeoutReportsActiveAndNeverStartedWrites() throws Exception {
        var fixture = new Fixture();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = fixture.pools.get("default");
        fixture.manager.submit(
                first,
                () -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
        fixture.manager.submit(first, () -> {});
        assertTrue(started.await(2, TimeUnit.SECONDS));

        var result = fixture.manager.shutdown(Duration.ofMillis(25));
        try {
            assertFalse(result.completed());
            assertTrue(result.activeAtTimeoutTaskCount() >= 1);
            assertTrue(result.notStartedTaskCount() >= 1);
            assertThrows(
                    RejectedExecutionException.class,
                    () -> fixture.manager.submit(first, () -> fail("ran after shutdown")));
        } finally {
            release.countDown();
            fixture.close();
        }
    }

    @Test
    void repeatedShutdownIsSafe() {
        var fixture = new Fixture();
        try {
            assertTrue(fixture.manager.shutdown(Duration.ofSeconds(1)).completed());
            assertTrue(fixture.manager.shutdown(Duration.ofSeconds(1)).completed());
        } finally {
            fixture.close();
        }
    }

    @Test
    void blockedCallerRunStillHonorsShutdownDeadline() throws Exception {
        var fixture = new Fixture(1);
        var pool = fixture.pools.get("default");
        var workerStarted = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var callerStarted = new CountDownLatch(1);
        var releaseCaller = new CountDownLatch(1);

        fixture.manager.submit(
                pool,
                () -> {
                    workerStarted.countDown();
                    try {
                        releaseWorker.await();
                    } catch (InterruptedException ignored) {
                        // Keep the worker occupied until the test releases it.
                    }
                });
        assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
        fixture.manager.submit(pool, () -> {}); // fill the one-slot queue

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var caller =
                    executor.submit(
                            () ->
                                    fixture.manager.submit(
                                            pool,
                                            () -> {
                                                callerStarted.countDown();
                                                try {
                                                    releaseCaller.await();
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                }
                                            }));
            assertTrue(callerStarted.await(2, TimeUnit.SECONDS));

            var shutdown =
                    executor.submit(
                            () -> fixture.manager.shutdown(Duration.ofMillis(50)));
            var result = shutdown.get(1, TimeUnit.SECONDS);
            assertFalse(result.completed());
            assertTrue(result.activeAtTimeoutTaskCount() >= 1);

            releaseCaller.countDown();
            releaseWorker.countDown();
            caller.get(2, TimeUnit.SECONDS);
        } finally {
            releaseCaller.countDown();
            releaseWorker.countDown();
            fixture.close();
        }
    }

    @Test
    void queueBackpressureStillRunsOnCallerBeforeShutdown() throws Exception {
        var fixture = new Fixture(1);
        var pool = fixture.pools.get("default");
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        fixture.manager.submit(
                pool,
                () -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        fixture.manager.submit(pool, () -> {}); // fill the one-slot queue

        var caller = Thread.currentThread();
        var ranOnCaller = new java.util.concurrent.atomic.AtomicBoolean();
        fixture.manager.submit(pool, () -> ranOnCaller.set(Thread.currentThread() == caller));
        assertTrue(ranOnCaller.get());
        release.countDown();
        assertTrue(fixture.manager.shutdown(Duration.ofSeconds(1)).completed());
    }

    @Test
    void fullOrderedCapacityRejectsOnlySuccessorsOfABusyEntity() throws Exception {
        var pool = new ThreadPoolExecutor(
                4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8),
                new ThreadPoolExecutor.AbortPolicy());
        var manager = new DatabaseWriterManager(Map.of("default", pool), 1);
        var order = new CopyOnWriteArrayList<String>();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            manager.submitOrdered(pool, "a", () -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                order.add("a1");
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            manager.submitOrdered(pool, "a", () -> order.add("a2")); // fills the capacity of one

            var unrelated = new CountDownLatch(2);
            manager.submitOrdered(pool, "b", unrelated::countDown);
            manager.submitOrdered(pool, "c", unrelated::countDown);
            assertTrue(unrelated.await(2, TimeUnit.SECONDS));
            assertThrows(
                    RejectedExecutionException.class,
                    () -> manager.submitOrdered(pool, "a", () -> order.add("a3")));

            release.countDown();
            assertTrue(manager.shutdown(Duration.ofSeconds(2)).completed());
            assertEquals(List.of("a1", "a2"), order);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final LinkedHashMap<String, ExecutorService> pools = new LinkedHashMap<>();
        final DatabaseWriterManager manager;

        Fixture() {
            this(8);
        }

        Fixture(int queueCapacity) {
            for (var name : java.util.List.of("default", "account", "item", "group")) {
                pools.put(
                        name,
                        new ThreadPoolExecutor(
                                1,
                                1,
                                0,
                                TimeUnit.MILLISECONDS,
                                new ArrayBlockingQueue<>(queueCapacity),
                                new ThreadPoolExecutor.AbortPolicy()));
            }
            manager = new DatabaseWriterManager(pools);
        }

        @Override
        public void close() {
            for (var pool : pools.values()) pool.shutdownNow();
        }
    }
}
