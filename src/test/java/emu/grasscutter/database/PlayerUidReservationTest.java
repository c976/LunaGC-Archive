package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlayerUidReservationTest {
    @Test
    void sequentialAllocationStartsAtOne() {
        var store = new FakeStore();
        assertEquals(1, PlayerUidReservation.next(0, store));
        assertEquals(2, PlayerUidReservation.next(0, store));
    }

    @Test
    void concurrentAllocationNeverReturnsSameUid() throws Exception {
        var store = new FakeStore();
        int calls = 128;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<Integer>>(calls);
            for (int i = 0; i < calls; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return PlayerUidReservation.next(0, store);
                                }));
            }
            start.countDown();
            var ids = ConcurrentHashMap.<Integer>newKeySet();
            for (var future : futures) assertTrue(ids.add(future.get(5, TimeUnit.SECONDS)));
            assertEquals(calls, ids.size());
        }
    }

    @Test
    void reservedUidRaceHasSingleWinner() throws Exception {
        var store = new FakeStore();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var one = executor.submit(() -> { start.await(); return PlayerUidReservation.next(42, store); });
            var two = executor.submit(() -> { start.await(); return PlayerUidReservation.next(42, store); });
            start.countDown();
            int first = one.get(5, TimeUnit.SECONDS);
            int second = two.get(5, TimeUnit.SECONDS);
            assertNotEquals(first, second);
            assertTrue(first == 42 || second == 42);
        }
    }

    @Test
    void blockedReservationDoesNotExposeOldCounterValue() throws Exception {
        var store = new FakeStore();
        var firstReserved = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        store.afterCounterReservation =
                value -> {
                    if (value == 1) {
                        firstReserved.countDown();
                        try {
                            releaseFirst.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }
                };

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> PlayerUidReservation.next(0, store));
            assertTrue(firstReserved.await(2, TimeUnit.SECONDS));
            var second = executor.submit(() -> PlayerUidReservation.next(0, store));
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(2, second.get()));
            releaseFirst.countDown();
            assertEquals(1, first.get(2, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
        }
    }

    private static final class FakeStore implements PlayerUidReservation.Store {
        private final AtomicInteger counter = new AtomicInteger();
        private final Set<Integer> reservations = ConcurrentHashMap.newKeySet();
        private final Set<Integer> players = ConcurrentHashMap.newKeySet();
        private volatile java.util.function.IntConsumer afterCounterReservation = ignored -> {};

        @Override
        public int highestUid() {
            return players.stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        @Override
        public boolean playerExists(int uid) {
            return players.contains(uid);
        }

        @Override
        public boolean tryReserve(int uid) {
            return reservations.add(uid);
        }

        @Override
        public int reserveNext(int floor) {
            counter.accumulateAndGet(floor, Math::max);
            int reserved = counter.incrementAndGet();
            afterCounterReservation.accept(reserved);
            return reserved;
        }
    }
}
