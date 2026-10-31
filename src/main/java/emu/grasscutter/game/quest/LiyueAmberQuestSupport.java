package emu.grasscutter.game.quest;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;

/** Translate actual break evidence into the native state event for MQ1008's five task ambers. */
public final class LiyueAmberQuestSupport {
    private LiyueAmberQuestSupport() {}

    public static boolean onModifierRemoved(Player player, GameEntity entity,
            AbilityData ability, AbilityModifier modifier) {
        if (!(entity instanceof EntityGadget gadget) || ability == null || modifier == null
                || !"SceneObj_Amber_GoldenAmber".equals(ability.abilityName)
                || ability.modifiers == null || ability.modifiers.get("GoldenAmber") != modifier
                || modifier.onRemoved == null) return false;
        // Only bridge the exact native callback; never infer a break from generic damage or another modifier.
        boolean nativeBreak = java.util.Arrays.stream(modifier.onRemoved).anyMatch(action ->
                action.type == AbilityModifier.AbilityModifierAction.Type.ExecuteGadgetLua
                    && action.param1 != null && !action.param1.isDynamic()
                    && action.param1.getConstant() == ScriptGadgetState.GearStart);
        return nativeBreak && reportBreak(player, gadget);
    }

    public static boolean onGadgetDeath(EntityGadget gadget) {
        var world = gadget.getScene().getWorld();
        return world != null && reportBreak(world.getHost(), gadget);
    }

    static void restoreSavedBreak(GameMainQuest main) {
        var player = main.getOwner();
        var scene = player.getScene();
        if (scene == null || scene.getId() != 3) return;
        var manager = scene.getScriptManager();
        if (manager == null) return;
        manager.whenInitialized(ready -> {
            if (!ready) return;
            scene.runWhenFinished(() -> {
                var rescue = main.getChildQuestById(100803);
                if (player.getScene() != scene || main.isFinished()
                        || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE
                        || rescue == null || rescue.getState() != QuestState.QUEST_STATE_UNFINISHED
                        || player.getPlayerProgress().getCurrentProgress("13310390801") > 0) return;
                manager.getGroupById(133103908);
                var instance = manager.getGroupInstanceById(133103908);
                if (instance == null) return;
                int suite = instance.getActiveSuiteId();
                int config = 908004 + suite;
                if (suite < 2 || suite > 6
                        || instance.getCachedGadgetStates().getOrDefault(config, -1)
                            != ScriptGadgetState.GearStart) return;
                manager.callEvent(new ScriptArgs(133103908, EventType.EVENT_GADGET_STATE_CHANGE,
                        ScriptGadgetState.GearStart, config).setParam3(ScriptGadgetState.Default));
            });
        });
    }

    private static boolean reportBreak(Player player, EntityGadget gadget) {
        if (player == null || !QuestManager.isQuestingActive() || player.getScene() != gadget.getScene()
                || gadget.getScene().getId() != 3
                || gadget.getGadgetId() != 70360045 || gadget.getGroupId() != 133103908
                || gadget.getConfigId() < 908006 || gadget.getConfigId() > 908010) return false;
        var main = player.getQuestManager().getMainQuestById(1008);
        if (main == null || main.getParentQuestId() != 1008 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE)
            return false;
        var intro = main.getChildQuestById(100808);
        var rescue = main.getChildQuestById(100803);
        if (intro == null || intro.getState() != QuestState.QUEST_STATE_FINISHED
                || rescue == null || rescue.getState() != QuestState.QUEST_STATE_UNFINISHED) return false;
        synchronized (gadget) {
            if (gadget.getState() == ScriptGadgetState.Default) gadget.updateState(ScriptGadgetState.GearStart);
        }
        return true;
    }
}
