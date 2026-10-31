package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;

/** Restore only the missing Yanxiao dish while MQ1003's final Xiao talk is pending. */
final class LiyueWangshuItemRecovery {
    private static final int ITEM = 100428;

    private LiyueWangshuItemRecovery() {}

    static boolean resumeOnLogin(GameMainQuest main) {
        if (main.getParentQuestId() != 1003 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE) return false;
        var chef = main.getChildQuestById(100316);
        var talk = main.getChildQuestById(100317);
        if (chef == null || chef.getState() != QuestState.QUEST_STATE_FINISHED
                || talk == null || talk.getState() != QuestState.QUEST_STATE_UNFINISHED
                || main.getTalks() == null || !main.getTalks().containsKey(100316)) return false;
        var chefData = chef.getQuestData();
        var talkData = talk.getQuestData();
        if (chefData == null || chefData.getMainId() != 1003 || chefData.getGainItems() == null
                || chefData.getGainItems().stream().noneMatch(i -> i.getId() == ITEM && i.getCount() == 1)
                || talkData == null || talkData.getMainId() != 1003 || talkData.getFinishCond() == null
                || talkData.getFinishCond().size() != 1) return false;
        var finish = talkData.getFinishCond().get(0);
        if (finish.getType() != QuestContent.QUEST_CONTENT_COMPLETE_TALK
                || finish.getParam() == null || finish.getParam().length == 0
                || finish.getParam()[0] != 100317) return false;
        var itemData = GameData.getItemDataMap().get(ITEM);
        if (itemData == null || itemData.getMaterialType() != MaterialType.MATERIAL_QUEST) return false;

        var inventory = main.getOwner().getInventory();
        synchronized (inventory) {
            if (inventory.getItemCountById(ITEM) < 1) {
                if (inventory.addItem(ITEM, 1, ActionReason.QuestItem)) {
                    Grasscutter.getLogger().info("Restored Wangshu quest dish: uid={} item={}",
                            main.getOwner().getUid(), ITEM);
                } else {
                    Grasscutter.getLogger().warn("Could not restore Wangshu quest dish: uid={} item={}",
                            main.getOwner().getUid(), ITEM);
                }
            }
        }
        // Preserve the completed chef conversation and the pending final talk on repeated logins.
        return true;
    }
}
