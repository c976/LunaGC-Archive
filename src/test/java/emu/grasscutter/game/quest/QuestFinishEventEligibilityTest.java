package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import org.junit.jupiter.api.Test;

final class QuestFinishEventEligibilityTest {
    private static final Gson GSON = new Gson();

    @Test
    void activePlotQuestWithoutAcceptConditionsMustReceiveFinishEvent() {
        // 7.1 subquest 35104 starts without acceptCond, but must finish via FINISH_PLOT.
        var data = GSON.fromJson(
                """
                {
                  "subId": 35104,
                  "mainId": 351,
                  "order": 1,
                  "finishCond": [
                    {"type": "QUEST_CONTENT_FINISH_PLOT", "param": [35104, 0]}
                  ],
                  "failCond": []
                }
                """, QuestData.class);
        assertNull(data.getAcceptCond());

        var quest = new GameQuest(null, data);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);

        assertTrue(GameMainQuest.shouldHandleFinishEvent(
                quest, QuestContent.QUEST_CONTENT_FINISH_PLOT));
        assertFalse(GameMainQuest.shouldHandleFinishEvent(
                quest, QuestContent.QUEST_CONTENT_TRIGGER_FIRE));

        // The matching event is eligible to advance cumulative finish progress.
        assertTrue(QuestProgress.recordMatch(quest.getFinishProgressList(), 0, true));
        assertArrayEquals(new int[] {1}, quest.getFinishProgressList());
        assertFalse(QuestProgress.recordMatch(quest.getFinishProgressList(), 0, true));

        quest.setState(QuestState.QUEST_STATE_UNSTARTED);
        assertFalse(GameMainQuest.shouldHandleFinishEvent(
                quest, QuestContent.QUEST_CONTENT_FINISH_PLOT));

        quest.setState(QuestState.QUEST_STATE_FINISHED);
        assertFalse(GameMainQuest.shouldHandleFinishEvent(
                quest, QuestContent.QUEST_CONTENT_FINISH_PLOT));
    }

    @Test
    void ignoresMissingQuestDataAndUnrelatedEvents() {
        assertFalse(GameMainQuest.shouldHandleFinishEvent(
                null, QuestContent.QUEST_CONTENT_FINISH_PLOT));
        assertFalse(GameMainQuest.shouldHandleFinishEvent(new GameQuest(), null));

        var noConditions = GSON.fromJson(
                """
                {"subId": 90001, "mainId": 900, "finishCond": [], "failCond": []}
                """, QuestData.class);
        var quest = new GameQuest(null, noConditions);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);

        assertFalse(GameMainQuest.shouldHandleFinishEvent(
                quest, QuestContent.QUEST_CONTENT_FINISH_PLOT));
    }
}
