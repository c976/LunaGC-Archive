package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RewardCssParserTest {
    private static boolean matches(String selector, Map<String, ?> attributes) {
        var rules = RewardCssParser.parse(
                selector + " { genshin.currency.mora: 1; }");
        assertEquals(1, rules.size());
        assertEquals(1, rules.getFirst().selectors().size());
        return rules.getFirst().selectors().getFirst().matches("chest", attributes);
    }

    @Test
    void missingAndPresentButDifferentAreDistinct() {
        assertTrue(matches("chest[!grade]", Map.of()));
        assertFalse(matches("chest[!grade]", Map.of("grade", "")));
        assertTrue(matches("chest[grade]", Map.of("grade", "")));
        assertFalse(matches("chest[grade != \"common\"]", Map.of()));
        assertFalse(matches("chest[grade != \"common\"]", Map.of("grade", "common")));
        assertTrue(matches("chest[grade != \"common\"]", Map.of("grade", "exquisite")));
    }

    @Test
    void numericConditionsHaveExactInclusiveBounds() {
        assertTrue(matches("chest[70 <= level <= 79]", Map.of("level", 70)));
        assertTrue(matches("chest[70 <= level <= 79]", Map.of("level", 79)));
        assertFalse(matches("chest[70 <= level <= 79]", Map.of("level", 80)));
        assertFalse(matches("chest[70 < level < 79]", Map.of("level", 70)));
        assertTrue(matches("chest[level >= 90]", Map.of("level", 90)));
        assertThrows(IllegalArgumentException.class, () -> matches("chest[power = 1.50]", Map.of("power", 1)));
        assertThrows(IllegalArgumentException.class, () -> RewardCssAttributes.validate(Map.of("level", "bad")));
        assertThrows(IllegalArgumentException.class, () -> RewardCssAttributes.validate(Map.of("level", "90")));
        assertFalse(matches("chest[level >= 90]", Map.of()));
    }

    @Test
    void membershipSupportsStringsAndNumericValues() {
        assertTrue(matches("chest[city in (1, 2, 3)]", Map.of("city", 2)));
        assertFalse(matches("chest[city in (1, 2, 3)]", Map.of("city", 4)));
        assertTrue(matches("chest[grade in (\"common\", \"precious\")]",
                Map.of("grade", "precious")));
        assertFalse(matches("chest[grade in (\"common\", \"precious\")]",
                Map.of("grade", "luxurious")));
        assertTrue(matches("chest[tag=\"8,10\"]", Map.of("tag", "8,10")));
    }

    @Test
    void selectorsAndDeclarationsAreParsedWithoutLibraryRewriting() {
        var rules = RewardCssParser.parse("""
                /* prefix */
                chest[!grade], chest[grade in ("common", "exquisite")] {
                    --element: "[anemo, cryo]";
                    genshin.currency.sigil.$element: "0:50, 1:50";
                    astaps.loot.world_gem_two<3, ...$element>: 1;
                }
                """);
        assertEquals(1, rules.size());
        assertEquals(2, rules.getFirst().selectors().size());
        assertEquals(3, rules.getFirst().declarations().size());
        assertEquals("genshin.currency.sigil.$element",
                rules.getFirst().declarations().get(1).name());
        assertEquals("0:50, 1:50",
                rules.getFirst().declarations().get(1).value());
        assertEquals("astaps.loot.world_gem_two<3, ...$element>",
                rules.getFirst().declarations().get(2).name());
    }

    @Test
    void nestedSelectorsExpandParentGroupsAndMultipleLevels() {
        var rules = RewardCssParser.parse("""
                boss[type="world"][tag="anemo_hypostasis"],
                boss[type="world"][tag="cryo_hypostasis"] {
                    --element: anemo;
                    &[1 <= level <= 4], &[5 <= level <= 9] {
                        genshin.currency.mora: 1;
                        &[city=1] {
                            genshin.currency.primogem: 2;
                        }
                    }
                    genshin.currency.mora: 3;
                }
                """);
        assertEquals(4, rules.size());
        assertEquals(2, rules.get(0).selectors().size());
        assertEquals(4, rules.get(1).selectors().size());
        assertEquals(4, rules.get(2).selectors().size());
        assertEquals(2, rules.get(3).selectors().size());
        assertEquals("--element", rules.get(0).declarations().getFirst().name());
        assertEquals("genshin.currency.mora", rules.get(1).declarations().getFirst().name());
        assertEquals("genshin.currency.primogem", rules.get(2).declarations().getFirst().name());
        assertEquals("genshin.currency.mora", rules.get(3).declarations().getFirst().name());
        assertEquals(3, rules.get(1).selectors().getFirst().specificity());
        assertEquals(4, rules.get(2).selectors().getFirst().specificity());
        assertTrue(rules.get(1).selectors().getFirst().matches("boss",
                Map.of("type", "world", "tag", "anemo_hypostasis", "level", 1)));
        assertTrue(rules.get(1).selectors().get(3).matches("boss",
                Map.of("type", "world", "tag", "cryo_hypostasis", "level", 8)));
        assertFalse(rules.get(1).selectors().getFirst().matches("boss",
                Map.of("type", "world", "tag", "anemo_hypostasis", "level", 10)));
        assertTrue(rules.get(2).selectors().getFirst().matches("boss",
                Map.of("type", "world", "tag", "anemo_hypostasis", "level", 1, "city", 1)));
    }

    @Test
    void emptyIntermediateNestingWarnsButStillParses() {
        var warnings = new ArrayList<String>();
        var rules = RewardCssParser.parse("""
                blossom {
                    &[type="wealth"] {
                        &[WR=0] {
                            genshin.currency.mora: 12000;
                        }
                    }
                }
                """, warnings::add);
        assertEquals(1, rules.size());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("2:5"), warnings.toString());
        assertTrue(warnings.getFirst().contains("no direct declarations"));
        assertTrue(rules.getFirst().selectors().getFirst().matches("blossom",
                Map.of("type", "wealth", "WR", 0)));
    }

    @Test
    void nestingWithSharedDeclarationsDoesNotWarn() {
        var warnings = new ArrayList<String>();
        var rules = RewardCssParser.parse("""
                blossom {
                    genshin.experience.adventure: 100;
                    &[WR=0] {
                        genshin.experience.companionship: 10;
                        &[type="wealth"] {
                            genshin.currency.mora: 12000;
                        }
                        &[type="revelation"] {
                            genshin.avatar_up.wanderers_advice: "7~8";
                        }
                    }
                }
                """, warnings::add);
        assertEquals(4, rules.size());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void invalidNestedFormsReportSourceLocation() {
        for (String source : new String[] {
                "&[level=1] { genshin.currency.mora: 1; }",
                "boss { & { genshin.currency.mora: 1; } }",
                "boss { &[level=1] > &[city=1] { genshin.currency.mora: 1; } }",
                "boss { &:is([level=1]) { genshin.currency.mora: 1; } }",
                "boss { &[level=1], chest[level=2] { genshin.currency.mora: 1; } }",
                "boss { &[level=\"1\"] { genshin.currency.mora: 1; } }"
        }) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> RewardCssParser.parse(source), source);
            assertTrue(failure.getMessage().startsWith("Invalid reward CSS: 1:"),
                    failure.getMessage());
        }
    }

    @Test
    void invalidOrExcludedSyntaxFailsAtLoadTime() {
        for (String selector : new String[] {
                "chest:is([city=1], [city=2])", "chest:not([grade=\"common\"])",
                "chest:where([city=1])", "chest.class", "chest#id",
                "rewards > chest", "*", "chest[100 <= level <= 90]",
                "chest[level >= \"high\"]", "chest[city in (1, \"2\")]"
        }) {
            assertThrows(IllegalArgumentException.class, () ->
                    RewardCssParser.parse(selector + " { genshin.currency.mora: 1; }"),
                    selector);
        }
        assertThrows(IllegalArgumentException.class, () ->
                RewardCssParser.parse("chest { genshin.currency.mora: 1 !important; }"));
        assertThrows(IllegalArgumentException.class, () ->
                RewardCssParser.parse("chest { &[grade=1] { genshin.currency.mora: 2; } }"));
    }

    @Test
    void multilineCommentsInsideResourcePropertiesAreIgnored() {
        var rules = RewardCssParser.parse("""
                chest {
                    genshin.currency./* a comment
                            over two lines */mora: 1;
                }
                """);
        assertEquals("genshin.currency.mora",
                rules.getFirst().declarations().getFirst().name());
    }

    @Test
    void unknownNegatedAttributesReportSourceLocation() {
        var error = assertThrows(IllegalArgumentException.class, () ->
                RewardCssParser.parse("""
                        chest { genshin.currency.mora: 1; }
                        chest[!unknown] { genshin.currency.mora: 2; }
                        """));
        assertTrue(error.getMessage().contains("2:"), error.getMessage());
        assertTrue(error.getMessage().contains("Unknown LootCSS selector attribute"),
                error.getMessage());
    }

    @Test
    void errorsIncludeOriginalLineAndColumn() {
        var error = assertThrows(IllegalArgumentException.class, () ->
                RewardCssParser.parse("""
                        chest { genshin.currency.mora: 1; }
                        chest[ { genshin.currency.mora: 2; }
                        """));
        assertTrue(error.getMessage().contains("2:"), error.getMessage());
    }

    @Test
    void typedAttributesRejectImplicitConversionsAtCompileAndRuntime() {
        for (String selector : new String[] {
                "chest[city=\"1\"]", "chest[WR in (\"1\", \"2\")]",
                "chest[grade=1]", "chest[1 <= tag <= 2]",
                "chest[level=1.5]", "chest[unknown=1]"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> RewardCssParser.parse(selector + " { genshin.currency.mora: 1; }"),
                    selector);
        }
        for (Map<String, ?> values : java.util.List.<Map<String, ?>>of(
                Map.of("city", "1"), Map.of("WR", "4"),
                Map.of("grade", 3), Map.of("level", "90"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> RewardCssAttributes.validate(values), values.toString());
        }
        assertDoesNotThrow(() -> RewardCssAttributes.validate(
                Map.of("city", 1, "area", 77, "WR", 4, "level", 90,
                        "grade", "common", "tag", "anemo_hypostasis")));
    }
}
