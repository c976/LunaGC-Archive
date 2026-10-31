package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.common.PointData;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class UnlockAllCommandTest {
    private static final Gson GSON = new Gson();

    private static PointData point(String json) {
        return GSON.fromJson(json, PointData.class);
    }

    @Test
    void identifiesWaypointStatueAndOtherPointTypes() {
        assertEquals(UnlockAllCommand.PointCategory.WAYPOINT,
                UnlockAllCommand.classifyPoint(point("{\"$type\":\"TransPointNormal\"}")));
        assertEquals(UnlockAllCommand.PointCategory.WAYPOINT,
                UnlockAllCommand.classifyPoint(point("{\"$type\":\"SceneTransPoint\"}")));
        assertEquals(UnlockAllCommand.PointCategory.STATUE,
                UnlockAllCommand.classifyPoint(point("{\"maxSpringVolume\":100}")));
        assertEquals(UnlockAllCommand.PointCategory.STATUE,
                UnlockAllCommand.classifyPoint(point("{\"gadgetId\":70130009}")));
        assertEquals(UnlockAllCommand.PointCategory.OTHER,
                UnlockAllCommand.classifyPoint(point("{\"$type\":\"DungeonEntry\"}")));
        assertEquals(UnlockAllCommand.PointCategory.OTHER,
                UnlockAllCommand.classifyPoint(null));
    }

    @Test
    void countsOnlyNewlyUnlockedPointsAndAreas() {
        var result = new UnlockAllCommand.MapUnlockResult(4, 2, 3, 30);
        assertEquals(9, result.totalPoints());
        assertEquals(4, result.waypoints());
        assertEquals(2, result.statues());
        assertEquals(3, result.otherPoints());
        assertEquals(30, result.areaFlags());
    }

    @Test
    void preservesMapAndForceSyntax() {
        var command = new UnlockAllCommand().createCommandLine(null, null);
        assertDoesNotThrow(() -> command.parseArgs("map"));
        assertDoesNotThrow(() -> new UnlockAllCommand().createCommandLine(null, null)
                .parseArgs("map", "--force"));
        assertThrows(CommandLine.ParameterException.class,
                () -> new UnlockAllCommand().createCommandLine(null, null)
                        .parseArgs("map", "--force", "extra"));
    }
}
