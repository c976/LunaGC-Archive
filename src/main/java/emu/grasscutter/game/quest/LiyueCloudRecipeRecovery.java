package emu.grasscutter.game.quest;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;

/** Recover recipes earned by MQ1009's three investigations while their offering is pending. */
final class LiyueCloudRecipeRecovery {
    private LiyueCloudRecipeRecovery() {}

    static boolean resumeOnLogin(GameMainQuest main) {
        if (main.getParentQuestId() != 1009 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE) return false;
        var entry = main.getChildQuestById(100915);
        var next = main.getChildQuestById(100912);
        if (entry == null || entry.getState() != QuestState.QUEST_STATE_FINISHED
                || next == null || next.getState() != QuestState.QUEST_STATE_UNSTARTED) return false;
        for (int id = 100902; id <= 100911; id++) {
            var q = main.getChildQuestById(id);
            if (q == null || q.getQuestData() == null || q.getQuestData().getMainId() != 1009
                    || (q.getState() != QuestState.QUEST_STATE_UNSTARTED
                        && q.getState() != QuestState.QUEST_STATE_UNFINISHED
                        && q.getState() != QuestState.QUEST_STATE_FINISHED)) return false;
            var accept = q.getQuestData().getAcceptCond();
            if (accept == null || accept.size() != 1) return false;
            var c = accept.get(0);
            if (c.getType() != QuestCond.QUEST_COND_STATE_EQUAL || c.getParam() == null
                    || c.getParam().length < 2 || c.getParam()[0] != (id <= 100906 ? 100915 : 100906)
                    || c.getParam()[1] != QuestState.QUEST_STATE_FINISHED.getValue()) return false;
        }
        for (int id : new int[] {100905, 100906, 100910, 100911}) {
            var finish = main.getChildQuestById(id).getQuestData().getFinishCond();
            if (finish == null || finish.size() != 1
                    || finish.get(0).getType() != QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS
                    || finish.get(0).getParam() == null || finish.get(0).getParam().length == 0
                    || finish.get(0).getParam()[0] != id
                    || finish.get(0).getCount() != (id == 100905 || id == 100910 ? 3 : 1)) return false;
        }
        for (int id = 100902; id <= 100906; id++) {
            var q = main.getChildQuestById(id);
            if (q.getState() == QuestState.QUEST_STATE_UNSTARTED) q.start();
        }
        int[][] rewards = {{100902, 109410, 2103, 100907},
                           {100903, 109406, 1101, 100908},
                           {100904, 109411, 2104, 100909}};
        for (var reward : rewards) {
            var investigation = main.getChildQuestById(reward[0]);
            var offering = main.getChildQuestById(reward[3]);
            if (investigation == null || investigation.getState() != QuestState.QUEST_STATE_FINISHED
                    || offering == null || (offering.getState() != QuestState.QUEST_STATE_UNFINISHED
                        && offering.getState() != QuestState.QUEST_STATE_UNSTARTED)
                    || main.getTalks() == null || !main.getTalks().containsKey(reward[0])) continue;
            var data = investigation.getQuestData();
            var item = GameData.getItemDataMap().get(reward[1]);
            if (data == null || data.getMainId() != 1009 || data.getGainItems() == null
                    || data.getGainItems().stream().noneMatch(i -> i.getId() == reward[1] && i.getCount() == 1)
                    || item == null || item.getItemUse() == null
                    || item.getItemUse().stream().noneMatch(u ->
                        u.getUseOp() == ItemUseOp.ITEM_USE_UNLOCK_COOK_RECIPE
                            && u.getUseParam() != null && u.getUseParam().length > 0
                            && String.valueOf(reward[2]).equals(u.getUseParam()[0]))) continue;
            var player = main.getOwner();
            var inventory = player.getInventory();
            synchronized (inventory) {
                if (!player.getUnlockedRecipies().containsKey(reward[2])
                        && inventory.getItemCountById(reward[1]) == 0) {
                    inventory.addItem(reward[1], 1, ActionReason.QuestItem);
                }
            }
        }
        int investigated = 0, offered = 0;
        for (int id = 100902; id <= 100904; id++)
            if (finished(main, id)) investigated++;
        for (int id = 100907; id <= 100909; id++)
            if (finished(main, id)) offered++;
        restore(main, 100905, investigated);
        if (finished(main, 100905)) restore(main, 100906, 1);
        restore(main, 100910, offered);
        if (finished(main, 100910)) restore(main, 100911, 1);
        entry.triggerStateEvents();
        for (int id = 100902; id <= 100911; id++) {
            var q = main.getChildQuestById(id);
            if (q.getState() == QuestState.QUEST_STATE_FINISHED) q.triggerStateEvents();
        }
        // A saved offering notification may precede its corresponding quest completion.
        for (int group = 133101914; group <= 133101916; group++)
            main.getOwner().getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_LUA_NOTIFY,
                    String.valueOf(group), 0, 0);
        LiyueSceneGroupRecovery.restoreForQuest(main);
        return true;
    }

    private static boolean finished(GameMainQuest main, int id) {
        return main.getChildQuestById(id).getState() == QuestState.QUEST_STATE_FINISHED;
    }

    private static void restore(GameMainQuest main, int id, int count) {
        var player = main.getOwner();
        int saved = Math.max(0, player.getPlayerProgress().getCurrentProgress(String.valueOf(id)));
        if (count > saved) player.getProgressManager().addQuestProgress(id, count - saved);
        player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, id);
    }
}
