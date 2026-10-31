package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.home.HomeWorld;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sun.misc.Unsafe;

@ExtendWith(ServerResourceFixture.class)
public final class GameServerWorldTickConcurrencyTest {
    @Test
    void successfulTicksPublishCompletionAndTiming() throws Exception {
        var server = server();

        assertEquals(0, server.getCompletedTickCount());
        assertEquals(0, server.getLastTickCompletedNanos());
        server.onTick();

        assertEquals(1, server.getCompletedTickCount());
        assertTrue(server.getLastTickCompletedAtMillis() > 0);
        assertNotEquals(0, server.getLastTickCompletedNanos());
        assertTrue(server.getLastTickDurationMillis() >= 0);
        assertTrue(server.getMaxTickDurationMillis() >= server.getLastTickDurationMillis());
        long firstCompletion = server.getLastTickCompletedNanos();
        long previousMax = server.getMaxTickDurationMillis();

        server.onTick();

        assertEquals(2, server.getCompletedTickCount());
        assertTrue(server.getLastTickCompletedNanos() - firstCompletion >= 0);
        assertTrue(server.getMaxTickDurationMillis() >= previousMax);
        assertTrue(server.getMaxTickDurationMillis() >= server.getLastTickDurationMillis());
    }

    @Test
    void abortedTickDoesNotPublishSuccessfulCompletion() throws Exception {
        var server = server();
        server.onTick();
        long completedAt = server.getLastTickCompletedAtMillis();
        long completedNanos = server.getLastTickCompletedNanos();
        // Force an uncaught top-level failure without mutating global plugins or watchdog state.
        set(server, GameServer.class, "players", null);

        assertThrows(NullPointerException.class, server::onTick);

        assertEquals(1, server.getCompletedTickCount());
        assertEquals(completedAt, server.getLastTickCompletedAtMillis());
        assertEquals(completedNanos, server.getLastTickCompletedNanos());
        assertTrue(server.getLastTickDurationMillis() >= 0);
        assertTrue(server.getMaxTickDurationMillis() >= server.getLastTickDurationMillis());
    }

