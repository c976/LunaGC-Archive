package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NativeLootYamlExporterTest {
    private static final Map<Integer, String> TABLES =
            Map.of(100, "mondstadt_combat", 200, "character_experience");

    private static String itemName(int id) {
        return switch (id) {
            case 202 -> "genshin.currency.mora";
            case 104003 -> "genshin.avatar_up.heros_wit";
            case 104002 -> "genshin.avatar_up.adventurers_experience";
            default -> throw new IllegalArgumentException("Unrecognized item");
        };
    }

    private static int itemId(String name) {
        return switch (name) {
            case "genshin.currency.mora" -> 202;
            case "genshin.avatar_up.heros_wit" -> 104003;
            case "genshin.avatar_up.adventurers_experience" -> 104002;
            default -> throw new IllegalArgumentException("Unrecognized qualified item");
        };
    }

    @Test
    void preservesIndependentRootAndExclusiveChildPool() {
        var source = Map.of(
                100, new NativeLootYamlExporter.NativeTable(1, List.of(
                        new NativeLootYamlExporter.NativeEntry(202, "300", 10000),
                        new NativeLootYamlExporter.NativeEntry(200, "1", 7000))),
                200, new NativeLootYamlExporter.NativeTable(0, List.of(
                        new NativeLootYamlExporter.NativeEntry(104003, "1", 1000),
                        new NativeLootYamlExporter.NativeEntry(104002, "2", 1000))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100), TABLES,
                NativeLootYamlExporterTest::itemName);
        assertTrue(yaml.indexOf("character_experience:") < yaml.indexOf("mondstadt_combat:"));
        assertTrue(yaml.contains("reward: genshin.currency.mora"));
        assertTrue(yaml.contains("reward: *character_experience"));
        assertTrue(yaml.contains("weight: 1"));
        assertFalse(yaml.contains("weight: 1000"));
        assertFalse(yaml.contains("count: \"1\""));

        var loot = YamlLootTables.parse(yaml, NativeLootYamlExporterTest::itemId);
        int none = 0, big = 0, small = 0;
        var random = new Random(42);
        for (int n = 0; n < 30_000; n++) {
            var reward = loot.rollIds("astaps.loot.mondstadt_combat", random);
            assertEquals(300, reward.get(202).intValue());
            if (!reward.containsKey(104003) && !reward.containsKey(104002)) none++;
            else if (reward.containsKey(104003)) {
                assertEquals(1, reward.get(104003).intValue());
                big++;
            } else {
                assertEquals(2, reward.get(104002).intValue());
                small++;
            }
        }
        assertEquals(0.30, none / 30_000.0, 0.018);
        assertEquals(0.35, big / 30_000.0, 0.018);
        assertEquals(0.35, small / 30_000.0, 0.018);
    }

    @Test
    void nativeIndependentChanceAndNativeCountAreTwoSeparateRolls() {
        var source = Map.of(100, new NativeLootYamlExporter.NativeTable(1, List.of(
                new NativeLootYamlExporter.NativeEntry(104003, "2;3", 5000))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100), TABLES,
                NativeLootYamlExporterTest::itemName);
        assertTrue(yaml.contains("reward:\n      - {reward: null, weight: 1}\n"
                + "      - {reward: genshin.avatar_up.heros_wit, count: \"2;3\", weight: 1}"));
        var loot = YamlLootTables.parse(yaml, NativeLootYamlExporterTest::itemId);
        int zero = 0, two = 0, three = 0;
        var random = new Random(9);
        for (int n = 0; n < 30_000; n++) {
            int count = loot.rollIds("astaps.loot.mondstadt_combat", random)
                    .getOrDefault(104003, 0);
            switch (count) {
                case 0 -> zero++;
                case 2 -> two++;
                case 3 -> three++;
                default -> fail("Unexpected count: " + count);
            }
        }
        assertEquals(0.5, zero / 30_000.0, 0.02);
        assertEquals(0.25, two / 30_000.0, 0.02);
        assertEquals(0.25, three / 30_000.0, 0.02);
    }

    @Test
    void exporterNormalizesNativeFractionalExpectationsExactly() {
        var source = Map.of(100, new NativeLootYamlExporter.NativeTable(1, List.of(
                new NativeLootYamlExporter.NativeEntry(104003, "2.5556", 10000))));
        String yaml = NativeLootYamlExporter.export(source, List.of(100),
                Map.of(100, "fractional_bonus"), NativeLootYamlExporterTest::itemName);
        assertTrue(yaml.contains("count: \"2:1111, 3:1389\""));
        var loot = YamlLootTables.parse(yaml, NativeLootYamlExporterTest::itemId);
        var rng = new Random(107);
        int threes = 0;
        for (int n = 0; n < 10000; n++) {
            int amount = loot.rollIds("astaps.loot.fractional_bonus", rng).get(104003);
            assertTrue(amount == 2 || amount == 3);
            if (amount == 3) threes++;
        }
        assertEquals(0.5556, threes / 10000.0, 0.02);
    }

    @Test
    void subPercentNativeChanceStillHasExactIntegerWeights() {
        var source = Map.of(100, new NativeLootYamlExporter.NativeTable(1, List.of(
                new NativeLootYamlExporter.NativeEntry(202, "1", 1))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100),
                Map.of(100, "rare"), NativeLootYamlExporterTest::itemName);
        assertTrue(yaml.contains("{reward: null, weight: 9999}"));
        assertTrue(yaml.contains("{reward: genshin.currency.mora, weight: 1}"));
        var loot = YamlLootTables.parse(yaml, NativeLootYamlExporterTest::itemId);
        assertTrue(loot.rollIds("astaps.loot.rare", new Random(4)).isEmpty());
    }

    @Test
    void exporterPreservesNativeArtifactProperties() {
        var source = Map.of(100, new NativeLootYamlExporter.NativeTable(1, List.of(
                new NativeLootYamlExporter.NativeEntry(55311, "1", 10000))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100),
                Map.of(100, "artifact_chest"),
                id -> id == 55311 ? "genshin.artifact.berserker" : null,
                id -> new NativeLootYamlExporter.ArtifactProperties("goblet", 3, 1));
        assertTrue(yaml.contains(
                "type: Artifact, set: berserker, properties: {slot: goblet, rarity: 3, substats: 1}"));
        var artifacts = new ArtifactResolver(List.of(
                new ArtifactResolver.Variant(55311, "berserker", 4, 3, 1)));
        var loot = YamlLootTables.parse(yaml,
                key -> artifacts.resourceIds().getOrDefault(key, -2), artifacts);
        assertEquals(Map.of(55311, 1),
                loot.rollIds("astaps.loot.artifact_chest", new Random(1)));
        assertThrows(IllegalArgumentException.class, () ->
                NativeLootYamlExporter.export(source, List.of(100),
                        Map.of(100, "artifact_chest"),
                        id -> "genshin.artifact.berserker.goblet.3.1",
                        id -> new NativeLootYamlExporter.ArtifactProperties("goblet", 3, 1)));
    }

    @Test
    void exporterRejectsMissingOrFakeNamesAndCyclicTables() {
        var empty = Map.of(10, new NativeLootYamlExporter.NativeTable(1, List.of()));
        String yaml = NativeLootYamlExporter.export(empty, List.of(10),
                Map.of(10, "empty_reward"), NativeLootYamlExporterTest::itemName);
        assertEquals(Map.of(), YamlLootTables.parse(yaml, NativeLootYamlExporterTest::itemId)
                .rollIds("astaps.loot.empty_reward", new Random(0)));

        var unknown = Map.of(10, new NativeLootYamlExporter.NativeTable(1,
                List.of(new NativeLootYamlExporter.NativeEntry(202, "1", 10000))));
        assertThrows(IllegalArgumentException.class, () ->
                NativeLootYamlExporter.export(unknown, List.of(10), Map.of(),
                        NativeLootYamlExporterTest::itemName));
        assertThrows(IllegalArgumentException.class, () ->
                NativeLootYamlExporter.export(unknown, List.of(10),
                        Map.of(10, "chest"), ignored -> "genshin.currency.id_202"));
        var cyclic = Map.of(10, new NativeLootYamlExporter.NativeTable(0,
                List.of(new NativeLootYamlExporter.NativeEntry(10, "1", 1))));
        assertThrows(IllegalArgumentException.class, () ->
                NativeLootYamlExporter.export(cyclic, List.of(10),
                        Map.of(10, "cycle"), NativeLootYamlExporterTest::itemName));
    }
}
