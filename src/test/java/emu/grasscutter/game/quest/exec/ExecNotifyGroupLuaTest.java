package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.scripts.constants.EventType;
import org.junit.jupiter.api.Test;

final class ExecNotifyGroupLuaTest {
    @Test
    void queuedEventKeepsItsFinishOrStartMeaning() {
        assertEquals(EventType.EVENT_QUEST_FINISH,
                ExecNotifyGroupLua.eventTypeFor(QuestState.QUEST_STATE_FINISHED));
        assertEquals(EventType.EVENT_QUEST_START,
                ExecNotifyGroupLua.eventTypeFor(QuestState.QUEST_STATE_UNFINISHED));
    }

    @Test
    void thirdActDungeonFinishWaitsForMondstadtReturn() {
        var finished = QuestState.QUEST_STATE_FINISHED;
        // 39403 finishes in dungeon 1008; its Lua cleanup is in scene 3.
        assertFalse(ExecNotifyGroupLua.canDeliver(finished, finished, 3, 1008, false));
        assertTrue(ExecNotifyGroupLua.canDeliver(finished, finished, 3, 3, false));
        assertFalse(ExecNotifyGroupLua.canDeliver(finished,
                QuestState.QUEST_STATE_UNSTARTED, 3, 3, false));
        assertFalse(ExecNotifyGroupLua.canDeliver(finished, finished, 3, 3, true));
    }
}
