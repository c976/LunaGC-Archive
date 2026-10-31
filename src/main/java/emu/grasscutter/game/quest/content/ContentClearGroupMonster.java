package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import lombok.val;

@QuestValueContent(QUEST_CONTENT_CLEAR_GROUP_MONSTER)
public class ContentClearGroupMonster extends BaseContent {

    static boolean matchesGroupEvent(int expectedGroupId, int... eventParams) {
        return expectedGroupId > 0 && eventParams.length > 0
                && eventParams[0] == expectedGroupId;
    }

    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        if (condition == null || condition.getParam() == null
                || condition.getParam().length == 0) return false;
        val groupId = condition.getParam()[0];
        // Preserve the upstream event-source guard before the scoped suite check.
        if (!matchesGroupEvent(groupId, params)) return false;
        boolean jadeBattle = quest.getMainQuestId() == 1021
                && (quest.getSubQuestId() == 102102 || quest.getSubQuestId() == 102109)
                || quest.getMainQuestId() == 1022 && quest.getSubQuestId() == 102205;
        if (jadeBattle) {
            var scene = quest.getOwner().getScene();
            if (scene == null || scene.getId() != 3) return false;
            var instance = scene.getScriptManager().getGroupInstanceById(groupId);
            // An empty default suite cannot complete the task before its battle has spawned.
            if (instance == null || instance.getActiveSuiteId() < 2) return false;
        }
        return quest.getOwner().getScene().getScriptManager().isClearedGroupMonsters(groupId);
    }
}
