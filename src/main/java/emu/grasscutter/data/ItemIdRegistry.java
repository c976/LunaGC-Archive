package emu.grasscutter.data;

import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.utils.lang.Language;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Item identities shared by loot.yaml and rewards.css.
 *
 * <p>Known stable names are explicit. Other names are provisional 7.1 English
 * TextMap slugs: a collision is an error, never a silent choice of item ID.
 * An unnamed item remains addressable by its numeric ID.
 */
public final class ItemIdRegistry {
    /** Internal placeholder, never a real game item ID. */
    public static final int REGIONAL_SIGIL = -1;

    public record Info(int id, String english, String key, String category) {}

    private static final Map<Integer, String> VERIFIED_ENGLISH = Map.of(
            104001, "Wanderer's Advice",
            104002, "Adventurer's Experience",
            104003, "Hero's Wit",
            201, "Primogem",
            202, "Mora");

    private static final Map<Integer, String> STABLE_NAMES = Map.ofEntries(
            Map.entry(101, "character"),
            Map.entry(102, "adventure"),
            Map.entry(105, "companionship"),
            Map.entry(106, "resin"),
            Map.entry(107, "legendary_key"),
            Map.entry(121, "home"),
            Map.entry(201, "primogem"),
            Map.entry(202, "mora"),
            Map.entry(203, "genesis_crystal"),
            Map.entry(204, "realm_currency"),
            Map.entry(301, "sigil.pyro"),
            Map.entry(302, "sigil.hydro"),
            Map.entry(303, "sigil.dendro"),
            Map.entry(304, "sigil.electro"),
            Map.entry(305, "sigil.anemo"),
            Map.entry(306, "sigil.cryo"),
            Map.entry(307, "sigil.geo"),
            Map.entry(308, "sigil.lunar"),
            Map.entry(104001, "wanderers_advice"),
            Map.entry(104002, "adventurers_experience"),
            Map.entry(104003, "heros_wit"),
            Map.entry(104011, "enhancement_ore"),
            Map.entry(104012, "fine_ore"),
            Map.entry(104013, "mystic_ore"),
            // Native Cryo Regisvine ascension drop. TextMapEN can be incomplete in
            // installed resources, so keep its identity independent of display names.
            Map.entry(113010, "hoarfrost_core"),
            // Stable internal resource path for native Boss material 113080.
            Map.entry(113080, "precision_clockwork_core"));

    // Explicit item identities for the historic chest reward graph.
    // Artifact identities are generated from native set/slot/rarity data at runtime.
    private static final Map<Integer, String> CHEST_DROP_NAMES = Map.ofEntries(
            Map.entry(11101, "dull_blade"),
            Map.entry(11201, "silver_sword"),
            Map.entry(11303, "travelers_handy_sword"),
            Map.entry(11305, "fillet_blade"),
            Map.entry(12101, "waster_greatsword"),
            Map.entry(12201, "old_mercs_pal"),
            Map.entry(12303, "ferrous_shadow"),
            Map.entry(12306, "skyrider_greatsword"),
            Map.entry(13101, "beginners_protector"),
            Map.entry(13201, "iron_point"),
            Map.entry(13301, "white_tassel"),
            Map.entry(13302, "halberd"),
            Map.entry(14101, "apprentices_notes"),
            Map.entry(14201, "pocket_grimoire"),
            Map.entry(14303, "otherworldly_story"),
            Map.entry(14305, "twin_nephrite"),
            Map.entry(15101, "hunters_bow"),
            Map.entry(15201, "seasoned_hunters_bow"),
            Map.entry(15303, "recurve_bow"),
            Map.entry(15305, "messenger"),
            Map.entry(100061, "raw_meat"),
            Map.entry(100064, "fowl"),
            Map.entry(101692, "sigil.light_realm"),
            Map.entry(104301, "teachings_of_freedom"),
            Map.entry(104302, "guide_to_freedom"),
            Map.entry(104304, "teachings_of_resistance"),
            Map.entry(104305, "guide_to_resistance"),
            Map.entry(104307, "teachings_of_ballad"),
            Map.entry(104308, "guide_to_ballad"),
            Map.entry(104310, "teachings_of_prosperity"),
            Map.entry(104311, "guide_to_prosperity"),
            Map.entry(104313, "teachings_of_diligence"),
            Map.entry(104314, "guide_to_diligence"),
            Map.entry(104316, "teachings_of_gold"),
            Map.entry(104317, "guide_to_gold"),
            Map.entry(104320, "guide_to_transience"),
            Map.entry(104321, "philosophies_of_transience"),
            Map.entry(104323, "guide_to_elegance"),
            Map.entry(104324, "philosophies_of_elegance"),
            Map.entry(104326, "guide_to_light"),
            Map.entry(104327, "philosophies_of_light"),
            Map.entry(104329, "guide_to_admonition"),
            Map.entry(104330, "philosophies_of_admonition"),
            Map.entry(104332, "guide_to_ingenuity"),
            Map.entry(104333, "philosophies_of_ingenuity"),
            Map.entry(104335, "guide_to_praxis"),
            Map.entry(104336, "philosophies_of_praxis"),
            Map.entry(107010, "crimson_agate"),
            Map.entry(109202, "recipe_northern_smoked_chicken"),
            Map.entry(109207, "recipe_mushroom_pizza"),
            Map.entry(109406, "recipe_mora_meat"),
            Map.entry(109407, "recipe_stir_fried_filet"));

