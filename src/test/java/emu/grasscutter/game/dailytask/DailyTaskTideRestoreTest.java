package emu.grasscutter.game.dailytask;

import static emu.grasscutter.game.dailytask.DailyTaskGroupLifecycleTest.alloc;
import static emu.grasscutter.game.dailytask.DailyTaskGroupLifecycleTest.set;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.service.ScriptMonsterSpawnService;
import emu.grasscutter.scripts.service.ScriptMonsterTideService;

import org.junit.jupiter.api.*;

import java.util.*;

/** A suspended commission's pending monster-tide waves must survive leave/re-entry and restarts. */
final class DailyTaskTideRestoreTest {
    static final int GROUP = 133212166, MONSTER_ID = 21010201;
    SceneGroup group;

    @BeforeEach
    void setup() {
        group = SceneGroup.of(GROUP);
        group.block_id = 3001;
        group.monsters = new LinkedHashMap<>();
        for (int config = 1; config <= 4; config++) {
            var monster = new SceneMonster();
            monster.config_id = config;
            monster.monster_id = MONSTER_ID;
            monster.pos = new Position();
            monster.rot = new Position();
            group.monsters.put(config, monster);
        }
    }

    @Test
    void restoredSurvivorsAreNotCountedAsNewSpawns() throws Exception {
        var scripts = scripts();
        scripts.startMonsterTideInGroup("1", group, new Integer[] {1, 2, 3, 4}, 4, 2);
        var tide = scripts.getMonsterTides(GROUP).get(0);
        assertEquals(2, tide.snapshot().alive);
        assertEquals(2, tide.snapshot().tideCount);

        scripts.restoreMonster(GROUP, 3001, group.monsters.get(1));
        scripts.restoreMonster(GROUP, 3001, group.monsters.get(2));
        assertEquals(2, tide.snapshot().alive);
        assertEquals(2, tide.snapshot().tideCount);

        scripts.createMonster(GROUP, 3001, group.monsters.get(3));
        assertEquals(3, tide.snapshot().alive, "an ordinary spawn is still counted");
    }

    @Test
    void coldRestoreResumesPendingWavesWithoutSpawningAnotherFirstTurn() throws Exception {
        var before = scripts();
        before.startMonsterTideInGroup("1", group, new Integer[] {1, 2, 3, 4}, 4, 2);
        var saved = new Gson().toJson(before.getMonsterTides(GROUP).get(0).snapshot());

        var cold = scripts();
        cold.restoreMonsterTide(
                group, new Gson().fromJson(saved, ScriptMonsterTideService.State.class));
        assertEquals(0, cold.added.size(), "restoring does not spawn a new first turn");
        var tide = cold.getMonsterTides(GROUP).get(0);
        assertEquals(2, tide.snapshot().alive);
        assertEquals(2, tide.snapshot().tideCount);

        cold.getScriptMonsterSpawnService().onMonsterDead(null);
        assertEquals(1, cold.added.size(), "a kill after restore spawns the next pending wave");
        assertEquals(1, tide.snapshot().killCount);
    }

    @Test
    void finishedTidesAreNotSavedAndUnloadDetachesListeners() throws Exception {
        var scripts = scripts();
        scripts.startMonsterTideInGroup("1", group, new Integer[] {1}, 0, 0);
        assertFalse(scripts.getMonsterTides(GROUP).get(0).isPending());
        scripts.unloadMonsterTides(GROUP);
        assertTrue(scripts.getMonsterTides(GROUP).isEmpty());
        assertTrue(scripts.getScriptMonsterSpawnService().onMonsterCreatedListener.isEmpty());
        assertTrue(scripts.getScriptMonsterSpawnService().onMonsterDeadListener.isEmpty());
    }

    @Test
    void cancelledTideIsNotTrackedAndRestoredTideStaysCancellable() throws Exception {
        var scripts = scripts();
        scripts.startMonsterTideInGroup("1", group, new Integer[] {1, 2}, 2, 1);
        scripts.unloadCurrentMonsterTide();
        assertTrue(scripts.getMonsterTides(GROUP).isEmpty());

        var state = new ScriptMonsterTideService.State();
        state.source = "2";
        state.tideCount = 1;
        state.alive = 1;
        state.sceneLimit = 1;
        state.orders = List.of(2);
        state.configIds = List.of(1, 2);
        scripts.restoreMonsterTide(group, state);
        assertEquals(1, scripts.getMonsterTides(GROUP).size());
        scripts.unloadCurrentMonsterTide();
        assertTrue(scripts.getMonsterTides(GROUP).isEmpty());
        assertTrue(scripts.getScriptMonsterSpawnService().onMonsterDeadListener.isEmpty());
    }

    @Test
    void detachedTideIgnoresUnrelatedDeathsAndRefusedSpawnsAreSkipped() throws Exception {
        var scripts = scripts();
        scripts.startMonsterTideInGroup("1", group, new Integer[] {1, 2, 3, 4}, 4, 2);
        scripts.unloadMonsterTides(GROUP);
        scripts.added.clear();
        scripts.getScriptMonsterSpawnService().onMonsterDead(null);
        assertTrue(scripts.added.isEmpty(), "a detached tide never reacts to other deaths");

        var live = scripts();
        live.startMonsterTideInGroup("1", group, new Integer[] {1, 2, 3, 4}, 4, 2);
        live.refuseCreation = true;
        live.added.clear();
        assertDoesNotThrow(() -> live.getScriptMonsterSpawnService().onMonsterDead(null));
        assertTrue(live.added.isEmpty());
    }

