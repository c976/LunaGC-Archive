package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;

/**
 * Reads YAML syntax nodes without discarding complex mapping keys.
 * Weighted branches may use a list or entry mapping as the key. Reusing
 * composed nodes preserves YAML anchor identity for recursive references.
 */
final class LootYamlSyntax {
    private LootYamlSyntax() {}

    static Object parse(String source, LoadSettings settings) {
        var root = new Compose(settings).composeString(source);
        return root.map(node -> value(node, new IdentityHashMap<>())).orElse(null);
    }

    private static Object value(Node node, IdentityHashMap<Node, Object> shared) {
        if (node instanceof ScalarNode scalar) return scalar(scalar);
        Object cached = shared.get(node);
        if (cached != null) return cached;
        if (node instanceof SequenceNode sequence) {
            var result = new ArrayList<Object>();
            shared.put(node, result);
            for (Node child : sequence.getValue()) {
                result.add(value(child, shared));
            }
            return result;
        }
        if (node instanceof MappingNode mapping) {
            var result = new LinkedHashMap<Object, Object>();
            shared.put(node, result);
            for (var tuple : mapping.getValue()) {
                Object key = value(tuple.getKeyNode(), shared);
                Object entry = value(tuple.getValueNode(), shared);
                if (key == null) {
                    throw new IllegalArgumentException("Loot YAML mapping key is null");
                }
                try {
                    if (result.containsKey(key)) {
                        throw new IllegalArgumentException("Duplicate loot YAML key: " + key);
                    }
                    result.put(key, entry);
                } catch (StackOverflowError error) {
                    throw new IllegalArgumentException("Recursive YAML mapping key", error);
                }
            }
            return result;
        }
        throw new IllegalArgumentException("Unsupported loot YAML node: " + node);
    }

    private static Object scalar(ScalarNode node) {
        Tag tag = node.getTag();
        String text = node.getValue();
        if (Tag.NULL.equals(tag)) return null;
        if (Tag.BOOL.equals(tag)) return Boolean.parseBoolean(text);
        if (Tag.INT.equals(tag)) {
            try {
                String normalized = text.replace("_", "");
                if (normalized.matches("[-+]?0[xX][0-9a-fA-F]+")) {
                    return Long.parseLong(normalized.replaceFirst("(?i)0x", ""), 16);
                }
                long parsed = Long.parseLong(normalized);
                if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                    return Integer.valueOf((int) parsed);
                }
                return Long.valueOf(parsed);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid YAML integer: " + text, ex);
            }
        }
        if (Tag.FLOAT.equals(tag)) {
            try {
                return new BigDecimal(text.replace("_", ""));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid YAML decimal: " + text, ex);
            }
        }
        if (Tag.STR.equals(tag)) return text;
        throw new IllegalArgumentException("Unsupported YAML scalar tag: " + tag);
    }
}
