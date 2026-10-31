package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import emu.grasscutter.game.drop.ArtifactResolver;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BundledChestRewardsTest {
    private static String resource(String path) throws Exception {
        try (var stream = BundledChestRewardsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ItemIdRegistry items() {
        var names = new java.util.HashMap<Integer, String>(ItemIdRegistry.chestDropNames());
        names.put(101, "Character EXP");
        names.put(102, "Adventure EXP");
        names.put(105, "Companionship EXP");
        names.put(201, "Primogem");
        names.put(202, "Mora");
        names.put(104001, "Wanderer's Advice");
        names.put(104002, "Adventurer's Experience");
        names.put(104003, "Hero's Wit");
        names.put(104011, "Enhancement Ore");
        names.put(104012, "Fine Enhancement Ore");
        names.put(104013, "Mystic Enhancement Ore");
        names.put(303, "Dendro Sigil");
        names.put(304, "Electro Sigil");
        names.put(305, "Anemo Sigil");
        names.put(307, "Geo Sigil");
        return new ItemIdRegistry(names);
    }

    static ArtifactResolver artifacts() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        String[] sets = {"berserker", "instructor", "the_exile",
                "adventurer", "lucky_dog", "traveling_doctor"};
        int[] prefixes = {55, 57, 59, 60, 61, 63};
        for (int set = 0; set < sets.length; set++) {
            for (int slot = 1; slot <= 5; slot++) {
                int firstRank = set < 3 ? 3 : 1;
                int lastRank = set < 3 ? 4 : 3;
                for (int rank = firstRank; rank <= lastRank; rank++) {
                    int firstSubstat = rank <= 2 ? 0 : rank - 2;
                    int lastSubstat = rank == 1 ? 0 : firstSubstat + 1;
                    for (int substats = firstSubstat; substats <= lastSubstat; substats++) {
                        int nativeSlot = switch (slot) {
                            case 1 -> 4; // flower
                            case 2 -> 2; // plume
                            case 3 -> 5; // sands
                            case 4 -> 1; // goblet
                            case 5 -> 3; // circlet
                            default -> throw new IllegalArgumentException("Invalid slot");
                        };
                        variants.add(new ArtifactResolver.Variant(
                                prefixes[set] * 1000 + rank * 100 + nativeSlot * 10 + substats,
                                sets[set], slot, rank, substats));
                    }
                }
            }
        }
        return new ArtifactResolver(variants);
    }

    static String chestYaml() throws Exception {
        String source = resource("/rewards/loot.yaml");
        // The contents list names this section too. Use its final heading.
        return source.substring(0, source.lastIndexOf("\n# 2. World Boss artifacts\n"));
    }

    private static String chestCss() throws Exception {
        return RewardConfiguration.readBundled("chests.lcss");
    }

    private static YamlLootTables loot() throws Exception {
        var artifacts = artifacts();
        var artifactKeys = artifacts.resourceIds();
        var registry = items();
        return YamlLootTables.parse(chestYaml(),
                key -> key.startsWith("genshin.artifact.")
                        ? artifactKeys.getOrDefault(key, -2) : registry.resolve(key), artifacts);
    }

    @Test
    void twelveChestTiersContainNoFixedCurrencies() throws Exception {
        String cssSource = chestCss();
        var loot = loot();
        for (String pool : new String[]{
                "weapons_1_star", "weapons_2_star", "weapons_3_star",
                "weapons_1_to_2_low", "weapons_1_to_2_high",
                "weapons_2_to_3_low", "weapons_2_to_3_high",
                "meat_ingredients"}) {
            assertTrue(loot.contains("astaps.loot." + pool), pool);
        }
        for (int i = 1; i <= 20; i++) {
            String pool = "astaps.loot.chest_artifact_mix_%02d".formatted(i);
            assertTrue(loot.contains(pool), pool);
        }
        assertEquals(28, loot.names().stream().filter(name -> !name.contains("<")).count(),
                "8 common pools and 20 native artifact mixtures");
        assertTrue(cssSource.replace("\\", "").contains("astaps.loot.chest_artifact_mix_01:"));
        assertFalse(chestYaml().contains("type: genshin.currency.mora"));
        assertFalse(chestYaml().contains("type: genshin.currency.primogem"));
    }

    @Test
    void allTwentyFourGradeAndLevelCombinationsSelectTwelveTiers() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        String[] grades = {"common", "exquisite", "precious", "luxurious"};
        int[] levels = {1, 6, 11, 16, 21, 26};
        for (String grade : grades) {
            for (int level : levels) {
                var selected = css.resolveIds("chest", Map.of(
                        "type", "normal", "grade", grade, "city", 1,
                        "level", level));
                assertEquals(30, selected.items().get(102).intValue());
                assertTrue(java.util.stream.Stream.concat(
                        selected.loot().keySet().stream(), selected.rangedLoot().keySet().stream())
                        .anyMatch(name -> name.startsWith("astaps.loot.weapons_")),
                        grade + " / " + level + " has no weapon candidate");
                assertTrue(java.util.stream.Stream.concat(
                        selected.loot().keySet().stream(), selected.rangedLoot().keySet().stream())
                        .anyMatch(name -> name.startsWith("astaps.loot.chest_artifact_mix_")),
                        grade + " / " + level + " has no native artifact candidate");
                assertTrue(selected.rangedItems().containsKey(202),
                        grade + " / " + level + " has no native Mora range");
            }
        }
    }

    @Test
    void regionalPrimogemsAndThirtyAdventureExpRemainCssOwned() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] base = {2, 5, 10, 10}, low = {0, 2, 5, 10};
        for (int k = 0; k < kinds.length; k++) {
            assertEquals(base[k], css.resolveIds("chest", Map.of("type", "normal", "grade", kinds[k]))
                    .items().get(201).intValue());
            assertEquals(low[k], css.resolveIds("chest",
                    Map.of("type", "normal", "grade", kinds[k], "city", 1)).items().get(201).intValue());
            for (int area : new int[]{10, 77, 8, 19, 40, 41, 47, 300}) {
                assertEquals(base[k], css.resolveIds("chest", Map.of(
                        "type", "normal", "grade", kinds[k], "city", 1, "area", area))
                        .items().get(201).intValue());
            }
            var selected = css.resolveIds("chest", Map.of(
                    "type", "normal", "grade", kinds[k], "city", 2, "level", 26));
            for (int seed = 0; seed < 32; seed++) {
                var rolled = RewardRoller.rollIds(selected, loot, new Random(seed),
                        new RewardContext(3, 0, "liyue"));
                assertEquals(30, rolled.get(102).intValue());
                assertEquals(low[k], rolled.getOrDefault(201, 0).intValue());
                assertTrue(rolled.get(202) >= 240 && rolled.get(202) <= 2160);
                assertTrue(rolled.get(307) >= 1 && rolled.get(307) <= 6);
            }
        }
    }

    @Test
    void missingChestLevelCannotSilentlyFallBackToGradeRewards() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        for (String kind : new String[]{"common", "exquisite", "precious", "luxurious"}) {
            for (Integer level : new Integer[]{null, 0, -1}) {
                var props = new java.util.HashMap<String, Object>();
                props.put("type", "normal");
                props.put("grade", kind);
                if (level != null) props.put("level", level);
                var selection = css.resolveIds("chest", props);
                assertTrue(selection.loot().isEmpty(), kind + " level=" + level);
                assertFalse(selection.items().containsKey(202), "Mora must have no fallback");
                assertFalse(selection.rangedItems().containsKey(202), "Mora range must have no fallback");
                assertFalse(selection.items().containsKey(ItemIdRegistry.REGIONAL_SIGIL),
                        "Sigil must have no fallback");
                assertEquals(30, selection.items().get(102).intValue());
                assertThrows(IllegalStateException.class,
                        () -> RewardConfiguration.requireChestLoot("chest", selection, props));
            }
            var valid = Map.of("type", "normal", "grade", kind, "level", 26);
            assertDoesNotThrow(() -> RewardConfiguration.requireChestLoot(
                    "chest", css.resolveIds("chest", valid), valid));
        }
        var unrecognized = Map.of("type", "normal", "grade", "unknown", "level", 26);
        assertThrows(IllegalStateException.class, () ->
                RewardConfiguration.requireChestLoot(
                        "chest", css.resolveIds("chest", unrecognized), unrecognized));
    }

    @Test
    void specialChestTypesDoNotInheritNormalChestRewards() throws Exception {
        var css = CssRewards.parse(chestCss(), items(), loot());
        for (String type : new String[]{"mora", "remarkable"}) {
            var attrs = Map.of("type", type, "grade", "common", "level", 26);
            var selection = css.resolveIds("chest", attrs);
            assertTrue(selection.items().isEmpty(), type);
            assertTrue(selection.loot().isEmpty(), type);
            assertTrue(selection.rangedItems().isEmpty(), type);
            assertThrows(IllegalStateException.class, () ->
                    RewardConfiguration.requireChestLoot("chest", selection, attrs));
        }
        var missingType = Map.of("grade", "common", "level", 26);
        assertTrue(css.resolveIds("chest", missingType).loot().isEmpty());
    }

    @Test
    void allTiersHaveWeaponAndEnhancementOreCandidates() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        for (String grade : new String[]{"common", "exquisite", "precious", "luxurious"}) {
            for (int level : new int[]{1, 6, 11, 16, 21, 26}) {
                var selection = css.resolveIds("chest", Map.of(
                        "type", "normal", "city", 1, "grade", grade,
                        "level", level));
                assertTrue(java.util.stream.Stream.concat(selection.loot().keySet().stream(),
                        selection.rangedLoot().keySet().stream())
                        .anyMatch(name -> name.startsWith("astaps.loot.weapons_")));
                assertTrue(java.util.stream.IntStream.of(104011, 104012, 104013)
                        .anyMatch(id -> selection.items().containsKey(id)
                                || selection.rangedItems().containsKey(id)),
                        grade + " / " + level + " has no enhancement ore");
            }
        }
    }

    @Test
    void mergedBattleAndPuzzlePoolsUseNativePuzzleOreDistributions() throws Exception {
        // Preserve the inclusive native puzzle-ore count distributions, now in CSS.
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        String[] grades = {"common", "common", "common", "common", "common", "common",
                "exquisite", "exquisite", "precious", "precious", "luxurious", "luxurious"};
        int[] levels = {1, 6, 11, 16, 21, 26, 21, 26, 21, 26, 21, 26};
        double[][] expected = {
                {2.1, 0.2, 0}, {2.6, 0.4, 0}, {3.1, 0.6, 0},
                {4.1, 0.6, 0}, {5.1, 0.6, 0}, {0, 3.1, 0},
                {0, 4.1, 0}, {0, 2.1, 1}, {0, 3.1, 1},
                {0, 0, 3}, {0, 0, 3.1}, {0, 0, 3.2}
        };
        int[] ids = {104011, 104012, 104013};
        var random = new Random(721);
        int trials = 1500;
        for (int i = 0; i < grades.length; i++) {
            var selected = css.resolveIds("chest", Map.of("type", "normal", "city", 1,
                    "grade", grades[i], "level", levels[i]));
            double[] total = new double[ids.length];
            for (int n = 0; n < trials; n++) {
                var rolled = RewardRoller.rollIds(selected, loot, random,
                        new RewardContext(3, 0, "mondstadt"));
                for (int k = 0; k < ids.length; k++) total[k] += rolled.getOrDefault(ids[k], 0);
            }
            for (int k = 0; k < ids.length; k++) {
                assertEquals(expected[i][k], total[k] / trials, 0.10,
                        grades[i] + " / " + levels[i] + ", ore " + ids[k]);
            }
        }
    }

    @Test
    void nativeRegionalRecipesAreDefinedByCssInsteadOfLoot() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        int[] recipes = {109202, 109207, 109406, 109407};
        int[][] cases = {
                // grade, level, city, northern chicken, pizza, mora meat, stir fried filet
                {0, 21, 1, 1, 0, 0, 0},
                {0, 21, 2, 0, 0, 1, 1},
                {1, 21, 1, 0, 1, 0, 0},
                {1, 21, 2, 0, 0, 0, 0},
                {0, 26, 1, 0, 0, 0, 0},
                {0, 21, 3, 0, 0, 0, 0},
                {1, 20, 1, 0, 0, 0, 0}
        };
        for (int[] test : cases) {
            String kind = test[0] == 0 ? "exquisite" : "precious";
            var selected = css.resolveIds("chest", Map.of(
                    "type", "normal", "grade", kind,
                    "level", test[1],
                    "city", test[2]));
            assertEquals(test[3], selected.items().getOrDefault(recipes[0], 0).intValue());
            assertEquals(test[4], selected.items().getOrDefault(recipes[1], 0).intValue());
            assertEquals(test[5], selected.items().getOrDefault(recipes[2], 0).intValue());
            assertEquals(test[6], selected.items().getOrDefault(recipes[3], 0).intValue());
            assertTrue(java.util.stream.Stream.concat(
                    selected.loot().keySet().stream(),
                    selected.rangedLoot().keySet().stream())
                    .anyMatch(name -> name.startsWith("astaps.loot.weapons_")),
                    "Chest must retain its weapon pool");
        }
        String yaml = chestYaml();
        for (String name : new String[]{"recipe_mora_meat",
                "recipe_stir_fried_filet", "recipe_mushroom_pizza",
                "recipe_northern_smoked_chicken"}) {
            assertFalse(yaml.contains("type: genshin.material." + name));
        }
    }

    @Test
    void nativeArtifactVariantsRemainResolvableWithVariadicTemplates() throws Exception {
        String source = chestYaml();
        assertTrue(source.contains("chest_artifact<RARITY, SUBSTATS, ...SETS>:"));
        assertTrue(source.contains("type: Artifact"));
        assertTrue(source.contains("set: \"...$SETS:1\""));
        assertEquals(20, java.util.regex.Pattern.compile("(?m)^chest_artifact_mix_[0-9]+:")
                .matcher(source).results().count());
        var chosen = artifacts().compileType(
                Map.of("set", "berserker", "slot", "goblet", "rarity", 3, "substats", 1));
        assertEquals(55311, chosen.select(new Random(1)));
    }
}
