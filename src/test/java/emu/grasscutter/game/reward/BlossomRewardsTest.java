package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BlossomRewardsTest {
    private static RewardConfiguration rewards() throws IOException {
        var items = new ItemIdRegistry(Map.of(
                101, "Character EXP",
                102, "Adventure EXP",
                105, "Companionship EXP",
                201, "Primogem",
                202, "Mora",
                104001, "Wanderer's Advice",
                104002, "Adventurer's Experience",
                104003, "Hero's Wit"));
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        assertFalse(yaml.contains("revelation_"), "Blossom rewards must not use loot.yaml");
        var loot = YamlLootTables.parse("{}", items::resolve);
        String selectors = RewardConfiguration.readBundled("blossoms.lcss");
        assertEquals(1, selectors.lines().filter(line ->
                line.equals("blossom {")).count());
        assertEquals(1, selectors.lines().filter(line ->
                line.equals("blossom[type=\"wealth\"] {")).count());
        assertEquals(1, selectors.lines().filter(line ->
                line.equals("blossom[type=\"revelation\"] {")).count());
        assertEquals(14, selectors.lines().filter(line ->
                line.stripLeading().startsWith("&[WR=")
                        || line.stripLeading().startsWith("&[6 <= WR <= 9]")).count());
        assertEquals(0, selectors.lines().filter(line ->
                line.stripLeading().startsWith("&[type=")).count());
        var warnings = new java.util.ArrayList<String>();
        assertEquals(15, RewardCssParser.parse(selectors, warnings::add).size());
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertFalse(selectors.contains("astaps.loot."));
        return new RewardConfiguration(loot, CssRewards.parse(
                selectors, items, loot));
    }

    @Test
    void wealthSupportsSingleDoubleAndTripleClaims() throws IOException {
        var config = rewards();
        int[] mora = {12000, 20000, 28000, 36000, 44000, 52000, 60000, 60000, 60000, 60000};
        int[] friendship = {10, 15, 15, 15, 15, 20, 20, 20, 20, 20};
        for (int wl = 0; wl <= 9; wl++) {
            assertEquals(Map.of(102, 100, 105, friendship[wl], 202, mora[wl]),
                    config.rollBlossom("wealth", wl, 1, new Random(wl)));
            assertEquals(Map.of(102, 200, 105, friendship[wl] * 2, 202, mora[wl] * 2),
                    config.rollBlossom("wealth", wl, 2, new Random(wl)));
            assertEquals(Map.of(102, 300, 105, friendship[wl] * 3, 202, mora[wl] * 3),
                    config.rollBlossom("wealth", wl, 3, new Random(wl)));
        }
    }

    @Test
    void revelationUsesIndependentTripleRolls() throws IOException {
        var config = rewards();
        int[][] advice = {{7,8}, {10,12}, {0,0}, {0,0}, {0,0}, {0,0}, {0,0}};
        int[][] adventurer = {{3,4}, {5,6}, {10,11}, {13,14}, {6,7}, {6,7}, {6,7}};
        int[][] hero = {{0,0}, {0,0}, {0,0}, {0,0}, {2,3}, {3,4}, {4,5}};
        for (int wl = 0; wl <= 9; wl++) {
            int tier = Math.min(wl, 6);
            var result = config.rollBlossom("revelation", wl, 1, new Random(wl));
            assertEquals(100, result.get(102));
            assertTrue(result.get(105) > 0);
            assertFalse(result.containsKey(202));
            for (var expected : new Object[][]{{104001, advice[tier]}, {104002, adventurer[tier]}, {104003, hero[tier]}}) {
                int count = result.getOrDefault((int) expected[0], 0);
                int[] interval = (int[]) expected[1];
                assertTrue(count >= interval[0] && count <= interval[1],
                        "WL" + wl + " id=" + expected[0] + " count=" + count);
            }
            var tripled = config.rollBlossom("revelation", wl, 3, new Random(wl));
            assertEquals(300, tripled.get(102));
            for (var expected : new Object[][]{{104001, advice[tier]}, {104002, adventurer[tier]}, {104003, hero[tier]}}) {
                int count = tripled.getOrDefault((int) expected[0], 0);
                int[] interval = (int[]) expected[1];
                assertTrue(count >= interval[0] * 3 && count <= interval[1] * 3);
            }
        }
    }

    @Test
    void worldLevelRangesPreserveFriendshipTierBoundaries() throws IOException {
        var config = rewards();
        for (int wl : new int[]{0, 1, 4, 5, 9}) {
            int expected = wl == 0 ? 10 : wl <= 4 ? 15 : 20;
            assertEquals(expected, config.rollBlossom(
                    "wealth", wl, 1, new Random(1)).get(105));
            assertEquals(expected, config.rollBlossom(
                    "revelation", wl, 1, new Random(1)).get(105));
        }
    }

    @Test
    void missingTypeOrWorldLevelCannotGrantCommonExperienceOnly() throws IOException {
        var config = rewards();
        for (var attrs : java.util.List.of(
                Map.of("type", "wealth", "WR", 10),
                Map.of("type", "revelation", "WR", -1),
                Map.of("type", "other", "WR", 4),
                Map.of("WR", 4),
                Map.of("kind", "wealth", "WR", 4))) {
            assertThrows(RuntimeException.class,
                    () -> config.roll("blossom", attrs, new Random(1)), attrs.toString());
        }
        assertThrows(IllegalArgumentException.class,
                () -> config.rollBlossom("wealth", 10, 1, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> config.rollBlossom("unknown", 5, 1, new Random(1)));
    }
}
