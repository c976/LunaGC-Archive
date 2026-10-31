package emu.grasscutter.game.icecreature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Rows copied from the 7.1 Ice Creature tables, obfuscated field names and all. */
class IceCreatureConfigTest {
    private static IceCreatureConfig fixture() {
        var config = new IceCreatureConfig();
        config.parseTypes(JsonParser.parseString(
                "[{\"id\": 2, \"EIJNPJAALDL\": 20, \"gadgetIdList\": [73094010, 73190019]},"
                        + " {\"id\": 20, \"EIJNPJAALDL\": 50, \"gadgetIdList\": [73174002, 73174007]}]"));
        config.parseSkins(JsonParser.parseString("[{\"AFLDPGPHHJN\": 21, \"gadgetId\": 73174021}]"));
        config.parseAreas(JsonParser.parseString(
                "[{\"AFLDPGPHHJN\": 2, \"id\": 1, \"maxNum\": 7, \"HEENPDGJJFI\": 55}]"));
        config.parseMisc(JsonParser.parseString("[{\"sceneIdList\": [3, 51327, 20382]}]"));
        config.parseConstValues(JsonParser.parseString(
                "[{\"value\": [\"400\", \"\"], \"name\": \"CONST_VALUE_SNEZHNAYA_ICE_CREATURE_MATERIAL_LIMIT\"}]"));
        return config;
    }

    @Test
    void gadgetsAndSkinsResolveToTheirType() {
        var config = fixture();
        assertEquals(2, config.typeOf(73094010));
        assertEquals(2, config.typeOf(73190019));
        assertEquals(21, config.typeOf(73174021));
        assertEquals(0, config.typeOf(70000000));
        assertEquals(50, config.costOf(20));
    }

    @Test
    void areaLimitsAndScenes() {
        var config = fixture();
        assertEquals(7, config.maxInArea(55, 2));
        assertEquals(0, config.maxInArea(55, 20));
        assertTrue(config.isBuildArea(55));
        assertFalse(config.isBuildArea(56));
        assertTrue(config.isBuildScene(51327));
        assertFalse(config.isBuildScene(5));
        assertEquals(400f, config.getMaterialLimit());
    }

    @Test
    void missingTablesFallBackToTheOverworldAndDefaultLimit() {
        var config = new IceCreatureConfig();
        config.parseTypes(null);
        assertTrue(config.isBuildScene(3));
        assertEquals(IceCreatureConfig.DEFAULT_MATERIAL_LIMIT, config.getMaterialLimit());
    }

    @Test
    void standingCreaturesHoldTheirCost() {
        var a = new IceCreatureRecord();
        a.setCost(150);
        var b = new IceCreatureRecord();
        b.setCost(200);
        assertEquals(400f, IceCreatureSystem.materialLeft(400f, java.util.List.of()));
        assertEquals(50f, IceCreatureSystem.materialLeft(400f, java.util.List.of(a, b)));
        b.setCost(300);
        assertEquals(0f, IceCreatureSystem.materialLeft(400f, java.util.List.of(a, b)));
    }

    @Test
    void guidsAreNeverReusedAfterRemoval() {
        long first = IceCreatureSystem.nextGuid(7, java.util.List.of(), 1_000);
        var standing = new IceCreatureRecord();
        standing.setGuid(first);
        long second = IceCreatureSystem.nextGuid(7, java.util.List.of(standing), 1_000);
        assertTrue(second > first, "built in the same second");
        // Every creature removed: a later build still moves forward.
        long third = IceCreatureSystem.nextGuid(7, java.util.List.of(), 1_001);
        assertTrue(third > first);
        assertEquals(7L, third >>> 32);
    }
}