    // Verified chest weapon IDs, also used when fixtures have no category metadata.
    private static final java.util.regex.Pattern ASCENSION_GEM =
            java.util.regex.Pattern.compile(
                    "(?:agnidus_agate|nagadus_emerald|prithiva_topaz|shivada_jade"
                    + "|vajrada_amethyst|varunada_lazurite|vayuda_turquoise)"
                    + "_(?:sliver|fragment|chunk|gemstone)");

    /** Three native talent-book tiers, grouped by their subject. */
    private static final java.util.regex.Pattern TALENT_BOOK =
            java.util.regex.Pattern.compile(
                    "(teachings_of|guide_to|philosophies_of)_([a-z][a-z0-9_]*)");

    /** Exclusively consumed advancement materials in the bundled reward graph. */
    private static final Set<String> AVATAR_ASCENSION_ONLY = Set.of(
            "artificed_spare_clockwork_component_coppelia",
            "artificed_spare_clockwork_component_coppelius",
            "basalt_pillar",
            "cloudseam_scale",
            "cleansing_heart",
            "crystalline_bloom",
            "dew_of_repudiation",
            "dragonheirs_false_fin",
            "emperors_resolution",
            "ensnaring_gaze",
            "everflame_seed",
            "fontemer_unihorn",
            "fragment_of_a_golden_melody",
            "frostnights_glory",
            "gold_inscribed_secret_source_core",
            "hoarfrost_core",
            "hurricane_seed",
            "juvenile_jade",
            "light_guiding_tetrahedron",
            "lightbearing_scale_feather",
            "lightning_prism",
            "majestic_hooked_beak",
            "marionette_core",
            "mark_of_the_binding_blessing",
            "overripe_flamegranate",
            "perpetual_caliber",
            "perpetual_heart",
            "plume_of_the_fallen_watcher",
            "precision_clockwork_core",
            "precision_kuuvahki_stamping_die",
            "prismatic_severed_tail",
            "pseudo_stamens",
            "quelled_creeper",
            "remnant_of_the_dreadwing",
            "riftborn_regalia",
            "runic_fang",
            "secret_source_airflow_accumulator",
            "severed_tail_of_the_sky_roamer",
            "smoldering_pearl",
            "sparkless_statue_core",
            "storm_beads",
            "talisman_of_the_enigmatic_land",
            "thunderclap_fruitcore",
            "tourbillon_device",
            "unscorched_blossom_branch",
            "water_that_failed_to_transcend");
    private static final Set<String> TALENT_LEVEL_UP_ONLY = Set.of(
            "ashen_heart",
            "bloodjade_branch",
            "dakas_bell",
            "dragon_lords_crown",
            "dvalins_claw",
            "dvalins_plume",
            "dvalins_sigh",
            "gilded_scale",
            "guide_to_ballad",
            "guide_to_freedom",
            "guide_to_resistance",
            "hellfire_butterfly",
            "mirror_of_mushin",
            "molten_moment",
            "mudra_of_the_malefic_general",
            "puppet_strings",
            "ring_of_boreas",
            "shadow_of_the_warrior",
            "shard_of_a_foul_legacy",
            "spirit_locket_of_boreas",
            "tail_of_boreas",
            "teachings_of_ballad",
            "teachings_of_freedom",
            "teachings_of_resistance",
            "tears_of_the_calamitous_god",
            "the_meaning_of_aeons",
            "tusk_of_monoceros_caeli");
    private static final java.util.regex.Pattern WEAPON_BILLET =
            java.util.regex.Pattern.compile(
                    "(?:northlander|midlander|borderland)_(?:sword|bow|claymore|catalyst|polearm)_billet");

