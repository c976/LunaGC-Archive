package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.binout.routes.RoutePoint;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.scheduler.ServerTask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Separate stop-marker unit; the timing tests run independently on unmarked routes. */
class ConfigRouteStopTest {
    @Test
    void bothStopMarkerAliasesLoadWithoutLosingOtherPointAliases() {
        for (String name : List.of("reachStop", "_reachStop")) {
            RoutePoint point =
                    new Gson()
                            .fromJson(
                                    "{\""
                                            + name
                                            + "\":true,\"_pos\":{\"_x\":2,\"_y\":0,\"_z\":0},\"_targetVelocity\":2}",
                                    RoutePoint.class);
            assertTrue(point.isReachStop());
            assertNotNull(point.getPos());
            assertEquals(2, point.getPos().getX());
            assertEquals(2, point.getTargetVelocity());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void segmentPauseResumeAndAlreadyScheduledStaleTaskRemainSafe(int rate) throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(rate);
        RoutePoint[] points = new RoutePoint[5];
        for (int i = 0; i < points.length; i++) {
            points[i] = new RoutePoint();
            points[i].setPos(new Position(2 * (i + 1), 0, 0));
            points[i].setTargetVelocity(2);
        }
        points[1].setReachStop(true);
        points[3].setReachStop(true);
        points[4].setHasReachEvent(true);
        f.scene.route.setPoints(points);
        List<ServerTask> old = new ArrayList<>();
        for (int segment = 0; segment < 3; segment++) {
            int start = segment == 0 ? 0 : segment == 1 ? 1 : 3;
            int end = segment == 0 ? 1 : segment == 1 ? 3 : 4;
            assertEquals(0, f.lib.StartPlatform(174002));
            assertEquals(start, f.config.toProto().getStartIndex());
            assertEquals(end - start + 1, f.ids().size());
            int events = f.reachEvents().size();
            old.forEach(ServerTask::run);
            f.polls(1000);
            assertEquals(start, f.config.getStartIndex());
            assertTrue(f.config.isStarted());
            assertEquals(events, f.reachEvents().size());
            old = new ArrayList<>(f.tasks());
            // Simulate a retained far-end callback already in the scheduler, not merely unscheduled
            // points.
            var field = EntityGadget.class.getDeclaredField("configRouteRun");
            field.setAccessible(true);
            var run = (ConfigRouteRun) field.get(f.gadget);
            var late = new ConfigRouteRun.Task(f.gadget, run, 4);
            int lateId = f.scene.scheduler.scheduleRepeatingTask(late, 1);
            late.setTaskId(lateId);
            f.config.getScheduledIndexes().add(lateId);
            old.add(f.scene.scheduler.getTask(lateId));
            int origin = f.config.getStartSceneTime();
            int travel = (end - start) * 1000;
            f.scene.now = origin + travel - 1;
            f.polls(1000);
            assertTrue(f.config.isStarted());
            f.scene.now++;
            f.polls(1);
            assertEquals(end, f.config.getStartIndex());
            assertFalse(f.config.isStarted());
            assertFalse(f.config.isActive());
            assertTrue(f.config.getScheduledIndexes().isEmpty());
            assertNull(f.scene.scheduler.getTask(lateId));
            events = f.reachEvents().size();
            f.scene.now += 10000;
            f.polls(1000);
            old.forEach(ServerTask::run);
            assertEquals(end, f.config.getStartIndex());
            assertEquals(events, f.reachEvents().size());
        }
        assertEquals(2, f.reachEvents().size());
        assertEquals(4, f.reachEvents().get(1).param3);
    }

    @Test
    void badResumeIsAtomicAndSinglePointCompletionStopsActiveState() throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(200);
        f.scene.route.getPoints()[1].setReachStop(true);
        assertTrue(f.gadget.startPlatform());
        f.scene.now = 102300;
        f.polls(1);
        assertEquals(1, f.config.getStartIndex());
        f.scene.route.getPoints()[2].setTargetVelocity(-1);
        int packets = f.scene.packets.size(), events = f.reachEvents().size();
        assertEquals(2, f.lib.StartPlatform(174002));
        assertEquals(1, f.config.getStartIndex());
        assertFalse(f.config.isStarted());
        assertFalse(f.config.isActive());
        assertEquals(packets, f.scene.packets.size());
        assertEquals(events, f.reachEvents().size());
        var single = new ConfigRouteDeadlineTest.Fixture(200);
        single.scene.route.setPoints(new RoutePoint[] {single.scene.route.getPoints()[0]});
        assertTrue(single.gadget.startPlatform());
        single.scene.now = 101300;
        single.polls(1);
        assertFalse(single.config.isStarted());
        assertFalse(single.config.isActive());
    }

    @Test
    void finalPointClosesTheRouteWithoutRefreshingTheEntity() throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(200);
        assertTrue(f.gadget.startPlatform());
        int packets = f.scene.packets.size();
        f.scene.now = f.config.getStartSceneTime() + 10000;
        f.polls(10);
        assertEquals(2, f.config.getStartIndex());
        assertFalse(f.config.isStarted());
        assertFalse(f.config.isActive());
        assertTrue(f.config.getScheduledIndexes().isEmpty());
        assertEquals(packets, f.scene.packets.size());
    }

    @Test
    void speedLevelRoutesKeepTheLegacyStart() throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(200);
        for (var point : f.scene.route.getPoints()) point.setTargetVelocity(0);
        assertEquals(0, f.lib.StartPlatform(174002));
        assertTrue(f.config.isStarted());
        assertFalse(f.scene.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void markersDoNotSegmentExistingLiyueRoutes(int rate) throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(rate);
        f.scene.sceneId = 1052;
        f.scene.dungeon =
                ConfigRouteDeadlineTest.empty(emu.grasscutter.game.dungeons.DungeonManager.class);
        var data = new emu.grasscutter.data.excels.dungeon.DungeonData();
        ConfigRouteDeadlineTest.field(data, "id", 1103);
        ConfigRouteDeadlineTest.field(f.scene.dungeon, "dungeonData", data);
        f.gadget.setGroupId(201052008);
        f.gadget.setConfigId(8011);
        f.scene.route.getPoints()[1].setReachStop(true);
        assertTrue(f.gadget.startPlatform());
        assertEquals(3, f.ids().size());
    }
}
