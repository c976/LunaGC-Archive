package emu.grasscutter.game.dailytask;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.SceneScriptManager;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Explicit completion/reset invalidates snapshots before persistence; failed unloads retain them.
 */
class DailyTaskUnregisterStateTest {
    private static final int GROUP = 133001001;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulUnregisterClearsSnapshotsBeforeTheSaveBoundary(boolean alias) throws Exception {
        var f = fixture(alias);
        assertTrue(DailyTaskGroupLifecycle.unregisterOwned(f.scene, GROUP, f.manager));
        assertTrue(f.scene.sawClearedSnapshots);
        assertNull(f.scripts.active.getDailyTaskSnapshot());
        assertNull(f.scripts.cached.getDailyTaskSnapshot());
        assertFalse(f.state.owned.containsKey(GROUP));
        assertFalse(f.state.restored.contains(GROUP));
        assertFalse(f.state.recoveryRequested.contains(GROUP));
        assertFalse(f.state.settling.contains(GROUP));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void declinedUnregisterRestoresSnapshotAndOwnership(boolean alias) throws Exception {
        var f = fixture(alias);
        f.scene.result = false;
        assertFalse(DailyTaskGroupLifecycle.unregisterOwned(f.scene, GROUP, f.manager));
        assertRetained(f, alias);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exceptionRestoresSnapshotAndOwnership(boolean alias) throws Exception {
        var f = fixture(alias);
        f.scene.failure = new IllegalStateException("test unload failure");
        assertThrows(
                IllegalStateException.class,
                () -> DailyTaskGroupLifecycle.unregisterOwned(f.scene, GROUP, f.manager));
        assertRetained(f, alias);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void errorRestoresSnapshotAndOwnership(boolean alias) throws Exception {
        var f = fixture(alias);
        f.scene.failure = new AssertionError("test unload error");
        assertThrows(
                AssertionError.class,
                () -> DailyTaskGroupLifecycle.unregisterOwned(f.scene, GROUP, f.manager));
        assertRetained(f, alias);
    }

    @Test
    void anotherManagerCannotDiscardTheHostsSnapshot() throws Exception {
        var f = fixture(false);
        assertFalse(
                DailyTaskGroupLifecycle.unregisterOwned(f.scene, GROUP, new DailyTaskManager(f.player)));
        assertEquals(0, f.scene.calls);
        assertEquals("active", f.scripts.active.getDailyTaskSnapshot());
        assertEquals("cached", f.scripts.cached.getDailyTaskSnapshot());
        assertTrue(f.state.owned.containsKey(GROUP));
    }

    private static void assertRetained(Fixture f, boolean alias) {
        assertTrue(f.scene.sawClearedSnapshots);
        assertEquals("active", f.scripts.active.getDailyTaskSnapshot());
        assertEquals(alias ? "active" : "cached", f.scripts.cached.getDailyTaskSnapshot());
        assertTrue(f.state.owned.containsKey(GROUP));
        assertTrue(f.state.restored.contains(GROUP));
        assertTrue(f.state.recoveryRequested.contains(GROUP));
        assertTrue(f.state.settling.contains(GROUP));
    }

    private static Fixture fixture(boolean alias) throws Exception {
        var player = allocate(Owner.class);
        var world = allocate(HostWorld.class);
        var scene = allocate(RecordingScene.class);
        var scripts = allocate(Scripts.class);
        var tides = SceneScriptManager.class.getDeclaredField("monsterTides");
        tides.setAccessible(true);
        tides.set(scripts, new java.util.ArrayList<>());
        world.host = player;
        scene.world = world;
        scene.scripts = scripts;
        scene.player = player;
        scene.result = true;
        scripts.active = allocate(SceneGroupInstance.class);
        scripts.active.setDailyTaskSnapshot("active");
        scripts.cached = alias ? scripts.active : allocate(SceneGroupInstance.class);
        if (!alias) scripts.cached.setDailyTaskSnapshot("cached");
        var manager = new DailyTaskManager(player);
        player.manager = manager;
        player.setWorld(world);
        var state = new DailyTaskGroupLifecycle.State(manager, scene);
        state.owned.put(
                GROUP, new DailyTaskGroupLifecycle.Plan(1, GROUP, 20250101, 0, new Position(), 40, 60));
        state.managed.add(GROUP);
        state.restored.add(GROUP);
        state.recoveryRequested.add(GROUP);
        state.settling.add(GROUP);
        scene.setDailyTaskGroups(state);
        return new Fixture(player, scene, scripts, manager, state);
    }

    private record Fixture(
            Owner player,
            RecordingScene scene,
            Scripts scripts,
            DailyTaskManager manager,
            DailyTaskGroupLifecycle.State state) {}

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    static final class Owner extends Player {
        DailyTaskManager manager;

        @Override
        public int getUid() {
            return 10001;
        }

        @Override
        public DailyTaskManager getDailyTaskManager() {
            return manager;
        }
    }

    static final class HostWorld extends World {
        Player host;

        HostWorld() {
            super((Player) null);
        }

        @Override
        public Player getHost() {
            return host;
        }
    }

    static final class Scripts extends SceneScriptManager {
        SceneGroupInstance active, cached;

        Scripts() {
            super(null);
        }

        @Override
        public SceneGroupInstance getGroupInstanceById(int id) {
            return active;
        }

        @Override
        public Map<Integer, SceneGroupInstance> getCachedGroupInstances() {
            return Map.of(GROUP, cached);
        }
    }

    static final class RecordingScene extends Scene {
        HostWorld world;
        Scripts scripts;
        Player player;
        boolean result, sawClearedSnapshots;
        int calls;
        Throwable failure;

        RecordingScene() {
            super(null, null);
        }

        @Override
        public int getId() {
            return 3;
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts;
        }

        @Override
        public List<Player> getPlayers() {
            return List.of(player);
        }

        @Override
        public boolean unregisterDynamicGroup(int id) {
            calls++;
            sawClearedSnapshots =
                    scripts.active.getDailyTaskSnapshot() == null
                            && scripts.cached.getDailyTaskSnapshot() == null;
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            return result;
        }
    }
}