    // Common enemy drops keep one source-based identity across growth systems.
    // The list is limited to explicitly identified monster materials.
    private static final Set<String> MONSTER_DROPS = Set.of(
            "slime_condensate", "slime_secretions", "slime_concentrate",
            "damaged_mask", "stained_mask", "ominous_mask",
            "firm_arrowhead", "sharp_arrowhead", "weathered_arrowhead",
            "chaos_device", "chaos_circuit", "chaos_core",
            "treasure_hoarder_insignia", "silver_raven_insignia",
            "golden_raven_insignia");

    public enum Usage {
        AVATAR_LEVEL_UP, AVATAR_ASCENSION, WEAPON_LEVEL_UP, WEAPON_ASCENSION,
        ARTIFACT_LEVEL_UP, TALENT_LEVEL_UP
    }

    /** All inventory reward types have one canonical allowlist shared by callers. */
    private static final Set<String> INVENTORY_RESOURCE_TYPES = Set.of(
            "currency", "experience", "avatar_up", "weapon_up",
            "artifact_up", "avatar_ascension", "weapon_ascension",
            "talent_up", "weapon_crafting", "monster_drop",
            "material", "food", "furnishing", "weapon");

    private static final Set<Integer> CHEST_WEAPON_IDS = Set.of(
            11101, 11201, 11303, 11305, 12101, 12201, 12303, 12306,
            13101, 13201, 13301, 13302, 14101, 14201, 14303, 14305,
            15101, 15201, 15303, 15305);

    private final Map<Integer, Info> byId;
    private final Map<String, Integer> byName;
    private final Set<String> ambiguous;
    private final Map<Integer, Set<Usage>> usageById;

    public ItemIdRegistry(Map<Integer, String> englishNames) {
        this(englishNames, Map.of());
    }

    public ItemIdRegistry(Map<Integer, String> englishNames, Map<Integer, String> categories) {
        this(englishNames, categories, Map.of());
    }

    public ItemIdRegistry(Map<Integer, String> englishNames, Map<Integer, String> categories,
            Map<Integer, Set<Usage>> usage) {
        var ids = new LinkedHashMap<Integer, Info>();
        var names = new HashMap<String, Integer>();
        var collisions = new HashSet<String>();
        for (var entry : englishNames.entrySet()) {
            int id = entry.getKey();
            if (id <= 0) throw new IllegalArgumentException("Invalid item ID: " + id);
            String english = entry.getValue();
            if (english == null || english.isBlank()) english = VERIFIED_ENGLISH.get(id);
            String category = categories.getOrDefault(id, switch (id) {
                case 104001, 104002, 104003 -> "Character EXP Materials";
                case 104011, 104012, 104013 -> "Weapon Enhancement Materials";
                case 201, 202 -> "Currencies";
                default -> CHEST_WEAPON_IDS.contains(id) ? "Weapons" : "";
            });
            String name = "Artifacts".equals(category) ? "" :
                    STABLE_NAMES.containsKey(id) ? STABLE_NAMES.get(id)
                    : CHEST_DROP_NAMES.containsKey(id) ? CHEST_DROP_NAMES.get(id)
                    : shortName(category, english);
            String type = resourceType(id, category, name, usage.getOrDefault(id, Set.of()));
            String key = name.isEmpty() || type == null ? null :
                    canonicalResource(type, name).toString();
            ids.put(id, new Info(id, english, key, category));
            if (key != null) putUnique(names, collisions, key, id);
            if (english != null && !english.isBlank()) {
                putUnique(names, collisions, english, id);
            }
        }
        this.byId = Map.copyOf(ids);
        this.byName = Map.copyOf(names);
        this.ambiguous = Set.copyOf(collisions);
        this.usageById = Map.copyOf(usage);
    }

