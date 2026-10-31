package emu.grasscutter.game.reward;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.ResourceRegistry;
import java.util.function.ToIntFunction;
import emu.grasscutter.data.ResourceId;
import emu.grasscutter.game.drop.LootCount;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rewards resolved with the dedicated reward stylesheet parser and cascade. */
public final class CssRewards {
    /**
     * ID-oriented inspection adapter for existing internal diagnostics.
     * Rolling always delegates to the canonical string-key selection below.
     */
    public record Selection(Map<Integer, Integer> items, Map<String, Integer> loot,
            Map<Integer, LootCount.Spec> rangedItems, Map<String, LootCount.Spec> rangedLoot,
            KeySelection keys) {
        public Selection {
            items = Map.copyOf(items);
            loot = Map.copyOf(loot);
            rangedItems = Map.copyOf(rangedItems);
            rangedLoot = Map.copyOf(rangedLoot);
            java.util.Objects.requireNonNull(keys, "keys");
        }
    }

    /** The result of evaluating LootCSS, before any native ID lookup. */
    public record KeySelection(Map<String, Integer> items, Map<String, Integer> loot,
            Map<String, LootCount.Spec> rangedItems, Map<String, LootCount.Spec> rangedLoot) {
        public KeySelection {
            items = Map.copyOf(items);
            loot = Map.copyOf(loot);
            rangedItems = Map.copyOf(rangedItems);
            rangedLoot = Map.copyOf(rangedLoot);
        }
    }

    private record LootDeclaration(RewardResourceExpression resource,
                                   Integer fixed, LootCount.Spec ranged) {}
    private record ItemDeclaration(RewardResourceExpression resource,
                                   Integer fixed, LootCount.Spec ranged) {}

    private record Rule(RewardCssParser.Selector selector,
                        List<ItemDeclaration> items, List<LootDeclaration> loot,
                        Map<String, List<String>> bindings, int order) {}

    private final List<Rule> rules;
    private final Set<String> bindingNames;
    private final YamlLootTables loot;
    private final ToIntFunction<String> itemRegistry;

    private CssRewards(List<Rule> rules, Set<String> bindingNames,
                       YamlLootTables loot, ToIntFunction<String> itemRegistry) {
        this.rules = List.copyOf(rules);
        this.bindingNames = Set.copyOf(bindingNames);
        this.loot = loot;
        this.itemRegistry = itemRegistry;
    }

    public static CssRewards parse(String source, ItemIdRegistry items, YamlLootTables loot) {
        return parse(source, items::resolve, loot);
    }

    public static CssRewards parse(String source, ResourceRegistry items, YamlLootTables loot) {
        return parse(source, items::resolve, loot);
    }

    private static CssRewards parse(String source, ToIntFunction<String> items,
            YamlLootTables loot) {
        List<RewardCssParser.Rule> parsed = RewardCssParser.parse(source);
        var variables = new HashSet<String>();
        for (var rule : parsed) {
            for (var declaration : rule.declarations()) {
                if (declaration.name().startsWith("--")) {
                    variables.add(declaration.name().substring(2));
                }
            }
        }
        var rules = new ArrayList<Rule>();
        for (var rule : parsed) {
            var itemDeclarations = new ArrayList<ItemDeclaration>();
            var lootDeclarations = new ArrayList<LootDeclaration>();
            var bindings = new HashMap<String, List<String>>();
            for (var declaration : rule.declarations()) {
                String name = declaration.name(), value = declaration.value();
                if (name.startsWith("--")) {
                    bindings.put(name.substring(2), parseBinding(value));
                    continue;
                }
                Integer fixed = value.matches("[0-9]+") ? Integer.valueOf(value) : null;
                LootCount.Spec rolled = fixed == null ? LootCount.parseRoll(value) : null;
                if (!name.startsWith("genshin.") && !name.matches("[a-z][a-z0-9_]*\\.loot\\..+")) {
                    throw new IllegalArgumentException("Invalid reward resource property: " + name);
                }
                boolean table = name.matches("[a-z][a-z0-9_]*\\.loot\\..+");
                var resource = RewardResourceExpression.parse(name, table);
                resource.requireDeclared(variables);
                if (table) {
                    if (!resource.hasVariables()) {
                        for (String expanded : resource.expand(Map.of())) loot.resolveName(expanded);
                    }
                    lootDeclarations.add(new LootDeclaration(resource, fixed, rolled));
                } else {
                    if (!resource.hasVariables()) {
                        for (String expanded : resource.expand(Map.of())) {
                            ResourceId.parse(expanded);
                            items.applyAsInt(expanded);
                        }
                    }
                    itemDeclarations.add(new ItemDeclaration(resource, fixed, rolled));
                }
            }
            // Every selector in a comma group creates an independent rule in source order.
            for (var selector : rule.selectors()) {
                rules.add(new Rule(selector, List.copyOf(itemDeclarations),
                        List.copyOf(lootDeclarations), Map.copyOf(bindings), rules.size()));
            }
        }
        return new CssRewards(rules, variables, loot, items);
    }

