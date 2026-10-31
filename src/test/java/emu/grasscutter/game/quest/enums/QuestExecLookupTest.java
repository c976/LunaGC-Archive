package emu.grasscutter.game.quest.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * QuestExec's lookup map must index QUEST_EXEC names, never QUEST_CONTENT.
 * An empty map makes every dynamic ID/name lookup silently resolve to NONE.
 */
final class QuestExecLookupTest {
    @Test
    void allQuestExecutionOpCodesRoundTripByValueAndName() {
        for (var exec : QuestExec.values()) {
            assertEquals(exec, QuestExec.getContentTriggerByValue(exec.getValue()), exec.name());
            assertEquals(exec, QuestExec.getContentTriggerByName(exec.name()), exec.name());
        }
    }

    @Test
    void unsupportedWeatherActionIsDecodedButNotClaimedToBeImplemented() {
        assertEquals(QuestExec.QUEST_EXEC_SET_WEATHER_GADGET,
                QuestExec.getContentTriggerByValue(22));
        assertEquals(QuestExec.QUEST_EXEC_NOTIFY_GROUP_LUA,
                QuestExec.getContentTriggerByName("QUEST_EXEC_NOTIFY_GROUP_LUA"));
        assertEquals(QuestExec.QUEST_EXEC_NONE,
                QuestExec.getContentTriggerByName("NOT_AN_EXEC_TYPE"));
    }
}
