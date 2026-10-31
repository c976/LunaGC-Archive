package emu.grasscutter.data;

import emu.grasscutter.data.excels.CookRecipeData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.drop.ArtifactResolver;
import emu.grasscutter.utils.lang.Language;
import java.util.*;

/**
 * Resolves semantic IDs against native resources, preserving their different domains:
 * inventory items/weapons, artifact sets, avatars, and cooking recipes.
 * A recipe is a CookRecipeData ID, not its collectible recipe-scroll item.
 */
public final class GenshinResourceIds {
    private static volatile GenshinResourceIds active;
    private final ItemIdRegistry items;
    private final Map<String, Integer> avatars;
    private final Map<String, Integer> recipes;
    private final Set<String> ambiguousAvatars;
    private final Set<String> ambiguousRecipes;

    public GenshinResourceIds(ItemIdRegistry items, Map<Integer, String> avatarNames,
                              Map<Integer, String> recipeNames) {
        this.items = Objects.requireNonNull(items);
        var avatarIndex = new HashMap<String, Integer>();
        var recipeIndex = new HashMap<String, Integer>();
        var avatarCollisions = new HashSet<String>();
        var recipeCollisions = new HashSet<String>();
        avatarNames.forEach((id, name) -> add(avatarIndex, avatarCollisions, id, name));
        recipeNames.forEach((id, name) -> add(recipeIndex, recipeCollisions, id, name));
        avatars = Map.copyOf(avatarIndex);
        recipes = Map.copyOf(recipeIndex);
        ambiguousAvatars = Set.copyOf(avatarCollisions);
        ambiguousRecipes = Set.copyOf(recipeCollisions);
    }

    private static void add(Map<String, Integer> index, Set<String> collisions,
                            int id, String sourceName) {
        if (id <= 0) throw new IllegalArgumentException("Invalid native resource ID: " + id);
        String name = ItemIdRegistry.slug(sourceName);
        if (name.isEmpty()) return; // The native resource has no verified English identity.
        Integer previous = index.putIfAbsent(name, id);
        if (previous != null && previous != id) collisions.add(name);
    }

    /** Lazily indexed after the game resources and TextMap are loaded. */
    public static GenshinResourceIds current() {
        var found = active;
        if (found != null) return found;
        synchronized (GenshinResourceIds.class) {
            if (active == null) active = fromGameResources();
            return active;
        }
    }

    public static GenshinResourceIds fromGameResources() {
        var avatarNames = new HashMap<Integer, String>();
        GameData.getAvatarDataMap().values().forEach(avatar -> {
            String name = english(avatar.getNameTextMapHash());
            if (name == null) name = avatar.getName(); // Native prefab identifier.
            if (name != null) avatarNames.put(avatar.getId(), name);
        });

        var recipeNames = new HashMap<Integer, String>();
        for (CookRecipeData recipe : GameData.getCookRecipeDataMap().values()) {
            if (recipe.getQualityOutputVec() == null) continue;
            var dishes = new ArrayList<String>();
            recipe.getQualityOutputVec().forEach(output -> {
                if (output == null) return;
                ItemData item = GameData.getItemDataMap().get(output.getId());
                if (item != null) {
                    String name = english(item.getNameTextMapHash());
                    if (name != null) dishes.add(name);
                }
            });
            String name = unmodifiedDishName(dishes);
            if (name != null) recipeNames.put(recipe.getId(), name);
        }
        return new GenshinResourceIds(ItemIdRegistry.fromGameResources(), avatarNames, recipeNames);
    }

    private static String english(long hash) {
        var text = Language.getTextMapKey(hash);
        if (text == null) return null;
        String name = text.get(0);
        return name == null || name.isBlank() || name.startsWith("[") ? null : name;
    }

    /** Recipe output vectors contain quality variants; never name a recipe after a variant. */
    static String unmodifiedDishName(Collection<String> dishNames) {
        var base = new HashSet<String>();
        for (String name : dishNames) {
            if (name == null || name.isBlank()
                    || name.startsWith("Suspicious ") || name.startsWith("Delicious ")) continue;
            base.add(name);
        }
        return base.size() == 1 ? base.iterator().next() : null;
    }

    public int resolve(String reference) {
        ResourceId resource = ResourceAliases.GENSHIN.resolve(ResourceId.parse(reference));
        if (!(resource.size() >= 3 && resource.segment(0).equals("genshin"))) {
            throw new IllegalArgumentException("Unsupported resource namespace: " + reference);
        }
        return switch (resource.segment(1)) {
            case "avatar" -> lookup(avatars, ambiguousAvatars, resource);
            case "recipe" -> ItemIdRegistry.isInventoryResourcePath(resource)
                    ? items.resolve(resource.toString())
                    : lookup(recipes, ambiguousRecipes, resource);
            case "artifact" -> {
                int setId = ArtifactResolver.nativeSetId(resource.tail(2));
                if (!GameData.getReliquarySetDataMap().containsKey(setId)) {
                    throw new IllegalArgumentException("Missing native artifact set: " + reference);
                }
                yield setId;
            }
            default -> {
                if (ItemIdRegistry.isInventoryResourceType(resource.segment(1))) {
                    yield items.resolve(resource.toString());
                }
                throw new IllegalArgumentException("Unsupported Genshin resource type: "
                        + resource.segment(1));
            }
        };
    }

    private static int lookup(Map<String, Integer> index, Set<String> ambiguous, ResourceId resource) {
        if (ambiguous.contains(resource.tail(2))) {
            throw new IllegalArgumentException("Ambiguous native resource: " + resource);
        }
        Integer id = index.get(resource.tail(2));
        if (id == null) throw new IllegalArgumentException("Unknown native resource: " + resource);
        return id;
    }
}
