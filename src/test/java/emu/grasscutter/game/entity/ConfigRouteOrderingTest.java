package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.binout.routes.RoutePoint;
import emu.grasscutter.game.world.Position;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Regression for ConcurrentHashMap bucket order, not only monotonic task IDs. */
class ConfigRouteOrderingTest {
    static class TrackingPosition extends Position {
        final List<Integer> visited = new ArrayList<>();

        @Override
        public Position set(Position value) {
            visited.add(Math.round(value.getX() / 2) - 1);
            return super.set(value);
        }
    }

    static void crossBucketBoundary(ConfigRouteDeadlineTest.Fixture f) {
        // Allocate/cancel without growing the map: IDs 14,15,16 occupy buckets 14,15,0.
        for (int i = 0; i < 14; i++) {
            int id = f.scene.scheduler.scheduleTask(() -> {});
            f.scene.scheduler.cancelTask(id);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void clockJumpAcrossAllPointsCannotLetFinalOvertakeEarlierCallbacks(int rate) throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(rate);
        crossBucketBoundary(f);
        var position = new TrackingPosition();
        ConfigRouteDeadlineTest.field(f.gadget, "position", position);
        for (var point : f.scene.route.getPoints()) point.setHasReachEvent(true);
        assertTrue(f.gadget.startPlatform());
        position.visited.clear(); // Exclude the explicit handoff alignment; track callbacks only.
        List<Integer> ids = f.ids();
        assertEquals(List.of(14, 15, 16), ids);
        f.scene.now = f.config.getStartSceneTime() + 10000;
        int previous = 0;
        for (int polls = 0; polls < 4; polls++) {
            f.polls(1);
            assertTrue(f.config.getStartIndex() >= previous);
            previous = f.config.getStartIndex();
        }
        assertEquals(
                List.of(0, 1, 2),
                position.visited,
                "map bucket 0 final callback must not overtake buckets 14/15");
        assertEquals(List.of(0, 1, 2), f.reachEvents().stream().map(e -> e.param3).toList());
        assertEquals(2, f.config.getStartIndex());
        assertFalse(f.config.isStarted());
        assertFalse(f.config.isActive());
        for (int id : ids) assertNull(f.scene.scheduler.getTask(id));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void clockJumpAndPreScheduledFinalCannotOvertakeStopsOrReachEvents(int rate) throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(rate);
        crossBucketBoundary(f);
        var position = new TrackingPosition();
        ConfigRouteDeadlineTest.field(f.gadget, "position", position);
        RoutePoint[] points = new RoutePoint[5];
        for (int i = 0; i < 5; i++) {
            points[i] = new RoutePoint();
            points[i].setPos(new Position(2 * (i + 1), 0, 0));
            points[i].setTargetVelocity(2);
            points[i].setHasReachEvent(true);
        }
        points[1].setReachStop(true);
        points[3].setReachStop(true);
        f.scene.route.setPoints(points);
        for (int stop : new int[] {1, 3, 4}) {
            assertTrue(f.gadget.startPlatform());
            List<Integer> ids = f.ids();
            var field = EntityGadget.class.getDeclaredField("configRouteRun");
            field.setAccessible(true);
            var run = (ConfigRouteRun) field.get(f.gadget);
            var later = new ConfigRouteRun.Task(f.gadget, run, 4);
            int id = f.scene.scheduler.scheduleRepeatingTask(later, 1);
            later.setTaskId(id);
            f.config.getScheduledIndexes().add(id);
            f.scene.now = f.config.getStartSceneTime() + 10000;
            for (int polls = 0; polls < 6; polls++) f.polls(1);
            assertEquals(stop, f.config.getStartIndex());
            assertFalse(f.config.isStarted());
            assertFalse(f.config.isActive());
            for (int original : ids) assertNull(f.scene.scheduler.getTask(original));
            assertNull(f.scene.scheduler.getTask(id));
        }
        assertEquals(List.of(0, 1, 1, 2, 3, 3, 4), position.visited);
        assertEquals(
                List.of(0, 1, 2, 3, 4),
                f.reachEvents().stream().map(e -> e.param3).toList(),
                "every intended reach event precedes final/stop completion");
    }
}
