package emu.grasscutter.game.drop;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceId;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.ItemType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Resolves a set identity plus independent attributes to an original reliquary item ID.
 * Each roll selects slot, rarity, and then the initial append-prop count.
 */
public final class ArtifactResolver {
    private static final Map<Integer, String> SET_NAMES = Map.ofEntries(
            Map.entry(10005, "berserker"),
            Map.entry(10007, "instructor"),
            Map.entry(10009, "the_exile"),
            Map.entry(10010, "adventurer"),
            Map.entry(10011, "lucky_dog"),
            Map.entry(10013, "traveling_doctor"),
            Map.entry(15001, "gladiators_finale"),
            Map.entry(15003, "wanderers_troupe"),
            Map.entry(15009, "prayers_for_illumination"),
            Map.entry(15010, "prayers_for_destiny"),
            Map.entry(15011, "prayers_for_wisdom"),
            Map.entry(15013, "prayers_to_springtime"));

    public record Variant(int id, String set, int slot, int rarity, int substats) {}
    public record Selector(List<Variant> variants, LootCount.Spec slot,
                           LootCount.Spec rarity, LootCount.Spec substats,
                           WeightedChoice<List<Variant>> sets) {
        public Selector(List<Variant> variants, LootCount.Spec slot,
                        LootCount.Spec rarity, LootCount.Spec substats) {
            this(variants, slot, rarity, substats, null);
        }

        /** Hold the selected set fixed while rolling several artifact copies. */
        public Selector bindSet(RandomGenerator random) {
            return sets == null ? this
                    : new Selector(sets.sample(random), slot, rarity, substats);
        }

        public Variant selectVariant(RandomGenerator random) {
            // The set is chosen before its native slot/rarity/substat variants.
            // A circlet-only set therefore never offers unavailable slots.
            List<Variant> available = sets == null ? variants : sets.sample(random);
            int selectedSlot = slot == null
                    ? randomDistinct(available, Variant::slot, random)
                    : slot.sample(random);
            List<Variant> matchingSlot = available.stream()
                    .filter(v -> v.slot == selectedSlot).toList();
            if (matchingSlot.isEmpty()) {
                throw new IllegalArgumentException("Artifact slot not available: " + selectedSlot);
            }
            int selectedRarity = rarity == null
                    ? randomDistinct(matchingSlot, Variant::rarity, random)
                    : rarity.sample(random);
            List<Variant> matchingRarity = matchingSlot.stream()
                    .filter(v -> v.rarity == selectedRarity).toList();
            if (matchingRarity.isEmpty()) {
                throw new IllegalArgumentException("Artifact rarity not available: " + selectedRarity);
            }
            int selectedSubstats;
            if (substats != null) {
                selectedSubstats = substats.sample(random);
            } else {
                // Standard initial-substat ranges. Special or unusual native rows are
                // only selected when explicitly requested.
                int min = switch (selectedRarity) {
                    case 1, 2 -> 0;
                    case 3 -> 1;
                    case 4 -> 2;
                    case 5 -> 3;
                    default -> throw new IllegalArgumentException("Invalid artifact rarity");
                };
                int max = selectedRarity == 1 ? 0 : min + 1;
                List<Integer> valid = matchingRarity.stream().map(Variant::substats)
                        .distinct().filter(n -> n >= min && n <= max).sorted().toList();
                if (valid.isEmpty()) {
                    throw new IllegalArgumentException("No ordinary substat variant at rarity "
                            + selectedRarity);
                }
                selectedSubstats = valid.get(random.nextInt(valid.size()));
            }
            return matchingRarity.stream()
                    .filter(v -> v.substats == selectedSubstats)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No native artifact variant for slot " + selectedSlot
                            + ", rarity " + selectedRarity + ", substats " + selectedSubstats))
                    ;
        }

        public int select(RandomGenerator random) {
            return selectVariant(random).id();
        }

        public String selectKey(RandomGenerator random) {
            return variantKey(selectVariant(random));
        }
    }

    public static String variantKey(Variant variant) {
        return "genshin.artifact." + variant.set() + "." + slotName(variant.slot())
                + "." + variant.rarity() + "." + variant.substats();
    }

    private interface NumberOf {
        int get(Variant variant);
    }

    private static int randomDistinct(List<Variant> variants, NumberOf field,
                                      RandomGenerator random) {
        var choices = variants.stream().map(field::get).distinct().sorted().toList();
        return choices.get(random.nextInt(choices.size()));
    }

    private final Map<String, List<Variant>> bySet;

    public ArtifactResolver(List<Variant> variants) {
        var sets = new LinkedHashMap<String, List<Variant>>();
        for (Variant v : variants) {
            if (v.id <= 0 || v.slot < 1 || v.slot > 5 || v.rarity < 1 || v.rarity > 5
                    || v.substats < 0 || v.substats > 4) {
                throw new IllegalArgumentException("Invalid native artifact variant: " + v);
            }
            sets.computeIfAbsent(v.set, ignored -> new ArrayList<>()).add(v);
        }
        sets.replaceAll((name, list) -> List.copyOf(list.stream()
                .sorted(Comparator.comparingInt(Variant::id)).toList()));
        for (var entry : sets.entrySet()) {
            var seen = new java.util.HashSet<String>();
            for (Variant v : entry.getValue()) {
                String key = v.slot + ":" + v.rarity + ":" + v.substats;
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("Ambiguous native artifact variant "
                            + entry.getKey() + " " + key);
                }
            }
        }
        this.bySet = Map.copyOf(sets);
    }

    /**
     * Derive the initial Registry entries from native variants when needed.
     * ArtifactResolver itself retains no parallel resource-name-to-ID dictionary.
     */
    public Map<String, Integer> resourceIds() {
        var result = new LinkedHashMap<String, Integer>();
        for (var variants : bySet.values()) {
            for (Variant variant : variants) {
                String key = variantKey(variant);
                Integer previous = result.putIfAbsent(key, variant.id());
                if (previous != null && previous != variant.id()) {
                    throw new IllegalArgumentException("Ambiguous artifact resource: " + key);
                }
            }
        }
        return Map.copyOf(result);
    }

    /** Resolve the source-backed set identity, not an arbitrary item variant ID. */
    public static int nativeSetId(String name) {
        for (var entry : SET_NAMES.entrySet()) {
            if (entry.getValue().equals(name)) return entry.getKey();
        }
        throw new IllegalArgumentException("Unknown native artifact set: " + name);
    }

    /** Stable set identity for the first native chest-artifact families. */
    public static String setName(int nativeSetId) {
        return SET_NAMES.get(nativeSetId);
    }

    public static ArtifactResolver fromGameResources() {
        var variants = new ArrayList<Variant>();
        for (ItemData item : GameData.getItemDataMap().values()) {
            if (item.getItemType() != ItemType.ITEM_RELIQUARY) continue;
            String set = SET_NAMES.get(item.getSetId());
            if (set == null) continue;
            int slot = item.getEquipType().getValue();
            // Special affix depots may duplicate ordinary set/slot/rarity/count combinations.
            if (item.getAppendPropDepotId() != item.getRankLevel() * 100 + 1) continue;
            if (slot < 1 || slot > 5) continue;
            variants.add(new Variant(item.getId(), set, slot,
                    item.getRankLevel(), item.getAppendPropNum()));
        }
        return new ArtifactResolver(variants);
    }

    public static String slotName(int slot) {
        return switch (slot) {
            case 1 -> "flower";
            case 2 -> "plume";
            case 3 -> "sands";
            case 4 -> "goblet";
            case 5 -> "circlet";
            default -> throw new IllegalArgumentException("Unknown artifact slot: " + slot);
        };
    }

    private static LootCount.Spec property(String field, Object raw) {
        if (raw == null) return null;
        if ("slot".equals(field)) {
            // Keep inclusive numeric ranges; individual slot names and weighted
            // slot choices follow the same scalar/choice compiler as other fields.
            if (raw instanceof String text && text.matches("[0-9]+[~;][0-9]+")) {
                return LootCount.parse(text);
            }
            var choice = WeightedChoice.compile(raw, "artifact.slot", value -> {
                String name = value.toString();
                return switch (name) {
                    case "flower" -> 1;
                    case "plume" -> 2;
                    case "sands" -> 3;
                    case "goblet" -> 4;
                    case "circlet" -> 5;
                    default -> {
                        if (!name.matches("[1-5]")) {
                            throw new IllegalArgumentException("Unknown artifact slot: " + name);
                        }
                        yield Integer.parseInt(name);
                    }
                };
            });
            return choice::sample;
        }
        return LootCount.parse(raw);
    }

    /** A Loot-domain Artifact selector chooses native pieces from a named set. */
    public Selector compileType(Object rawProperties) {
        Map<?, ?> props = rawProperties instanceof Map<?, ?> m ? m : throwInvalidProperties();
        for (Object key : props.keySet()) {
            if (!"set".equals(key) && !"slot".equals(key)
                    && !"rarity".equals(key) && !"substats".equals(key)) {
                throw new IllegalArgumentException("Unknown artifact property: " + key);
            }
        }
        if (!props.containsKey("set")) {
            throw new IllegalArgumentException("Artifact requires a set");
        }
        WeightedChoice<List<Variant>> sets =
                WeightedChoice.compile(props.get("set"), "artifact.set", candidate -> {
                    String set = candidate.toString();
                    List<Variant> nativeVariants = bySet.get(set);
                    if (nativeVariants == null || nativeVariants.isEmpty()) {
                        throw new IllegalArgumentException("Unknown native artifact set: " + set);
                    }
                    return nativeVariants;
                });
        return new Selector(List.of(), property("slot", props.get("slot")),
                property("rarity", props.get("rarity")),
                property("substats", props.get("substats")), sets);
    }

    private static Map<?, ?> throwInvalidProperties() {
        throw new IllegalArgumentException("Artifact properties must be a YAML mapping");
    }
}
