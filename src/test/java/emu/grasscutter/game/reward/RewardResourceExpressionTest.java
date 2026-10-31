package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RewardResourceExpressionTest {
    @Test
    void templateArgumentsDistinguishLiteralsAndVariables() {
        var literal = RewardResourceExpression.parse("astaps.loot.gem<anemo>", true);
        assertFalse(literal.hasVariables());
        literal.requireDeclared(Set.of());
        assertEquals(List.of("astaps.loot.gem<anemo>"),
                literal.expand(Map.of("anemo", List.of("geo"))));

        var variable = RewardResourceExpression.parse("astaps.loot.gem<$element>", true);
        assertTrue(variable.hasVariables());
        variable.requireDeclared(Set.of("element"));
        assertEquals(List.of("astaps.loot.gem<anemo>"),
                variable.expand(Map.of("element", List.of("anemo"))));
        assertThrows(IllegalArgumentException.class,
                () -> variable.expand(Map.of("element", List.of("anemo", "cryo"))));
    }

    @Test
    void pathReferencesExpandOverListBindings() {
        var variable = RewardResourceExpression.parse(
                "genshin.avatar_ascension.$material", false);
        assertEquals(List.of("genshin.avatar_ascension.hurricane_seed",
                        "genshin.avatar_ascension.marionette_core"),
                variable.expand(Map.of("material",
                        List.of("hurricane_seed", "marionette_core"))));
        assertThrows(IllegalArgumentException.class,
                () -> variable.requireDeclared(Set.of("element")));
        var indexed = RewardResourceExpression.parse(
                "genshin.avatar_ascension.$element[1].sliver", false);
        assertEquals(List.of("genshin.avatar_ascension.cryo.sliver"),
                indexed.expand(Map.of("element", List.of("anemo", "cryo"))));
    }

    @Test
    void spreadAndIndexYieldTheSameCanonicalTemplate() {
        var spread = RewardResourceExpression.parse(
                "astaps.loot.world_gem_two<3, ...$element>", true);
        var indexed = RewardResourceExpression.parse(
                "astaps.loot.world_gem_two<3, $element[0], $element[1]>", true);
        var bindings = Map.of("element", List.of("anemo", "cryo"));
        assertEquals(List.of("astaps.loot.world_gem_two<3,anemo,cryo>"),
                spread.expand(bindings));
        assertEquals(spread.expand(bindings), indexed.expand(bindings));
    }

    @Test
    void rejectsParenthesesAndMalformedTemplateSyntax() {
        for (String source : List.of(
                "astaps.loot.gem(anemo)",
                "astaps.loot.gem<<element>>",
                "genshin.avatar_ascension.<element>",
                "astaps.loot.gem<...$element[0]>",
                "astaps.loot.gem<3,>",
                "astaps.loot.gem<$element[-1]>",
                "astaps.loot.gem<$element[2147483648]>")) {
            assertThrows(IllegalArgumentException.class,
                    () -> RewardResourceExpression.parse(source, true), source);
        }
    }
}
