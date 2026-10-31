package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LootProbabilityTest {
    private static final YamlLootTables.ItemResolver ITEMS = name -> name.equals("coin") ? 202 : -2;

    private static YamlLootTables parse(String yaml) { return YamlLootTables.parse(yaml, ITEMS); }

    @Test void geometricDepthBoundaryAndAlmostSureTermination() {
        // Both terminate almost surely with mean <=100; only 0.8 meets the depth tail.
        assertDoesNotThrow(() -> parse("a: {type: a, count: '0:2, 1:8'}"));
        var rejected = assertThrows(IllegalArgumentException.class,
                () -> parse("a: {type: a, count: '0:19, 1:81'}"));
        assertTrue(rejected.getMessage().contains("P(H > 64)"));
        assertThrows(IllegalArgumentException.class, () -> parse("a: {type: a}"));
        assertThrows(IllegalArgumentException.class, () -> parse("a: {type: a, count: '0:1, 2:1'}"));
        assertThrows(IllegalArgumentException.class, () -> parse("a: {type: a, count: '0:1, 2:2'}"));
    }

    @Test void finiteWideBurstIsRejectedDespiteSmallMeanAndDepth() {
        var failure = assertThrows(IllegalArgumentException.class, () -> parse("""
                leaf: {type: coin}
                burst: {type: leaf, count: "0:999, 8000:1"}
                """));
        assertTrue(failure.getMessage().contains("P(N > 100)"));
    }

    @Test void inclusiveNodeBudgetAndTailProbabilityBoundary() {
        assertDoesNotThrow(() -> parse("leaf: {type: coin}\nroot: {type: leaf, count: 99}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("leaf: {type: coin}\nroot: {type: leaf, count: 100}"));
        assertDoesNotThrow(() -> parse("leaf: {type: coin}\nroot: {type: leaf, count: '0:999999, 100:1'}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("leaf: {type: coin}\nroot: {type: leaf, count: '0:999998, 100:2'}"));
    }

    @Test void deterministicChainChecksDepthIndependently() {
        StringBuilder yaml = new StringBuilder("p0: {type: coin}\n");
        for (int i = 1; i < 64; i++) yaml.append("p").append(i).append(": {type: p").append(i - 1).append("}\n");
        assertDoesNotThrow(() -> parse(yaml.toString()));
        yaml.append("p64: {type: p63}\n");
        var failure = assertThrows(IllegalArgumentException.class, () -> parse(yaml.toString()));
        assertTrue(failure.getMessage().contains("P(H > 64)"));
    }

    @Test void jointTypeChoiceIsMadeOnceBeforeIndependentRepetitions() {
        var node = new LootRecursionSafety.Graph.Node("child");
        var oneNode = LootProbability.Poly.one(100).shift();
        var law = LootProbability.choice(List.of(LootProbability.empty(),
                LootProbability.repeat(node, 100)), List.of(BigDecimal.ONE, BigDecimal.ONE));
        var result = law.evaluate(ignored -> oneNode, 100).shift();
        assertEquals(0, new BigDecimal("0.5").compareTo(result.tail()));
        // Choosing a pool once for a batch must not turn into 100 independent type choices.
        assertThrows(IllegalArgumentException.class, () -> parse("""
                leaf: {type: coin}
                root: {type: "coin:999, leaf:1", count: 8000}
                """));
    }

    @Test void uniformAndFractionalCountsKeepNativeDistributions() {
        var node = new LootRecursionSafety.Graph.Node("child");
        var p = LootProbability.Poly.one(100).shift();
        var fraction = LootProbability.repeat(node, "2:7, 3:3").evaluate(ignored -> p, 100);
        assertEquals(0, new BigDecimal("0.7").compareTo(fraction.coefficients[2]));
        assertEquals(0, new BigDecimal("0.3").compareTo(fraction.coefficients[3]));
        var uniform = LootProbability.repeat(node, "0;2147483647").evaluate(ignored -> p, 100);
        assertEquals(101, uniform.coefficients.length);
        assertTrue(uniform.tail().compareTo(new BigDecimal("0.999999")) > 0);
        assertDoesNotThrow(() -> parse("leaf: {type: coin}\nroot: {type: leaf, count: '0;99'}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("leaf: {type: coin}\nroot: {type: leaf, count: '0;100'}"));
    }

    @Test void zeroCountLawsNeverReadAnUnreachableChild() {
        var child = new LootRecursionSafety.Graph.Node("unreachable");
        for (String count : List.of("0", "0;0", "0:1")) {
            var result = LootProbability.repeat(child, count).evaluate(ignored -> {
                fail("Zero executions must not evaluate a child: " + count);
                return null;
            }, 100);
            assertEquals(0, result.tail().signum());
            assertEquals(0, BigDecimal.ONE.compareTo(result.coefficients[0]));
        }
    }

    @Test void zeroCallsAndItemQuantitiesDoNotCreatePoolNodes() {
        var tables = parse("a: {type: a, count: 0}\ncoin: {reward: coin, count: 2147483647}");
        assertEquals(Map.of(), tables.rollIds("a", new Random(1)));
    }

    @Test void failedTemplateValidationCannotBeBypassedByRetry() {
        var tables = parse("leaf: {type: coin}\nwide<N>:\n  - {type: leaf, count: $N}");
        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalArgumentException.class, () -> tables.resolveName("wide<101>"));
            assertFalse(tables.contains("astaps.loot.wide<101>"));
        }
    }

    @Test void acceptedRareLongDrawUsesExplicitStackWithoutTruncatingRewards() {
        var tables = parse("""
                a:
                  - {type: coin}
                  - {type: a, count: "0:1, 1:1"}
                """);
        var longDraw = new Random(1) {
            int draws;
            @Override public double nextDouble() { return draws++ < 20000 ? 0.75 : 0.25; }
        };
        assertEquals(Map.of(202, 20001), tables.rollIds("a", longDraw));
    }
}
