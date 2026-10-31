package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_ENTER_MY_WORLD;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_ENTER_MY_WORLD)
public class ContentEnterMyWorld extends BaseContent {
    // params[0] is the scene entered by the player. Match the condition's
    // scene ID too: entering any scene must not satisfy unrelated objectives.
    static boolean matchesScene(int expectedSceneId, int enteredSceneId, int currentSceneId) {
        return expectedSceneId > 0
                && expectedSceneId == enteredSceneId
                && currentSceneId == enteredSceneId;
    }

    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        if (condition == null || condition.getParam() == null
                || condition.getParam().length == 0 || params.length == 0) return false;
        // Amber's first segment was already cleared; this login packet precedes native re-entry.
        if (MondstadtStoryQuestSupport.keepEarnedAmberContinuation(quest, params[0])) return false;
        return matchesScene(condition.getParam()[0], params[0], quest.getOwner().getSceneId());
    }
}
