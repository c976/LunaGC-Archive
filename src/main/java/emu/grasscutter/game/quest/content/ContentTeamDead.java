package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_TEAM_DEAD;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

/**
 * A team wipe is emitted by EntityAvatar only after the last active avatar dies.
 * The explicit event flag prevents accidental failure on an unrelated event.
 */
@QuestValueContent(QUEST_CONTENT_TEAM_DEAD)
public final class ContentTeamDead extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition,
            String paramStr, int... params) {
        return params.length > 0 && params[0] == 1;
    }
}