    public static ItemIdRegistry fromGameResources() {
        var englishNames = new LinkedHashMap<Integer, String>();
        var categories = new LinkedHashMap<Integer, String>();
        for (var item : GameData.getItemDataMap().values()) {
            var strings = Language.getTextMapKey(item.getNameTextMapHash());
            String english = strings == null ? null : strings.get(0);
            if (english != null && (english.isBlank() || english.startsWith("["))) {
                english = null;
            }
            englishNames.put(item.getId(), english);
            categories.put(item.getId(), categoryOf(item.getItemType(), item.getMaterialType()));
        }
        // Adventure EXP (102) is settled by Player.earnExp, not dropped as
        // a bag material. Its explicit native virtual ID can be absent from Excel.
        englishNames.putIfAbsent(102, "Adventure EXP");
        categories.putIfAbsent(102, "Currencies and Virtual Items");
        return new ItemIdRegistry(englishNames, categories, nativeDevelopmentUses(categories));
    }

    /** Determine how each material is consumed from the native advancement cost data. */
    private static Map<Integer, Set<Usage>> nativeDevelopmentUses(
            Map<Integer, String> categories) {
        var uses = new HashMap<Integer, java.util.EnumSet<Usage>>();
        categories.forEach((id, category) -> {
            Usage type = switch (category) {
                case "Character EXP Materials" -> Usage.AVATAR_LEVEL_UP;
                case "Weapon Enhancement Materials" -> Usage.WEAPON_LEVEL_UP;
                case "Artifact EXP Materials" -> Usage.ARTIFACT_LEVEL_UP;
                default -> null;
            };
            if (type != null) uses.computeIfAbsent(id, ignored ->
                    java.util.EnumSet.noneOf(Usage.class)).add(type);
        });
        // Mora is consumed across every advancement path, including level-up.
        uses.put(202, java.util.EnumSet.allOf(Usage.class));
        for (var data : GameData.getAvatarPromoteDataMap().values()) {
            for (var cost : data.getCostItems()) {
                uses.computeIfAbsent(cost.getId(), ignored ->
                        java.util.EnumSet.noneOf(Usage.class)).add(Usage.AVATAR_ASCENSION);
            }
        }
        for (var data : GameData.getWeaponPromoteDataMap().values()) {
            for (var cost : data.getCostItems()) {
                uses.computeIfAbsent(cost.getId(), ignored ->
                        java.util.EnumSet.noneOf(Usage.class)).add(Usage.WEAPON_ASCENSION);
            }
        }
        for (var data : GameData.getProudSkillDataMap().values()) {
            if (data.getCostItems() == null) continue;
            for (var cost : data.getCostItems()) {
                uses.computeIfAbsent(cost.getId(), ignored ->
                        java.util.EnumSet.noneOf(Usage.class)).add(Usage.TALENT_LEVEL_UP);
            }
        }
        var result = new HashMap<Integer, Set<Usage>>();
        uses.forEach((id, domains) -> result.put(id, Set.copyOf(domains)));
        return Map.copyOf(result);
    }

    public Set<Usage> usesOf(int id) {
        return usageById.getOrDefault(id, Set.of());
    }

    /** Named resources consumed by multiple advancement systems. */
    public Map<String, Set<Usage>> sharedDevelopmentMaterials() {
        var result = new LinkedHashMap<String, Set<Usage>>();
        usageById.forEach((id, domains) -> {
            Info item = byId.get(id);
            if (domains.size() > 1 && item != null && item.key() != null) {
                result.put(item.key(), domains);
            }
        });
        return Map.copyOf(result);
    }

    /** Short forms are scoped to their exact native material categories. */
    static String shortName(String category, String english) {
        String name = slug(english);
        if ("Artifact EXP Materials".equals(category)) {
            if ("sanctifying_unction".equals(name)) return "unction";
            if ("sanctifying_essence".equals(name)) return "essence";
        }
        return name;
    }