    private static List<String> parseBinding(String value) {
        String names = value.replaceAll("\\s+", "");
        if (names.startsWith("[") && names.endsWith("]")) names = names.substring(1, names.length() - 1);
        else if (names.contains(",") || names.contains("[") || names.contains("]")) {
            throw new IllegalArgumentException("Binding lists require brackets: " + value);
        }
        var bindings = new ArrayList<String>();
        for (String name : names.split(",", -1)) {
            ResourceId.parse("binding." + name);
            if (bindings.contains(name)) throw new IllegalArgumentException("Duplicate binding value: " + name);
            bindings.add(name);
        }
        return List.copyOf(bindings);
    }

    /** Resolve CSS selectors, variables and cascade to exact resource names. */
    public KeySelection resolve(String type, Map<String, ?> attributes) {
        RewardCssAttributes.validate(attributes);
        var matched = new ArrayList<Rule>();
        for (var rule : rules) {
            if (rule.selector.matches(type, attributes)) matched.add(rule);
        }
        // With only type and attribute selectors, specificity is the attribute count.
        // The type-selector component is equal for every rule matching this object.
        matched.sort(Comparator.comparingInt((Rule rule) -> rule.selector.specificity())
                .thenComparingInt(Rule::order));
        var bindings = new HashMap<String, List<String>>();
        bindingNames.forEach(name -> bindings.put(name, List.of()));
        for (var rule : matched) bindings.putAll(rule.bindings);

        var fixed = new HashMap<String, Integer>();
        var ranged = new HashMap<String, LootCount.Spec>();
        var selectedLoot = new HashMap<String, Integer>();
        var rangedLoot = new HashMap<String, LootCount.Spec>();
        for (var rule : matched) {
            for (var declaration : rule.items) {
                for (String name : declaration.resource.expand(bindings)) {
                    ResourceId.parse(name);
                    if (declaration.fixed != null) {
                        ranged.remove(name);
                        fixed.put(name, declaration.fixed);
                    } else {
                        fixed.remove(name);
                        ranged.put(name, declaration.ranged);
                    }
                }
            }
            for (var declaration : rule.loot) {
                for (String expanded : declaration.resource.expand(bindings)) {
                    String name = loot.resolveName(expanded);
                    if (declaration.fixed != null) {
                        rangedLoot.remove(name);
                        selectedLoot.put(name, declaration.fixed);
                    } else {
                        selectedLoot.remove(name);
                        rangedLoot.put(name, declaration.ranged);
                    }
                }
            }
        }
        return new KeySelection(fixed, selectedLoot, ranged, rangedLoot);
    }

    /** Explicitly convert to native IDs at an integer consumer boundary. */
    public Selection resolveIds(String type, Map<String, ?> attributes) {
        var keys = resolve(type, attributes);
        var fixed = new HashMap<Integer, Integer>();
        var ranged = new HashMap<Integer, LootCount.Spec>();
        keys.items().forEach((key, count) -> fixed.put(itemRegistry.applyAsInt(key), count));
        keys.rangedItems().forEach((key, count) -> ranged.put(itemRegistry.applyAsInt(key), count));
        return new Selection(fixed, keys.loot(), ranged, keys.rangedLoot(), keys);
    }
}
