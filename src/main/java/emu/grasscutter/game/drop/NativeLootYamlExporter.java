package emu.grasscutter.game.drop;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.ResourceId;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.game.inventory.ItemType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Historical source-table exporter. Callers must supply human-readable loot
 * table names and qualified item resource names. No numeric YAML identifiers.
 */
public final class NativeLootYamlExporter {
    public record NativeEntry(int id, String countRange, int weight) {}
    public record NativeTable(int randomType, List<NativeEntry> drops) {}
    private NativeLootYamlExporter() {}

    public record ArtifactProperties(String slot, int rarity, int substats) {}

    private static ArtifactProperties propertiesOf(int itemId) {
        var item = GameData.getItemDataMap().get(itemId);
        if (item == null || item.getItemType() != ItemType.ITEM_RELIQUARY) {
            throw new IllegalArgumentException("Missing native artifact item: " + itemId);
        }
        if (item.getAppendPropDepotId() != item.getRankLevel() * 100 + 1) {
            throw new IllegalArgumentException(
                    "Special native artifact cannot be represented by ordinary properties: " + itemId);
        }
        return new ArtifactProperties(ArtifactResolver.slotName(item.getEquipType().getValue()),
                item.getRankLevel(), item.getAppendPropNum());
    }

    public static Map<Integer, NativeTable> fromResources(Map<Integer, DropTableData> resources) {
        var result = new HashMap<Integer, NativeTable>();
        resources.forEach((id, table) -> {
            var drops = new ArrayList<NativeEntry>();
            if (table.getDropVec() != null) {
                table.getDropVec().forEach(drop ->
                        drops.add(new NativeEntry(drop.getId(), drop.getCountRange(), drop.getWeight())));
            }
            result.put(id, new NativeTable(table.getRandomType(), List.copyOf(drops)));
        });
        return Map.copyOf(result);
    }

    /**
     * Names are provided by a verified source mapping. Unknown table or item
     * identities fail the export rather than producing synthetic ID aliases.
     */
    public static String export(
            Map<Integer, NativeTable> tables,
            Collection<Integer> roots,
            Map<Integer, String> tableNames,
            IntFunction<String> itemNames) {
        return export(tables, roots, tableNames, itemNames,
                NativeLootYamlExporter::propertiesOf);
    }

    public static String export(
            Map<Integer, NativeTable> tables,
            Collection<Integer> roots,
            Map<Integer, String> tableNames,
            IntFunction<String> itemNames,
            IntFunction<ArtifactProperties> artifactProperties) {
        if (tableNames == null || itemNames == null || artifactProperties == null) {
            throw new IllegalArgumentException("Missing resource name mappings");
        }
        var ordered = new ArrayList<Integer>();
        var visited = new HashSet<Integer>();
        var active = new HashSet<Integer>();
        for (int root : roots) visit(root, tables, visited, active, ordered);
        var yaml = new StringBuilder();
        var used = new HashSet<String>();

        for (int id : ordered) {
            String tableName = tableNames.get(id);
            if (tableName == null) throw new IllegalArgumentException("Unidentified loot table");
            ResourceId.of("astaps", "loot", tableName);
            if (!used.add(tableName)) throw new IllegalArgumentException("Duplicate loot name: " + tableName);

            var table = tables.get(id);
            if (table.randomType != 0 && table.randomType != 1) {
                throw new IllegalArgumentException("Unsupported native random type");
            }
            var valid = table.drops.stream()
                    .filter(entry -> entry.id > 0 && entry.weight > 0).toList();
            yaml.append(tableName).append(": &").append(tableName);
            if (valid.isEmpty()) {
                yaml.append(" []\n\n");
                continue;
            }

            int divisor = 0;
            if (table.randomType == 0) {
                for (NativeEntry entry : valid) divisor = gcd(divisor, entry.weight);
            }
            yaml.append('\n');
            for (NativeEntry entry : valid) {
                String count = entry.countRange;
                if (count == null) throw new IllegalArgumentException("Missing native count");
                count = normalizeNativeCount(count);
                String target;
                if (tables.containsKey(entry.id)) {
                    target = tableNames.get(entry.id);
                    if (target == null) throw new IllegalArgumentException("Unidentified nested loot table");
                    ResourceId.of("astaps", "loot", target);
                    target = "*" + target;
                } else {
                    target = itemNames.apply(entry.id);
                    if (target == null) {
                        var nativeItem = GameData.getItemDataMap().get(entry.id);
                        if (nativeItem != null && nativeItem.getItemType() == ItemType.ITEM_RELIQUARY) {
                            String set = ArtifactResolver.setName(nativeItem.getSetId());
                            if (set != null) target = "genshin.artifact." + set;
                        }
                    }
                    if (target == null) throw new IllegalArgumentException("Unknown item in chest source");
                    ResourceId resource = ResourceId.parse(target);
                    if (resource.size() < 3 || !"genshin".equals(resource.segment(0))
                            || !(ItemIdRegistry.isInventoryResourcePath(resource)
                                    || "artifact".equals(resource.segment(1)))
                            || resource.tail(2).matches("id_[0-9]+")) {
                        throw new IllegalArgumentException("Expected semantic genshin resource name");
                    }
                    if ("artifact".equals(resource.segment(1))) {
                        if (resource.size() != 3) {
                            throw new IllegalArgumentException("Artifact name must identify a set only");
                        }
                        ArtifactProperties p = artifactProperties.apply(entry.id);
                        if (p == null || p.rarity() < 1 || p.rarity() > 5
                                || p.substats() < 0 || p.substats() > 4) {
                            throw new IllegalArgumentException("Invalid native artifact properties");
                        }
                        String slot = p.slot();
                        if (!java.util.Set.of("flower", "plume", "sands", "goblet", "circlet")
                                .contains(slot)) {
                            throw new IllegalArgumentException("Invalid native artifact slot");
                        }
                        target = "type: Artifact, set: " + resource.tail(2)
                                + ", properties: {slot: " + slot
                                + ", rarity: " + p.rarity() + ", substats: " + p.substats() + "}";
                    }
                }

                if (table.randomType == 0) {
                    appendEntry(yaml, "  ", target, count,
                            entry.weight / divisor);
                } else if (entry.weight == 10000) {
                    appendEntry(yaml, "  ", target, count, null);
                } else {
                    if (entry.weight > 10000) {
                        throw new IllegalArgumentException("Invalid independent roll probability");
                    }
                    // Preserve all native basis-point chances without decimal counts.
                    int chanceDivisor = gcd(entry.weight, 10000 - entry.weight);
                    yaml.append("  - reward:\n");
                    appendEntry(yaml, "      ", "null", "1",
                            (10000 - entry.weight) / chanceDivisor);
                    appendEntry(yaml, "      ", target, count,
                            entry.weight / chanceDivisor);
                }
            }
            yaml.append('\n');
        }
        return yaml.toString();
    }

