package emu.grasscutter.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Shared namespace aliases. Prefix aliases preserve the remaining resource
 * path, while scoped grade aliases replace the final segment of a resource
 * family ({@code scope.family.grade}). Consumers always see canonical paths.
 */
public final class ResourceAliases {
    private final Map<ResourceId, ResourceId> aliases;
    private final Map<ResourceId, Map<String, String>> grades;

    public ResourceAliases(Map<ResourceId, ResourceId> aliases) {
        this(aliases, Map.of());
    }

    /**
     * Grade aliases apply uniformly to every family immediately below their
     * scope. For example, scope "genshin.talent_up" maps "2" to "teachings"
     * for any talent book family, with no list of subjects to maintain.
     */
    public ResourceAliases(Map<ResourceId, ResourceId> aliases,
                           Map<ResourceId, Map<String, String>> grades) {
        this.aliases = Map.copyOf(Objects.requireNonNull(aliases));
        var checkedGrades = new HashMap<ResourceId, Map<String, String>>();
        for (var entry : Objects.requireNonNull(grades).entrySet()) {
            var scope = Objects.requireNonNull(entry.getKey(), "Grade alias scope");
            var values = Map.copyOf(entry.getValue());
            for (var grade : values.entrySet()) {
                validateSegment(grade.getKey());
                validateSegment(grade.getValue());
                if (grade.getKey().equals(grade.getValue())) {
                    throw new IllegalArgumentException("Self-referential grade alias: "
                            + scope + "." + grade.getKey());
                }
            }
            checkedGrades.put(scope, values);
        }
        this.grades = Map.copyOf(checkedGrades);
        for (var entry : aliases.entrySet()) {
            if (entry.getValue().startsWith(entry.getKey())) {
                throw new IllegalArgumentException("Self-referential resource alias: " + entry.getKey());
            }
            resolve(entry.getKey());
        }
        for (var entry : this.grades.entrySet()) {
            for (String grade : entry.getValue().keySet()) {
                // Validate transitive grade cycles even when no native items are loaded.
                resolve(entry.getKey().append("alias_validation_family", grade));
            }
        }
    }

    private static void validateSegment(String segment) {
        if (segment == null || !segment.matches("[\\p{L}\\p{N}_-]+")) {
            throw new IllegalArgumentException("Invalid grade alias segment: " + segment);
        }
    }

    public ResourceId resolve(ResourceId input) {
        var path = Objects.requireNonNull(input, "Resource path");
        var seen = new HashSet<ResourceId>();
        int limit = (aliases.size() + grades.values().stream()
                .mapToInt(Map::size).sum() + 1) * 4 + 4;
        for (int steps = 0; steps <= limit; steps++) {
            if (!seen.add(path)) {
                throw new IllegalArgumentException("Cyclic resource alias: " + input);
            }
            ResourceId best = null;
            for (var candidate : aliases.keySet()) {
                if (path.startsWith(candidate)
                        && (best == null || candidate.size() > best.size())) {
                    best = candidate;
                }
            }
            if (best != null) {
                path = path.replacePrefix(best, aliases.get(best));
                continue;
            }

            ResourceId bestScope = null;
            String newGrade = null;
            for (var entry : grades.entrySet()) {
                var scope = entry.getKey();
                if (path.size() == scope.size() + 2 && path.startsWith(scope)) {
                    String resolved = entry.getValue().get(path.segment(path.size() - 1));
                    if (resolved != null
                            && (bestScope == null || scope.size() > bestScope.size())) {
                        bestScope = scope;
                        newGrade = resolved;
                    }
                }
            }
            if (newGrade == null) return path;
            var parts = new ArrayList<>(path.segments());
            parts.set(parts.size() - 1, newGrade);
            path = new ResourceId(parts);
        }
        throw new IllegalArgumentException("Unbounded resource alias expansion: " + input);
    }

    public ResourceId resolve(String input) {
        return resolve(ResourceId.parse(input));
    }

    /** Both the elemental family and its numeric grade are namespace aliases. */
    public static final ResourceAliases GENSHIN = genshin();

    private static ResourceAliases genshin() {
        var entries = new LinkedHashMap<ResourceId, ResourceId>();
        String[][] families = {
            {"anemo", "vayuda_turquoise"},
            {"geo", "prithiva_topaz"},
            {"electro", "vajrada_amethyst"},
            {"dendro", "nagadus_emerald"},
            {"hydro", "varunada_lazurite"},
            {"pyro", "agnidus_agate"},
            {"cryo", "shivada_jade"}
        };
        for (String[] family : families) {
            entries.put(ResourceId.of("genshin", "avatar_ascension", family[0]),
                    ResourceId.of("genshin", "avatar_ascension", family[1]));
        }

        return new ResourceAliases(entries, Map.of(
                ResourceId.of("genshin", "avatar_ascension"), Map.of(
                        "2", "sliver",
                        "3", "fragment",
                        "4", "chunk",
                        "5", "gemstone"),
                ResourceId.of("genshin", "talent_up"), Map.of(
                        "2", "teachings",
                        "3", "guide",
                        "4", "philosophies")));
    }
}
