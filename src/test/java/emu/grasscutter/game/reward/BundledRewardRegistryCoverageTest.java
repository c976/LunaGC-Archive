package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ResourceRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Check authored keys against the bundled dictionary without loading game data. */
final class BundledRewardRegistryCoverageTest {
    private static final Pattern LCSS_ITEM =
            Pattern.compile("(?m)^\\s*(genshin\\.[a-z0-9_.$]+)\\s*:");
    private static final Pattern YAML_ITEM =
            Pattern.compile("(?m)^\\s*-\\s*(genshin\\.[a-z0-9_.]+)\\s*:");

    @Test
    void allBundledRewardNamesHaveOneExplicitResourceId() throws Exception {
        var entries = new HashMap<String, Integer>();
        for (String line : RewardConfiguration.readBundled("registry.tsv").split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\\t", -1);
            assertEquals(2, fields.length, "Invalid registry row: " + line);
            Integer previous = entries.putIfAbsent(fields[0], Integer.parseInt(fields[1]));
            assertNull(previous, "Duplicate registry key: " + fields[0]);
        }
        var registry = new ResourceRegistry(entries);
        String chest = RewardConfiguration.readBundled("chests.lcss");
        String boss = RewardConfiguration.readBundled("world-boss.lcss");
        Set<String> materials = bindings(boss, "material");
        Set<String> bossElements = bindings(boss, "element");
        Set<String> chestElements = bindings(chest, "element");
        assertFalse(materials.isEmpty());
        assertFalse(bossElements.isEmpty());
        assertFalse(chestElements.isEmpty());

        var matcher = LCSS_ITEM.matcher(RewardConfiguration.readBundledLootCss());
        int checked = 0;
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!key.contains("$")) {
                registry.resolve(key);
                checked++;
                continue;
            }
            final Set<String> values;
            if (key.startsWith("genshin.avatar_ascension.$material")) {
                values = materials;
            } else if (key.startsWith("genshin.avatar_ascension.$element.")) {
                values = bossElements;
            } else if (key.equals("genshin.currency.sigil.$element")) {
                values = chestElements;
            } else {
                fail("Uncovered variable resource name: " + key);
                return;
            }
            for (String value : values) {
                registry.resolve(key.replace("$material", value).replace("$element", value));
                checked++;
            }
        }

        var yamlItems = YAML_ITEM.matcher(RewardConfiguration.readBundled("loot.yaml"));
        int yamlChecked = 0;
        while (yamlItems.find()) {
            registry.resolve(yamlItems.group(1));
            yamlChecked++;
        }
        assertTrue(checked > 250, "Expected bundled LCSS declarations to be checked");
        assertTrue(yamlChecked > 10, "Expected bundled YAML candidates to be checked");
    }

    private static Set<String> bindings(String css, String name) {
        var matches = Pattern.compile("(?m)^\\s*--" + name + "\\s*:\\s*([^;]+);")
                .matcher(css);
        var values = new HashSet<String>();
        while (matches.find()) {
            String raw = matches.group(1).replace("[", "").replace("]", "").replace("\"", "");
            for (String value : raw.split(",")) {
                String trimmed = value.trim();
                assertTrue(trimmed.matches("[a-z][a-z0-9_]*"),
                        "Invalid reward binding: " + trimmed);
                values.add(trimmed);
            }
        }
        return Set.copyOf(values);
    }
}
