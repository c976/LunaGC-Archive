package emu.grasscutter.game.activity.condition.all;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static emu.grasscutter.game.activity.condition.ActivityConditions.NEW_ACTIVITY_COND_QUEST_FINISH;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.condition.*;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;

@ActivityCondition(NEW_ACTIVITY_COND_QUEST_FINISH)
public class QuestFinished extends ActivityConditionBaseHandler {
    @Override
    public boolean execute(
            PlayerActivityData activityData, ActivityConfigItem activityConfig, int... params) {
        // With questing off no quest ever finishes, which would lock nearly every 7.x activity
        // for good. PlayerProgressManager unlocks quest-gated open states the same way.
        if (!GAME_OPTIONS.questing.enabled) {
            return true;
        }

        GameQuest quest = activityData.getPlayer().getQuestManager().getQuestById(params[0]);

        return quest != null && quest.getState() == QuestState.QUEST_STATE_FINISHED;
    }
}
