package emu.grasscutter.game.entity.gadget.chest;

import emu.grasscutter.game.entity.EntityGadget;

/**
 * Preserves a chest's identity independently of its reward grade.
 * A shared gadget ID cannot distinguish normal, Mora, and Remarkable chests.
 */
public final class ChestTypeResolver {
    private ChestTypeResolver() {}

    /** The imported point's kind is source metadata, not a chest reward grade. */
    public static String fromExploreKind(String kind) {
        if (kind == null) return null;
        return switch (kind) {
            case "chest_mora" -> "mora";
            case "chest_remarkable" -> "remarkable";
            case "chest_common", "chest_exquisite", "chest_precious",
                    "chest_luxurious", "chest_puzzle" -> "normal";
            default -> null;
        };
    }

    /** A source kind names a grade only for the four ordinary chest grades. */
    public static String gradeFromExploreKind(String kind) {
        if (kind == null) return null;
        return switch (kind) {
            case "chest_common" -> "common";
            case "chest_exquisite" -> "exquisite";
            case "chest_precious" -> "precious";
            case "chest_luxurious" -> "luxurious";
            default -> null;
        };
    }

    public static String resolve(EntityGadget gadget) {
        var meta = gadget.getMetaGadget();
        if (meta != null && meta.chestType != null && !meta.chestType.isBlank()) {
            return meta.chestType;
        }
        var data = gadget.getGadgetData();
        return fromNativeJsonName(data == null ? null : data.getJsonName());
    }

    public static boolean isNativeInteract(EntityGadget gadget) {
        var data = gadget.getGadgetData();
        String name = data == null ? null : data.getJsonName();
        return "SearchPoint".equals(name)
                || (name != null && name.startsWith("SceneObj_Area_Common_Property_Ani_Prop_BGM_"));
    }

    /** Recognize the native ordinary chest prefabs without guessing unknown gadget identities. */
    static String fromNativeJsonName(String jsonName) {
        if (jsonName == null) return null;
        if (jsonName.matches("SceneObj_(?:Chest|EssenceChest)_(?:Default|Locked|Bramble|Frozen|Rock)_Lv[0-9]+")) {
            return "normal";
        }
        return null;
    }
}
