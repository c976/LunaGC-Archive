package emu.grasscutter.game.quest;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;

/** Mechanics confined to Madame Ping's plot dungeon 11, scene 1017. */
public final class LiyueTeapotDungeonSupport {
    private LiyueTeapotDungeonSupport() {}

    public static boolean isTargetScene(Scene scene) {
        var dungeon = scene != null ? scene.getDungeonManager() : null;
        return scene != null && scene.getId() == 1017 && dungeon != null
                && dungeon.getDungeonData() != null && dungeon.getDungeonData().getId() == 11;
    }

    public static boolean isTimerGroup(Scene scene, int group) {
        return isTargetScene(scene) && (group == 201017008 || group == 201017016);
    }

    public static boolean isCobweb(EntityGadget gadget) {
        if (gadget == null || !isTargetScene(gadget.getScene()) || gadget.getGadgetId() != 70220036)
            return false;
        return switch (gadget.getGroupId()) {
            case 201017001 -> gadget.getConfigId() == 1005;
            case 201017006 -> gadget.getConfigId() == 6005;
            case 201017009 -> gadget.getConfigId() == 9001;
            case 201017014 -> gadget.getConfigId() == 14004;
            case 201017015 -> gadget.getConfigId() == 15004;
            case 201017018 -> gadget.getConfigId() == 18002;
            default -> false;
        };
    }

    public static void prepareCobweb(EntityGadget gadget) {
        if (!isCobweb(gadget) || Float.isFinite(gadget.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)))
            return;
        var config = gadget.getConfigGadget();
        if (config == null || config.getCombat() == null || config.getCombat().getProperty() == null)
            return;
        float hp = config.getCombat().getProperty().getHP();
        if (!Float.isFinite(hp) || hp <= 0) return;
        // Native destruction uses an animator-driven hit sequence. Until that sequence is
        // handled server-side, retain the resource's finite HP for these six task obstacles.
        gadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, hp);
        gadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, hp);
        gadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
    }
}
