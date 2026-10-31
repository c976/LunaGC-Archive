package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.ResourceAliases;
import emu.grasscutter.game.drop.ArtifactResolver;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Regression checks for the staged native world Boss migration. */
final class BundledBossRewardsTest {
    @Test
    void worldBossTemplatesCoverAllMappedIdentitiesWithoutInventingWeeklyDrops() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        String css = RewardConfiguration.readBundledLootCss();
        css = css.replace("\\", "");
        assertTrue(yaml.contains("world_gem_two<COUNT, ...ELEMENTS>:"));
        assertTrue(yaml.contains("type: Gem"));
        assertTrue(yaml.contains("world_gem_four<COUNT, ...ELEMENTS>:"));
        assertTrue(yaml.contains("world_gem_one<...ELEMENTS>:"));
        assertTrue(css.contains("astaps.loot.world_gem_one<$element[0],$element[1]>:"));
        for (int rarity = 1; rarity <= 4; rarity++) {
            String template = "world_artifact_" + rarity + "star<...SETS>:";
            assertTrue(yaml.contains(template), template);
            assertFalse(yaml.contains("world_artifact_" + rarity + "star_1<"));
            assertFalse(css.contains("world_artifact_" + rarity + "star_1<"));
        }
        assertTrue(yaml.contains("world_artifact_5star:"));
        assertTrue(yaml.contains("world_artifact_5star_plume_goblet<PLUME_SUBSTATS, GOBLET_SUBSTATS>:"));
        assertTrue(yaml.contains("world_artifact_5star_goblet<SUBSTATS>:"));
        assertTrue(css.contains("astaps.loot.world_"));
        assertFalse(css.contains("boss[type=\"weekly\"]"));
        String identities = css.substring(css.indexOf("/* 身份绑定"),
                css.indexOf("/* 通用等级奖励"));
        assertEquals(48, identities.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\"")).count());
        assertTrue(css.contains("astaps.loot.world_artifact_5star:"));
        assertFalse(css.contains("world_artifact_5star<"));
        assertTrue(css.contains("world_artifact_5star_plume_goblet<0,0>"));
        assertTrue(css.contains("world_artifact_5star_goblet<1>"));
        assertTrue(css.contains("world_artifact_5star_goblet<0>"));
        assertFalse(css.contains("world_artifact_5star_2<"));
        assertFalse(css.contains("world_artifact_5star_special<"));
        assertFalse(css.contains("world_artifact_5star_piece<"));
        assertFalse(css.contains("world_artifact_5star_plume_goblet<instructor,"));
    }

    @Test
    void bundledWorldAndWeeklyRulesLoadAndNativeFallbackIsPreserved() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        String originalCss = RewardConfiguration.readBundledLootCss();
        // Keep fixture-name extraction independent from the stylesheet parser.
        String stylesheet = originalCss.replace("\\", "");
        var ids = new HashMap<String, Integer>();
        var nextId = new AtomicInteger(3000000);
        var artifacts = new ArrayList<ArtifactResolver.Variant>();
        var nextArtifactId = new AtomicInteger(4000000);
        // Collect native sets from the artifact template arguments and the
        // fixed-set YAML entries. Prayers sets contain circlets only.
        var sets = new java.util.LinkedHashSet<String>();
        Pattern.compile("world_artifact_[1-4]star<([^>]+)>")
                .matcher(stylesheet).results()
                .flatMap(match -> java.util.Arrays.stream(match.group(1).split(",")))
                .map(String::trim)
                .forEach(sets::add);
        Pattern.compile("(?m)^\\s*set:\\s*(.+)$")
                .matcher(yaml).results()
                .flatMap(match -> java.util.Arrays.stream(match.group(1)
                        .replace(Character.toString(34), "").split(",")))
                .map(String::trim)
                .map(value -> value.split(":", 2)[0])
                .filter(name -> name.matches("[a-z0-9_]+"))
                .forEach(sets::add);
        sets.forEach(name -> {
            for (int rarity = 1; rarity <= 5; rarity++) {
                for (int slot = 1; slot <= 5; slot++) {
                    if (name.startsWith("prayers_") && slot != 5) continue;
                    for (int substats = 0; substats <= 4; substats++) {
                        artifacts.add(new ArtifactResolver.Variant(
                                nextArtifactId.incrementAndGet(),
                                name, slot, rarity, substats));
                    }
                }
            }
        });
        var artifactResolver = new ArtifactResolver(artifacts);
        var artifactKeys = artifactResolver.resourceIds();
        var loot = YamlLootTables.parse(yaml,
                id -> id.startsWith("genshin.artifact.")
                        ? artifactKeys.getOrDefault(id, -2)
                        : ids.computeIfAbsent(ResourceAliases.GENSHIN.resolve(id).toString(),
                                ignored -> nextId.incrementAndGet()),
                artifactResolver);
        var known = new HashMap<Integer, String>(ItemIdRegistry.chestDropNames());
        known.putAll(Map.ofEntries(
                Map.entry(101, "Character EXP"),
                Map.entry(102, "Adventure EXP"),
                Map.entry(105, "Companionship EXP"),
                Map.entry(201, "Primogem"),
                Map.entry(202, "Mora"),
                Map.entry(104001, "Wanderer's Advice"),
                Map.entry(104002, "Adventurer's Experience"),
                Map.entry(104003, "Hero's Wit"),
                Map.entry(104011, "Enhancement Ore"),
                Map.entry(104012, "Fine Enhancement Ore"),
                Map.entry(104013, "Mystic Enhancement Ore"),
                Map.entry(303, "Dendro Sigil"),
                Map.entry(304, "Electro Sigil"),
                Map.entry(305, "Anemo Sigil"),
                Map.entry(307, "Geo Sigil")));
        var categories = new HashMap<Integer, String>();
        Pattern.compile("genshin\\.(material|avatar_ascension|weapon_ascension|talent_up|weapon_crafting)\\.([a-z0-9_.]+)\\s*:")
                .matcher(stylesheet).results()
                .map(match -> Map.entry(match.group(1), match.group(2)))
                .distinct()
                .forEach(entry -> {
                    String category = entry.getKey();
                    String name = entry.getValue();
                    String canonical = ResourceAliases.GENSHIN.resolve(
                            "genshin." + category + "." + name).toString();
                    int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                    String displayName = category.equals("avatar_ascension")
                            ? canonical.substring("genshin.avatar_ascension.".length())
                                    .replace('.', ' ').replace('_', ' ')
                            : name.replace('_', ' ');
                    known.put(id, displayName);
                    categories.put(id, switch (category) {
                        case "avatar_ascension", "weapon_crafting" -> "Character Development Items";
                        case "talent_up" -> "Talent Level-Up Materials";
                        default -> "Materials";
                    });
                });
        Pattern.compile("--material: ([a-z0-9_]+);")
                .matcher(stylesheet).results().map(match -> match.group(1)).distinct()
                .forEach(name -> {
                    String canonical = "genshin.avatar_ascension." + name;
                    int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                    known.put(id, name.replace('_', ' '));
                    categories.put(id, "Character Development Items");
                });
        // Some Bosses expose several material variants in an array binding.
        Pattern.compile("--material:\\s*\\[([^]]+)\\]")
                .matcher(stylesheet).results()
                .flatMap(match -> java.util.Arrays.stream(match.group(1).split(",")))
                .map(String::trim)
                .forEach(name -> {
                    String canonical = "genshin.avatar_ascension." + name;
                    int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                    known.put(id, name.replace('_', ' '));
                    categories.put(id, "Character Development Items");
                });
        // CSS uses elemental gem aliases; register all seven canonical gem families.
        for (String family : new String[]{"vayuda_turquoise", "prithiva_topaz",
                "vajrada_amethyst", "nagadus_emerald", "varunada_lazurite",
                "agnidus_agate", "shivada_jade"}) {
            for (String grade : new String[]{"sliver", "fragment", "chunk", "gemstone"}) {
                String canonical = "genshin.avatar_ascension." + family + "." + grade;
                int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                known.put(id, family.replace('_', ' ') + " " + grade);
                categories.put(id, "Character Development Items");
            }
        }
        var css = CssRewards.parse(originalCss, new ItemIdRegistry(known, categories),
                loot);
        var config = new RewardConfiguration(loot, css);
        verifyHistoricalWorldBossFixedRewards(css);
        String identityCss = stylesheet.substring(
                stylesheet.indexOf("/* 身份绑定"),
                stylesheet.indexOf("/* 通用等级奖励"));
        var bosses = identityCss.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\""))
                .map(line -> line.substring(
                        line.indexOf("[tag=\"") + 6, line.indexOf("\"] {")))
                .distinct().toList();
        assertEquals(48, bosses.size());
        // Compare CSS coverage to the historical native world-Boss roots.
        var nativeRoots = new java.util.HashMap<String, java.util.List<Integer>>();
        var source = com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(java.nio.file.Path.of("data/ChestDrop.json")))
                .getAsJsonArray();
        for (var row : source) {
            var entry = row.getAsJsonObject();
            if (!entry.has("sourceType") || entry.get("sourceType").getAsInt() != 34) continue;
            nativeRoots.computeIfAbsent(entry.get("index").getAsString(),
                    ignored -> new java.util.ArrayList<>())
                    .add(entry.get("minLevel").getAsInt());
        }
        assertEquals(48, nativeRoots.size(), "Native world-Boss identity count changed");
        for (var nativeBoss : nativeRoots.entrySet()) {
            var nativeLevels = nativeBoss.getValue();
            assertEquals(25, nativeLevels.size(), "Native rows: " + nativeBoss.getKey());
            assertEquals(25, nativeLevels.stream().distinct().count(), nativeBoss.getKey());
            assertEquals(1, nativeLevels.getFirst(), nativeBoss.getKey());
            assertEquals(120, nativeLevels.getLast(), nativeBoss.getKey());
            for (int level : nativeLevels) {
                assertNotNull(config.rollWorldBoss(nativeBoss.getKey(), level,
                        new java.util.Random(71)),
                        "CSS missed native Boss/level: " + nativeBoss.getKey() + "@" + level);
            }
        }

        for (var boss : bosses) {
            for (int level : new int[] {1, 5, 10, 15, 20, 25, 29}) {
                var attributes = Map.of(
                        "type", "world", "tag", boss, "level", level);
                var selection = css.resolveIds("boss", attributes);
                assertTrue(!selection.rangedItems().isEmpty()
                        || !selection.rangedLoot().isEmpty(), boss + "@" + level);
                assertTrue(selection.loot().isEmpty());
                assertNotNull(config.rollWorldBoss(boss, level, new java.util.Random(71)),
                        boss + "@" + level);
            }
            for (int level : new int[] {30, 35, 40, 45, 50, 55, 60, 65, 70, 75,
                    80, 85, 90, 93, 99, 100, 104}) {
                assertNotNull(config.rollWorldBoss(boss, level, new java.util.Random(71)),
                        "Unconfigured middle world Boss: " + boss + "@" + level);
            }
            // The last CSS level interval is open-ended (93~).
            assertNotNull(config.rollWorldBoss(boss, 105, new java.util.Random(71)),
                    "Lv.105+ retains the level-93-plus configured range: " + boss);
        }
        var pureMaterial = css.resolveIds("boss", Map.of(
                "type", "world", "tag", "precision_clockwork", "level", 1));
        assertEquals(1, pureMaterial.rangedItems().size());
        assertTrue(pureMaterial.loot().isEmpty());
        assertTrue(pureMaterial.rangedLoot().keySet().stream()
                .allMatch(name -> name.startsWith("astaps.loot.world_")));
        assertNotNull(config.rollWorldBoss("精密发条机关", 1, new java.util.Random(71)));

        var dual = css.resolveIds("boss", Map.of(
                "type", "world", "tag", "maguu_kenki", "level", 45));
        assertTrue(dual.rangedLoot().keySet().stream()
                .anyMatch(key -> key.startsWith("astaps.loot.world_")));
        var weekly = css.resolveIds("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", 90));
        assertTrue(weekly.loot().isEmpty());
        assertTrue(weekly.rangedLoot().isEmpty());
    }
    /**
     * Independently check all 1,200 source rows (48 identities x 25 tiers)
     * against the canonical 7.1 ChestDrop and DropTable source. The source
     * evaluator does not call the CSS engine or YAML reward code.
     *
     * Native artifact, gem and material distributions are checked separately;
     * these four fixed payouts are exact and must never drift.
     */
    private static void verifyHistoricalWorldBossFixedRewards(CssRewards css) throws Exception {
        var chestSource = java.nio.file.Path.of("data/ChestDrop.json");
        var tableSource = java.nio.file.Path.of("src/test/resources/native-snapshots/DropTableExcelConfigData.json");
        var tableMap = new HashMap<Integer, com.google.gson.JsonObject>();
        try (var reader = java.nio.file.Files.newBufferedReader(tableSource)) {
            for (var row : com.google.gson.JsonParser.parseReader(reader).getAsJsonArray()) {
                var table = row.getAsJsonObject();
                tableMap.put(table.get("id").getAsInt(), table);
            }
        }
        var cache = new HashMap<Integer, double[]>();
        int checked = 0;
        var seen = new java.util.HashSet<String>();
        try (var reader = java.nio.file.Files.newBufferedReader(chestSource)) {
            for (var raw : com.google.gson.JsonParser.parseReader(reader).getAsJsonArray()) {
                var entry = raw.getAsJsonObject();
                if (!entry.has("sourceType") || entry.get("sourceType").getAsInt() != 34) continue;
                String nativeTag = entry.get("index").getAsString();
                int level = entry.get("minLevel").getAsInt();
                String label = nativeTag + " level=" + level;
                assertTrue(seen.add(label), "Duplicate historical Boss reference: " + label);
                var selected = css.resolveIds("boss", Map.of(
                        "type", "world", "tag", RewardConfiguration.cssBossTag(nativeTag),
                        "level", level));
                double[] expected = expectedNativePayout(entry.get("dropId").getAsInt(),
                        tableMap, cache, new java.util.HashSet<>());
                int[] ids = {101, 102, 105, 202};
                for (int i = 0; i < ids.length; i++) {
                    int id = ids[i];
                    assertFalse(selected.rangedItems().containsKey(id),
                            "Native fixed payout became random: " + label + " item=" + id);
                    assertEquals(expected[i], selected.items().getOrDefault(id, 0).doubleValue(),
                            0.000001, "Source mismatch: " + label + " item=" + id);
                }
                checked++;
            }
        }
        assertEquals(1200, checked, "The inherited historical world Boss reference changed");
    }

    private static double[] expectedNativePayout(int id,
            Map<Integer, com.google.gson.JsonObject> tables,
            Map<Integer, double[]> memo, java.util.Set<Integer> active) {
        if (memo.containsKey(id)) return memo.get(id);
        var table = tables.get(id);
        double[] totals = new double[4];
        if (table == null) {
            int[] fixedIds = {101, 102, 105, 202};
            for (int i = 0; i < fixedIds.length; i++) {
                if (fixedIds[i] == id) totals[i] = 1;
            }
            return totals;
        }
        if (!active.add(id)) throw new IllegalStateException("Native drop table cycle: " + id);
        int mode = table.get("randomType").getAsInt();
        assertTrue(mode == 0 || mode == 1, "Unrecognized native randomType: " + id);
        var items = table.has("dropVec") && table.get("dropVec").isJsonArray()
                ? table.getAsJsonArray("dropVec") : new com.google.gson.JsonArray();
        double sum = 0;
        for (var value : items) {
            var item = value.getAsJsonObject();
            if (item.get("itemId").getAsInt() > 0 && item.get("weight").getAsInt() > 0) {
                sum += item.get("weight").getAsInt();
            }
        }
        for (var value : items) {
            var item = value.getAsJsonObject();
            int child = item.get("itemId").getAsInt();
            int weight = item.get("weight").getAsInt();
            if (child <= 0 || weight <= 0) continue;
            double probability = mode == 1 ? weight / 10000.0 : weight / sum;
            String quantity = item.get("countRange").getAsString();
            double expectedCount;
            if (quantity.contains(";")) {
                String[] parts = quantity.split(";", -1);
                assertEquals(2, parts.length, "Malformed source count: " + id);
                expectedCount = (Double.parseDouble(parts[0]) + Double.parseDouble(parts[1])) / 2.0;
            } else {
                expectedCount = Double.parseDouble(quantity);
            }
            double[] sub = expectedNativePayout(child, tables, memo, active);
            for (int i = 0; i < totals.length; i++) {
                totals[i] += probability * expectedCount * sub[i];
            }
        }
        active.remove(id);
        memo.put(id, totals);
        return totals;
    }

}
