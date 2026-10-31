package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.ResourceRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CssRewardsTest {
    private final ItemIdRegistry items = new ItemIdRegistry(Map.of(
            102, "Adventure EXP",
            201, "Primogem",
            202, "Mora",
            104003, "Hero's Wit"));

    private final YamlLootTables loot = YamlLootTables.parse("""
            currency:
              - {reward: genshin.currency.mora, count: 100}
            经验书:
              - {reward: genshin.avatar_up.heros_wit}
            surprise:
              - {reward: genshin.currency.mora, count: 30}
              - {reward: genshin.avatar_up.heros_wit}
            """, items::resolve);

    @Test
    void lootCssProducesFullyQualifiedNamesBeforeItemSettlement() {
        var css = CssRewards.parse("""
                boss[type="world"] {
                    --material: hoarfrost_core;
                    genshin.avatar_ascension.$material: 2;
                    genshin.currency.mora: 300;
                }
                """, new ItemIdRegistry(Map.of(
                        113010, "Hoarfrost Core", 202, "Mora")), loot);
        var keys = css.resolve("boss", Map.of("type", "world"));
        assertEquals(Map.of(
                "genshin.avatar_ascension.hoarfrost_core", 2,
                "genshin.currency.mora", 300), keys.items());
    }

    @Test
    void cssAndYamlCombineByFinalNamesBeforeOneIdLookup() {
        var items = new ItemIdRegistry(Map.of(202, "Mora", 104003, "Hero's Wit"));
        var registry = new ResourceRegistry(Map.of(
                "genshin.currency.mora", 202,
                "genshin.avatar_up.heros_wit", 104003));
        var pool = YamlLootTables.parse("""
                books:
                  - {type: genshin.avatar_up.heros_wit, count: 2}
                """, registry::resolve);
        var css = CssRewards.parse("""
                chest[type="normal"] {
                    astaps.loot.books: 1;
                    genshin.currency.mora: 10;
                }
                """, items, pool);
        var keys = RewardRoller.roll(
                css.resolve("chest", Map.of("type", "normal")), pool, new Random(1));
        assertEquals(Map.of(
                "genshin.avatar_up.heros_wit", 2,
                "genshin.currency.mora", 10), keys);
        assertEquals(Map.of(104003, 2, 202, 10),
                RewardRoller.resolveKeys(keys, pool, RewardContext.NONE));
    }

    @Test
    void explicitRegistryRejectsMissingStaticKeysWhenCompilingCss() {
        var registry = new ResourceRegistry(Map.of("genshin.currency.mora", 202));
        assertThrows(IllegalArgumentException.class,
                () -> CssRewards.parse("chest { genshin.currency.unknown: 1; }",
                        registry, loot));
        var css = CssRewards.parse("chest { genshin.currency.mora: 1; }",
                registry, loot);
        assertEquals(Map.of("genshin.currency.mora", 1),
                css.resolve("chest", Map.of()).items());
    }

    @Test
    void dottedResourcePropertiesCoexistWithNativeCssCascade() {
        var css = CssRewards.parse("""
                chest {
                    genshin.currency.mora: "1000~1200";
                    astaps.loot.currency: 1;
                }
                chest[grade="common"] {
                    genshin.currency.mora: 500;
                    astaps.loot.currency: 2;
                }
                """, items, loot);
        var base = css.resolveIds("chest", Map.of());
        assertTrue(base.rangedItems().containsKey(202));
        assertEquals(Map.of("astaps.loot.currency", 1), base.loot());
        var common = css.resolveIds("chest", Map.of("grade", "common"));
        assertEquals(Map.of(202, 500), common.items());
        assertEquals(Map.of("astaps.loot.currency", 2), common.loot());
        assertFalse(common.rangedItems().containsKey(202));
        assertEquals(500, RewardRoller.rollIds(common, loot, new Random(7)).get(202));
    }

    @Test
    void canonicalGemKeysOverrideOneAnotherInCss() {
        var registry = new ResourceRegistry(Map.of(
                "genshin.avatar_ascension.anemo.2", 8001));
        var css = CssRewards.parse("""
                boss {
                    genshin.avatar_ascension.anemo.2: 3;
                }
                boss[tag="special"] {
                    genshin.avatar_ascension.anemo.2: 5;
                }
                """, registry, loot);
        assertEquals(Map.of("genshin.avatar_ascension.anemo.2", 3),
                css.resolve("boss", Map.of()).items());
        assertEquals(Map.of("genshin.avatar_ascension.anemo.2", 5),
                css.resolve("boss", Map.of("tag", "special")).items());
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("boss { genshin.avatar_ascension.vayuda_turquoise.sliver: 3; }",
                        registry, loot));
    }

    @Test
    void canonicalNamesCascadeBeforeTheSingleRegistryLookup() {
        var registry = new ResourceRegistry(Map.of(
                "genshin.avatar_ascension.anemo.2", 8101,
                "genshin.avatar_ascension.anemo.5", 8102,
                "genshin.talent_up.freedom.3", 8201));
        var pool = YamlLootTables.parse("{}", registry::resolve);
        var css = CssRewards.parse("""
                boss {
                    genshin.avatar_ascension.anemo.2: 1;
                    genshin.avatar_ascension.anemo.5: 2;
                    genshin.talent_up.freedom.3: 3;
                }
                boss[tag="override"] {
                    genshin.avatar_ascension.anemo.2: 4;
                    genshin.talent_up.freedom.3: 5;
                }
                """, registry, pool);
        assertEquals(Map.of(
                "genshin.avatar_ascension.anemo.2", 1,
                "genshin.avatar_ascension.anemo.5", 2,
                "genshin.talent_up.freedom.3", 3),
                css.resolve("boss", Map.of()).items());
        var keys = css.resolve("boss", Map.of("tag", "override"));
        assertEquals(Map.of(
                "genshin.avatar_ascension.anemo.2", 4,
                "genshin.avatar_ascension.anemo.5", 2,
                "genshin.talent_up.freedom.3", 5), keys.items());
        assertEquals(Map.of(8101, 4, 8102, 2, 8201, 5),
                RewardRoller.resolveKeys(RewardRoller.roll(keys, pool, new Random(1)),
                        pool, RewardContext.NONE));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "boss { genshin.talent_up.freedom.guide: 1; }", registry, pool));
    }

    @Test
    void importantOverridesMoreSpecificAndLaterNormalDeclarations() {
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { genshin.currency.mora: 8 !important; }
                """, items, loot));
    }

    @Test
    void duplicateDottedDeclarationsKeepCssImportantAndLastValueSemantics() {
        var css = CssRewards.parse("""
                chest {
                    genshin.currency.mora: 1;
                    genshin.currency.mora: 4;
                    genshin.currency.primogem: 2;
                    genshin.currency.primogem: 8;
                }
                """, items, loot);
        assertEquals(Map.of(202, 4, 201, 8), css.resolveIds("chest", Map.of()).items());
    }

    @Test
    void malformedSelectorsReportOriginalSourceLocations() {
        var error = assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { genshin.currency.mora: 1; }
                chest[ { genshin.currency.primogem: 2; }
                """, items, loot));
        assertTrue(error.getMessage().contains("Invalid reward CSS:"));
        assertTrue(error.getMessage().contains("2:"));
        assertFalse(error.getMessage().contains("--genshin"));
    }

    @Test
    void bindingsUseExactNamesWithoutDomainSpecificMappings() {
        var css = CssRewards.parse("""
                chest {
                    --Reward_Name: mora;
                    --material: "[mora, primogem]";
                    genshin.currency.$material: 2;
                    genshin.currency.$Reward_Name: 5;
                }
                """, items, loot);
        assertEquals(Map.of(202, 5, 201, 2), css.resolveIds("chest", Map.of()).items());
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { --Reward_Name: mora; genshin.currency.$reward_name: 1; }
                """, items, loot));
    }

    @Test
    void templateLiteralsCannotBeShadowedByVariablesWithTheSameName() {
        var templates = YamlLootTables.parse("""
                pick<KIND>:
                  - {reward: "genshin.currency.$KIND"}
                """, items::resolve);
        var css = CssRewards.parse("""
                chest {
                    --mora: primogem;
                    --element: mora;
                    astaps.loot.pick<mora>: 1;
                }
                chest[mode="variable"] { astaps.loot.pick<$mora>: 2; }
                chest[mode="element"] { astaps.loot.pick<$element>: 3; }
                chest[mode="element"][grade="common"] { --element: primogem; }
                """, items, templates);
        assertEquals(Map.of(202, 1), RewardRoller.rollIds(css.resolveIds("chest", Map.of()), templates, new Random(1)));
        assertEquals(Map.of(202, 1, 201, 2), RewardRoller.rollIds(
                css.resolveIds("chest", Map.of("mode", "variable")), templates, new Random(1)));
        assertEquals(Map.of(202, 3), RewardRoller.rollIds(
                css.resolveIds("chest", Map.of("mode", "element")), templates, new Random(1)));
        assertEquals(Map.of(202, 1, 201, 3), RewardRoller.rollIds(
                css.resolveIds("chest", Map.of("mode", "element", "grade", "common")), templates, new Random(1)));
    }

    @Test
    void bindingNamesHaveNoImplicitRewardMeaning() {
        var unused = CssRewards.parse("chest { --loot: currency; }", items, loot);
        assertTrue(unused.resolveIds("chest", Map.of()).loot().isEmpty());
        var referenced = CssRewards.parse(
                "chest { --loot: currency; astaps.loot.$loot: 2; }", items, loot);
        assertEquals(Map.of("astaps.loot.currency", 2), referenced.resolveIds("chest", Map.of()).loot());
    }

    @Test
    void undeclaredVariablesFailWhileLoadingConfiguration() {
        var error = assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "chest { genshin.currency.$anemo: 1; }", items, loot));
        assertTrue(error.getMessage().contains("Undeclared reward variable $anemo"));
        var tableError = assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "chest { astaps.loot.pick<$missing>: 1; }", items, loot));
        assertTrue(tableError.getMessage().contains("Undeclared reward variable $missing"));
    }

    @Test
    void talentBookFamiliesAreIndependentCssRewards() {
        var bookItems = new ItemIdRegistry(Map.of(
                7201, "Teachings of Freedom",
                7202, "Guide to Freedom",
                7203, "Philosophies of Freedom"));
        var css = CssRewards.parse("""
                chest {
                    genshin.talent_up.freedom.teachings: 1;
                    genshin.talent_up.freedom.guide: 2;
                    genshin.talent_up.freedom.philosophies: 3;
                }
                """, bookItems, loot);
        assertEquals(Map.of(7201, 1, 7202, 2, 7203, 3),
                css.resolveIds("chest", Map.of()).items());
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "chest { genshin.talent_up.teachings_of_freedom: 1; }",
                bookItems, loot));
    }

    @Test
    void inventoryRewardsSupportBonusCountsAndFixedCascades() {
        var css = CssRewards.parse("""
                chest {
                    genshin.currency.mora: '1:57.11,2:42.89';
                }
                chest[grade="common"] {
                    genshin.currency.mora: 3;
                }
                """, items, loot);
        var ranged = css.resolveIds("chest", Map.of());
        assertTrue(ranged.items().isEmpty());
        assertTrue(ranged.rangedItems().containsKey(202));
        var random = new Random(71);
        boolean one = false, two = false;
        for (int i = 0; i < 300; i++) {
            int count = RewardRoller.rollIds(ranged, loot, random).get(202);
            assertTrue(count == 1 || count == 2);
            one |= count == 1;
            two |= count == 2;
        }
        assertTrue(one && two);
        var fixed = css.resolveIds("chest", Map.of("grade", "common"));
        assertEquals(3, fixed.items().get(202));
        assertFalse(fixed.rangedItems().containsKey(202));
        assertEquals(3, RewardRoller.rollIds(fixed, loot, new Random(71)).get(202));
    }

    @Test
    void weaponPropertiesResolveToNativeWeaponItems() {
        var registry = new ItemIdRegistry(Map.of(11101, "Dull Blade", 202, "Mora"));
        var css = CssRewards.parse("""
                chest { genshin.weapon.dull_blade: 1; }
                """, registry, loot);
        assertEquals(1, css.resolveIds("chest", Map.of()).items().get(11101));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { genshin.material.dull_blade: 1; }", registry, loot));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { genshin.avatar.amber: 1; }", registry, loot));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { genshin.recipe.mora_meat: 1; }", registry, loot));
    }

    @Test
    void recipeItemRewardsUseRecipeNamespaceButCannotGrantRecipeDefinitions() {
        var registry = new ItemIdRegistry(Map.of(109406, "Recipe: Mora Meat"));
        var css = CssRewards.parse("""
                chest { genshin.recipe.mora_meat.item: 1; }
                """, registry, loot);
        assertEquals(1, css.resolveIds("chest", Map.of()).items().get(109406));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { genshin.recipe.mora_meat: 1; }", registry, loot));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { genshin.material.recipe_mora_meat: 1; }",
                        registry, loot));
    }

    @Test
    void commaGroupedLevelRangesKeepIndependentSelectors() {
        var css = CssRewards.parse("""
                boss[type="world"][93 <= level <= 99],
                boss[type="world"][100 <= level <= 104],
                boss[type="weekly"][level >= 90] {
                    genshin.currency.mora: 6000;
                }
                boss[type="world"][tag="无相之雷"][93 <= level <= 99] {
                    genshin.currency.primogem: 2;
                }
                """, items, loot);
        assertEquals(6000, css.resolveIds("boss", Map.of(
                "type", "world", "level", 93)).items().get(202));
        assertEquals(6000, css.resolveIds("boss", Map.of(
                "type", "world", "level", 104)).items().get(202));
        assertEquals(6000, css.resolveIds("boss", Map.of(
                "type", "weekly", "level", 105)).items().get(202));
        assertTrue(css.resolveIds("boss", Map.of(
                "type", "world", "level", 105)).items().isEmpty());
        var tagged = css.resolveIds("boss", Map.of(
                "type", "world", "tag", "无相之雷", "level", 95));
        assertEquals(6000, tagged.items().get(202));
        assertEquals(2, tagged.items().get(201));
    }

    @Test
    void independentNamespacedLootPropertiesCanSelectSeveralTables() {
        var css = CssRewards.parse("""
                chest[grade="common"] {
                    astaps.loot.currency: 1;
                    astaps.loot.经验书: 2;
                    genshin.currency.primogem: 2;
                }
                """, items, loot);
        var selected = css.resolveIds("chest", Map.of("grade", "common"));
        assertEquals(Map.of("astaps.loot.currency", 1, "astaps.loot.经验书", 2),
                selected.loot());
        assertEquals(Map.of(201, 2), selected.items());
        assertEquals(Map.of(202, 100, 104003, 2, 201, 2),
                RewardRoller.rollIds(selected, loot, new Random(1)));
    }

    @Test
    void aQualifiedLootPropertySupportsExecutionCounts() {
        var css = CssRewards.parse("""
                chest { astaps.loot.surprise: 3; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.surprise", 3),
                css.resolveIds("chest", Map.of()).loot());
        assertEquals(Map.of(202, 90, 104003, 3),
                RewardRoller.rollIds(css.resolveIds("chest", Map.of()), loot, new Random(1)));
    }

    @Test
    void lootPropertiesCascadeIndependentlyRatherThanReplacingAList() {
        var css = CssRewards.parse("""
                chest[grade="common"] {
                    astaps.loot.currency: 1;
                    astaps.loot.经验书: 1;
                    genshin.currency.primogem: 2;
                }
                chest[grade="common"][level=6] {
                    astaps.loot.currency: 0;
                    astaps.loot.surprise: 2;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 1, "astaps.loot.经验书", 1),
                css.resolveIds("chest", Map.of("grade", "common", "level", 1)).loot());
        var selected = css.resolveIds("chest", Map.of("grade", "common", "level", 6));
        assertEquals(Map.of(
                "astaps.loot.currency", 0,
                "astaps.loot.经验书", 1,
                "astaps.loot.surprise", 2), selected.loot());
        assertEquals(Map.of(202, 60, 104003, 3, 201, 2),
                RewardRoller.rollIds(selected, loot, new Random(1)));
    }

    @Test
    void zeroDisablesOneLootPropertyWithoutClearingItems() {
        var css = CssRewards.parse("""
                chest { astaps.loot.currency: 1; genshin.currency.primogem: 2; }
                chest[level=6] { astaps.loot.currency: 0; }
                """, items, loot);
        var selected = css.resolveIds("chest", Map.of("level", 6));
        assertEquals(Map.of("astaps.loot.currency", 0), selected.loot());
        assertEquals(Map.of(201, 2), RewardRoller.rollIds(selected, loot, new Random(1)));
        assertThrows(IllegalStateException.class, () ->
                RewardConfiguration.requireChestLoot("chest", selected,
                        Map.of("level", 6)));
    }

    @Test
    void tablesCanBeUsedByUnrelatedObjects() {
        var css = CssRewards.parse("""
                monster[tag="elite"] { astaps.loot.currency: 1; }
                chest[grade="common"] { astaps.loot.currency: 3; }
                """, items, loot);
        assertEquals(Map.of(202, 100),
                RewardRoller.rollIds(css.resolveIds("monster", Map.of("tag", "elite")),
                        loot, new Random(1)));
        assertEquals(Map.of(202, 300),
                RewardRoller.rollIds(css.resolveIds("chest", Map.of("grade", "common")),
                        loot, new Random(1)));
    }

    @Test
    void fixedItemQuantitiesOverrideRolledTotals() {
        var css = CssRewards.parse("""
                chest {
                    astaps.loot.currency: 1;
                    astaps.loot.经验书: 1;
                    genshin.currency.mora: 25;
                    genshin.currency.primogem: 2;
                }
                chest[scene=3] { genshin.currency.mora: 0; }
                """, items, loot);
        assertEquals(Map.of(202, 25, 104003, 1, 201, 2),
                RewardRoller.rollIds(css.resolveIds("chest", Map.of()), loot, new Random(1)));
        assertEquals(Map.of(104003, 1, 201, 2),
                RewardRoller.rollIds(css.resolveIds("chest", Map.of("scene", 3)),
                        loot, new Random(1)));
    }

    @Test
    void selectorSpecificityBeatsLaterSourceOrderForSameLootProperty() {
        var css = CssRewards.parse("""
                chest[grade="common"][scene=3] { astaps.loot.surprise: 2; }
                chest[grade="common"] {
                    astaps.loot.surprise: 1;
                    astaps.loot.currency: 1;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.surprise", 2, "astaps.loot.currency", 1),
                css.resolveIds("chest", Map.of("grade", "common", "scene", 3)).loot());
    }

    @Test
    void elementSelectorOutranksEqualAttributeCountUniversalSelector() {
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                *[grade="common"] { astaps.loot.currency: 2; }
                """, items, loot));
    }

    @Test
    void chestTypeAndGradeSelectIndependently() {
        var css = CssRewards.parse("""
                chest[type="normal"][grade="common"] { astaps.loot.currency: 1; }
                chest[type="mora"] { astaps.loot.currency: 2; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 1), css.resolveIds("chest",
                Map.of("type", "normal", "grade", "common")).loot());
        assertEquals(Map.of("astaps.loot.currency", 2), css.resolveIds("chest",
                Map.of("type", "mora", "grade", "common")).loot());
        assertTrue(css.resolveIds("chest", Map.of("grade", "common")).loot().isEmpty());
        assertTrue(css.resolveIds("chest", Map.of("type", "normal", "grade", "luxurious"))
                .loot().isEmpty());
    }

    @Test
    void modernSelectorsUseLibraryMatchingAndSpecificity() {
        for (String selector : new String[] {
                "chest.special", "chest:not([grade=\"bad\"])",
                "rewards > chest#target", "chest:where([grade=\"common\"])", "chest#target"
        }) {
            assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                    selector + " { genshin.currency.mora: 1; }", items, loot));
        }
    }

    @Test
    void isAndWhereFollowSelectorsLevelFourSpecificity() {
        for (String selector : new String[] {
                "boss:is([tag=\"a\"], [tag=\"b\"])", "boss:where([tag=\"a\"])",
                "boss:not([tag=\"a\"])"
        }) {
            assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                    selector + " { genshin.currency.mora: 1; }", items, loot));
        }
    }

    @Test
    void nestingExpandsInSourceOrderWithInheritedBindingsAndSpecificity() {
        var css = CssRewards.parse("""
                boss[type="world"][tag in ("anemo_hypostasis", "cryo_hypostasis")] {
                    --material: mora;
                    genshin.currency.$material: 1;

                    &[1 <= level <= 4] {
                        genshin.currency.$material: 53;
                        &[city=1] {
                            genshin.currency.primogem: 2;
                        }
                    }
                    &[5 <= level <= 9] {
                        genshin.currency.$material: 43;
                    }
                    genshin.currency.$material: 3;
                }
                boss[type="world"][tag="anemo_hypostasis"][level=6] {
                    genshin.currency.mora: 57;
                }
                """, items, loot);
        assertEquals(Map.of(202, 53), css.resolveIds("boss", Map.of(
                "type", "world", "tag", "anemo_hypostasis", "level", 2)).items());
        assertEquals(Map.of(202, 53, 201, 2), css.resolveIds("boss", Map.of(
                "type", "world", "tag", "cryo_hypostasis", "level", 2, "city", 1)).items());
        assertEquals(Map.of(202, 57), css.resolveIds("boss", Map.of(
                "type", "world", "tag", "anemo_hypostasis", "level", 6)).items());
        assertEquals(Map.of(202, 43), css.resolveIds("boss", Map.of(
                "type", "world", "tag", "cryo_hypostasis", "level", 6)).items());
        assertEquals(Map.of(202, 3), css.resolveIds("boss", Map.of(
                "type", "world", "tag", "cryo_hypostasis", "level", 10)).items());
        assertTrue(css.resolveIds("boss", Map.of(
                "type", "weekly", "tag", "anemo_hypostasis", "level", 2)).items().isEmpty());
    }

    @Test
    void nestedSelectorAlternativesKeepTheirOwnMatchingAndOrder() {
        var css = CssRewards.parse("""
                chest[grade="common"], chest[grade="precious"] {
                    &[level=2], &[level=3] {
                        genshin.currency.mora: 10;
                    }
                    &[level=3] {
                        genshin.currency.mora: 20;
                    }
                }
                """, items, loot);
        assertEquals(10, css.resolveIds("chest", Map.of(
                "grade", "common", "level", 2)).items().get(202));
        assertEquals(20, css.resolveIds("chest", Map.of(
                "grade", "precious", "level", 3)).items().get(202));
        assertTrue(css.resolveIds("chest", Map.of(
                "grade", "precious", "level", 4)).items().isEmpty());
    }

    @Test
    void repeatedLootPropertyUsesLastDeclaration() {
        var css = CssRewards.parse("""
                chest {
                    astaps.loot.currency: 1;
                    astaps.loot.currency: 2;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 2),
                css.resolveIds("chest", Map.of()).loot());
    }

    @Test
    void malformedRewardDeclarationsAreRejected() {
        for (String declaration : new String[]{
                "astaps.loot.missing: 1;",
                "astaps.loot.currency: none;",
                "astaps.loot.currency: -1;",
                "genshin.loot.currency: 1;",
                "genshin.avatar.amber: 1;",
                "astaps.item.mora: 1;"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    CssRewards.parse("chest { " + declaration + " }", items, loot),
                    declaration);
        }
    }

    @Test
    void lootExecutionCountsSupportRangesAndPercentageDistributions() {
        var css = CssRewards.parse("""
                chest {
                    astaps.loot.currency: "1~3";
                    astaps.loot.经验书: "1:50,2:30";
                }
                """, items, loot);
        var selection = css.resolveIds("chest", Map.of());
        assertTrue(selection.loot().isEmpty());
        assertEquals(2, selection.rangedLoot().size());

        var currencyCounts = new java.util.HashSet<Integer>();
        var bookCounts = new java.util.HashSet<Integer>();
        var random = new Random(71);
        for (int i = 0; i < 300; i++) {
            var rewards = RewardRoller.rollIds(selection, loot, random);
            currencyCounts.add(rewards.getOrDefault(202, 0) / 100);
            bookCounts.add(rewards.getOrDefault(104003, 0));
        }
        assertEquals(java.util.Set.of(1, 2, 3), currencyCounts);
        assertEquals(java.util.Set.of(1, 2), bookCounts);
    }

    @Test
    void fixedAndVariableLootCountsOverrideOneAnother() {
        var css = CssRewards.parse("""
                chest { astaps.loot.currency: "1~3"; }
                chest[level=6] { astaps.loot.currency: 2; }
                chest[level=6][city=1] { astaps.loot.currency: "0:70,3:30"; }
                """, items, loot);
        var original = css.resolveIds("chest", Map.of());
        assertTrue(original.loot().isEmpty());
        assertTrue(original.rangedLoot().containsKey("astaps.loot.currency"));

        var fixed = css.resolveIds("chest", Map.of("level", 6));
        assertEquals(Map.of("astaps.loot.currency", 2), fixed.loot());
        assertTrue(fixed.rangedLoot().isEmpty());
        assertEquals(200, RewardRoller.rollIds(fixed, loot, new Random(2)).get(202));

        var variable = css.resolveIds("chest", Map.of("level", 6, "city", 1));
        assertTrue(variable.loot().isEmpty());
        assertEquals(1, variable.rangedLoot().size());
        var outcomes = new java.util.HashSet<Integer>();
        var random = new Random(72);
        for (int i = 0; i < 100; i++) {
            outcomes.add(RewardRoller.rollIds(variable, loot, random).getOrDefault(202, 0));
        }
        assertEquals(java.util.Set.of(0, 300), outcomes);
    }

    @Test
    void quotedLevelRangesCoverSixSourceIntervalsWithoutFallback() {
        var css = CssRewards.parse("""
                chest[grade="common"] { genshin.currency.primogem: 2; }
                chest[grade="common"][1 <= level <= 5] { astaps.loot.surprise: 1; }
                chest[grade="common"][6 <= level <= 10] { astaps.loot.currency: 2; }
                chest[grade="common"][11 <= level <= 15] { astaps.loot.surprise: 3; }
                chest[grade="common"][16 <= level <= 20] { astaps.loot.currency: 4; }
                chest[grade="common"][21 <= level <= 25] { astaps.loot.surprise: 5; }
                chest[grade="common"][level >= 26] { astaps.loot.currency: 6; }
                """, items, loot);
        int[] levels = {1, 5, 6, 10, 11, 15, 16, 20, 21, 25, 26, 99};
        String[] names = {
                "surprise", "surprise", "currency", "currency",
                "surprise", "surprise", "currency", "currency",
                "surprise", "surprise", "currency", "currency"
        };
        int[] times = {1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6};
        for (int i = 0; i < levels.length; i++) {
            assertEquals(Map.of("astaps.loot." + names[i], times[i]),
                    css.resolveIds("chest", Map.of("grade", "common",
                            "level", levels[i])).loot(),
                    "level " + levels[i]);
        }
        for (Integer level : new Integer[]{0, -1}) {
            var attrs = Map.of("grade", "common", "level", level);
            var selection = css.resolveIds("chest", attrs);
            assertTrue(selection.loot().isEmpty());
            assertEquals(2, selection.items().get(201).intValue());
            assertThrows(IllegalStateException.class, () ->
                    RewardConfiguration.requireChestLoot("chest", selection, attrs));
        }
        assertTrue(css.resolveIds("chest", Map.of("grade", "common")).loot().isEmpty());
    }

    @Test
    void standardIsSelectorsMatchAttributeAlternatives() {
        var css = CssRewards.parse("""
                chest[type="normal"][grade="common"][city=1] {
                    genshin.currency.primogem: 0;
                }
                chest[type="normal"][grade="common"][area in (10, 77, 8, 19, 40, 41, 47, 300)] {
                    genshin.currency.primogem: 2;
                }
                boss[type="world"][tag in ("electro_hypostasis", "anemo_hypostasis")] {
                    genshin.currency.mora: 100;
                }
                """, items, loot);
        for (int area : new int[]{10, 77, 8, 19, 40, 41, 47, 300}) {
            assertEquals(2, css.resolveIds("chest", Map.of("type", "normal",
                    "grade", "common", "city", 1, "area", area)).items().get(201));
        }
        assertEquals(0, css.resolveIds("chest", Map.of("type", "normal",
                "grade", "common", "city", 1, "area", 9)).items().get(201));
        assertEquals(100, css.resolveIds("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis")).items().get(202));
        assertEquals(100, css.resolveIds("boss", Map.of(
                "type", "world", "tag", "anemo_hypostasis")).items().get(202));
        assertTrue(css.resolveIds("boss", Map.of(
                "type", "world", "tag", "geo_hypostasis")).items().isEmpty());
        // Quoted commas are literal attribute values in standard CSS.
        var literal = CssRewards.parse("""
                chest[tag="8,10"] { genshin.currency.mora: 1; }
                """, items, loot);
        assertEquals(1, literal.resolveIds("chest", Map.of("tag", "8,10"))
                .items().get(202));
        assertTrue(literal.resolveIds("chest", Map.of("area", 8)).items().isEmpty());
        assertTrue(literal.resolveIds("chest", Map.of("area", 10)).items().isEmpty());
    }

    @Test
    void isSelectorKeepsCssSpecificityAndLevelRanges() {
        var css = CssRewards.parse("""
                boss[type="world"][tag in ("a", "b")][70 <= level <= 79] {
                    genshin.currency.mora: 10;
                }
                boss[type="world"][70 <= level <= 79] {
                    genshin.currency.mora: 20;
                }
                boss[type="world"][tag="b"][70 <= level <= 79] {
                    genshin.currency.mora: 30;
                }
                """, items, loot);
        assertEquals(10, css.resolveIds("boss", Map.of(
                "type", "world", "tag", "a", "level", 75)).items().get(202));
        assertEquals(30, css.resolveIds("boss", Map.of(
                "type", "world", "tag", "b", "level", 75)).items().get(202));
        assertEquals(20, css.resolveIds("boss", Map.of(
                "type", "world", "tag", "c", "level", 75)).items().get(202));
        assertTrue(css.resolveIds("boss", Map.of(
                "type", "world", "tag", "a", "level", 80)).items().isEmpty());
    }

    @Test
    void numericRangesApplyToEveryAttributeAndKeepSpecificity() {
        var css = CssRewards.parse("""
                blossom[type="wealth"][6 <= world_level <= 9] {
                    genshin.currency.mora: 60000;
                }
                blossom[type="wealth"][world_level=7][city=1] {
                    genshin.currency.mora: 30000;
                }
                chest[rank >= 2] {
                    genshin.currency.primogem: 5;
                }
                """, items, loot);
        for (int value : new int[]{6, 7, 8, 9}) {
            assertEquals(60000, css.resolveIds("blossom", Map.of(
                    "type", "wealth", "world_level", value)).items().get(202));
        }
        assertEquals(30000, css.resolveIds("blossom", Map.of(
                "type", "wealth", "world_level", 7, "city", 1)).items().get(202));
        for (int value : new int[]{5, 10}) {
            assertTrue(css.resolveIds("blossom", Map.of(
                    "type", "wealth", "world_level", value)).items().isEmpty());
        }
        assertTrue(css.resolveIds("blossom", Map.of("type", "wealth")).items().isEmpty());
        assertTrue(css.resolveIds("chest", Map.of("rank", 1)).items().isEmpty());
        assertEquals(5, css.resolveIds("chest", Map.of("rank", 2)).items().get(201));
        assertEquals(5, css.resolveIds("chest", Map.of("rank", 999)).items().get(201));
    }

    @Test
    void levelRangesPreserveSpecificityAndExactEquality() {
        var css = CssRewards.parse("""
                chest[6 <= level <= 10] { astaps.loot.surprise: 2; }
                chest[11 <= level <= 16] { astaps.loot.currency: 3; }
                chest[grade="common"][16 <= level <= 20] { astaps.loot.surprise: 4; }
                chest[level=30] { astaps.loot.currency: 5; }
                """, items, loot);
        assertTrue(css.resolveIds("chest", Map.of("level", 5)).loot().isEmpty());
        assertEquals(Map.of("astaps.loot.surprise", 2),
                css.resolveIds("chest", Map.of("level", 6)).loot());
        assertEquals(Map.of("astaps.loot.surprise", 2),
                css.resolveIds("chest", Map.of("level", 10)).loot());
        assertEquals(Map.of("astaps.loot.currency", 3),
                css.resolveIds("chest", Map.of("level", 11)).loot());
        assertEquals(Map.of("astaps.loot.currency", 3, "astaps.loot.surprise", 4),
                css.resolveIds("chest", Map.of("grade", "common", "level", 16)).loot());
        assertTrue(css.resolveIds("chest", Map.of("grade", "common", "level", 21))
                .loot().isEmpty());
        assertEquals(Map.of("astaps.loot.currency", 5),
                css.resolveIds("chest", Map.of("level", 30)).loot());
    }

    @Test
    void rangeMembershipAttributesValidateBoundsAndPreserveLiteralEquality() {
        for (String attr : new String[]{"level", "WR", "world_level", "rank"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    CssRewards.parse("chest[10 <= " + attr
                            + " <= 5] { astaps.loot.currency: 1; }", items, loot));
        }
        var css = CssRewards.parse("""
                chest[tag="6~10"] { genshin.currency.mora: 9; }
                chest[6 <= level <= 10] { genshin.currency.primogem: 2; }
                """, items, loot);
        assertEquals(Map.of(202, 9), css.resolveIds("chest", Map.of("tag", "6~10")).items());
        assertEquals(Map.of(201, 2), css.resolveIds("chest", Map.of("level", 7)).items());
        assertTrue(css.resolveIds("chest", Map.of("level", 11)).items().isEmpty());
        assertTrue(css.resolveIds("chest", Map.of()).items().isEmpty());
    }

    @Test
    void whereSelectorsHaveZeroSpecificityAndMatchNatively() {
        var css = CssRewards.parse("""
                boss[type="world"][tag in ("a", "b")] {
                    genshin.currency.mora: 10;
                }
                boss[type="world"][tag="a"] {
                    genshin.currency.mora: 20;
                }
                """, items, loot);
        assertEquals(20, css.resolveIds("boss", Map.of("type", "world", "tag", "a")).items().get(202));
        assertEquals(10, css.resolveIds("boss", Map.of("type", "world", "tag", "b")).items().get(202));
    }

    @Test
    void numericMembershipSupportsOverlappingOpenRangesAndLargeValues() {
        var css = CssRewards.parse("""
                boss[type="world"][70 <= level <= 79] {
                    genshin.currency.mora: 70;
                }
                boss[type="world"][level >= 79] {
                    genshin.currency.primogem: 79;
                }
                boss[type="world"][level >= 93] {
                    genshin.currency.mora: 93;
                }
                """, items, loot);
        assertEquals(Map.of(202, 70), css.resolveIds("boss",
                Map.of("type", "world", "level", 70)).items());
        assertEquals(Map.of(202, 70, 201, 79), css.resolveIds("boss",
                Map.of("type", "world", "level", 79)).items());
        assertEquals(Map.of(201, 79), css.resolveIds("boss",
                Map.of("type", "world", "level", 80)).items());
        assertEquals(Map.of(202, 93, 201, 79), css.resolveIds("boss",
                Map.of("type", "world", "level", 93)).items());
        assertThrows(IllegalArgumentException.class, () -> css.resolveIds("boss",
                Map.of("type", "world", "level", "999999999999999999999999999")));
        assertThrows(IllegalArgumentException.class, () -> css.resolveIds("boss",
                Map.of("type", "world", "level", "unknown")));
        assertTrue(css.resolveIds("boss", Map.of("type", "world")).items().isEmpty());
        assertTrue(css.resolveIds("boss", Map.of("type", "world", "level", -1)).items().isEmpty());
    }

    @Test
    void rangedCurrencyRollsInclusivelyAndOverridesLoot() {
        var css = CssRewards.parse("""
                chest {
                    astaps.loot.currency: 1;
                    genshin.currency.mora: "3~5";
                }
                """, items, loot);
        var selected = css.resolveIds("chest", Map.of());
        var observed = new java.util.HashSet<Integer>();
        var random = new Random(214);
        for (int i = 0; i < 100; i++) {
            int mora = RewardRoller.rollIds(selected, loot, random).get(202);
            assertTrue(mora >= 3 && mora <= 5);
            observed.add(mora);
        }
        assertEquals(java.util.Set.of(3, 4, 5), observed);
    }

    @Test
    void fixedAndRangedCurrencyRespectCascadePrecedence() {
        var css = CssRewards.parse("""
                chest { genshin.currency.mora: "3~5"; }
                chest[city=1] { genshin.currency.mora: 9; }
                chest[city=1][area=2] { genshin.currency.mora: "11~12"; }
                """, items, loot);
        assertEquals(9, RewardRoller.rollIds(
                css.resolveIds("chest", Map.of("city", 1)), loot, new Random(1))
                .get(202).intValue());
        int value = RewardRoller.rollIds(css.resolveIds("chest",
                Map.of("city", 1, "area", 2)), loot, new Random(2)).get(202);
        assertTrue(value == 11 || value == 12);
    }

    @Test
    void typedPredicatesRespectMissingAttributesAndNumericTypes() {
        var css = CssRewards.parse("""
                boss[level >= 90] {
                    genshin.currency.mora: 100;
                }
                boss[level != 90] {
                    genshin.currency.primogem: 5;
                }
                boss[!level] {
                    genshin.experience.adventure: 10;
                }
                boss[70 <= level <= 79] {
                    genshin.currency.mora: 200;
                }
                """, items, loot);
        assertEquals(Map.of(202, 100), css.resolveIds("boss", Map.of("level", 90)).items());
        assertEquals(Map.of(202, 100, 201, 5),
                css.resolveIds("boss", Map.of("level", 93)).items());
        assertEquals(Map.of(202, 200, 201, 5),
                css.resolveIds("boss", Map.of("level", 79)).items());
        assertEquals(Map.of(102, 10), css.resolveIds("boss", Map.of()).items());
        assertThrows(IllegalArgumentException.class, () -> css.resolveIds("boss", Map.of("level", "invalid")));
    }

    @Test
    void inPredicateHasNormalAttributeSpecificity() {
        var css = CssRewards.parse("""
                chest[city in (1, 2)] { genshin.currency.mora: 1; }
                chest[city = 2][grade="precious"] { genshin.currency.mora: 3; }
                chest[city in (1, 2)][grade="common"] { genshin.currency.mora: 4; }
                """, items, loot);
        assertEquals(1, css.resolveIds("chest", Map.of("city", 1)).items().get(202));
        assertEquals(3, css.resolveIds("chest", Map.of("city", 2, "grade", "precious")).items().get(202));
        assertEquals(4, css.resolveIds("chest", Map.of("city", 2, "grade", "common")).items().get(202));
        assertTrue(css.resolveIds("chest", Map.of("city", 3)).items().isEmpty());
        assertTrue(css.resolveIds("chest", Map.of()).items().isEmpty());
    }

    @Test
    void typedPredicatesRejectInvalidExpressions() {
        for (String selector : new String[] {
                "boss[90 <= level <= 70]",
                "boss[level >= \"high\"]",
                "boss[city in (1, \"2\")]"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> CssRewards.parse(selector + " { genshin.currency.mora: 1; }", items, loot));
        }
    }

    @Test
    void functionStylePoolCallsResolveThroughCssWithDollarBindings() {
        var templated = YamlLootTables.parse("""
                reward<KIND>:
                  - {value: "genshin.currency.$KIND"}
                """, items::resolve);
        var css = CssRewards.parse("""
                chest {
                    --kind: mora;
                    astaps.loot.reward<$kind>: 2;
                }
                chest[tag="bonus"] {
                    --kind: primogem;
                }
                """, items, templated);
        assertEquals(Map.of(202, 2),
                RewardRoller.rollIds(css.resolveIds("chest", Map.of()), templated, new Random(2)));
        assertEquals(Map.of(201, 2),
                RewardRoller.rollIds(css.resolveIds("chest", Map.of("tag", "bonus")),
                        templated, new Random(2)));
    }

    @Test
    void literalFunctionCallsValidateAgainstExistingTables() {
        var templated = YamlLootTables.parse("""
                reward<KIND>:
                  - {value: "genshin.currency.$KIND"}
                """, items::resolve);
        var css = CssRewards.parse("""
                chest { astaps.loot.reward<mora>: 2; }
                """, items, templated);
        assertEquals(Map.of(202, 2),
                RewardRoller.rollIds(css.resolveIds("chest", Map.of()), templated, new Random(3)));
    }

    @Test
    void decimalCountsAreOutsideTheQuantityGrammar() {
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "chest { genshin.currency.mora: 3.25; }", items, loot));
        var css = CssRewards.parse(
                "chest { genshin.currency.mora: \"3:3, 4:1\"; }", items, loot);
        var rng = new Random(51);
        for (int i = 0; i < 100; i++) {
            int value = RewardRoller.rollIds(css.resolveIds("chest", Map.of()), loot, rng).get(202);
            assertTrue(value == 3 || value == 4);
        }
    }
}
