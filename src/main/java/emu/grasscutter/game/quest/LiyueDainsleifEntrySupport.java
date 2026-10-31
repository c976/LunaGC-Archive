package emu.grasscutter.game.quest;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.quest.enums.LogicType;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.game.quest.enums.QuestState;

/** Rechecks reviewed Dainsleif entry gates without completing prerequisites or moving the player. */
public final class LiyueDainsleifEntrySupport {
    private LiyueDainsleifEntrySupport() {}

    static boolean isEntryEvent(QuestCond type, int[] params) {
        if (params == null || params.length == 0) return false;
        return (type == QuestCond.QUEST_COND_QUEST_GLOBAL_VAR_EQUAL && params[0] == 10006)
                || (type == QuestCond.QUEST_COND_STATE_EQUAL
                        && (params[0] == 1800027 || params[0] == 45406))
                || (type == QuestCond.QUEST_COND_ACTIVITY_END && params[0] == 2003001);
    }

    static void onConditionEvent(QuestManager manager, QuestCond type, int[] params) {
        if (isEntryEvent(type, params)) resumeOnLogin(manager);
    }

    static void resumeOnLogin(QuestManager manager) {
        if (!QuestManager.isQuestingActive()) return;
        var end = manager.getQuestById(102510);
        var main = manager.getMainQuestById(1025);
        if (end == null || end.getState() != QuestState.QUEST_STATE_FINISHED
                || main == null || !main.isFinished()
                || manager.getQuestGlobalVarValue(10006) != 1) return;

        for (int id : new int[] {1800001, 800010, 800001}) {
            var data = GameData.getQuestDataMap().get(id);
            if (data == null || manager.wasSubQuestStarted(data)) continue;
            var parent = manager.getMainQuestById(data.getMainId());
            if (parent != null && parent.isFinished()) continue;
            var conditions = data.getAcceptCond();
            if (conditions == null || conditions.isEmpty()) continue;
            int[] progress = new int[conditions.size()];
            for (int i = 0; i < conditions.size(); i++) {
                var condition = conditions.get(i);
                var params = condition.getParam();
                if (condition.getType() == null || params == null || params.length == 0
                        || params[0] <= 0) continue;
                progress[i] = manager.getPlayer().getServer().getQuestSystem()
                        .triggerCondition(manager.getPlayer(), data, condition,
                                condition.getParamStr(), params) ? 1 : 0;
            }
            // Read all gates from current state, including a ended activity that has no live handler.
            if (LogicType.calculate(data.getAcceptCondComb(), progress)) manager.addQuest(data);
        }
    }
}
