package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemIdRegistryTest {
    @Test
    void resourcePathsHaveArbitraryDepthAndRequireDottedSegments() {
        var id = ResourceId.parse("genshin.currency.mora");
        assertEquals("genshin.currency.mora", id.toString());
        assertEquals(ResourceId.of("genshin", "currency", "mora"), id);
        assertEquals(ResourceId.of("my_mod", "item", "my-item"),
                ResourceId.parse("my_mod.item.my-item"));

        var nested = ResourceId.parse("genshin.artifact.berserker.goblet.3.1");
        assertEquals(6, nested.size());
        assertEquals("berserker.goblet.3.1", nested.tail(2));
        assertEquals(ResourceId.parse("genshin.artifact.berserker"),
                ResourceId.parse("genshin.artifact").append("berserker"));
        assertEquals(ResourceId.parse("genshin.artifact.berserker.goblet"),
                nested.replacePrefix(ResourceId.parse("genshin.artifact.berserker.goblet.3.1"),
                        ResourceId.parse("genshin.artifact.berserker.goblet")));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("Genshin.item.mora"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin..mora"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin:item:mora"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("--genshin-currency-mora"));
    }

    @Test
    void qualifiedSigilsRoundTripThroughCssAndOnlyHaveOneCanonicalIdentity() {
        var sigils = Map.of(
                301, "pyro", 302, "hydro", 303, "dendro",
                304, "electro", 305, "anemo", 306, "cryo",
                307, "geo", 308, "lunar", 101692, "light_realm");
        var names = new java.util.HashMap<Integer, String>();
        sigils.forEach((nativeId, region) -> names.put(nativeId, region + " Sigil"));
        var registry = new ItemIdRegistry(names);
        for (var entry : sigils.entrySet()) {
            String qualified = "genshin.currency.sigil." + entry.getValue();
            var id = ResourceId.parse(qualified);
            assertEquals(qualified, id.toString());
            assertEquals(entry.getKey().intValue(), registry.resolve(qualified));
            assertEquals(qualified, registry.describe(entry.getKey()).key());
            assertThrows(IllegalArgumentException.class,
                    () -> registry.resolve("genshin.currency."
                            + entry.getValue() + "_sigil"));
        }
        assertEquals(ItemIdRegistry.REGIONAL_SIGIL, registry.resolve("genshin.currency.sigil"));
        assertEquals(4, ResourceId.parse("genshin.currency.mora.extra").size());
        assertThrows(IllegalArgumentException.class,
                () -> ResourceId.parse("genshin.currency.sigil:anemo"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.currency.sigil.unknown"));
    }

    @Test
    void recipeInventoryItemsBelongToRecipeFamilies() {
        var items = new ItemIdRegistry(Map.of(
                109406, "Recipe: Mora Meat",
                109202, "Recipe: Northern Smoked Chicken"));
        assertEquals("genshin.recipe.mora_meat.item", items.describe(109406).key());
        assertEquals("genshin.recipe.northern_smoked_chicken.item",
                items.describe(109202).key());
        assertEquals(109406, items.resolve("genshin.recipe.mora_meat.item"));
        assertEquals(109202,
                items.resolve("genshin.recipe.northern_smoked_chicken.item"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.recipe.mora_meat"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.material.recipe_mora_meat"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.recipe.mora_meat.unknown"));
    }

    @Test
    void itemIdsAreStrictlyTypedAndExistInLoadedResources() {
        var names = new ItemIdRegistry(Map.of(202, "Mora", 104003, "Hero's Wit"));
        assertEquals(202, names.resolve("genshin.currency.mora"));
        assertEquals(104003, names.resolve("genshin.avatar_up.heros_wit"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.avatar_level_up.heros_wit"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.weapon_level_up.enhancement_ore"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.item.mora"));
        assertEquals(202, names.resolve("202"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.material.id_202"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.material.id_104003"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.material.id_999999"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.avatar.amber"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("astaps.item.mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.currency.primogem"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin:item:mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("123456"));
    }

    @Test
    void nativeExperienceMaterialDomainsHaveDistinctShortIds() {
        var registry = new ItemIdRegistry(Map.of(
                104003, "Hero's Wit", 104011, "Enhancement Ore",
                104012, "Fine Enhancement Ore", 104013, "Mystic Enhancement Ore",
                105001, "Sanctifying Unction", 105002, "Sanctifying Essence",
                106, "Original Resin"), Map.of(
                104003, "Character EXP Materials",
                104011, "Weapon Enhancement Materials",
                104012, "Weapon Enhancement Materials",
                104013, "Weapon Enhancement Materials",
                105001, "Artifact EXP Materials",
                105002, "Artifact EXP Materials",
                106, "Currencies and Virtual Items"));
        assertEquals(104003, registry.resolve("genshin.avatar_up.heros_wit"));
        assertEquals(104011, registry.resolve("genshin.weapon_up.enhancement_ore"));
        assertEquals(104012, registry.resolve("genshin.weapon_up.fine_ore"));
        assertEquals(104013, registry.resolve("genshin.weapon_up.mystic_ore"));
        assertEquals(105001, registry.resolve("genshin.artifact_up.unction"));
        assertEquals(105002, registry.resolve("genshin.artifact_up.essence"));
        assertEquals(106, registry.resolve("genshin.currency.resin"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.material.heros_wit"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.weapon_up.mystic_enhancement_ore"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.resource.resin"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.artifact_up.mystic_ore"));
    }

    @Test
    void nativeHoarfrostCoreResolvesWithoutEnglishTextMapName() {
        // The real native ID must be recognized even if the resource pack
        // supplies only a localized name or no English text at all.
        var names = new java.util.HashMap<Integer, String>();
        names.put(113010, "极寒之核");
        names.put(113011, "Everflame Seed");
        var categories = Map.of(
                113010, "Character Development Items",
                113011, "Character Development Items");
        var usage = Map.of(
                113010, Set.of(ItemIdRegistry.Usage.AVATAR_ASCENSION),
                113011, Set.of(ItemIdRegistry.Usage.AVATAR_ASCENSION));
        var registry = new ItemIdRegistry(names, categories, usage);
        assertEquals("genshin.avatar_ascension.hoarfrost_core",
                registry.describe(113010).key());
        assertEquals(113010, registry.resolve("genshin.avatar_ascension.hoarfrost_core"));
        assertEquals(113011, registry.resolve("genshin.avatar_ascension.everflame_seed"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.material.hoarfrost_core"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.avatar_ascension.unknown_material"));

        names.put(113010, null);
        var missingEnglish = new ItemIdRegistry(names, categories, usage);
        assertEquals(113010,
                missingEnglish.resolve("genshin.avatar_ascension.hoarfrost_core"));
    }

    @Test
    void elementalGemsHaveCharacterAscensionIdentity() {
        String[] families = {
                "agnidus_agate", "nagadus_emerald", "prithiva_topaz",
                "shivada_jade", "vajrada_amethyst", "varunada_lazurite",
                "vayuda_turquoise"
        };
        String[] grades = {"sliver", "fragment", "chunk", "gemstone"};
        var names = new java.util.HashMap<Integer, String>();
        var categories = new java.util.HashMap<Integer, String>();
        int nextId = 7000;
        for (String family : families) {
            for (String grade : grades) {
                int id = ++nextId;
                names.put(id, (family + "_" + grade).replace('_', ' '));
                categories.put(id, "Character Development Items");
            }
        }
        names.put(8000, "Frostnight's Glory");
        categories.put(8000, "Character Development Items");
        var registry = new ItemIdRegistry(names, categories);
        nextId = 7000;
        for (String family : families) {
            for (String grade : grades) {
                int id = ++nextId;
                String name = family + "_" + grade;
                String qualified = "genshin.avatar_ascension." + family + "." + grade;
                assertEquals(id, registry.resolve(qualified));
                assertEquals(qualified, registry.describe(id).key());
                assertThrows(IllegalArgumentException.class,
                        () -> registry.resolve("genshin.avatar_ascension." + name));
                assertThrows(IllegalArgumentException.class,
                        () -> registry.resolve("genshin.material." + name));
            }
        }
        assertEquals(8000, registry.resolve("genshin.avatar_ascension.frostnights_glory"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.material.frostnights_glory"));
    }

    @Test
    void talentBooksUseSubjectAndTierPathWithNoLegacyAliases() {
        var names = new java.util.LinkedHashMap<Integer, String>();
        var categories = new java.util.HashMap<Integer, String>();
        String[] subjects = {"freedom", "resistance", "ballad", "prosperity",
                "diligence", "gold", "transience", "elegance", "light",
                "admonition", "ingenuity", "praxis", "justice"};
        String[] grades = {"teachings", "guide", "philosophies"};
        String[] nativePrefixes = {"teachings_of_", "guide_to_", "philosophies_of_"};
        int next = 12000;
        for (String subject : subjects) {
            for (String nativePrefix : nativePrefixes) {
                int id = ++next;
                names.put(id, (nativePrefix + subject).replace('_', ' '));
                categories.put(id, "Talent Level-Up Materials");
            }
        }
        var items = new ItemIdRegistry(names, categories);
        next = 12000;
        for (String subject : subjects) {
            for (int grade = 0; grade < grades.length; grade++) {
                int id = ++next;
                String canonical = "genshin.talent_up." + subject + "." + grades[grade];
                String legacy = "genshin.talent_up." + nativePrefixes[grade] + subject;
                String wrongCategory = "genshin.material." + nativePrefixes[grade] + subject;
                assertEquals(canonical, items.describe(id).key());
                assertEquals(id, items.resolve(canonical));
                assertThrows(IllegalArgumentException.class, () -> items.resolve(legacy));
                assertThrows(IllegalArgumentException.class,
                        () -> items.resolve(wrongCategory));
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.talent_up.freedom"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.talent_up.freedom.unknown"));
    }

    @Test
    void levelUpAndAscensionHaveDistinctTypesAcrossSystems() {
        var registry = new ItemIdRegistry(Map.of(
                104003, "Hero's Wit", 104011, "Enhancement Ore",
                105001, "Sanctifying Unction", 701, "Avatar Ascension Fragment",
                702, "Weapon Ascension Crystal", 703, "Talent Upgrade Book"),
                Map.of(
                104003, "Character EXP Materials", 104011, "Weapon Enhancement Materials",
                105001, "Artifact EXP Materials", 701, "Character Development Items",
                702, "Materials", 703, "Talent Level-Up Materials"),
                Map.of(701, Set.of(ItemIdRegistry.Usage.AVATAR_ASCENSION),
                        702, Set.of(ItemIdRegistry.Usage.WEAPON_ASCENSION),
                        703, Set.of(ItemIdRegistry.Usage.TALENT_LEVEL_UP)));
        assertEquals("genshin.avatar_up.heros_wit", registry.describe(104003).key());
        assertEquals("genshin.weapon_up.enhancement_ore", registry.describe(104011).key());
        assertEquals("genshin.artifact_up.unction", registry.describe(105001).key());
        assertEquals("genshin.avatar_ascension.avatar_ascension_fragment",
                registry.describe(701).key());
        assertEquals("genshin.weapon_ascension.weapon_ascension_crystal",
                registry.describe(702).key());
        assertEquals("genshin.talent_up.talent_upgrade_book", registry.describe(703).key());
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.avatar_exp.heros_wit"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.talent.talent_upgrade_book"));
    }

    @Test
    void nativeConsumptionDeterminesExclusiveAndSharedResourceTypes() {
        var registry = new ItemIdRegistry(Map.of(
                701, "Shared Development Material", 702, "Weapon Only Crystal",
                703, "Talent Only Book", 704, "Avatar Only Fragment",
                705, "Northlander Sword Billet"), Map.of(
                701, "Character Development Items", 702, "Materials",
                703, "Talent Level-Up Materials", 704, "Character Development Items",
                705, "Character Development Items"), Map.of(
                701, Set.of(ItemIdRegistry.Usage.AVATAR_ASCENSION,
                        ItemIdRegistry.Usage.WEAPON_ASCENSION,
                        ItemIdRegistry.Usage.TALENT_LEVEL_UP),
                702, Set.of(ItemIdRegistry.Usage.WEAPON_ASCENSION),
                703, Set.of(ItemIdRegistry.Usage.TALENT_LEVEL_UP),
                704, Set.of(ItemIdRegistry.Usage.AVATAR_ASCENSION)));
        assertEquals("genshin.material.shared_development_material", registry.describe(701).key());
        assertEquals("genshin.weapon_ascension.weapon_only_crystal", registry.describe(702).key());
        assertEquals("genshin.talent_up.talent_only_book", registry.describe(703).key());
        assertEquals("genshin.avatar_ascension.avatar_only_fragment", registry.describe(704).key());
        assertEquals("genshin.weapon_crafting.northlander_sword_billet", registry.describe(705).key());
        assertEquals(Set.of("genshin.material.shared_development_material"),
                registry.sharedDevelopmentMaterials().keySet());
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.avatar_ascension.shared_development_material"));
    }

    @Test
    void monsterDropIdentityIsIndependentOfAdvancementUsage() {
        var registry = new ItemIdRegistry(Map.of(
                701, "Slime Condensate",
                702, "Damaged Mask",
                703, "Firm Arrowhead",
                704, "Chaos Device",
                705, "Treasure Hoarder Insignia"),
                Map.of(701, "Character Development Items",
                        702, "Character Development Items",
                        703, "Materials",
                        704, "Materials",
                        705, "Character Development Items"),
                Map.of(701, Set.of(ItemIdRegistry.Usage.AVATAR_ASCENSION,
                        ItemIdRegistry.Usage.WEAPON_ASCENSION,
                        ItemIdRegistry.Usage.TALENT_LEVEL_UP),
                        702, Set.of(ItemIdRegistry.Usage.AVATAR_ASCENSION)));
        for (var item : Map.of(
                701, "slime_condensate",
                702, "damaged_mask",
                703, "firm_arrowhead",
                704, "chaos_device",
                705, "treasure_hoarder_insignia").entrySet()) {
            String key = "genshin.monster_drop." + item.getValue();
            assertEquals(key, registry.describe(item.getKey()).key());
            assertEquals(item.getKey().intValue(), registry.resolve(key));
            assertEquals(key, ResourceId.parse(key).toString());
        }
        assertEquals(Set.of(ItemIdRegistry.Usage.AVATAR_ASCENSION,
                ItemIdRegistry.Usage.WEAPON_ASCENSION,
                ItemIdRegistry.Usage.TALENT_LEVEL_UP), registry.usesOf(701));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.avatar_ascension.slime_condensate"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.material.damaged_mask"));
    }

    @Test
    void weaponsHaveOneCanonicalTypeWithoutItemAliases() {
        var names = new ItemIdRegistry(Map.of(11101, "Dull Blade", 202, "Mora"));
        assertEquals(11101, names.resolve("genshin.weapon.dull_blade"));
        assertEquals("genshin.weapon.dull_blade", names.describe(11101).key());
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.material.dull_blade"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.weapon.mora"));
        assertEquals(202, names.resolve("genshin.currency.mora"));
        var typed = new ItemIdRegistry(Map.of(7777, "Test Blade"),
                Map.of(7777, "Weapons"));
        assertEquals(7777, typed.resolve("genshin.weapon.test_blade"));
        assertThrows(IllegalArgumentException.class, () -> typed.resolve("genshin.material.test_blade"));
    }

    @Test
    void artifactReferenceNamesOnlyTheirSets() {
        assertTrue(ItemIdRegistry.isArtifactReference("genshin.artifact.berserker"));
        assertFalse(ItemIdRegistry.isArtifactReference("genshin.artifact.berserker.goblet.3.1"));
        assertTrue(ItemIdRegistry.chestDropNames().values().stream()
                .noneMatch(name -> name.startsWith("genshin.artifact.")));
    }

    @Test
    void identicalEnglishNamesDoNotSilentlySelectOneItem() {
        var names = new ItemIdRegistry(Map.of(501, "Duplicated", 502, "Duplicated"),
                Map.of(501, "Materials", 502, "Materials"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.material.duplicated"));
        assertEquals(501, names.resolve("501"));
        assertEquals(502, names.resolve("502"));
    }
}
