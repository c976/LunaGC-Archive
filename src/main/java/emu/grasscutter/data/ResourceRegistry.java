package emu.grasscutter.data;

import emu.grasscutter.game.drop.ArtifactResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An explicitly authored resource-name dictionary.
 *
 * <p>Entries supply both the name and native ID. No display text, native item
 * category, usage metadata, aliases or implicit naming rules participate.
 */
public final class ResourceRegistry {
    private static volatile ResourceRegistry active;
    private final Map<String, Integer> ids;

    /**
     * Command resolution and bundled rewards share exactly one registered dictionary.
     * Loading it uses the native item table, without relying on localization text.
     */
    public static ResourceRegistry current() {
        var registry = active;
        if (registry != null) return registry;
        synchronized (ResourceRegistry.class) {
            if (active == null) {
                try {
                    active = fromGameResources(ArtifactResolver.fromGameResources());
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot load resource registry", e);
                }
            }
            return active;
        }
    }

    /** Install the dictionary together with the artifact variants used by reward compilation. */
    public static ResourceRegistry current(ArtifactResolver artifacts) throws IOException {
        synchronized (ResourceRegistry.class) {
            if (active == null) active = fromGameResources(artifacts);
            return active;
        }
    }

    public ResourceRegistry(Map<String, Integer> entries) {
        Objects.requireNonNull(entries, "entries");
        for (var entry : entries.entrySet()) {
            String name = entry.getKey();
            ResourceId path = ResourceId.parse(name);
            if (path.size() < 3) {
                throw new IllegalArgumentException("Incomplete resource name: " + name);
            }
            Integer id = entry.getValue();
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Invalid native ID for " + name + ": " + id);
            }
        }
        ids = Map.copyOf(entries);
    }

    /** Build the packaged fallback dictionary used before rewards are initialized. */
    public static ResourceRegistry fromGameResources(ArtifactResolver artifacts) throws IOException {
        try (var stream = ResourceRegistry.class.getResourceAsStream("/rewards/registry.tsv")) {
            if (stream == null) throw new IOException("Missing bundled rewards/registry.tsv");
            return fromDefinitions(new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    artifacts);
        }
    }

    /** Compile an external registry against native game item IDs. */
    public static ResourceRegistry fromDefinitions(String definitions, ArtifactResolver artifacts) {
        return compile(definitions, GameData.getItemDataMap().keySet(), artifacts.resourceIds());
    }

    /** Publish only after the matching reward CSS and YAML have compiled successfully. */
    public static synchronized void install(ResourceRegistry registry) {
        active = Objects.requireNonNull(registry);
    }

    /** Validate and combine the authored dictionary without needing a running game server. */
    static ResourceRegistry compile(String definitions, java.util.Set<Integer> nativeItemIds,
            Map<String, Integer> artifactIds) {
        var names = new LinkedHashMap<String, Integer>();
        String[] lines = definitions.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] columns = line.split("\\t", -1);
            if (columns.length != 2 || !columns[1].matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException("Malformed registry.tsv line " + (i + 1));
            }
            int id = Integer.parseInt(columns[1]);
            // Native virtual rewards are handled by Player, not inventory ItemData.
            if (id != 101 && id != 102 && id != 105 && !nativeItemIds.contains(id)) {
                throw new IllegalArgumentException("Unknown native ID at registry.tsv:"
                        + (i + 1) + ": " + id);
            }
            if (names.putIfAbsent(columns[0], id) != null) {
                throw new IllegalArgumentException("Duplicate registry resource at line "
                        + (i + 1) + ": " + columns[0]);
            }
        }
        for (var entry : artifactIds.entrySet()) {
            if (!nativeItemIds.contains(entry.getValue())) {
                throw new IllegalArgumentException("Unknown native artifact ID: " + entry.getValue());
            }
            if (names.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("Duplicate artifact resource: " + entry.getKey());
            }
        }
        return new ResourceRegistry(names);
    }

    public int resolve(String name) {
        Integer id = ids.get(name);
        if (id == null) {
            throw new IllegalArgumentException("Unknown resource: " + name);
        }
        return id;
    }

    public Map<String, Integer> entries() {
        return ids;
    }
}
