package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** A weighted YAML choice is independent of the eventual value's domain and type. */
final class YamlWeightedValues {
    private YamlWeightedValues() {}

    /**
     * Treat a one-key mapping with a numeric value as the compact choice syntax.
     * Both a scalar key and an arbitrary structured YAML key are valid candidates.
     * Do not reinterpret domain data with ordinary value/type/reward fields.
     */
    static Map<?, ?> normalize(Map<?, ?> value) {
        if (value.size() != 1 || value.containsKey("value")
                || value.containsKey("type") || value.containsKey("reward")) {
            return value;
        }
        var entry = value.entrySet().iterator().next();
        if (!(entry.getValue() instanceof Number)) return value;
        var result = new LinkedHashMap<String, Object>();
        result.put("value", entry.getKey());
        result.put("weight", entry.getValue());
        return result;
    }

    /** Decode any YAML value through its owning consumer, preserving its type. */
    static <T> Value<T> compile(
            List<?> alternatives, Function<Object, T> decode, String location) {
        var values = new ArrayList<T>();
        var weights = new ArrayList<BigDecimal>();
        for (int i = 0; i < alternatives.size(); i++) {
            String at = location + "[" + i + "]";
            if (!(alternatives.get(i) instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException(at + ": expected a weighted value");
            }
            Map<?, ?> normalized = normalize(raw);
            if (normalized.size() != 2 || !normalized.containsKey("value")
                    || !normalized.containsKey("weight")) {
                throw new IllegalArgumentException(at + ": expected value and weight");
            }
            values.add(decode.apply(normalized.get("value")));
            weights.add(WeightedChoice.weight(normalized.get("weight"), at));
        }
        return Value.choose(values, weights, location);
    }
}
