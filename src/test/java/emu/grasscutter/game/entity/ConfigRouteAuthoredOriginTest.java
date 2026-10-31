package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.binout.routes.*;
import emu.grasscutter.game.entity.gadget.platform.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native configured index/time origin contract, not a rendered-motion or receipt-time oracle. */
class ConfigRouteAuthoredOriginTest {
    static class ActualScene extends ConfigRouteDeadlineTest.TestScene {
        EntityGadget target;
        World world;

        @Override
        public GameEntity getEntityByConfigId(int id, int group) {
            return id == 174002 && group == 133003174 ? target : null;
        }

        @Override
        public World getWorld() {
            return world;
        }
    }

    static class Fixture {
        ActualScene scene;
        EntityGadget gadget;
        ConfigRoute config;
        ScriptLib lib;
        Position spawn;

        Fixture(int rate) throws Exception {
            var source = new ConfigRouteDeadlineTest.Fixture(rate);
            scene = ConfigRouteDeadlineTest.empty(ActualScene.class);
            scene.sceneId = 3;
            scene.now = 10216;
            scene.world = ConfigRouteDeadlineTest.empty(World.class);
            scene.scheduler = source.scene.scheduler;
            scene.packets = new ArrayList<>();
            scene.manager = source.scene.manager;
            scene.manager.scene = scene;
            JsonObject raw;
            try (var stream = getClass().getResourceAsStream("/routes/seelie300300282.json")) {
                assertNotNull(stream);
                raw =
                        JsonParser.parseString(
                                        new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                                .getAsJsonObject();
            }
            spawn = new Gson().fromJson(raw.get("spawn"), Position.class);
            scene.route = new Route();
            scene.route.setPoints(new Gson().fromJson(raw.get("points"), RoutePoint[].class));
            gadget = ConfigRouteDeadlineTest.empty(EntityGadget.class);
            scene.target = gadget;
            ConfigRouteDeadlineTest.field(gadget, "scene", scene);
            ConfigRouteDeadlineTest.field(gadget, "position", new Position(spawn));
            ConfigRouteDeadlineTest.field(gadget, "rotation", new Position());
            ConfigRouteDeadlineTest.field(gadget, "bornPos", new Position(spawn));
            ConfigRouteDeadlineTest.field(gadget, "bornRot", new Position());
            ConfigRouteDeadlineTest.field(gadget, "extraGlobalValues", new LinkedHashMap<>());
            gadget.setId(8388703);
            gadget.setGroupId(133003174);
            gadget.setConfigId(174002);
            gadget.setGadgetId(70710010);
            var meta = new SceneGadget();
            meta.route_id = 300300283;
            meta.pos = new Position(spawn);
            meta.rot = new Position();
            gadget.setMetaGadget(meta);
            config = new ConfigRoute(meta);
            gadget.setRouteConfig(config);
            lib = new ScriptLib();
            lib.setSceneScriptManager(scene.manager);
            var group = new SceneGroup();
            group.id = 133003174;
            lib.setCurrentGroup(group);
        }

        void change() {
            assertEquals(0, lib.SetPlatformRouteId(174002, 300300282));
        }

        void start() {
            scene.now = 11811;
            assertEquals(0, lib.StartPlatform(174002));
        }

        ConfigRouteRun run() throws Exception {
            var f = EntityGadget.class.getDeclaredField("configRouteRun");
            f.setAccessible(true);
            return (ConfigRouteRun) f.get(gadget);
        }

        long[] authored(int from) {
            long[] times = new long[scene.route.getPoints().length];
            double seconds = 0;
            Position previous = scene.route.getPoints()[from].getPos();
            for (int i = from; i < times.length; i++) {
                var p = scene.route.getPoints()[i];
                seconds += p.getPos().computeDistance(previous) / p.getTargetVelocity();
                previous = p.getPos();
                times[i] = (long) Math.ceil(seconds * 1000);
            }
            return times;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void initialPlanMustNotPrependUnserializedSpawnToAuthoredSource(int rate) throws Exception {
        var f = new Fixture(rate);
        f.change();
        f.start();
        var run = f.run();
        long[] expected = f.authored(0);
        assertEquals(13561, f.config.toProto().getStartSceneTime());
        assertEquals(0, f.config.toProto().getStartIndex());
        assertFalse(f.config.toProto().hasStartPos());
        assertAll(
                "authored point zero is the origin; no 1750ms approach exists in the config"
                        + " sampler",
                () -> assertEquals(expected[0], run.travelMillis[0], "source point"),
                () -> assertEquals(expected[1], run.travelMillis[1], "point 1"),
                () -> assertEquals(expected[2], run.travelMillis[2], "point 2"),
                () -> assertEquals(expected[3], run.travelMillis[3], "first stop"));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void initialSerializedMotionRemainsContinuousBeforeAuthoredTimeline(int rate) throws Exception {
        var f = new Fixture(rate);
        f.change();
        f.start();
        var message = f.gadget.toProto();
        var motion = new Position(message.getMotionInfo().getPos());
        var platform = message.getGadget().getPlatform();
        assertEquals(
                0,
                motion.computeDistance(f.spawn),
                0.00001,
                "future-start publication must preserve previous pose");
        assertEquals(1750, f.run().leadMillis);
        assertEquals(0, platform.getStartIndex());
        assertEquals(13561, platform.getStartSceneTime());
        assertEquals(0, platform.getStartRouteTime());
        assertFalse(platform.hasStartPos());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void resumesThreeTenThirteenKeepOriginIndicesDeadlinesAndCatchupSafety(int rate)
            throws Exception {
        for (int from : new int[] {3, 10, 13}) {
            var f = new Fixture(rate);
            f.change();
            f.config.setStartIndex(from);
            f.config.setStarted(false);
            f.config.setActive(false);
            f.gadget.getPosition().set(f.scene.route.getPoints()[from].getPos());
            f.start();
            var run = f.run();
            long[] expected = f.authored(from);
            assertEquals(from, f.config.toProto().getStartIndex());
            assertEquals(12111, f.config.toProto().getStartSceneTime());
            for (int i = from; i <= run.endIndex; i++)
                assertEquals(expected[i], run.travelMillis[i]);
            var ids = new ArrayList<>(f.config.getScheduledIndexes());
            f.scene.now = 12110;
            for (int n = 0; n < 1000; n++) f.scene.scheduler.runTasks();
            assertNotNull(f.scene.scheduler.getTask(ids.get(0)));
            f.scene.now = 12111;
            f.scene.scheduler.runTasks();
            assertNull(f.scene.scheduler.getTask(ids.get(0)));
            assertTrue(f.config.isStarted());
            assertEquals(from, f.config.getStartIndex());
        }
    }

    @Test
    void idleSpawnRouteChangeAndResourcesRemainUnchangedUntilValidStart() throws Exception {
        var f = new Fixture(200);
        var point = new Position(f.scene.route.getPoints()[0].getPos());
        assertEquals(300300283, f.config.getRouteId());
        assertEquals(0, f.gadget.getPosition().computeDistance(f.spawn));
        f.change();
        assertEquals(0, f.gadget.getPosition().computeDistance(f.spawn));
        f.scene.route.getPoints()[0].setTargetVelocity(-1);
        int packets = f.scene.packets.size();
        assertEquals(2, f.lib.StartPlatform(174002));
        assertEquals(0, f.gadget.getPosition().computeDistance(f.spawn));
        assertEquals(packets, f.scene.packets.size());
        assertEquals(0, f.scene.route.getPoints()[0].getPos().computeDistance(point));
    }
}
