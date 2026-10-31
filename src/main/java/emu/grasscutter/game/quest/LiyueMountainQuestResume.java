package emu.grasscutter.game.quest;

import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;

/** Preserve MQ1008's search stage rather than replaying its random-amber intro on login. */
final class LiyueMountainQuestResume {
    private LiyueMountainQuestResume() {}

    static boolean resumeOnLogin(GameMainQuest main) {
        if (main.getParentQuestId() != 1008 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE) return false;
        var intro = main.getChildQuestById(100808);
        var next = main.getChildQuestById(100805);
        if (intro == null || intro.getState() != QuestState.QUEST_STATE_FINISHED
                || next == null || next.getState() != QuestState.QUEST_STATE_UNSTARTED) return false;
        for (int id : new int[] {100801, 100802, 100803}) {
            var q = main.getChildQuestById(id);
            if (q == null || q.getQuestData() == null || q.getQuestData().getMainId() != 1008
                    || (q.getState() != QuestState.QUEST_STATE_UNSTARTED
                        && q.getState() != QuestState.QUEST_STATE_UNFINISHED
                        && q.getState() != QuestState.QUEST_STATE_FINISHED
                        && !(id == 100802 && q.getState() == QuestState.QUEST_STATE_FAILED))) return false;
            var accept = q.getQuestData().getAcceptCond();
            if (accept == null || accept.size() != 1) return false;
            var c = accept.get(0);
            if (c.getType() != QuestCond.QUEST_COND_STATE_EQUAL || c.getParam() == null
                    || c.getParam().length < 2 || c.getParam()[0] != 100808
                    || c.getParam()[1] != QuestState.QUEST_STATE_FINISHED.getValue()) return false;
        }
        var search = main.getChildQuestById(100804);
        var rescued = main.getChildQuestById(100803);
        if (search == null || search.getQuestData() == null || search.getQuestData().getMainId() != 1008
                || (search.getState() != QuestState.QUEST_STATE_UNSTARTED
                    && search.getState() != QuestState.QUEST_STATE_UNFINISHED
                    && search.getState() != QuestState.QUEST_STATE_FINISHED)) return false;
        var finish = rescued.getQuestData().getFinishCond();
        if (finish == null || finish.size() != 1
                || finish.get(0).getType() != QuestContent.QUEST_CONTENT_LUA_NOTIFY
                || !"13310390801".equals(finish.get(0).getParamStr())) return false;
        for (int id : new int[] {100801, 100802, 100803}) {
            var q = main.getChildQuestById(id);
            if (q.getState() == QuestState.QUEST_STATE_UNSTARTED) q.start();
        }
        var player = main.getOwner();
        var brother = main.getChildQuestById(100802);
        if (brother.getState() == QuestState.QUEST_STATE_FINISHED
                || rescued.getState() == QuestState.QUEST_STATE_FINISHED) {
            restore(main, 100801);
        }
        if (rescued.getState() == QuestState.QUEST_STATE_FINISHED) restore(main, 100802);
        intro.triggerStateEvents();
        for (int id : new int[] {100801, 100802, 100803, 100804}) {
            var q = main.getChildQuestById(id);
            if (q.getState() == QuestState.QUEST_STATE_FINISHED || q.getState() == QuestState.QUEST_STATE_FAILED)
                q.triggerStateEvents();
        }
        // A persisted Lua notification is evidence, not a reason to replay the intro/re-randomize amber.
        player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "13310390801", 0, 0);
        LiyueSceneGroupRecovery.restoreForQuest(main);
        LiyueAmberQuestSupport.restoreSavedBreak(main);
        return true;
    }

    private static void restore(GameMainQuest main, int id) {
        var player = main.getOwner();
        if (player.getPlayerProgress().getCurrentProgress(String.valueOf(id)) < 1)
            player.getProgressManager().addQuestProgress(id, 1);
        player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, id);
    }
}
