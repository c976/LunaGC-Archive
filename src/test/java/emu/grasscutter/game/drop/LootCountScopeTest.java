package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LootCountScopeTest {
    private static final int MORA = 202;
    private static final int WIT = 104003;

    private static final YamlLootTables.ItemResolver ITEMS = name -> switch (name) {
        case "genshin.currency.mora" -> MORA;
        case "genshin.avatar_up.heros_wit" -> WIT;
        default -> -2;
    };

    private static YamlLootTables parseWithArtifacts(String yaml,
            YamlLootTables.ItemResolver itemIds, ArtifactResolver artifacts) {
        var artifactKeys = artifacts.resourceIds();
        return YamlLootTables.parse(yaml, name ->
                name.startsWith("genshin.artifact.")
                        ? artifactKeys.getOrDefault(name, -2) : itemIds.resolve(name), artifacts);
    }

    @Test
    void typeAndPoolShareRelativeWeightSampling() {
        var tables = YamlLootTables.parse("""
                weapon: {type: "genshin.currency.mora:3, genshin.avatar_up.heros_wit:1"}
                same: {type: "genshin.currency.mora:3, genshin.avatar_up.heros_wit:1", count: 2}
                repeated: {type: weapon, count: 2}
                """, ITEMS);
        var random = new Random(165);
        int sameMora = 0, mixed = 0;
        for (int i = 0; i < 12_000; i++) {
            var same = tables.rollIds("same", random);
            assertEquals(2, same.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(same.equals(Map.of(MORA, 2)) || same.equals(Map.of(WIT, 2)));
            if (same.containsKey(MORA)) sameMora++;
            var repeated = tables.rollIds("repeated", random);
            assertEquals(2, repeated.values().stream().mapToInt(Integer::intValue).sum());
            if (repeated.size() == 2) mixed++;
        }
        assertEquals(0.75, sameMora / 12_000.0, 0.03);
        assertEquals(0.375, mixed / 12_000.0, 0.03);
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: "genshin.currency.mora:3, genshin.avatar_up.heros_wit"}
                """, ITEMS));
    }

    @Test
    void artifactSlotUsesTheSameWeightedExpression() {
        var artifacts = new ArtifactResolver(java.util.List.of(
                new ArtifactResolver.Variant(900001, "berserker", 1, 3, 1),
                new ArtifactResolver.Variant(900002, "berserker", 2, 3, 1)));
        var tables = parseWithArtifacts("""
                artifacts:
                  - {type: Artifact, set: berserker, properties: {slot: "flower:3, plume:1", rarity: 3, substats: 1}}
                """, ITEMS, artifacts);
        int flowers = 0;
        var random = new Random(166);
        for (int i = 0; i < 12_000; i++) {
            var reward = tables.rollIds("artifacts", random);
            assertEquals(1, reward.values().stream().mapToInt(Integer::intValue).sum());
            if (reward.containsKey(900001)) flowers++;
        }
        assertEquals(0.75, flowers / 12_000.0, 0.03);
    }

    @Test
    void directEntryFieldsShareTheSameWeightedCompiler() {
        var names = (YamlLootTables.ItemResolver) item -> switch (item) {
            case "genshin.avatar_ascension.anemo.2" -> 2102;
            case "genshin.avatar_ascension.anemo.3" -> 2103;
            case "genshin.avatar_ascension.pyro.2" -> 2202;
            case "genshin.avatar_ascension.pyro.3" -> 2203;
            case "genshin.avatar_ascension.hydro.2" -> 2302;
            case "genshin.avatar_ascension.hydro.3" -> 2303;
            default -> -2;
        };
        var tables = YamlLootTables.parse("""
                ascension: {type: Gem, element: anemo, rarity: "2:3, 3:1"}
                mixed:
                  type: Gem
                  element: "pyro:3, hydro:1"
                  rarity: "2:3, 3:1"
                  count: "2:1, 3:1"
                """, names);
        var random = new Random(77);
        int slivers = 0, pyro = 0, rarity2 = 0, doubles = 0;
        for (int i = 0; i < 20_000; i++) {
            var first = tables.rollIds("ascension", random);
            assertEquals(1, first.values().stream().mapToInt(Integer::intValue).sum());
            if (first.containsKey(2102)) slivers++;
            var second = tables.rollIds("mixed", random);
            int total = second.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total == 2 || total == 3);
            if (total == 2) doubles++;
            pyro += second.entrySet().stream().filter(e -> e.getKey() / 100 == 22)
                    .mapToInt(Map.Entry::getValue).sum();
            rarity2 += second.entrySet().stream().filter(e -> e.getKey() % 10 == 2)
                    .mapToInt(Map.Entry::getValue).sum();
        }
        assertEquals(0.75, slivers / 20_000.0, 0.025);
        assertEquals(0.5, doubles / 20_000.0, 0.025);
        assertEquals(0.75, pyro / 50_000.0, 0.025);
        assertEquals(0.75, rarity2 / 50_000.0, 0.025);
    }

    @Test
    void invalidDirectAttributesFailWithoutNullPointerExceptions() {
        for (String expression : java.util.List.of("null", "true", "0")) {
            assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                    bad: {type: Gem, element: anemo, rarity: %s}
                    """.formatted(expression), name -> 2102));
        }
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: genshin.currency.mora, newField: "1:2, 2:3"}
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: Gem, element: anemo, rarity: 2, properties: {rarity: 3}}
                """, name -> 2102));
    }

    @Test
    void complexWeightedBranchesUseTheSamePoolChooser() {
        var tables = YamlLootTables.parse("""
                grouped:
                  - [{type: genshin.currency.mora, count: 2}, {type: genshin.avatar_up.heros_wit, count: 3}]: 3
                  - {type: genshin.currency.mora, count: 7}: 1
                """, ITEMS);
        var random = new Random(85);
        int grouped = 0;
        for (int i = 0; i < 12_000; i++) {
            var result = tables.rollIds("grouped", random);
            if (result.equals(Map.of(MORA, 2, WIT, 3))) grouped++;
            else assertEquals(Map.of(MORA, 7), result);
        }
        assertEquals(0.75, grouped / 12_000.0, 0.03);
    }

    @Test
    void complexWeightedBranchesCannotMixWithUnweightedSiblings() {
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid:
                  - [{type: genshin.currency.mora}]: 3
                  - {type: genshin.avatar_up.heros_wit}
                """, ITEMS));
    }

    @Test
    void uppercaseTemplateFieldsShareWeightedSemantics() {
        var itemNames = (YamlLootTables.ItemResolver) name -> switch (name) {
            case "A" -> MORA;
            case "B" -> WIT;
            case "genshin.currency.mora" -> MORA;
            default -> -2;
        };
        var tables = YamlLootTables.parse("""
                boss<MORA,FIRST,SECOND,N>:
                  - {type: genshin.currency.mora, count: "$MORA"}
                  - {type: "$FIRST:3, $SECOND:1", count: "$N"}
                boss_a: {type: "boss<1000,A,B,2>"}
                """, itemNames);
        var rng = new Random(33);
        int bothMora = 0;
        int bothWit = 0;
        for (int i = 0; i < 12_000; i++) {
            var result = tables.rollIds("boss_a", rng);
            int mora = result.getOrDefault(MORA, 0);
            int wit = result.getOrDefault(WIT, 0);
            assertEquals(1002, mora + wit);
            if (mora == 1002) bothMora++;
            else {
                assertEquals(1000, mora);
                assertEquals(2, wit);
                bothWit++;
            }
        }
        assertEquals(0.75, bothMora / 12_000.0, 0.03);
        assertEquals(0.25, bothWit / 12_000.0, 0.03);
    }

    @Test
    void weightedTypeAndWeightedRarityEvaluateIndependently() {
        var ids = (YamlLootTables.ItemResolver) name -> switch (name) {
            case "genshin.avatar_ascension.anemo.2" -> 3102;
            case "genshin.avatar_ascension.anemo.3" -> 3103;
            case "genshin.avatar_ascension.pyro.2" -> 3202;
            case "genshin.avatar_ascension.pyro.3" -> 3203;
            default -> -2;
        };
        var tables = YamlLootTables.parse("""
                gems:
                  type: Gem
                  element: "anemo:3, pyro:1"
                  rarity: "2:3, 3:1"
                """, ids);
        var counts = new int[4];
        var rng = new Random(148);
        for (int i = 0; i < 20_000; i++) {
            var reward = tables.rollIds("gems", rng);
            assertEquals(1, reward.values().stream().mapToInt(Integer::intValue).sum());
            int item = reward.keySet().iterator().next();
            counts[switch (item) {
                case 3102 -> 0;
                case 3103 -> 1;
                case 3202 -> 2;
                case 3203 -> 3;
                default -> throw new AssertionError("Unexpected reward: " + item);
            }]++;
        }
        assertEquals(0.5625, counts[0] / 20_000.0, 0.025);
        assertEquals(0.1875, counts[1] / 20_000.0, 0.025);
        assertEquals(0.1875, counts[2] / 20_000.0, 0.025);
        assertEquals(0.0625, counts[3] / 20_000.0, 0.025);
    }

    @Test
    void weightedArtifactTypesAcceptTheSameWeightedSlotField() {
        var artifacts = new ArtifactResolver(java.util.List.of(
                new ArtifactResolver.Variant(4101, "berserker", 1, 3, 1),
                new ArtifactResolver.Variant(4102, "berserker", 2, 3, 1),
                new ArtifactResolver.Variant(4201, "instructor", 1, 3, 1),
                new ArtifactResolver.Variant(4202, "instructor", 2, 3, 1)));
        var tables = parseWithArtifacts("""
                artifacts:
                  type: Artifact
                  set: "berserker:3, instructor:1"
                  slot: "flower:3, plume:1"
                  rarity: 3
                  substats: 1
                """, ITEMS, artifacts);
        var counts = new java.util.HashMap<Integer, Integer>();
        var rng = new Random(167);
        for (int i = 0; i < 20_000; i++) {
            var reward = tables.rollIds("artifacts", rng);
            assertEquals(1, reward.values().stream().mapToInt(Integer::intValue).sum());
            counts.merge(reward.keySet().iterator().next(), 1, Integer::sum);
        }
        assertEquals(0.5625, counts.getOrDefault(4101, 0) / 20_000.0, 0.025);
        assertEquals(0.1875, counts.getOrDefault(4102, 0) / 20_000.0, 0.025);
        assertEquals(0.1875, counts.getOrDefault(4201, 0) / 20_000.0, 0.025);
        assertEquals(0.0625, counts.getOrDefault(4202, 0) / 20_000.0, 0.025);
    }

    @Test
    void namedPoolsMayCallThemselves() {
        var tables = YamlLootTables.parse("""
                echo:
                  - {type: genshin.currency.mora}
                  - {type: echo, count: "0:9, 1:1"}
                """, ITEMS);
        var random = new Random(918);
        int sum = 0;
        for (int i = 0; i < 10_000; i++) {
            var result = tables.rollIds("echo", random);
            int count = result.getOrDefault(MORA, 0);
            assertTrue(count >= 1);
            sum += count;
        }
        assertEquals(1.0 / 0.9, sum / 10_000.0, 0.035);
    }

    @Test
    void mutuallyRecursivePoolsMayCallEachOther() {
        var tables = YamlLootTables.parse("""
                left:
                  - {type: genshin.currency.mora}
                  - {type: right, count: "0:9, 1:1"}
                right:
                  - {type: genshin.avatar_up.heros_wit}
                  - {type: left, count: "0:9, 1:1"}
                """, ITEMS);
        var random = new Random(919);
        for (int i = 0; i < 1_000; i++) {
            var result = tables.rollIds("left", random);
            assertTrue(result.getOrDefault(MORA, 0) >= 1);
            assertTrue(result.getOrDefault(MORA, 0) >= result.getOrDefault(WIT, 0));
        }
    }

    @Test
    void recursiveYamlAliasesAreAccepted() {
        var tables = YamlLootTables.parse("""
                echo: &self
                  - {type: genshin.currency.mora}
                  - {type: *self, count: "0:9, 1:1"}
                """, ITEMS);
        var random = new Random(920);
        for (int i = 0; i < 1_000; i++) {
            assertTrue(tables.rollIds("echo", random).getOrDefault(MORA, 0) >= 1);
        }
    }

    @Test
    void recursiveParameterizedPoolsAreAccepted() {
        var tables = YamlLootTables.parse("""
                echo<N>:
                  - {type: genshin.currency.mora, count: "$N"}
                  - {type: "echo<1>", count: "0:9, 1:1"}
                root: {type: "echo<2>"}
                """, ITEMS);
        var random = new Random(921);
        for (int i = 0; i < 1_000; i++) {
            assertTrue(tables.rollIds("root", random).getOrDefault(MORA, 0) >= 2);
        }
    }

    @Test
    void countHasNoArtificialHundredThousandCeiling() {
        var tables = YamlLootTables.parse("""
                plenty: {type: genshin.currency.mora, count: 100001}
                """, ITEMS);
        assertEquals(Map.of(MORA, 100001), tables.rollIds("plenty", new Random(922)));
        var random = new Random(923);
        assertEquals(Integer.MAX_VALUE,
                LootCount.parse("2147483647").sample(random));
        int value = LootCount.parse("2147483646;2147483647").sample(random);
        assertTrue(value == 2147483646 || value == 2147483647);
    }

    @Test
    void countsUseExplicitRelativeWeights() {
        var distribution = LootCount.parse("2:586, 3:414");
        var random = new Random(71);
        int threes = 0;
        for (int i = 0; i < 20_000; i++) {
            int count = distribution.sample(random);
            assertTrue(count == 2 || count == 3);
            if (count == 3) threes++;
        }
        assertEquals(0.414, threes / 20_000.0, 0.02);
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("2, 3:101"));
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("2:586, 3"));
    }

    @Test
    void nestedPoolCountRerollsButLeafCountCopiesTheSelectedItem() {
        var tables = YamlLootTables.parse("""
                weapon: &weapon
                  - {reward: genshin.currency.mora, weight: 60}
                  - {reward: genshin.avatar_up.heros_wit, weight: 40}
                same:
                  - reward:
                      - {reward: genshin.currency.mora, count: 2, weight: 60}
                      - {reward: genshin.avatar_up.heros_wit, count: 2, weight: 40}
                independent:
                  - {reward: *weapon, count: 2}
                ranged_independent:
                  - {reward: *weapon, count: "2:58.6, 3:41.4"}
                """, ITEMS);

        int both = 0, sameMora = 0, independentMora = 0, independentWit = 0;
        var random = new Random(5);
        for (int i = 0; i < 20_000; i++) {
            Map<Integer, Integer> fixedChoice = tables.rollIds("same", random);
            assertEquals(2, fixedChoice.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(fixedChoice.equals(Map.of(MORA, 2))
                    || fixedChoice.equals(Map.of(WIT, 2)));
            if (fixedChoice.containsKey(MORA)) sameMora++;

            Map<Integer, Integer> repeated = tables.rollIds("independent", random);
            assertEquals(2, repeated.values().stream().mapToInt(Integer::intValue).sum());
            if (repeated.size() == 2) {
                assertEquals(Map.of(MORA, 1, WIT, 1), repeated);
                both++;
            } else if (repeated.containsKey(MORA)) independentMora++;
            else if (repeated.containsKey(WIT)) independentWit++;
            else fail("Unexpected repeated pool result: " + repeated);
        }
        assertEquals(0.60, sameMora / 20_000.0, 0.025);
        assertEquals(0.48, both / 20_000.0, 0.025);
        assertEquals(0.36, independentMora / 20_000.0, 0.025);
        assertEquals(0.16, independentWit / 20_000.0, 0.025);
    }

    @Test
    void rarityAndElementDistributionsUseRelativeWeights() {
        var itemIds = (YamlLootTables.ItemResolver) name -> switch (name) {
            case "genshin.avatar_ascension.anemo.2" -> 1202;
            case "genshin.avatar_ascension.anemo.3" -> 1203;
            case "genshin.avatar_ascension.pyro.2" -> 1204;
            case "genshin.avatar_ascension.hydro.2" -> 1205;
            default -> -2;
        };
        var tables = YamlLootTables.parse("""
                rarity:
                  - {type: Gem, element: anemo, properties: {rarity: "2:3, 3:1"}}
                element:
                  - {type: Gem, properties: {element: "pyro:3, hydro:1", rarity: 2}}
                """, itemIds);
        var random = new Random(74);
        int rarityFirst = 0, elementFirst = 0;
        for (int i = 0; i < 10_000; i++) {
            if (tables.rollIds("rarity", random).containsKey(1202)) rarityFirst++;
            if (tables.rollIds("element", random).containsKey(1204)) elementFirst++;
        }
        assertEquals(0.75, rarityFirst / 10_000.0, 0.025);
        assertEquals(0.75, elementFirst / 10_000.0, 0.025);
    }

    @Test
    void explicitDistributionControlsIndependentPoolExecutions() {
        var tables = YamlLootTables.parse("""
                weapon: &weapon
                  - {reward: genshin.currency.mora, weight: 60}
                  - {reward: genshin.avatar_up.heros_wit, weight: 40}
                ranged_independent:
                  - {reward: *weapon, count: "2:58.6, 3:41.4"}
                """, ITEMS);
        var random = new Random(41);
        int threeExecutions = 0;
        for (int i = 0; i < 20_000; i++) {
            Map<Integer, Integer> output = tables.rollIds("ranged_independent", random);
            int total = output.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total == 2 || total == 3);
            if (total == 3) threeExecutions++;
        }
        assertEquals(0.414, threeExecutions / 20_000.0, 0.025);
    }

    @Test
    void variadicArtifactSetPackSharesTheSameWeightedTypeCompiler() {
        var variants = new ArtifactResolver(java.util.List.of(
                new ArtifactResolver.Variant(9101, "berserker", 1, 3, 1),
                new ArtifactResolver.Variant(9102, "instructor", 1, 3, 1),
                new ArtifactResolver.Variant(9103, "adventurer", 1, 3, 1),
                new ArtifactResolver.Variant(9104, "traveling_doctor", 1, 3, 1)));
        var loot = parseWithArtifacts("""
                chest<RARITY,SUBSTATS,...SETS>:
                  - type: Artifact
                    set: "...$SETS:1"
                    slot: flower
                    rarity: $RARITY
                    substats: $SUBSTATS
                two: {type: "chest<3,1,berserker,instructor>"}
                four: {type: "chest<3,1,berserker,instructor,adventurer,traveling_doctor>"}
                repeated: {type: "chest<3,1,berserker,berserker>"}
                """, ITEMS, variants);
        assertEquals(Map.of(9101, 1), loot.rollIds("repeated", new Random(60)));

        int[] counts = new int[4];
        var rng = new Random(61);
        for (int i = 0; i < 12_000; i++) {
            int item = loot.rollIds("four", rng).keySet().iterator().next();
            counts[item - 9101]++;
            assertTrue(loot.rollIds("two", rng).keySet().stream()
                    .allMatch(id -> id == 9101 || id == 9102));
        }
        for (int count : counts) assertEquals(0.25, count / 12_000.0, 0.03);
    }

    @Test
    void parameterPacksCanBeForwardedAndExpandedInOtherFields() {
        var loot = YamlLootTables.parse("""
                counts<...COUNTS>:
                  - {type: genshin.currency.mora, count: "...$COUNTS:1"}
                forward<...VALUES>:
                  - {type: "counts<...$VALUES>"}
                values: {type: "forward<2,3>"}
                empty<...VALUES>:
                  - {type: genshin.currency.mora}
                zero: {type: "empty<>"}
                """, ITEMS);
        assertEquals(Map.of(MORA, 1), loot.rollIds("zero", new Random(62)));
        var rng = new Random(63);
        int threes = 0;
        for (int i = 0; i < 12_000; i++) {
            int count = loot.rollIds("values", rng).get(MORA);
            assertTrue(count == 2 || count == 3);
            if (count == 3) threes++;
        }
        assertEquals(0.5, threes / 12_000.0, 0.03);
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid<...PACK,FIXED>:
                  - {type: genshin.currency.mora}
                """, ITEMS));
    }

    @Test
    void expectedPoolNodesIncludeRootAndApplyInclusiveHundredBoundary() {
        assertDoesNotThrow(() -> YamlLootTables.parse("""
                safe:
                  - {type: safe, count: "0:1, 1:1"}
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                too_many:
                  - {type: too_many, count: "0:99, 1:901"}
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                critical:
                  - {type: critical, count: "0:1, 2:1"}
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                always:
                  - {type: always}
                """, ITEMS));
    }

    @Test
    void expectedNodeCheckUsesPoolExecutionCountsButNotLeafItemQuantity() {
        assertDoesNotThrow(() -> YamlLootTables.parse("""
                leaf: {type: genshin.currency.mora, count: 2147483647}
                ninety_nine_children: {type: leaf, count: 99}
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                leaf: {type: genshin.currency.mora, count: 2147483647}
                hundred_children: {type: leaf, count: 100}
                """, ITEMS));
        assertDoesNotThrow(() -> YamlLootTables.parse("""
                tiny:
                  - {type: tiny, count: "0:999999999, 100:1"}
                """, ITEMS));
    }

    @Test
    void recursiveSafetyAccountsForMutualCallsAndWeightedBranches() {
        assertDoesNotThrow(() -> YamlLootTables.parse("""
                a: {type: b, count: 2}
                b: {type: a, count: "0:9, 1:1"}
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                a: {type: b, count: 2}
                b: {type: a, count: "0:3, 1:2"}
                """, ITEMS));
        assertDoesNotThrow(() -> YamlLootTables.parse("""
                safe:
                  - {type: safe}: 9
                  - {type: genshin.currency.mora}: 91
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                critical:
                  - {type: critical}: 1
                  - {type: genshin.currency.mora}: 0
                """, ITEMS));
    }

    @Test
    void recursiveSafetyChecksYamlAliasesAndParameterizedInstances() {
        assertDoesNotThrow(() -> YamlLootTables.parse("""
                root: &loop
                  - {type: *loop, count: "0:1, 1:1"}
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                root: &loop
                  - {type: *loop, count: "0:99, 1:901"}
                """, ITEMS));
        var safe = YamlLootTables.parse("""
                recursive<N>:
                  - {type: "recursive<1>", count: "0:2, 1:8"}
                root: {type: "recursive<1>"}
                """, ITEMS);
        assertTrue(safe.contains("astaps.loot.recursive<1>"));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                recursive<N>:
                  - {type: "recursive<1>", count: "0:99, 1:901"}
                root: {type: "recursive<1>"}
                """, ITEMS));
    }

    @Test
    void artifactParameterPacksInferAvailableSlotsWithoutChangingSetWeights() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        String[] sets = {"berserker", "instructor", "the_exile",
                "prayers_for_wisdom", "gladiators_finale"};
        for (int rarity : new int[]{1, 2, 3, 4}) {
            for (int set = 0; set < sets.length; set++) {
                for (int slot = 1; slot <= 5; slot++) {
                    if (set == 3 && slot != 5) continue;
                    int firstSubstat = Math.max(0, rarity - 2);
                    int lastSubstat = rarity == 1 ? 0 : rarity - 1;
                    for (int substats = firstSubstat; substats <= lastSubstat; substats++) {
                        variants.add(new ArtifactResolver.Variant(
                                rarity * 100_000 + (set + 1) * 1_000 + slot * 10 + substats,
                                sets[set], slot, rarity, substats));
                    }
                }
            }
        }
        var loot = parseWithArtifacts("""
                world_artifact_1star<...SETS>:
                  - type: Artifact
                    set: "...$SETS:1"
                    rarity: 1
                    substats: 0
                world_artifact_2star<...SETS>:
                  - type: Artifact
                    set: "...$SETS:1"
                    rarity: 2
                    substats: "0:66, 1:34"
                world_artifact_3star<...SETS>:
                  - type: Artifact
                    set: "...$SETS:1"
                    rarity: 3
                    substats: "1:66, 2:34"
                world_artifact_4star<...SETS>:
                  - type: Artifact
                    set: "...$SETS:1"
                    rarity: 4
                    substats: "2:66, 3:34"
                one: {type: "world_artifact_1star<berserker,instructor>"}
                two: {type: "world_artifact_2star<berserker,instructor>"}
                three: {type: "world_artifact_3star<berserker,instructor,the_exile,prayers_for_wisdom>"}
                four: {type: "world_artifact_4star<berserker,instructor,the_exile,prayers_for_wisdom,gladiators_finale>"}
                """, ITEMS, new ArtifactResolver(variants));
        var random = new Random(77);
        for (int rarity : new int[]{1, 2, 3, 4}) {
            int kinds = rarity <= 2 ? 2 : rarity == 3 ? 4 : 5;
            int[] selectedSets = new int[kinds];
            int[][] selectedSlots = new int[kinds][5];
            for (int i = 0; i < 20_000; i++) {
                String table = switch (rarity) {
                    case 1 -> "one";
                    case 2 -> "two";
                    case 3 -> "three";
                    default -> "four";
                };
                int id = loot.rollIds(table, random).keySet().iterator().next();
                assertEquals(rarity, id / 100_000);
                int set = (id % 100_000) / 1_000 - 1;
                int slot = (id % 1_000) / 10;
                int substats = id % 10;
                assertTrue(set >= 0 && set < kinds);
                assertTrue(slot >= 1 && slot <= 5);
                assertTrue(substats == Math.max(0, rarity - 2)
                        || substats == (rarity == 1 ? 0 : rarity - 1));
                if (set == 3) {
                    assertEquals(5, slot, "Prayers sets only contain circlets");
                }
                selectedSets[set]++;
                selectedSlots[set][slot - 1]++;
            }
            for (int set = 0; set < kinds; set++) {
                assertEquals(1.0 / kinds, selectedSets[set] / 20_000.0, 0.02);
                if (set == 3) continue;
                for (int slotCount : selectedSlots[set]) {
                    assertEquals(0.2, slotCount / (double) selectedSets[set], 0.03);
                }
            }
        }
    }

    @Test
    void genericGemPacksPreserveTypeProbabilitiesAndCopyCounts() {
        var ids = java.util.Map.ofEntries(
                Map.entry("genshin.avatar_ascension.anemo.sliver", 8101),
                Map.entry("genshin.avatar_ascension.anemo.fragment", 8102),
                Map.entry("genshin.avatar_ascension.anemo.chunk", 8103),
                Map.entry("genshin.avatar_ascension.anemo.gemstone", 8104),
                Map.entry("genshin.avatar_ascension.cryo.sliver", 8201),
                Map.entry("genshin.avatar_ascension.cryo.fragment", 8202),
                Map.entry("genshin.avatar_ascension.cryo.chunk", 8203),
                Map.entry("genshin.avatar_ascension.cryo.gemstone", 8204));
        var loot = YamlLootTables.parse("""
                one<...ELEMENTS>:
                  - {type: "genshin.avatar_ascension....$ELEMENTS.sliver:1", count: 3}
                two<COUNT,...ELEMENTS>:
                  - type: >-
                      genshin.avatar_ascension....$ELEMENTS.sliver:79,
                      genshin.avatar_ascension....$ELEMENTS.fragment:21
                    count: $COUNT
                three<COUNT,...ELEMENTS>:
                  - type: >-
                      genshin.avatar_ascension....$ELEMENTS.sliver:75,
                      genshin.avatar_ascension....$ELEMENTS.fragment:20,
                      genshin.avatar_ascension....$ELEMENTS.chunk:5
                    count: $COUNT
                four<COUNT,...ELEMENTS>:
                  - type: >-
                      genshin.avatar_ascension....$ELEMENTS.sliver:150,
                      genshin.avatar_ascension....$ELEMENTS.fragment:40,
                      genshin.avatar_ascension....$ELEMENTS.chunk:10,
                      genshin.avatar_ascension....$ELEMENTS.gemstone:1
                    count: $COUNT
                fragment<COUNT,...ELEMENTS>:
                  - {type: "genshin.avatar_ascension....$ELEMENTS.fragment:1", count: $COUNT}
                early: {type: "one<anemo,cryo>"}
                medium: {type: "two<3,anemo,cryo>"}
                higher: {type: "three<9,anemo>"}
                late: {type: "four<9,anemo,cryo>"}
                fragments: {type: "fragment<3,anemo,cryo>"}
                """, name -> ids.getOrDefault(name, -2));
        var random = new Random(121);
        var mediumCounts = new java.util.HashMap<Integer, Integer>();
        var lateCounts = new java.util.HashMap<Integer, Integer>();
        for (int i = 0; i < 20_000; i++) {
            var early = loot.rollIds("early", random);
            assertEquals(3, early.values().iterator().next());
            assertTrue(early.containsKey(8101) || early.containsKey(8201));
            var medium = loot.rollIds("medium", random);
            assertEquals(3, medium.values().iterator().next());
            mediumCounts.merge(medium.keySet().iterator().next(), 1, Integer::sum);
            var higher = loot.rollIds("higher", random);
            assertEquals(9, higher.values().iterator().next());
            assertTrue(higher.keySet().stream().allMatch(id -> id >= 8101 && id <= 8103));
            var late = loot.rollIds("late", random);
            assertEquals(9, late.values().iterator().next());
            lateCounts.merge(late.keySet().iterator().next(), 1, Integer::sum);
            var fragments = loot.rollIds("fragments", random);
            assertEquals(3, fragments.values().iterator().next());
            assertTrue(fragments.containsKey(8102) || fragments.containsKey(8202));
        }
        for (int id : new int[]{8101, 8201}) {
            assertEquals(0.395, mediumCounts.getOrDefault(id, 0) / 20_000.0, 0.018);
            assertEquals(150 / 402.0, lateCounts.getOrDefault(id, 0) / 20_000.0, 0.018);
        }
        for (int id : new int[]{8102, 8202}) {
            assertEquals(0.105, mediumCounts.getOrDefault(id, 0) / 20_000.0, 0.018);
            assertEquals(40 / 402.0, lateCounts.getOrDefault(id, 0) / 20_000.0, 0.018);
        }
        for (int id : new int[]{8103, 8203}) {
            assertEquals(10 / 402.0, lateCounts.getOrDefault(id, 0) / 20_000.0, 0.012);
        }
        for (int id : new int[]{8104, 8204}) {
            assertEquals(1 / 402.0, lateCounts.getOrDefault(id, 0) / 20_000.0, 0.005);
        }
    }

    @Test
    void canonicalItemTypesCompileWeightedIdentityAttributes() {
        var nativeArtifacts = new ArtifactResolver(java.util.List.of(
                new ArtifactResolver.Variant(9101, "berserker", 1, 3, 1),
                new ArtifactResolver.Variant(9102, "berserker", 2, 3, 1),
                new ArtifactResolver.Variant(9205, "prayers_for_wisdom", 5, 3, 1)));
        var nativeItems = (YamlLootTables.ItemResolver) name -> switch (name) {
            case "genshin.avatar_ascension.anemo.2" -> 1102;
            case "genshin.avatar_ascension.anemo.3" -> 1103;
            case "genshin.avatar_ascension.pyro.2" -> 1202;
            case "genshin.avatar_ascension.pyro.3" -> 1203;
            case "genshin.talent_up.freedom.2" -> 2102;
            case "genshin.talent_up.freedom.3" -> 2103;
            case "genshin.talent_up.resistance.2" -> 2202;
            case "genshin.talent_up.resistance.3" -> 2203;
            default -> -2;
        };
        var loot = parseWithArtifacts("""
                artifact:
                  type: Artifact
                  set: "berserker:3, prayers_for_wisdom:1"
                  rarity: 3
                  substats: 1
                artifact_copies:
                  type: Artifact
                  set: "berserker:3, prayers_for_wisdom:1"
                  rarity: 3
                  substats: 1
                  count: 2
                gem:
                  type: Gem
                  element: "anemo:3, pyro:1"
                  rarity: "2:3, 3:1"
                  count: 3
                talent:
                  type: TalentBook
                  series: "freedom:3, resistance:1"
                  rarity: "2:3, 3:1"
                  count: 2
                """, nativeItems, nativeArtifacts);
        int[] artifacts = new int[3], gems = new int[4], talents = new int[4];
        var random = new Random(2026);
        for (int i = 0; i < 20_000; i++) {
            int artifact = loot.rollIds("artifact", random).keySet().iterator().next();
            artifacts[switch (artifact) { case 9101 -> 0; case 9102 -> 1; case 9205 -> 2;
                default -> throw new AssertionError(artifact); }]++;
            var repeated = loot.rollIds("artifact_copies", random);
            assertEquals(2, repeated.values().stream().mapToInt(Integer::intValue).sum());
            // All copies share one weighted set choice. Native slots reroll.
            assertFalse(repeated.containsKey(9205)
                    && (repeated.containsKey(9101) || repeated.containsKey(9102)));
            var gem = loot.rollIds("gem", random);
            // Gem's weighted elements are rerolled for each copy, as specified
            // by the current domain type. Counts must conserve all three copies.
            assertEquals(3, gem.values().stream().mapToInt(Integer::intValue).sum());
            gem.forEach((nativeId, count) -> {
                int index = switch (nativeId) {
                    case 1102 -> 0; case 1103 -> 1; case 1202 -> 2; case 1203 -> 3;
                    default -> throw new AssertionError(nativeId);
                };
                gems[index] += count;
            });
            var talent = loot.rollIds("talent", random);
            assertEquals(1, talent.size());
            assertEquals(2, talent.values().iterator().next());
            talents[switch (talent.keySet().iterator().next()) {
                case 2102 -> 0; case 2103 -> 1; case 2202 -> 2; case 2203 -> 3;
                default -> throw new AssertionError(talent); }]++;
        }
        assertEquals(0.375, artifacts[0] / 20_000.0, 0.025);
        assertEquals(0.375, artifacts[1] / 20_000.0, 0.025);
        assertEquals(0.25, artifacts[2] / 20_000.0, 0.025);
        for (int[] results : new int[][]{gems, talents}) {
            int total = java.util.Arrays.stream(results).sum();
            assertEquals(0.5625, results[0] / (double) total, 0.025);
            assertEquals(0.1875, results[1] / (double) total, 0.025);
            assertEquals(0.1875, results[2] / (double) total, 0.025);
            assertEquals(0.0625, results[3] / (double) total, 0.025);
        }
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: Artifact, rarity: 3}
                """, nativeItems, nativeArtifacts));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: TalentBook, series: unknown, rarity: 2}
                """, nativeItems, nativeArtifacts));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: Gem, element: anemo, rarity: 1}
                """, nativeItems, nativeArtifacts));
    }

    @Test
    void fiveStarArtifactTemplatesPreserveNormalAndSpecialNativeDistributions() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        for (int set = 1; set <= 2; set++) {
            String name = set == 1 ? "gladiators_finale" : "wanderers_troupe";
            for (int slot = 1; slot <= 5; slot++) {
                for (int substats : new int[]{3, 4}) {
                    variants.add(new ArtifactResolver.Variant(
                            set * 100000 + slot * 10 + substats, name, slot, 5, substats));
                }
            }
        }
        for (int substats : new int[]{0, 2, 4}) {
            variants.add(new ArtifactResolver.Variant(
                    300020 + substats, "instructor", 2, 5, substats));
        }
        for (int substats : new int[]{0, 1, 3}) {
            variants.add(new ArtifactResolver.Variant(
                    300040 + substats, "instructor", 4, 5, substats));
        }
        var tables = parseWithArtifacts("""
                standard:
                  - type: Artifact
                    set: "gladiators_finale:1, wanderers_troupe:1"
                    rarity: 5
                    substats: "3:66, 4:34"
                plume_goblet<PLUME_SUBSTATS,GOBLET_SUBSTATS>:
                  - type: Artifact
                    set: instructor
                    slot: plume
                    rarity: 5
                    substats: $PLUME_SUBSTATS
                    weight: 17
                  - type: Artifact
                    set: instructor
                    slot: goblet
                    rarity: 5
                    substats: $GOBLET_SUBSTATS
                    weight: 33
                goblet<SUBSTATS>:
                  - type: Artifact
                    set: instructor
                    slot: goblet
                    rarity: 5
                    substats: $SUBSTATS
                normal: {type: standard}
                zero: {type: "plume_goblet<0,0>"}
                special: {type: "plume_goblet<4,3>"}
                fixed_goblet: {type: "goblet<1>"}
                empty_goblet: {type: "goblet<0>"}
                """, ITEMS, new ArtifactResolver(variants));
        var random = new Random(538);
        int[] selectedSets = new int[2];
        int[][] slots = new int[2][5];
        int zeroPlumes = 0, specialPlumes = 0;
        for (int i = 0; i < 20_000; i++) {
            int ordinary = tables.rollIds("normal", random).keySet().iterator().next();
            int set = ordinary / 100000 - 1;
            assertTrue(set == 0 || set == 1);
            int slot = (ordinary % 100000) / 10;
            int substats = ordinary % 10;
            assertTrue(slot >= 1 && slot <= 5);
            assertTrue(substats == 3 || substats == 4);
            selectedSets[set]++;
            slots[set][slot - 1]++;
            int zero = tables.rollIds("zero", random).keySet().iterator().next();
            assertTrue(zero == 300020 || zero == 300040);
            if (zero == 300020) zeroPlumes++;
            int special = tables.rollIds("special", random).keySet().iterator().next();
            assertTrue(special == 300024 || special == 300043);
            if (special == 300024) specialPlumes++;
            assertEquals(Map.of(300041, 1), tables.rollIds("fixed_goblet", random));
            assertEquals(Map.of(300040, 1), tables.rollIds("empty_goblet", random));
        }
        for (int set = 0; set < 2; set++) {
            assertEquals(0.5, selectedSets[set] / 20_000.0, 0.025);
            for (int slot : slots[set]) {
                assertEquals(0.2, slot / (double) selectedSets[set], 0.03);
            }
        }
        assertEquals(0.34, zeroPlumes / 20_000.0, 0.025);
        assertEquals(0.34, specialPlumes / 20_000.0, 0.025);
    }
}
