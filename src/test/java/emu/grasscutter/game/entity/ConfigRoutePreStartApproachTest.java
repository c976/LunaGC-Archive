package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.world.Position;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Server publication + timed handoff only; no invented native interpolation/render oracle. */
class ConfigRoutePreStartApproachTest {
    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 333})
    void preservePosePutApproachBeforeAuthoredOriginAndKeepTravelPrefixFree(int rate)
            throws Exception {
        var f = new ConfigRouteAuthoredOriginTest.Fixture(rate);
        f.change();
        Position before = new Position(f.gadget.toProto().getMotionInfo().getPos());
        f.start();
        var run = f.run();
        assertEquals(
                0,
                before.computeDistance(new Position(f.gadget.toProto().getMotionInfo().getPos())));
        assertEquals(1750, run.leadMillis);
        assertEquals(13561, f.config.getStartSceneTime());
        assertEquals(0, run.travelMillis[0]);
        assertEquals(1797, run.travelMillis[3]);
        var ids = new ArrayList<>(f.config.getScheduledIndexes());
        for (int n = 0; n < 1000; n++) f.scene.scheduler.runTasks();
        assertEquals(0, before.computeDistance(f.gadget.getPosition()));
        f.scene.now = 13560;
        for (int n = 0; n < 1000; n++) f.scene.scheduler.runTasks();
        assertNotNull(f.scene.scheduler.getTask(ids.get(0)));
        assertEquals(0, before.computeDistance(f.gadget.getPosition()));
        f.scene.now = 13561;
        f.scene.scheduler.runTasks();
        assertNull(f.scene.scheduler.getTask(ids.get(0)));
        assertEquals(
                0, f.gadget.getPosition().computeDistance(f.scene.route.getPoints()[0].getPos()));
        f.scene.now = 15357;
        f.scene.scheduler.runTasks();
        assertTrue(f.config.isStarted());
        f.scene.now = 15358;
        for (int n = 0; n < 20; n++) f.scene.scheduler.runTasks();
        assertEquals(3, f.config.getStartIndex());
        assertFalse(f.config.isStarted());
        assertFalse(f.config.isActive());
    }

    @Test
    void alignedAndSmallGapStartsRetainMinimumLead() throws Exception {
        for (float gap : new float[] {0, 0.1f}) {
            var f = new ConfigRouteAuthoredOriginTest.Fixture(200);
            f.change();
            f.gadget.getPosition().set(f.scene.route.getPoints()[0].getPos());
            f.gadget.getPosition().setX(f.gadget.getPosition().getX() + gap);
            f.start();
            assertEquals(300, f.run().leadMillis);
            assertEquals(12111, f.config.getStartSceneTime());
        }
    }

    @Test
    void initialLeadWrapAndApproachInvalidityAreAtomic() throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(200);
        f.scene.now = Integer.MAX_VALUE - 200;
        assertTrue(f.gadget.startPlatform());
        int origin = f.config.getStartSceneTime();
        assertEquals((Integer.MAX_VALUE - 200) + 1000, origin);
        var id = f.ids().get(0);
        f.scene.now = origin - 1;
        f.polls(1000);
        assertNotNull(f.scene.scheduler.getTask(id));
        f.scene.now = origin;
        f.polls(1);
        assertNull(f.scene.scheduler.getTask(id));
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE}) {
            var bad = new ConfigRouteDeadlineTest.Fixture(200);
            bad.gadget.getPosition().setX(value);
            assertEquals(2, bad.lib.StartPlatform(174002));
            assertTrue(bad.scene.packets.isEmpty());
            assertTrue(bad.scene.manager.events.isEmpty());
            assertTrue(bad.config.getScheduledIndexes().isEmpty());
            assertFalse(bad.config.isStarted());
        }
    }

    @Test
    void combinedLeadPlusTravelHorizonIsValidatedBeforeMutation() throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(200);
        f.scene.route.getPoints()[0].setTargetVelocity(0.000001f);
        f.scene.route.getPoints()[1].setTargetVelocity(0.00001f);
        f.scene.route.getPoints()[2].setTargetVelocity(0.00001f);
        // Initial lead=2e9ms and authored travel=4e8ms: each finite/int32, sum invalid.
        assertEquals(2, f.lib.StartPlatform(174002));
        assertTrue(f.scene.packets.isEmpty());
        assertTrue(f.scene.manager.events.isEmpty());
        assertEquals(0, f.gadget.getPosition().getX());
        assertFalse(f.config.isStarted());
    }
}
