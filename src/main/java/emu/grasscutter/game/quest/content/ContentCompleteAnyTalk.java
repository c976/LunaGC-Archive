package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_COMPLETE_ANY_TALK;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import java.util.Arrays;

@QuestValueContent(QUEST_CONTENT_COMPLETE_ANY_TALK)
public class ContentCompleteAnyTalk extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        if (params.length == 0 || condition.getParamStr() == null || condition.getParamStr().isBlank()) {
            return false;
        }

        // COMPLETE_ANY_TALK carries the current talk. Previously completed talks must not satisfy a
        // later event.
        return Arrays.stream(condition.getParamStr().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .mapToInt(Integer::parseInt)
                .anyMatch(talkId -> talkId == params[0]);
    }
}
