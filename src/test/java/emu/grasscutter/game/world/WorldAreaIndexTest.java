package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import emu.grasscutter.game.player.SnezhnayaFrigid;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldAreaIndexTest {
    /** Area 55 is an L shape, so a bounding-box test alone would wrongly claim its notch. */
    private static final String FIXTURE =
            """
            {"OBKNAJCIHEA": [
              {"LKBLPFHOAFB": {"BBLLBIOLMHP": 55, "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                {"_x": 0, "_z": 0}, {"_x": 100, "_z": 0}, {"_x": 100, "_z": 40},
                {"_x": 40, "_z": 40}, {"_x": 40, "_z": 100}, {"_x": 0, "_z": 100}]}}},
              {"LKBLPFHOAFB": {"BBLLBIOLMHP": 7, "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                {"_x": 200, "_z": 200}, {"_x": 300, "_z": 200}, {"_x": 300, "_z": 300}]}}}
            ]}
            """;

    private static WorldAreaIndex fixture() {
        return WorldAreaIndex.parse(JsonParser.parseString(FIXTURE));
    }

    @Test
    void findsTheAreaContainingAPoint() {
        var index = fixture();
        assertFalse(index.isEmpty());
        assertEquals(55, index.level1AreaAt(20, 80));
        assertEquals(55, index.level1AreaAt(80, 20));
        assertEquals(7, index.level1AreaAt(290, 210));
    }

    @Test
    void pointsOutsideEveryOutlineResolveToZero() {
        var index = fixture();
        // Inside area 55's bounding box but in the notch of the L.
        assertEquals(0, index.level1AreaAt(80, 80));
        // Inside area 7's bounding box but across its diagonal.
        assertEquals(0, index.level1AreaAt(210, 290));
        assertEquals(0, index.level1AreaAt(-5, -5));
    }

    @Test
    void degenerateOrMissingDataGivesAnEmptyIndex() {
        assertTrue(WorldAreaIndex.parse(JsonParser.parseString("{}")).isEmpty());
        assertTrue(WorldAreaIndex.parse(JsonParser.parseString("[]")).isEmpty());
        // A two-point outline cannot enclose anything and is skipped.
        assertTrue(
                WorldAreaIndex.parse(
                                JsonParser.parseString(
                                        "{\"OBKNAJCIHEA\": [{\"LKBLPFHOAFB\": {\"BBLLBIOLMHP\": 9,"
                                                + " \"JLFEAGHGCLP\": {\"BFLLDPOAJGD\": [{\"_x\": 0, \"_z\": 0},"
                                                + " {\"_x\": 1, \"_z\": 1}]}}}]}"))
                        .isEmpty());
    }

    @Test
    void snezhnayaIsOnlyTheOverworld() {
        var inSnezhnaya = new Position(8500, 300, 7000);
        assertFalse(SnezhnayaFrigid.contains(5, inSnezhnaya));
        assertFalse(SnezhnayaFrigid.contains(3, null));
        // Mondstadt, far from areas 55..59.
        assertFalse(SnezhnayaFrigid.contains(3, new Position(-500, 200, 1000)));
    }

    @Test
    void realOutlinesPutAKresnikMineInSnezhnaya() {
        var file = Path.of("resources/BinOutput/Scene/WorldArea/scene3_worldArea.json");
        assumeTrue(Files.exists(file), "needs the extracted 7.1 resource data");
        // A point well inside area 56, where most of Snezhnaya's 7.0 gadgets stand.
        assertTrue(SnezhnayaFrigid.contains(3, new Position(9300, 300, 7300)));
    }
}
