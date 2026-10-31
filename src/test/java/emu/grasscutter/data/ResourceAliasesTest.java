package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceAliasesTest {
    @Test
    void canonicalGemFamilyAliasesApplyToAnyRarity() {
        var a = ResourceAliases.GENSHIN;
        assertEquals("genshin.avatar_ascension.vayuda_turquoise.sliver",
                a.resolve("genshin.avatar_ascension.anemo.sliver").toString());
        assertEquals("genshin.avatar_ascension.vayuda_turquoise.gemstone",
                a.resolve("genshin.avatar_ascension.anemo.gemstone").toString());
        assertEquals("genshin.avatar_ascension.vajrada_amethyst.fragment",
                a.resolve("genshin.avatar_ascension.electro.fragment").toString());
        assertEquals("genshin.avatar_ascension.vayuda_turquoise",
                a.resolve("genshin.avatar_ascension.anemo").toString());
        assertEquals("genshin.weapon.dull_blade",
                a.resolve("genshin.weapon.dull_blade").toString());
    }

    @Test
    void longestPrefixWinsAndAliasesAreTransitive() {
        var aliases = new ResourceAliases(Map.of(
                ResourceId.parse("mod.element"), ResourceId.parse("mod.canonical"),
                ResourceId.parse("mod.element.anemo"), ResourceId.parse("mod.wind"),
                ResourceId.parse("mod.wind"), ResourceId.parse("mod.vayuda")));
        assertEquals("mod.vayuda.sliver",
                aliases.resolve("mod.element.anemo.sliver").toString());
        assertEquals("mod.canonical.cryo",
                aliases.resolve("mod.element.cryo").toString());
    }

    @Test
    void cyclesAndSelfReferencesFailImmediately() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceAliases(Map.of(
                        ResourceId.parse("x.a"), ResourceId.parse("x.b"),
                        ResourceId.parse("x.b"), ResourceId.parse("x.a"))));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceAliases(Map.of(
                        ResourceId.parse("x.a"), ResourceId.parse("x.a"))));
    }

    @Test
    void inventoryGemResourcesShareOneCanonicalIdentity() {
        var items = new ItemIdRegistry(Map.of(
                8001, "Vayuda Turquoise Sliver",
                8002, "Vajrada Amethyst Gemstone"));
        assertEquals("genshin.avatar_ascension.vayuda_turquoise.sliver",
                items.describe(8001).key());
        assertEquals(8001, items.resolve("genshin.avatar_ascension.anemo.sliver"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.avatar_ascension.vayuda_turquoise_sliver"));
        assertEquals(8001, items.resolve("genshin.avatar_ascension.vayuda_turquoise.sliver"));
        assertEquals(8002, items.resolve("genshin.avatar_ascension.electro.gemstone"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.avatar_ascension.anemo"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.avatar_ascension.geo.sliver"));
    }
    @Test
    void numericGemGradesResolveForEveryElementAndCanonicalFamily() {
        var aliases = ResourceAliases.GENSHIN;
        String[][] families = {
            {"anemo", "vayuda_turquoise"}, {"geo", "prithiva_topaz"},
            {"electro", "vajrada_amethyst"}, {"dendro", "nagadus_emerald"},
            {"hydro", "varunada_lazurite"}, {"pyro", "agnidus_agate"},
            {"cryo", "shivada_jade"}
        };
        String[] grades = {"sliver", "fragment", "chunk", "gemstone"};
        for (String[] family : families) {
            for (int i = 0; i < grades.length; i++) {
                String expected = "genshin.avatar_ascension." + family[1] + "." + grades[i];
                String tier = Integer.toString(i + 2);
                assertEquals(expected,
                        aliases.resolve("genshin.avatar_ascension." + family[0] + "." + tier)
                                .toString());
                assertEquals(expected,
                        aliases.resolve("genshin.avatar_ascension." + family[1] + "." + tier)
                                .toString());
            }
        }
        assertEquals("genshin.avatar_ascension.vayuda_turquoise.1",
                aliases.resolve("genshin.avatar_ascension.anemo.1").toString());
        assertEquals("genshin.avatar_ascension.vayuda_turquoise.6",
                aliases.resolve("genshin.avatar_ascension.anemo.6").toString());
    }

    @Test
    void numericTalentGradesAreScopedToEveryTalentFamily() {
        var aliases = ResourceAliases.GENSHIN;
        String[] names = {"freedom", "resistance", "ballad", "justice", "unknown_future_book"};
        String[] grades = {"teachings", "guide", "philosophies"};
        for (String family : names) {
            for (int i = 0; i < grades.length; i++) {
                assertEquals("genshin.talent_up." + family + "." + grades[i],
                        aliases.resolve("genshin.talent_up." + family + "." + (i + 2))
                                .toString());
            }
        }
        assertEquals("genshin.talent_up.freedom.5",
                aliases.resolve("genshin.talent_up.freedom.5").toString());
        assertEquals("genshin.weapon_up.freedom.2",
                aliases.resolve("genshin.weapon_up.freedom.2").toString());
    }

    @Test
    void gradeAliasesAreGenericAndRejectChainedCycles() {
        var scope = ResourceId.parse("mod.books");
        var aliases = new ResourceAliases(Map.of(), Map.of(scope, Map.of(
                "2", "intermediate", "intermediate", "advanced")));
        assertEquals("mod.books.something.advanced",
                aliases.resolve("mod.books.something.2").toString());
        assertThrows(IllegalArgumentException.class, () ->
                new ResourceAliases(Map.of(), Map.of(scope, Map.of(
                        "2", "3", "3", "2"))));
        assertThrows(IllegalArgumentException.class, () ->
                new ResourceAliases(Map.of(), Map.of(scope, Map.of("2", "2"))));
    }

    @Test
    void numericGradeAliasesResolveToExactExistingNativeItems() {
        var items = new ItemIdRegistry(Map.of(
                8001, "Vayuda Turquoise Sliver",
                8002, "Vajrada Amethyst Gemstone",
                8201, "Teachings of Freedom",
                8202, "Guide to Freedom",
                8203, "Philosophies of Freedom"));
        assertEquals(8001, items.resolve("genshin.avatar_ascension.anemo.2"));
        assertEquals(8002, items.resolve("genshin.avatar_ascension.electro.5"));
        assertEquals(8201, items.resolve("genshin.talent_up.freedom.2"));
        assertEquals(8202, items.resolve("genshin.talent_up.freedom.3"));
        assertEquals(8203, items.resolve("genshin.talent_up.freedom.4"));
        assertEquals(8203, items.resolve("genshin.talent_up.freedom.philosophies"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.avatar_ascension.anemo.1"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.avatar_ascension.anemo.6"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.talent_up.freedom.5"));
    }

}