    /**
     * Map native resource identity, not a vague catch-all "item" category.
     * Virtual rewards are explicit because these IDs affect different player properties.
     * A resource with unknown native classification has no guessed semantic ID.
     */
    static String resourceType(int id, String category, String name) {
        return resourceType(id, category, name, Set.of());
    }

    private static String resourceType(int id, String category, String name, Set<Usage> uses) {
        return switch (id) {
            case 101, 102, 105, 121 -> "experience";
            case 106, 107, 201, 202, 203, 204, 301, 302, 303, 304, 305, 306,
                    307, 308, 101692 -> "currency";
            default -> {
                if (name.startsWith("recipe_") && name.length() > "recipe_".length()) yield "recipe";
                if (MONSTER_DROPS.contains(name)) yield "monster_drop";
                if (TALENT_BOOK.matcher(name).matches()) yield "talent_up";
                if (uses.size() > 1) yield "material";
                if (ASCENSION_GEM.matcher(name).matches()
                        || AVATAR_ASCENSION_ONLY.contains(name)
                        || uses.contains(Usage.AVATAR_ASCENSION)) yield "avatar_ascension";
                if (WEAPON_BILLET.matcher(name).matches()) yield "weapon_crafting";
                if (TALENT_LEVEL_UP_ONLY.contains(name)
                        || uses.contains(Usage.TALENT_LEVEL_UP)) yield "talent_up";
                if (uses.contains(Usage.WEAPON_ASCENSION)) yield "weapon_ascension";
                yield switch (category) {
                case "Weapons" -> "weapon";
                case "Food" -> "food";
                case "Furnishings" -> "furnishing";
                case "Character EXP Materials" -> "avatar_up";
                case "Weapon Enhancement Materials" -> "weapon_up";
                case "Artifact EXP Materials" -> "artifact_up";
                case "Artifacts", "Other Items", "Currencies and Virtual Items", "" ->
                        CHEST_DROP_NAMES.containsKey(id) || STABLE_NAMES.containsKey(id)
                                ? "material" : null;
                case "Materials", "Character Development Items",
                        "Quest Items", "Furnishing Blueprints",
                        "Wood", "Bait", "Fishing Rods", "Wind Gliders", "Namecards",
                        "Outfits", "Seeds" -> "material";
                case "Talent Level-Up Materials" -> "talent_up";
                default -> throw new IllegalArgumentException(
                        "Unknown native resource category for " + id + ": " + category);
                };
            }
        };
    }

    private static ResourceId canonicalResource(String category, String name) {
        if ("talent_up".equals(category)) {
            var tier = TALENT_BOOK.matcher(name);
            if (tier.matches()) {
                String grade = switch (tier.group(1)) {
                    case "teachings_of" -> "teachings";
                    case "guide_to" -> "guide";
                    case "philosophies_of" -> "philosophies";
                    default -> throw new IllegalStateException("Unknown talent book grade");
                };
                return ResourceId.of("genshin", category, tier.group(2), grade);
            }
        }
        if ("recipe".equals(category)) {
            return ResourceId.of("genshin", "recipe", name.substring("recipe_".length()), "item");
        }
        if ("avatar_ascension".equals(category) && ASCENSION_GEM.matcher(name).matches()) {
            int lastSeparator = name.lastIndexOf('_');
            return ResourceId.of("genshin", category,
                    name.substring(0, lastSeparator), name.substring(lastSeparator + 1));
        }
        if (name.startsWith("sigil.") && "currency".equals(category)) {
            return ResourceId.parse("genshin.currency." + name);
        }
        return ResourceId.of("genshin", category, name);
    }

    public static boolean isInventoryResourceType(String type) {
        return INVENTORY_RESOURCE_TYPES.contains(type);
    }

    /** Recipes can own both cooking definitions and inventory items. Only the
     * explicit .item child denotes an inventory recipe item. */
    public static boolean isInventoryResourcePath(ResourceId resource) {
        return resource.size() >= 3
                && (isInventoryResourceType(resource.segment(1))
                    || ("recipe".equals(resource.segment(1))
                        && resource.size() == 4
                        && "item".equals(resource.segment(3))));
    }

    /** Artifacts use the same dotted resource convention as other game items. */
    public static boolean isArtifactReference(String reference) {
        return reference != null && reference.matches(
                "genshin\\.artifact\\.[a-z][a-z0-9_]*");
    }

