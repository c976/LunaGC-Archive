package emu.grasscutter.game.quest;

import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import java.util.List;

/** MQ1013's three reviewed worker branches must survive order-based login rewinds. */
final class LiyueCityQuestResume {
    private static final List<Integer> PARALLEL = List.of(101303, 101307, 101311, 101315, 101316, 101321, 101322, 101323);

    private LiyueCityQuestResume() {}

    static boolean resumeOnLogin(GameMainQuest main) {
        if (main.getParentQuestId() != 1013 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE) return false;
        var entry = main.getChildQuestById(101301);
        var next = main.getChildQuestById(101320);
        if (entry == null || entry.getState() != QuestState.QUEST_STATE_FINISHED
                || next == null || next.getState() != QuestState.QUEST_STATE_UNSTARTED) return false;
        for (int id : PARALLEL) {
            var quest = main.getChildQuestById(id);
            if (quest == null || quest.getQuestData() == null
                    || (quest.getState() != QuestState.QUEST_STATE_UNSTARTED
                        && quest.getState() != QuestState.QUEST_STATE_UNFINISHED
                        && quest.getState() != QuestState.QUEST_STATE_FINISHED
                        && !(quest.getState() == QuestState.QUEST_STATE_FAILED
                            && (id == 101303 || id == 101307 || id == 101311)))) return false;
            var data = quest.getQuestData();
            if (data.getMainId() != 1013 || data.getAcceptCond() == null
                    || data.getAcceptCond().size() != 1) return false;
            var condition = data.getAcceptCond().get(0);
            var params = condition.getParam();
            if (condition.getType() != QuestCond.QUEST_COND_STATE_EQUAL || params == null
                    || params.length < 2 || params[0] != 101301
                    || params[1] != QuestState.QUEST_STATE_FINISHED.getValue()) return false;
        }
        for (int id : new int[] {101315, 101316, 101321, 101322, 101323}) {
            var data = main.getChildQuestById(id).getQuestData();
            if (data.getFinishCond() == null || data.getFinishCond().size() != 1) return false;
            var finish = data.getFinishCond().get(0);
            if (finish.getType() != QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS
                    || finish.getParam() == null || finish.getParam().length == 0
                    || finish.getParam()[0] != id || finish.getCount() != (id == 101315 ? 3 : 1)) return false;
        }
        LiyueCityPurseSupport.restoreMissing(main);
        for (int id : PARALLEL) {
            var quest = main.getChildQuestById(id);
            if (quest.getState() == QuestState.QUEST_STATE_UNSTARTED) quest.start();
        }
        for (int id = 1; id <= 3; id++) {
            LiyueBargainSupport.prepare(main.getOwner(), id);
        }
        int paid = 0;
        for (int bargain : new int[] {101303, 101307, 101311}) {
            var state = main.getChildQuestById(bargain).getState();
            restore(main, 101321 + (bargain - 101303) / 4,
                    state == QuestState.QUEST_STATE_FINISHED || state == QuestState.QUEST_STATE_FAILED ? 1 : 0);
            if (state == QuestState.QUEST_STATE_FINISHED || finished(main, bargain + 1)
                    || finished(main, bargain + 3)) paid++;
        }
        restore(main, 101315, paid);
        // An unfinished 101315 will add the next counter through its normal finish action.
        if (finished(main, 101315)) restore(main, 101316, 1);
        else main.getOwner().getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, 101316);
        entry.triggerStateEvents();
        for (var quest : main.getChildQuests().values()) {
            int id = quest.getSubQuestId();
            if (id >= 101303 && id < 101320
                    && (quest.getState() == QuestState.QUEST_STATE_FINISHED
                        || quest.getState() == QuestState.QUEST_STATE_FAILED)) quest.triggerStateEvents();
        }
        return true;
    }

    private static boolean finished(GameMainQuest main, int id) {
        var quest = main.getChildQuestById(id);
        return quest != null && quest.getState() == QuestState.QUEST_STATE_FINISHED;
    }

    private static void restore(GameMainQuest main, int id, int count) {
        var owner = main.getOwner();
        int saved = Math.max(0, owner.getPlayerProgress().getCurrentProgress(String.valueOf(id)));
        if (count > saved) owner.getProgressManager().addQuestProgress(id, count - saved);
        owner.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, id);
    }
}
