package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public final class WaypointsCommandTest {
    @Test
    @DisplayName("7.1 scene teleport points are recognized as waypoints")
    public void sceneTransPointIsAccepted() {
        assertTrue(WaypointsCommand.isWaypointType("SceneTransPoint"));
    }

    @Test
    @DisplayName("legacy normal teleport points remain recognized")
    public void transPointNormalIsAccepted() {
        assertTrue(WaypointsCommand.isWaypointType("TransPointNormal"));
    }

    @Test
    @DisplayName("generic teleport points remain recognized")
    public void transPointIsAccepted() {
        assertTrue(WaypointsCommand.isWaypointType("TransPoint"));
    }

    @Test
    @DisplayName("virtual teleport points are not simple-unlocked")
    public void virtualTransPointIsRejected() {
        assertFalse(WaypointsCommand.isWaypointType("VirtualTransPoint"));
    }

    @Test
    @DisplayName("unrelated and missing point types are rejected")
    public void unrelatedTypesAreRejected() {
        assertFalse(WaypointsCommand.isWaypointType("DungeonEntry"));
        assertFalse(WaypointsCommand.isWaypointType(null));
    }
    @Test
    void explicitAreaOnlySelectsMatchingWaypointsAndAreas() {
        var waypoints = Map.of(101, 1, 102, 2, 103, 2, 104, 0);
        var selected = WaypointsCommand.selectWaypoints(waypoints, List.of(2));
        assertEquals(Set.of(102, 103), Set.copyOf(selected.points()));
        assertEquals(Set.of(2), selected.areas());
        var missing = WaypointsCommand.selectWaypoints(waypoints, List.of(404));
        assertTrue(missing.points().isEmpty());
        assertTrue(missing.areas().isEmpty());
        var areaZero = WaypointsCommand.selectWaypoints(waypoints, List.of(0));
        assertEquals(List.of(104), areaZero.points());
        assertTrue(areaZero.areas().isEmpty());
    }

    @Test
    void unfilteredUnlockPreservesLegacyFullSceneBehavior() {
        var selected = WaypointsCommand.selectWaypoints(Map.of(101, 1, 102, 2), List.of());
        assertEquals(Set.of(101, 102), Set.copyOf(selected.points()));
        assertTrue(selected.areas().contains(999));
        assertEquals(999, selected.areas().size());
        var empty = WaypointsCommand.selectWaypoints(Map.of(), null);
        assertTrue(empty.points().isEmpty());
        assertTrue(empty.areas().isEmpty());
    }
}
