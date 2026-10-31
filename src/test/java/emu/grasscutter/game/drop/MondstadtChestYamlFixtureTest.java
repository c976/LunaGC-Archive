package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MondstadtChestYamlFixtureTest {
    private record Sample(double weapons, double artifacts, double fineOre, double oreMean) {}

    @Test
    void levelOneAndSixPreserveTheirNativeRewardDifferences() throws Exception {
        String yaml;
        try (var stream = getClass().getResourceAsStream("/loot/mondstadt-common-native.yaml")) {
            assertNotNull(stream, "Missing checked-in 7.1 native chest fixture");
            yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        // The historical fixture retains numeric native IDs. Give them qualified
        // fixture names before passing them to the modern YAML parser.
        yaml = yaml.replaceAll("reward: ([0-9]+)", "reward: genshin.material.fixture_$1");
        var loot = YamlLootTables.parse(yaml,
                name -> Integer.parseInt(name.substring(name.lastIndexOf('_') + 1)));
        var lower = sample(loot, "astaps.loot.native_52010000", 13);
        var higher = sample(loot, "astaps.loot.native_52010100", 29);

        // Native roots: weapon roll 40/50%, artifact roll 52/80%,
        // fine ore roll 20/40%, base ore expected 2.1/2.6.
        assertEquals(0.40, lower.weapons(), 0.018);
        assertEquals(0.50, higher.weapons(), 0.018);
        assertEquals(0.52, lower.artifacts(), 0.018);
        assertEquals(0.80, higher.artifacts(), 0.018);
        assertEquals(0.20, lower.fineOre(), 0.018);
        assertEquals(0.40, higher.fineOre(), 0.018);
        assertEquals(2.10, lower.oreMean(), 0.025);
        assertEquals(2.60, higher.oreMean(), 0.025);
    }

    private static Sample sample(YamlLootTables loot, String name, long seed) {
        final int trials = 30_000;
        int weapons = 0, artifacts = 0, fineOre = 0;
        long baseOre = 0;
        var random = new Random(seed);
        for (int i = 0; i < trials; i++) {
            Map<Integer, Integer> reward = loot.rollIds(name, random);
            assertEquals(10, reward.get(102).intValue());
            assertEquals(1, reward.get(305).intValue());
            assertTrue(reward.get(202) >= 240 && reward.get(202) <= 360);
            if (reward.keySet().stream().anyMatch(id -> id >= 11000 && id < 16000)) weapons++;
            if (reward.keySet().stream().anyMatch(id -> id >= 60000 && id < 70000)) artifacts++;
            if (reward.containsKey(104012)) fineOre++;
            baseOre += reward.get(104011);
        }
        return new Sample(weapons / (double) trials, artifacts / (double) trials,
                fineOre / (double) trials, baseOre / (double) trials);
    }
}
