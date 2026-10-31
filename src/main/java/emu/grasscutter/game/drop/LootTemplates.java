package emu.grasscutter.game.drop;

import emu.grasscutter.data.ResourceId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compile parameterized Lootweave expressions with one unambiguous call syntax. */
final class LootTemplates {
    private static final Pattern DEFINITION =
            Pattern.compile("([a-z][a-z0-9_-]*)\\s*<\\s*([^<>]*)\\s*>");
    private static final Pattern INVOCATION =
            Pattern.compile("([a-z][a-z0-9_-]*)\\s*<\\s*((?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+)(?:\\s*,\\s*(?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+))*)?\\s*>");
    private static final Pattern EXPRESSION =
            Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern PARAMETER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern SPREAD =
            Pattern.compile("\\.\\.\\.\\$([A-Za-z_][A-Za-z0-9_]*)");

    private record Definition(List<String> parameters, String varargs, List<?> entries) {}
    record Prepared(LootTemplates templates, Map<?, ?> tables) {}

    private final String namespace;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<String, List<?>> instances = new HashMap<>();
    private final Set<String> expanding = new HashSet<>();

    private LootTemplates(String namespace) {
        this.namespace = namespace;
    }

    static Prepared prepare(Map<?, ?> document, String namespace) {
        var templates = new LootTemplates(namespace);
        var result = new LinkedHashMap<Object, Object>();
        for (var entry : document.entrySet()) {
            if (entry.getKey() instanceof String key) {
                Matcher definition = DEFINITION.matcher(key);
                if (definition.matches()) {
                    if (!(entry.getValue() instanceof List<?> entries)) {
                        throw new IllegalArgumentException("Loot template must be a YAML list: " + key);
                    }
                    String[] declared = definition.group(2).split("\\s*,\\s*", -1);
                    var parameters = new ArrayList<String>();
                    var seen = new HashSet<String>();
                    String varargs = null;
                    for (int i = 0; i < declared.length; i++) {
                        String parameter = declared[i].trim();
                        boolean pack = parameter.startsWith("...");
                        String name = pack ? parameter.substring(3) : parameter;
                        if (!PARAMETER.matcher(name).matches()) {
                            throw new IllegalArgumentException("Invalid loot template parameter: " + parameter);
                        }
                        if (!seen.add(name)) {
                            throw new IllegalArgumentException("Duplicate loot template parameter: " + key);
                        }
                        if (pack) {
                            if (i != declared.length - 1) {
                                throw new IllegalArgumentException("Parameter pack must be last: " + key);
                            }
                            varargs = name;
                        } else {
                            parameters.add(name);
                        }
                    }
                    if (templates.definitions.putIfAbsent(definition.group(1),
                            new Definition(List.copyOf(parameters), varargs, entries)) != null) {
                        throw new IllegalArgumentException("Duplicate loot template: " + key);
                    }
                    continue;
                }
            }
            result.put(entry.getKey(), entry.getValue());
        }
        for (String name : templates.definitions.keySet()) {
            if (result.containsKey(name)) {
                throw new IllegalArgumentException("Loot template shadows table: " + name);
            }
        }
        var shared = new IdentityHashMap<Object, Object>();
        result.replaceAll((key, value) -> templates.substitute(value, Map.of(), 0, shared));
        return new Prepared(templates, result);
    }

    private Object substitute(Object raw, Map<String, Object> parameters, int depth,
                              IdentityHashMap<Object, Object> shared) {
        if (raw instanceof List<?> list) {
            Object existing = shared.get(raw);
            if (existing != null) return existing;
            var output = new ArrayList<Object>(list.size());
            shared.put(raw, output);
            for (Object value : list) {
                output.add(substitute(value, parameters, depth + 1, shared));
            }
            return output;
        }
        if (raw instanceof Map<?, ?> map) {
            Object existing = shared.get(raw);
            if (existing != null) return existing;
            var output = new LinkedHashMap<Object, Object>();
            shared.put(raw, output);
            for (var entry : map.entrySet()) {
                Object key = substitute(entry.getKey(), parameters, depth + 1, shared);
                Object value = substitute(entry.getValue(), parameters, depth + 1, shared);
                if (("reward".equals(key) || "type".equals(key) || "value".equals(key))
                        && value instanceof String reward) {
                    value = expandReward(reward, depth + 1);
                }
                if (output.containsKey(key)) {
                    throw new IllegalArgumentException("Duplicate expanded loot key: " + key);
                }
                output.put(key, value);
            }
            return output;
        }
        if (raw instanceof String value && value.contains("${")) {
            throw new IllegalArgumentException("Obsolete loot template expression: " + value);
        }
        if (raw instanceof String value && value.contains("$")) {
            String source = expandPacks(value, parameters);
            Matcher matcher = EXPRESSION.matcher(source);
            StringBuffer expanded = new StringBuffer();
            while (matcher.find()) {
                String name = matcher.group(1);
                // Literal arguments are not implicitly resolved as parameters.
                if (!parameters.containsKey(name)) continue;
                matcher.appendReplacement(expanded,
                        Matcher.quoteReplacement(parameters.get(name).toString()));
            }
            matcher.appendTail(expanded);
            String result = expanded.toString();
            if (value.trim().matches("\\$[A-Za-z_][A-Za-z0-9_]*")) {
                return result.matches("[0-9]+") ? Integer.parseInt(result) : result;
            }
            return result;
        }
        return raw;
    }