    @Test
    void statusMetricsCanBeReadWhileAnotherThreadHoldsTickMonitor() throws Exception {
        var server = server();

        synchronized (server) {
            var reading = runAsync(() -> {
                assertEquals(0, server.getCompletedTickCount());
                assertEquals(0, server.getLastTickCompletedAtMillis());
                assertEquals(0, server.getLastTickCompletedNanos());
                assertEquals(0, server.getLastTickDurationMillis());
                assertEquals(0, server.getMaxTickDurationMillis());
            });
            reading.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void worldAndPlayerCallbacksRunOutsideGlobalCollectionMonitors() throws Exception {
        var server = server();
        var world = world();
        var player = player(1);
        var worldHeldCollectionLock = new AtomicBoolean();
        var playerHeldCollectionLock = new AtomicBoolean();
        world.tick = () -> worldHeldCollectionLock.set(holdsCollectionLock(server));
        player.tick = () -> playerHeldCollectionLock.set(holdsCollectionLock(server));
        server.registerWorld(world);
        server.registerPlayer(player);

        server.onTick();

        assertEquals(1, world.ticks.get());
        assertEquals(1, player.ticks.get());
        assertFalse(worldHeldCollectionLock.get());
        assertFalse(playerHeldCollectionLock.get());
    }

    @Test
    void slowWorldDoesNotBlockWorldRegistrationOrHomeCacheAccess() throws Exception {
        var server = server();
        var world = world();
        var added = world();
        var home = home(player(1));
        var tickStarted = new CountDownLatch(1);
        var releaseTick = new CountDownLatch(1);
        var collectionsAccessed = new CountDownLatch(1);
        world.tick = () -> {
            tickStarted.countDown();
            await(releaseTick);
        };
        server.registerWorld(world);

        var ticking = runAsync(server::onTick);
        await(tickStarted);
        var registering = runAsync(() -> {
            server.registerWorld(added);
            server.getHomeWorlds().put(1, home);
            collectionsAccessed.countDown();
        });
        try {
            assertTrue(collectionsAccessed.await(3, TimeUnit.SECONDS),
                    "A world tick must not retain the world-set or home-cache monitor");
        } finally {
            releaseTick.countDown();
        }
        ticking.get(5, TimeUnit.SECONDS);
        registering.get(5, TimeUnit.SECONDS);
        assertSame(home, server.getHomeWorlds().get(1));
        assertTrue(server.getWorlds().contains(added));
        assertEquals(0, added.ticks.get(), "New worlds wait until the next snapshot");
    }

    @Test
    void worldRemovedAfterSnapshotIsNotTicked() throws Exception {
        var server = server();
        var first = world();
        var removed = world();
        first.tick = () -> server.getWorlds().remove(removed);
        server.registerWorld(first);
        server.registerWorld(removed);

        server.onTick();

        assertEquals(1, first.ticks.get());
        assertEquals(0, removed.ticks.get());
    }

    @Test
    void emptyWorldIsRemoved() throws Exception {
        var server = server();
        var world = world();
        world.playerCount = 0;
        world.shouldRemove = true;
        server.registerWorld(world);

        server.onTick();

        assertFalse(server.getWorlds().contains(world));
    }

    @Test
    void playerJoiningAfterEmptyTickResultKeepsWorldRegistered() throws Exception {
        var server = server();
        var world = world();
        world.playerCount = 0;
        world.shouldRemove = true;
        world.tick = () -> world.playerCount = 1;
        server.registerWorld(world);

        server.onTick();

        assertTrue(server.getWorlds().contains(world));
    }

    @Test
    void emptyWorldCleanupWaitsForConcurrentJoinUnderWorldMonitor() throws Exception {
        var server = server();
        var world = world();
        var joining = new CountDownLatch(1);
        var emptyResult = new CountDownLatch(1);
        var finishJoin = new CountDownLatch(1);
        world.playerCount = 0;
        world.shouldRemove = true;
        world.tick = () -> {
            await(joining);
            emptyResult.countDown();
        };
        server.registerWorld(world);
        var joiningPlayer = runAsync(() -> {
            synchronized (world) {
                joining.countDown();
                await(finishJoin);
                world.playerCount = 1;
            }
        });
        await(joining);
        var ticking = runAsync(server::onTick);
        await(emptyResult);
        try {
            assertThrows(TimeoutException.class, () -> ticking.get(150, TimeUnit.MILLISECONDS),
                    "Retirement must synchronize with an in-progress player join");
        } finally {
            finishJoin.countDown();
        }
        joiningPlayer.get(5, TimeUnit.SECONDS);
        ticking.get(5, TimeUnit.SECONDS);
        assertTrue(server.getWorlds().contains(world));
    }

    @Test
    void throwingWorldDoesNotSkipOtherWorldsPlayersOrScheduler() throws Exception {
        var server = server();
        var failing = world();
        var healthy = world();
        var player = player(1);
        var scheduled = new AtomicInteger();
        failing.tick = () -> {
            throw new IllegalStateException("Expected test tick failure");
        };
        server.registerWorld(failing);
        server.registerWorld(healthy);
        server.registerPlayer(player);
        server.getScheduler().scheduleTask(scheduled::incrementAndGet);

        server.onTick();

        assertEquals(1, healthy.ticks.get());
        assertEquals(1, player.ticks.get());
        assertEquals(1, scheduled.get());
        assertTrue(server.getWorlds().contains(failing));
    }

    @Test
    void emptyHomeWorldRemovesBothIndexes() throws Exception {
        var server = server();
        var home = home(player(1));
        server.registerWorld(home);
        server.getHomeWorlds().put(1, home);

        server.onTick();

        assertFalse(server.getWorlds().contains(home));
        assertNull(server.getHomeWorlds().get(1));
    }

    @Test
    void oldEmptyHomeWorldDoesNotDeleteReplacementFromCache() throws Exception {
        var server = server();
        var owner = player(1);
        var oldHome = home(owner);
        var replacement = home(owner);
        oldHome.tick = () -> server.getHomeWorlds().put(1, replacement);
        server.registerWorld(oldHome);
        server.getHomeWorlds().put(1, oldHome);

        server.onTick();

        assertFalse(server.getWorlds().contains(oldHome));
        assertSame(replacement, server.getHomeWorlds().get(1));
    }

    @Test
    void occupiedHomeWorldIsNotReleased() throws Exception {
        var server = server();
        var home = home(player(1));
        home.playerCount = 1;
        server.registerWorld(home);
        server.getHomeWorlds().put(1, home);

        server.releaseHomeWorldIfEmpty(home);

        assertTrue(server.getWorlds().contains(home));
        assertSame(home, server.getHomeWorlds().get(1));
    }

    @Test
    void fiftyWorldAndPlayerFixturesKeepTickingDuringConcurrentCollectionChanges() throws Exception {
        var server = server();
        var worlds = new ArrayList<RecordingWorld>();
        var players = new ArrayList<RecordingPlayer>();
        var heldCollectionLock = new AtomicBoolean();
        Runnable callback = () -> {
            if (holdsCollectionLock(server)) heldCollectionLock.set(true);
        };
        for (int uid = 1; uid <= 50; uid++) {
            var world = world();
            var player = player(uid);
            world.tick = callback;
            player.tick = callback;
            worlds.add(world);
            players.add(player);
            server.registerWorld(world);
            server.registerPlayer(player);
        }
        var transientWorld = world();
        var transientHome = home(player(1001));
        var start = new CountDownLatch(1);
        var ticking = runAsync(() -> {
            await(start);
            for (int index = 0; index < 200; index++) server.onTick();
        });
        var changingCollections = runAsync(() -> {
            await(start);
            for (int index = 0; index < 2000; index++) {
                server.registerWorld(transientWorld);
                server.getHomeWorlds().put(1001, transientHome);
                assertSame(transientHome, server.getHomeWorlds().get(1001));
                server.deregisterWorld(transientWorld);
                assertTrue(server.getHomeWorlds().remove(1001, transientHome));
            }
        });
        start.countDown();
        ticking.get(10, TimeUnit.SECONDS);
        changingCollections.get(10, TimeUnit.SECONDS);

        assertFalse(heldCollectionLock.get());
        assertEquals(50, server.getWorlds().size());
        assertEquals(50, server.getPlayers().size());
        assertTrue(server.getHomeWorlds().isEmpty());
        for (var world : worlds) assertEquals(200, world.ticks.get());
        for (var player : players) assertEquals(200, player.ticks.get());
    }

    private static boolean holdsCollectionLock(GameServer server) {
        return Thread.holdsLock(server.getWorlds())
                || Thread.holdsLock(server.getHomeWorlds())
                || Thread.holdsLock(server.getPlayers());
    }

    private static GameServer server() throws Exception {
        var server = allocate(GameServer.class);
        set(server, GameServer.class, "worlds", Collections.synchronizedSet(new LinkedHashSet<>()));
        set(server, GameServer.class, "homeWorlds",
                Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<HomeWorld>()));
        set(server, GameServer.class, "players", new ConcurrentHashMap<Integer, Player>());
        set(server, GameServer.class, "scheduler", new ServerTaskScheduler());
        return server;
    }

    private static RecordingWorld world() throws Exception {
        var world = allocate(RecordingWorld.class);
        world.playerCount = 1;
        world.ticks = new AtomicInteger();
        world.tick = () -> {};
        return world;
    }

    private static RecordingPlayer player(int uid) throws Exception {
        var player = allocate(RecordingPlayer.class);
        player.uid = uid;
        player.ticks = new AtomicInteger();
        player.tick = () -> {};
        return player;
    }

    private static RecordingHomeWorld home(RecordingPlayer owner) throws Exception {
        var home = allocate(RecordingHomeWorld.class);
        home.owner = owner;
        home.tick = () -> {};
        return home;
    }

    private static CompletableFuture<Void> runAsync(Runnable action) {
        var result = new CompletableFuture<Void>();
        var thread = new Thread(() -> {
            try {
                action.run();
                result.complete(null);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        }, "game-server-world-lock-test");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for the test thread");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object instance, Class<?> owner, String name, Object value)
            throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class RecordingWorld extends World {
        private int playerCount;
        private boolean shouldRemove;
        private AtomicInteger ticks;
        private Runnable tick;

        private RecordingWorld() {
            super((Player) null);
        }

        @Override
        public int getPlayerCount() {
            return playerCount;
        }

        @Override
        public boolean onTick() {
            ticks.incrementAndGet();
            tick.run();
            return shouldRemove;
        }

        @Override
        public void save() {}
    }

    private static final class RecordingHomeWorld extends HomeWorld {
        private RecordingPlayer owner;
        private int playerCount;
        private Runnable tick;

        private RecordingHomeWorld() {
            super(null, null);
        }

        @Override
        public Player getHost() {
            return owner;
        }

        @Override
        public int getPlayerCount() {
            return playerCount;
        }

        @Override
        public boolean onTick() {
            tick.run();
            return true;
        }
    }

    private static final class RecordingPlayer extends Player {
        private int uid;
        private AtomicInteger ticks;
        private Runnable tick;

        @Override
        public int getUid() {
            return uid;
        }

        @Override
        public void onTick() {
            ticks.incrementAndGet();
            tick.run();
        }
    }
}