    public int resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Missing item reference");
        }
        String input = reference.trim();
        if (input.equalsIgnoreCase("genshin.currency.sigil")) {
            return REGIONAL_SIGIL;
        }
        if (input.matches("[0-9]+")) {
            try {
                int id = Integer.parseInt(input);
                if (byId.containsKey(id)) return id;
            } catch (NumberFormatException ignored) {
                // Invalid numeric item IDs are rejected below.
            }
            throw new IllegalArgumentException("Unknown item ID: " + input);
        }

        ResourceId qualified = null;
        if (input.matches("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\..+")) {
            qualified = ResourceAliases.GENSHIN.resolve(ResourceId.parse(input));
            if (qualified.size() < 3 || !qualified.segment(0).equals("genshin")
                    || !isInventoryResourcePath(qualified)) {
                throw new IllegalArgumentException("Unsupported inventory resource: " + input);
            }
        }

        String name = qualified == null ? input.toLowerCase(Locale.ROOT)
                : qualified.toString();
        if (ambiguous.contains(name)) {
            throw new IllegalArgumentException("Ambiguous item name: " + input);
        }
        Integer id = byName.get(name);
        if (id == null) throw new IllegalArgumentException("Unknown item: " + input);
        if (qualified != null && !name.equalsIgnoreCase(byId.get(id).key())) {
            throw new IllegalArgumentException("Wrong resource type for item: " + input
                    + " (expected " + byId.get(id).key() + ")");
        }
        return id;
    }

    /** Stable item identities for the archived historical chest rewards. */
    public static Map<Integer, String> chestDropNames() {
        return CHEST_DROP_NAMES;
    }

    public Info describe(int id) {
        return byId.get(id);
    }

    public Map<Integer, Info> all() {
        return byId;
    }

    private static void putUnique(Map<String, Integer> names, Set<String> collisions,
            String reference, int id) {
        String key = reference.toLowerCase(Locale.ROOT);
        Integer old = names.putIfAbsent(key, id);
        if (old != null && old != id) collisions.add(key);
    }

    /** Provisional English TextMap spelling, not a verified cross-version ID. */
    static String slug(String english) {
        if (english == null) return "";
        String ascii = english.replace("'", "").replace("’", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (ascii.isEmpty() || !ascii.matches("[a-z][a-z0-9_]*")) return "";
        return ascii;
    }

    public static String categoryOf(ItemType type, MaterialType material) {
        if (type == null) return "";
        switch (type) {
            case ITEM_WEAPON, ITEM_TPS_WEAPON -> { return "Weapons"; }
            case ITEM_RELIQUARY -> { return "Artifacts"; }
            case ITEM_FURNITURE -> { return "Furnishings"; }
            case ITEM_VIRTUAL -> { return "Currencies and Virtual Items"; }
            case ITEM_MATERIAL -> {}
            default -> { return "Other Items"; }
        }
        if (material == null) return "Materials";
        return switch (material) {
            case MATERIAL_EXP_FRUIT -> "Character EXP Materials";
            case MATERIAL_WEAPON_EXP_STONE -> "Weapon Enhancement Materials";
            case MATERIAL_RELIQUARY_MATERIAL -> "Artifact EXP Materials";
            case MATERIAL_AVATAR_MATERIAL -> "Character Development Items";
            case MATERIAL_TALENT -> "Talent Level-Up Materials";
            case MATERIAL_FOOD, MATERIAL_SPICE_FOOD -> "Food";
            case MATERIAL_QUEST -> "Quest Items";
            case MATERIAL_FURNITURE_FORMULA, MATERIAL_FURNITURE_SUITE_FORMULA -> "Furnishing Blueprints";
            case MATERIAL_WOOD -> "Wood";
            case MATERIAL_FISH_BAIT -> "Bait";
            case MATERIAL_FISH_ROD -> "Fishing Rods";
            case MATERIAL_FLYCLOAK -> "Wind Gliders";
            case MATERIAL_NAMECARD -> "Namecards";
            case MATERIAL_COSTUME -> "Outfits";
            case MATERIAL_HOME_SEED -> "Seeds";
            default -> "Materials";
        };
    }
}