    /**
     * Translate native source decimals at the data-import boundary.
     * LootCSS and YAML quantities use integers only; fractional source
     * expectations become exact weighted integer outcomes.
     */
    static String normalizeNativeCount(String raw) {
        if (raw == null) throw new IllegalArgumentException("Missing native count");
        String value = raw.trim();
        if (value.matches("[0-9]+\\.[0-9]+")) {
            BigDecimal decimal = new BigDecimal(value);
            BigInteger lower = decimal.toBigInteger();
            BigInteger fractional = decimal.remainder(BigDecimal.ONE)
                    .movePointRight(decimal.scale()).toBigIntegerExact();
            if (fractional.signum() == 0) {
                value = lower.toString();
            } else {
                BigInteger scale = BigInteger.TEN.pow(decimal.scale());
                BigInteger remaining = scale.subtract(fractional);
                BigInteger gcd = remaining.gcd(fractional);
                value = lower + ":" + remaining.divide(gcd)
                        + ", " + lower.add(BigInteger.ONE) + ":" + fractional.divide(gcd);
            }
        }
        LootCount.parse(value);
        return value;
    }

    private static void appendEntry(StringBuilder yaml, String indent,
            String target, String count, Integer weight) {
        yaml.append(indent).append("- {");
        if (target.startsWith("type: ")) yaml.append(target);
        else yaml.append("reward: ").append(target);
        if (!"1".equals(count)) {
            yaml.append(", count: \"").append(count).append('"');
        }
        if (weight != null) yaml.append(", weight: ").append(weight);
        yaml.append("}\n");
    }

    private static int gcd(int x, int y) {
        while (y != 0) {
            int remainder = x % y;
            x = y;
            y = remainder;
        }
        return x;
    }

    private static void visit(int id, Map<Integer, NativeTable> tables,
            Set<Integer> visited, Set<Integer> active, List<Integer> ordered) {
        if (!tables.containsKey(id)) throw new IllegalArgumentException("Unknown source root");
        if (visited.contains(id)) return;
        if (!active.add(id)) throw new IllegalArgumentException("Cyclic source table");
        for (NativeEntry entry : tables.get(id).drops) {
            if (entry.id > 0 && entry.weight > 0 && tables.containsKey(entry.id)) {
                visit(entry.id, tables, visited, active, ordered);
            }
        }
        active.remove(id);
        visited.add(id);
        ordered.add(id);
    }
}