    @Test
    void tideTrackingCanBeReadWhileLuaStartsAndCancelsTides() throws Exception {
        var scripts = scripts();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writer =
                    executor.submit(
                            () -> {
                                for (int i = 0; i < 2000; i++) {
                                    scripts.startMonsterTideInGroup(
                                            "concurrent", group, new Integer[] {1}, 0, 0);
                                    scripts.unloadCurrentMonsterTide();
                                }
                            });
            var reader =
                    executor.submit(
                            () -> {
                                for (int i = 0; i < 2000; i++)
                                    for (var tide : scripts.getMonsterTides(GROUP))
                                        assertEquals("concurrent", tide.snapshot().source);
                            });
            writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            reader.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertTrue(scripts.getMonsterTides(GROUP).isEmpty());
        assertNull(scripts.getScriptMonsterTideService());
        assertTrue(scripts.getScriptMonsterSpawnService().onMonsterCreatedListener.isEmpty());
        assertTrue(scripts.getScriptMonsterSpawnService().onMonsterDeadListener.isEmpty());
    }

    @Test
    void startupReleasesScriptsBeforeWaitingForSceneAndHonorsCancellation() throws Exception {
        var scripts = scripts();
        scripts.beforeFirstTurn = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            java.util.concurrent.Future<?> startup;
            synchronized (scripts.scene) {
                startup =
                        executor.submit(
                                () ->
                                        scripts.startMonsterTideInGroup(
                                                "departure", group, new Integer[] {1, 2}, 2, 1));
                assertTrue(scripts.beforeFirstTurn.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertFalse(startup.isDone(), "spawning must wait for the scene monitor");
                // Scene.removePlayer/onDestroy use scene -> scripts. This must not block on a
                // startup that holds scripts while waiting for this scene.
                scripts.unloadCurrentMonsterTide();
            }
            startup.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertTrue(
                scripts.added.isEmpty(),
                "cancellation before publication prevents first-turn spawns");
        assertTrue(scripts.getMonsterTides(GROUP).isEmpty());
        assertTrue(scripts.getScriptMonsterSpawnService().onMonsterCreatedListener.isEmpty());
    }

    @Test
    void departureBeforePublicationPersistsStartupAndColdResumePublishesItOnce() throws Exception {
        var scripts = scripts();
        scripts.beforeFirstTurn = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            java.util.concurrent.Future<?> startup;
            synchronized (scripts.scene) {
                startup =
                        executor.submit(
                                () ->
                                        scripts.startMonsterTideInGroup(
                                                "departure", group, new Integer[] {1, 2}, 2, 1));
                assertTrue(scripts.beforeFirstTurn.await(5, java.util.concurrent.TimeUnit.SECONDS));
                synchronized (scripts) {
                    set(SceneScriptManager.class, scripts, "destroyed", true);
                }
            }
            startup.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertTrue(scripts.added.isEmpty());
        var pending = scripts.getMonsterTides(GROUP).get(0).snapshot();
        assertFalse(pending.started);
        assertEquals(0, pending.alive);
        var cold = scripts();
        cold.restoreMonsterTide(
                group,
                new Gson()
                        .fromJson(
                                new Gson().toJson(pending), ScriptMonsterTideService.State.class));
        var tide = cold.getMonsterTides(GROUP).get(0);
        tide.spawnFirstTurn();
        assertEquals(1, cold.added.size());
        assertTrue(tide.snapshot().started);
        assertEquals(1, tide.snapshot().alive);
        tide.spawnFirstTurn();
        assertEquals(1, cold.added.size());
    }

    @Test
    void legacySnapshotsNeverRepublishTheirFirstTurn() throws Exception {
        var scripts = scripts();
        var state = new ScriptMonsterTideService.State();
        state.source = "legacy";
        state.tideCount = 2;
        state.sceneLimit = 1;
        state.orders = List.of(1, 2);
        state.configIds = List.of(1, 2);
        scripts.restoreMonsterTide(
                group, state); // Absent started field must retain legacy semantics.
        scripts.getMonsterTides(GROUP).get(0).spawnFirstTurn();
        assertTrue(scripts.added.isEmpty());
    }

    static T scripts() throws Exception {
        var scene = alloc(DailyTaskGroupLifecycleTest.S.class);
        scene.creationSentinel = false;
        var scripts = alloc(T.class);
        scripts.scene = scene;
        scripts.added = new ArrayList<>();
        set(
                SceneScriptManager.class,
                scripts,
                "scriptMonsterSpawnService",
                new ScriptMonsterSpawnService(scripts));
        set(SceneScriptManager.class, scripts, "monsterTides", new ArrayList<>());
        return scripts;
    }

    static final class T extends SceneScriptManager {
        DailyTaskGroupLifecycleTest.S scene;
        List<GameEntity> added;
        boolean refuseCreation;
        java.util.concurrent.CountDownLatch beforeFirstTurn;

        T() {
            super(null);
        }

        @Override
        public emu.grasscutter.game.world.Scene getScene() {
            if (beforeFirstTurn != null) {
                assertFalse(
                        Thread.holdsLock(this),
                        "startup must release scripts before acquiring scene");
                beforeFirstTurn.countDown();
            }
            return scene;
        }

        /**
         * Entity construction needs a full World; creation listeners follow the production gate.
         */
        @Override
        public emu.grasscutter.game.entity.EntityMonster createMonster(
                int groupId, int blockId, SceneMonster meta) {
            if (refuseCreation) return null;
            try {
                var entity = alloc(DailyTaskGroupLifecycleTest.E.class);
                entity.setConfigId(meta.config_id);
                if (!isRestoringMonster())
                    getScriptMonsterSpawnService()
                            .onMonsterCreatedListener
                            .forEach(l -> l.onNotify(entity));
                return entity;
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        }

        @Override
        public void addEntity(GameEntity entity) {
            added.add(entity);
        }
    }
}
