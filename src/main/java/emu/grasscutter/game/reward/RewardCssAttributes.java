package emu.grasscutter.game.reward;

import java.util.Map;
import java.util.Set;

/** The declared runtime types of LootCSS selector attributes. */
final class RewardCssAttributes {
    enum Type { INTEGER, STRING }

    private static final Set<String> INTEGER = Set.of(
            "city", "area", "WR", "level", "scene", "point",
            "gadget", "group", "config", "rank", "world_level", "wl", "tier", "power");
    private static final Set<String> STRING = Set.of("type", "grade", "tag", "mode", "kind");

    private RewardCssAttributes() {}

    static Type typeOf(String name) {
        if (INTEGER.contains(name)) return Type.INTEGER;
        if (STRING.contains(name)) return Type.STRING;
        throw new IllegalArgumentException("Unknown LootCSS selector attribute: " + name);
    }

    static void validate(Map<String, ?> attributes) {
        for (var entry : attributes.entrySet()) {
            Type type = typeOf(entry.getKey());
            Object value = entry.getValue();
            if (type == Type.INTEGER && !(value instanceof Integer)
                    || type == Type.STRING && !(value instanceof String)) {
                throw new IllegalArgumentException(
                        "LootCSS attribute " + entry.getKey() + " requires " + type + ": " + value);
            }
        }
    }
}
