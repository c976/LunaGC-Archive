package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.ArtifactResolver;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Native regional chest-artifact regression tests without external resource packs. */
final class ChestArtifactRewardsTest {
    private record Fixture(CssRewards css, YamlLootTables loot, Map<Integer, Integer> ranks) {}

    private static Fixture fixture() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        int y0 = yaml.indexOf("chest_artifact<RARITY, SUBSTATS, ...SETS>:");
        int y1 = yaml.indexOf("# 2. World Boss artifacts", y0);
        assertTrue(y0 >= 0 && y1 > y0);
        String css = RewardConfiguration.readBundled("chests.lcss");
        int c0 = css.indexOf("/* 宝箱圣遗物");
        assertTrue(c0 >= 0);

        var variants = new ArrayList<ArtifactResolver.Variant>();
        var ranks = new HashMap<Integer, Integer>();
        int id = 800000;
        for (String set : new String[] {"adventurer", "lucky_dog", "traveling_doctor"}) {
            for (int rarity = 1; rarity <= 3; rarity++) {
                int low = rarity <= 2 ? 0 : 1;
                int high = rarity == 1 ? 0 : low + 1;
                for (int slot = 1; slot <= 5; slot++) {
                    for (int substats = low; substats <= high; substats++) {
                        variants.add(new ArtifactResolver.Variant(++id, set, slot, rarity, substats));
                        ranks.put(id, rarity);
                    }
                }
            }
        }
        for (String set : new String[] {"berserker", "instructor", "the_exile"}) {
            for (int rarity = 3; rarity <= 4; rarity++) {
                for (int slot = 1; slot <= 5; slot++) {
                    for (int substats = rarity - 2; substats <= rarity - 1; substats++) {
                        variants.add(new ArtifactResolver.Variant(++id, set, slot, rarity, substats));
                        ranks.put(id, rarity);
                    }
                }
            }
        }
        var artifacts = new ArtifactResolver(variants);
        var artifactKeys = artifacts.resourceIds();
        var loot = YamlLootTables.parse(yaml.substring(y0, y1),
                key -> artifactKeys.getOrDefault(key, -2), artifacts);
        var rules = CssRewards.parse(css.substring(c0),
                new ItemIdRegistry(Map.of(202, "Mora")), loot);
        return new Fixture(rules, loot, Map.copyOf(ranks));
    }

    private static Map<String, Object> attrs(int city, String grade, int level) {
        return Map.of("type", "normal", "city", city,
                "grade", grade, "level", level);
    }

    @Test
    void allNativeCityGradeLevelRulesSelectOneArtifactProfile() throws Exception {
        var f = fixture();
        // The 26 source candidate pools are parameterized; only 20 weighted mixtures are named tables.
        assertEquals(20, f.loot.names().stream().filter(name -> !name.contains("<")).count());
        for (int city = 1; city <= 4; city++) {
            for (String grade : new String[] {"common", "exquisite", "precious", "luxurious"}) {
                for (int level = 1; level <= 120; level++) {
                    var selected = f.css.resolveIds("chest", attrs(city, grade, level));
                    assertEquals(1, selected.loot().size() + selected.rangedLoot().size(),
                            city + "/" + grade + "/" + level);
                }
            }
        }
        var absent = f.css.resolveIds("chest", attrs(5, "common", 1));
        assertTrue(absent.loot().isEmpty());
        assertTrue(absent.rangedLoot().isEmpty());
    }

    @Test
    void earlyMondstadtChestPreservesTriggerAndRarityProbabilities() throws Exception {
        var f = fixture();
        var selected = f.css.resolveIds("chest", attrs(1, "common", 1));
        var rng = new Random(71);
        int trials = 20000, oneStar = 0, twoStar = 0, total = 0;
        for (int i = 0; i < trials; i++) {
            for (var entry : RewardRoller.rollIds(selected, f.loot, rng).entrySet()) {
                total += entry.getValue();
                int rarity = f.ranks.get(entry.getKey());
                if (rarity == 1) oneStar += entry.getValue();
                if (rarity == 2) twoStar += entry.getValue();
            }
        }
        assertEquals(.52, (double) total / trials, .025);
        assertEquals(.468, (double) oneStar / trials, .025);
        assertEquals(.052, (double) twoStar / trials, .015);
    }

    @Test
    void sumeruLuxuriousChestRerollsThreeToFourArtifacts() throws Exception {
        var f = fixture();
        var selected = f.css.resolveIds("chest", attrs(4, "luxurious", 5));
        var rng = new Random(72);
        int trials = 10000, total = 0, threeStar = 0, fourStar = 0;
        for (int i = 0; i < trials; i++) {
            for (var entry : RewardRoller.rollIds(selected, f.loot, rng).entrySet()) {
                total += entry.getValue();
                if (f.ranks.get(entry.getKey()) == 3) threeStar += entry.getValue();
                else fourStar += entry.getValue();
            }
        }
        assertEquals(3.5, (double) total / trials, .04);
        assertEquals(.8, (double) threeStar / total, .035);
        assertEquals(.2, (double) fourStar / total, .035);
    }
}
