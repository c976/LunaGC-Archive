package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.binout.routes.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.entity.gadget.platform.ConfigRoute;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.scheduler.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/** Constructor-free scene fixtures exercise production EntityGadget, ScriptLib, and scheduler. */
class ConfigRouteDeadlineTest {
    static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static <T> T empty(Class<T> type) throws Exception {
        return type.cast(UNSAFE.allocateInstance(type));
    }

    static void field(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                f.set(object, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    static class TestScene extends Scene {
        int now, sceneId;
        Route route;
        TestGadget gadget;
        TestManager manager;
        ServerTaskScheduler scheduler;
        List<BasePacket> packets;
        DungeonManager dungeon;

        TestScene() {
            super(null, null);
        }

        @Override
        public int getId() {
            return sceneId;
        }

        @Override
        public int getSceneTime() {
            return now;
        }

        @Override
        public Route getSceneRouteById(int id) {
            return route;
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return manager;
        }

        @Override
        public ServerTaskScheduler getScheduler() {
            return scheduler;
        }

        @Override
        public DungeonManager getDungeonManager() {
            return dungeon;
        }

        @Override
        public GameEntity getEntityByConfigId(int id, int group) {
            return gadget;
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    static class TestManager extends SceneScriptManager {
        TestScene scene;
        List<ScriptArgs> events;

        TestManager() {
            super(null);
        }

        @Override
        public Scene getScene() {
            return scene;
        }

        @Override
        public Future<?> callEvent(ScriptArgs args) {
            events.add(args);
            return CompletableFuture.completedFuture(null);
        }
    }

    static class TestGadget extends EntityGadget {
        TestGadget() {
            super(null, 0, null);
        }

        @Override
        public SceneEntityInfoOuterClass.SceneEntityInfo toProto() {
            return SceneEntityInfoOuterClass.SceneEntityInfo.newBuilder()
                    .setEntityId(getId())
                    .setGadget(
                            SceneGadgetInfoOuterClass.SceneGadgetInfo.newBuilder()
                                    .setPlatform(getPlatformInfo()))
                    .build();
        }
    }

    static class Fixture {
        TestScene scene;

        /** Reach-point events only; each waypoint also fires a platform arrival event. */
        List<ScriptArgs> reachEvents() {
            return scene.manager.events.stream()
                    .filter(e -> e.type == emu.grasscutter.scripts.constants.EventType.EVENT_PLATFORM_REACH_POINT)
                    .toList();
        }
        TestGadget gadget;
        ConfigRoute config;
        ScriptLib lib;

        Fixture(int rate) throws Exception {
            scene = empty(TestScene.class);
            scene.sceneId = 3;
            scene.now = 100000;
            scene.scheduler = new ServerTaskScheduler();
            scene.packets = new ArrayList<>();
            scene.manager = empty(TestManager.class);
            scene.manager.scene = scene;
            scene.manager.events = new ArrayList<>();
            gadget = empty(TestGadget.class);
            scene.gadget = gadget;
            field(gadget, "scene", scene);
            field(gadget, "position", new Position());
            field(gadget, "rotation", new Position());
            gadget.setId(0x04000001);
            gadget.setGroupId(133003174);
            gadget.setConfigId(174002);
            var meta = new SceneGadget();
            meta.route_id = 300300282;
            meta.start_route = false;
            meta.rot = new Position();
            gadget.setMetaGadget(meta);
            config = new ConfigRoute(meta);
            gadget.setRouteConfig(config);
            RoutePoint[] points = new RoutePoint[3];
            for (int i = 0; i < 3; i++) {
                points[i] = new RoutePoint();
                points[i].setPos(new Position(2 * (i + 1), 0, 0));
                points[i].setTargetVelocity(2);
                points[i].setRotRoundLeaveDir(new Position(10, 20, 30));
            }
            points[2].setHasReachEvent(true);
            scene.route = new Route();
            scene.route.setPoints(points);
            lib = new ScriptLib();
            lib.setSceneScriptManager(scene.manager);
            var group = new SceneGroup();
            group.id = 133003174;
            lib.setCurrentGroup(group);
            Configuration.GAME_INFO.tickRateMs = rate;
        }

        List<Integer> ids() {
            return new ArrayList<>(config.getScheduledIndexes());
        }

        List<ServerTask> tasks() {
            return ids().stream().map(scene.scheduler::getTask).toList();
        }

        void polls(int n) {
            for (int i = 0; i < n; i++) scene.scheduler.runTasks();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void normalPollPhasesNeverFireEarly(int rate) throws Exception {
        for (int phase : new int[] {0, rate}) {
            Fixture f = new Fixture(rate);
            assertEquals(0, f.lib.StartPlatform(174002));
            List<Integer> ids = f.ids();
            assertEquals(101000, f.config.toProto().getStartSceneTime());
            assertEquals(0, f.config.toProto().getStartIndex());
            for (int elapsed = phase; elapsed <= 3000 + rate; elapsed += rate) {
                f.scene.now = 100000 + elapsed;
                f.polls(1);
                for (int i = 0; i < 3; i++) {
                    if (elapsed < 1000 + i * 1000)
                        assertNotNull(f.scene.scheduler.getTask(ids.get(i)));
                    else assertNull(f.scene.scheduler.getTask(ids.get(i)));
                }
                if (elapsed < 3000) assertEquals(1, f.scene.manager.events.size());
            }
            assertEquals(2, f.config.getStartIndex());
            assertEquals(3, f.scene.manager.events.size());
            var arrival = f.scene.manager.events.get(2);
            assertEquals(emu.grasscutter.scripts.constants.EventType.EVENT_PLATFORM_ARRIVAL, arrival.type);
            assertEquals(174002, arrival.param1);
            assertEquals(300300282, arrival.param2);
            assertEquals(2, arrival.param3);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void frozenClockAndWrapUseDeadlinesNotPollCounts(int rate) throws Exception {
        for (int initial : new int[] {100000, Integer.MAX_VALUE - 200}) {
            Fixture f = new Fixture(rate);
            f.scene.now = initial;
            assertTrue(f.gadget.startPlatform());
            List<Integer> ids = f.ids();
            List<ServerTask> tasks = f.tasks();
            f.polls(1000);
            assertEquals(0, f.gadget.getPosition().getX());
            assertEquals(1, f.scene.manager.events.size());
            for (int i = 0; i < 3; i++) {
                assertSame(tasks.get(i), f.scene.scheduler.getTask(ids.get(i)));
                assertEquals(1000, tasks.get(i).getTicks());
            }
            int origin = f.config.getStartSceneTime();
            assertEquals(initial + 1000, origin);
            for (int i = 0; i < 3; i++) {
                f.scene.now = origin + i * 1000 - 1;
                f.polls(1000);
                assertNotNull(f.scene.scheduler.getTask(ids.get(i)));
                f.scene.now++;
                f.polls(1);
                assertNull(f.scene.scheduler.getTask(ids.get(i)));
                assertEquals(i, f.config.getStartIndex());
                int events = f.scene.manager.events.size();
                f.polls(1000);
                tasks.get(i).run();
                assertEquals(events, f.scene.manager.events.size());
            }
        }
    }

    @Test
    void stopRestartAndRouteChangeRejectRetainedTasks() throws Exception {
        Fixture f = new Fixture(200);
        assertEquals(0, f.lib.StartPlatform(174002));
        List<ServerTask> old = f.tasks();
        List<Integer> ids = f.ids();
        assertEquals(0, f.lib.StopPlatform(174002));
        assertFalse(f.config.isStarted());
        assertFalse(f.config.isActive());
        for (int id : ids) assertNull(f.scene.scheduler.getTask(id));
        f.scene.now += 10000;
        old.forEach(ServerTask::run);
        assertEquals(0, f.gadget.getPosition().getX());
        assertEquals(0, f.lib.StartPlatform(174002));
        old.forEach(ServerTask::run);
        f.polls(1000);
        assertEquals(0, f.gadget.getPosition().getX());
        List<ServerTask> resumed = f.tasks();
        int packets = f.scene.packets.size();
        assertEquals(0, f.lib.SetPlatformRouteId(174002, 300300282));
        assertEquals(packets, f.scene.packets.size());
        assertEquals(0, f.lib.SetPlatformRouteId(174002, 300300283));
        assertTrue(f.config.getScheduledIndexes().isEmpty());
        assertEquals(0, f.lib.StartPlatform(174002));
        f.scene.now += 10000;
        resumed.forEach(ServerTask::run);
        assertEquals(0, f.gadget.getPosition().getX());
        f.polls(1);
        assertEquals(2, f.config.getStartIndex());
    }

    @Test
    void malformedOrdinaryPlanFailsAtomicallyIncludingFinalIndexReset() throws Exception {
        for (String bad :
                List.of(
                        "negative",
                        "NaN",
                        "infinite",
                        "overflow",
                        "position",
                        "null",
                        "index")) {
            Fixture f = new Fixture(200);
            f.config.setStartIndex(2);
            switch (bad) {
                case "negative" -> f.scene.route.getPoints()[0].setTargetVelocity(-1);
                case "NaN" -> f.scene.route.getPoints()[0].setTargetVelocity(Float.NaN);
                case "infinite" ->
                        f.scene.route.getPoints()[0].setTargetVelocity(Float.POSITIVE_INFINITY);
                case "overflow" -> f.scene.route.getPoints()[1].setTargetVelocity(Float.MIN_VALUE);
                case "position" ->
                        f.scene.route.getPoints()[0].setPos(new Position(Float.NaN, 0, 0));
                case "null" -> f.scene.route.getPoints()[0] = null;
                case "index" -> f.config.setStartIndex(-1);
            }
            int index = f.config.getStartIndex();
            assertEquals(2, f.lib.StartPlatform(174002), bad);
            assertEquals(index, f.config.getStartIndex());
            assertFalse(f.config.isStarted());
            assertFalse(f.config.isActive());
            assertTrue(f.config.getScheduledIndexes().isEmpty());
            assertTrue(f.scene.manager.events.isEmpty());
            assertTrue(f.scene.packets.isEmpty());
        }
    }

    @Test
    void zeroDistanceStillWaitsForAdvertisedLeadAndReturnsRemainCompatible() throws Exception {
        Fixture f = new Fixture(200);
        f.gadget.getPosition().set(f.scene.route.getPoints()[0].getPos());
        assertEquals(0, f.lib.StartPlatform(174002));
        List<Integer> ids = f.ids();
        int packets = f.scene.packets.size();
        assertEquals(0, f.lib.StartPlatform(174002));
        assertEquals(ids, f.ids());
        assertEquals(packets, f.scene.packets.size());
        f.scene.now = f.config.getStartSceneTime() - 1;
        f.polls(1000);
        assertNotNull(f.scene.scheduler.getTask(ids.get(0)));
        f.scene.now++;
        f.polls(1);
        assertNull(f.scene.scheduler.getTask(ids.get(0)));
        assertEquals(0, f.lib.StopPlatform(174002));
        packets = f.scene.packets.size();
        assertEquals(0, f.lib.StopPlatform(174002));
        assertEquals(packets, f.scene.packets.size());
        int index = f.config.getStartIndex();
        assertEquals(0, f.lib.StartPlatform(174002));
        assertEquals(index, f.config.toProto().getStartIndex());
        assertEquals(f.scene.now + 300, f.config.toProto().getStartSceneTime());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void existingLiyueConversionRotationAndGuardsRemain(int rate) throws Exception {
        Fixture f = new Fixture(rate);
        f.scene.sceneId = 1052;
        f.scene.dungeon = empty(DungeonManager.class);
        DungeonData data = new DungeonData();
        field(data, "id", 1103);
        field(f.scene.dungeon, "dungeonData", data);
        f.gadget.setGroupId(201052008);
        f.gadget.setConfigId(8011);
        f.scene.route.getPoints()[0].setWaitTime(2);
        assertTrue(f.gadget.startPlatform());
        List<ServerTask> tasks = f.tasks();
        int due = (int) Math.ceil(3300.0 / rate);
        f.polls(due - 1);
        assertEquals(0, f.gadget.getPosition().getX());
        f.polls(1);
        assertEquals(0, f.gadget.getRotation().computeDistance(new Position(10, 20, 30)));
        int index = f.config.getStartIndex();
        f.config.setStarted(false);
        tasks.get(2).run();
        assertEquals(index, f.config.getStartIndex());
        f.config.setStarted(true);
        f.config.setRouteId(123);
        tasks.get(2).run();
        assertEquals(index, f.config.getStartIndex());
    }

    @Test
    void retainedCallbackFiresOnceUnderConcurrentInvocations() throws Exception {
        Fixture f = new Fixture(200);
        assertTrue(f.gadget.startPlatform());
        List<ServerTask> tasks = f.tasks();
        ServerTask last = tasks.get(2);
        f.scene.now = 103300;
        tasks.get(0).run();
        tasks.get(1).run();
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 64; i++) futures.add(threads.submit(last));
            for (Future<?> future : futures) future.get();
        } finally {
            threads.shutdownNow();
        }
        assertEquals(3, f.scene.manager.events.size());
        assertEquals(2, f.config.getStartIndex());
    }
}
