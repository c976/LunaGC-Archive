package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.player.Player;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sun.misc.Unsafe;

@ExtendWith(ServerResourceFixture.class)
public final class WorldSceneTickConcurrencyTest {
    @Test
    void sceneRemovalCanAcquireMapWhileTickWaitsForSceneMonitor() throws Exception {
        var world = world();
        var scene = scene(1);
        var sceneLocked = new CountDownLatch(1);
        var tickStarted = new CountDownLatch(1);
        var mapAccessed = new CountDownLatch(1);
        var releaseScene = new CountDownLatch(1);
        scene.tick = () -> {
            tickStarted.countDown();
            synchronized (scene) {
                scene.ticks.incrementAndGet();
            }
        };
        world.registerScene(scene);

        // Keep the scene monitor until the map lookup has been observed, so a regression
        // fails deterministically without leaving two permanently deadlocked test threads.
        var removal = runAsync(() -> {
            CompletableFuture<Void> lookup;
            synchronized (scene) {
                sceneLocked.countDown();
                await(tickStarted);
                lookup = runAsync(() -> {
                    assertSame(scene, world.getScenes().get(scene.getId()));
                    mapAccessed.countDown();
                });
                await(releaseScene);
            }
            lookup.join();
            synchronized (scene) {
                world.deregisterScene(scene);
            }
        });

        await(sceneLocked);
        var ticking = runAsync(world::onTick);
        try {
            assertTrue(mapAccessed.await(3, TimeUnit.SECONDS),
                    "A scene callback must not retain the world's scenes-map monitor");
        } finally {
            releaseScene.countDown();
        }
        removal.get(5, TimeUnit.SECONDS);
        ticking.get(5, TimeUnit.SECONDS);
        assertFalse(world.getScenes().containsKey(scene.getId()));
    }

    @Test
    void sceneCallbacksRunOutsideMapMonitor() throws Exception {
        var world = world();
        var scene = scene(1);
        scene.tick = () -> {
            assertFalse(Thread.holdsLock(world.getScenes()));
            scene.ticks.incrementAndGet();
        };
        world.registerScene(scene);

        assertFalse(world.onTick());

        assertEquals(1, scene.ticks.get());
    }

    @Test
    void sceneRemovedAfterSnapshotIsNotTicked() throws Exception {
        var world = world();
        var first = scene(1);
        var removed = scene(2);
        first.tick = () -> world.deregisterScene(removed);
        world.registerScene(first);
        world.registerScene(removed);

        world.onTick();

        assertEquals(0, removed.ticks.get());
        assertFalse(world.getScenes().containsKey(removed.getId()));
    }

    @Test
    void sceneReplacedAfterSnapshotWaitsUntilNextTick() throws Exception {
        var world = world();
        var first = scene(1);
        var stale = scene(2);
        var replacement = scene(2);
        first.tick = () -> world.registerScene(replacement);
        world.registerScene(first);
        world.registerScene(stale);

        world.onTick();

        assertEquals(0, stale.ticks.get());
        assertEquals(0, replacement.ticks.get());
        assertSame(replacement, world.getScenes().get(2));
        world.onTick();
        assertEquals(1, replacement.ticks.get());
    }

    @Test
    void staleSceneRemovalDoesNotDeleteSameIdReplacement() throws Exception {
        var world = world();
        var stale = scene(1);
        var replacement = scene(1);
        world.registerScene(replacement);

        world.deregisterScene(stale);

        assertSame(replacement, world.getScenes().get(1));
    }

    @Test
    void emptyScenesAreNotTicked() throws Exception {
        var world = world();
        var empty = scene(1);
        empty.playerCount = 0;
        world.registerScene(empty);

        world.onTick();

        assertEquals(0, empty.ticks.get());
    }

    private static World world() throws Exception {
        var world = allocate(World.class);
        set(world, World.class, "players", new ArrayList<>(List.of(allocate(Player.class))));
        Int2ObjectMap<Scene> scenes =
                Int2ObjectMaps.synchronize(new Int2ObjectLinkedOpenHashMap<>());
        set(world, World.class, "scenes", scenes);
        // Skip time notification and saving; this fixture exercises only scene iteration.
        set(world, World.class, "tickCount", 1);
        set(world, World.class, "timeLocked", true);
        return world;
    }

    private static RecordingScene scene(int id) throws Exception {
        var scene = allocate(RecordingScene.class);
        scene.id = id;
        scene.playerCount = 1;
        scene.ticks = new AtomicInteger();
        scene.tick = scene.ticks::incrementAndGet;
        return scene;
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
        }, "world-scene-lock-test");
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

    private static final class RecordingScene extends Scene {
        private int id;
        private int playerCount;
        private AtomicInteger ticks;
        private Runnable tick;

        private RecordingScene() {
            super(null, null);
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public int getPlayerCount() {
            return playerCount;
        }

        @Override
        public void onTick() {
            tick.run();
        }

        @Override
        public void saveGroups() {}
    }
}