    /**
     * Spread an argument pack in any scalar field. At expression level, the
     * surrounding candidate text is repeated for each argument. Inside a
     * template call, arguments are spliced into that call instead.
     */
    private static String expandPacks(String value, Map<String, Object> parameters) {
        if (!SPREAD.matcher(value).find()) return value;
        var segments = new ArrayList<String>();
        int start = 0, depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                segments.add(value.substring(start, i).trim());
                start = i + 1;
            }
        }
        segments.add(value.substring(start).trim());
        var expanded = new ArrayList<String>();
        for (String segment : segments) expanded.addAll(expandPackSegment(segment, parameters));
        return String.join(", ", expanded);
    }

    private static List<String> expandPackSegment(String segment, Map<String, Object> parameters) {
        Matcher match = SPREAD.matcher(segment);
        if (!match.find()) return List.of(segment);
        String name = match.group(1);
        if (!(parameters.get(name) instanceof List<?> pack)) {
            throw new IllegalArgumentException("Unknown loot template parameter pack: " + name);
        }
        String before = segment.substring(0, match.start());
        String after = segment.substring(match.end());
        int depth = 0;
        for (int i = 0; i < match.start(); i++) {
            char c = segment.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
        }
        if (depth > 0) {
            return expandPackSegment(before + String.join(", ",
                    pack.stream().map(Object::toString).toList()) + after, parameters);
        }
        var results = new ArrayList<String>();
        for (Object argument : pack) {
            results.addAll(expandPackSegment(before + argument + after, parameters));
        }
        return results;
    }

    private Object expandReward(String reward, int depth) {
        String prefix = namespace + ".loot.";
        String local = reward.startsWith(prefix) ? reward.substring(prefix.length()) : reward;
        Matcher invocation = INVOCATION.matcher(local);
        if (!invocation.matches()) return reward;
        Definition definition = definitions.get(invocation.group(1));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown loot template: " + reward);
        }
        String arguments = invocation.group(2) == null ? "" : invocation.group(2);
        String invocationKey = invocation.group(1) + "<"
                + arguments.replaceAll("\\s+", "") + ">";
        // Resolve recursive calls lazily after the current instance is compiled.
        if (expanding.contains(invocationKey)) return prefix + invocationKey;
        return instantiate(invocation.group(1), arguments, definition, depth);
    }

    /** True if a qualified or local invocation names a declared template. */
    boolean isTemplateReference(String reference) {
        String prefix = namespace + ".loot.";
        String local = reference.startsWith(prefix)
                ? reference.substring(prefix.length()) : reference;
        Matcher match = INVOCATION.matcher(local);
        return match.matches() && definitions.containsKey(match.group(1));
    }

    /** Canonical resource spelling, including normalized template arguments. */
    String qualify(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Missing loot table reference");
        }
        reference = reference.trim();
        String prefix = namespace + ".loot.";
        String local = reference.startsWith(prefix) ? reference.substring(prefix.length()) : reference;
        Matcher invocation = INVOCATION.matcher(local);
        if (invocation.matches()) {
            ResourceId.parse(prefix + invocation.group(1));
            String arguments = invocation.group(2) == null ? "" : invocation.group(2);
            return prefix + invocation.group(1) + "<" + arguments.replaceAll("\\s+", "") + ">";
        }
        return ResourceId.parse(prefix + local).toString();
    }

    synchronized List<?> instantiateReference(String reference) {
        Object expanded = expandReward(reference, 0);
        if (!(expanded instanceof List<?> entries)) {
            throw new IllegalArgumentException("Unknown loot table: " + reference);
        }
        return entries;
    }

    private List<?> instantiate(String name, String arguments, Definition definition, int depth) {
        String normalized = arguments.replaceAll("\\s+", "");
        String[] values = normalized.isEmpty() ? new String[0] : normalized.split(",", -1);
        int fixed = definition.parameters.size();
        if (values.length < fixed || (definition.varargs == null && values.length != fixed)) {
            throw new IllegalArgumentException("Wrong number of loot template arguments: " + name);
        }
        String key = name + "<" + String.join(",", values) + ">";
        List<?> cached = instances.get(key);
        if (cached != null) return cached;
        if (!expanding.add(key)) {
            throw new IllegalStateException("Template recursion must be represented as a lazy reference: " + key);
        }
        try {
            var parameters = new LinkedHashMap<String, Object>();
            for (int i = 0; i < fixed; i++) {
                Object value;
                try {
                    value = values[i].matches("[0-9]+") ? Integer.valueOf(values[i]) : values[i];
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid loot template argument: " + key, ex);
                }
                parameters.put(definition.parameters.get(i), value);
            }
            if (definition.varargs != null) {
                parameters.put(definition.varargs,
                        List.of(values).subList(fixed, values.length));
            }
            if (definition.entries.isEmpty()) throw new IllegalArgumentException("Empty loot template: " + name);
            List<?> expanded = (List<?>) substitute(definition.entries, parameters, depth + 1,
                    new IdentityHashMap<>());
            instances.put(key, expanded);
            return expanded;
        } finally {
            expanding.remove(key);
        }
    }

}
