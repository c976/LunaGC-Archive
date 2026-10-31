package emu.grasscutter.game.quest.content;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.QuestValueContent;
import emu.grasscutter.game.quest.enums.QuestContent;
import org.junit.jupiter.api.Test;

final class ContentTeamDeadTest {
    @Test
    void onlyExplicitTeamWipeEventsSatisfyTheFailureCondition() {
        var handler = new ContentTeamDead();
        assertFalse(handler.execute(null, null, ""));
        assertFalse(handler.execute(null, null, "", 0));
        assertTrue(handler.execute(null, null, "", 1));
    }

    @Test
    void registersTheExactQuestContentType() {
        assertEquals(QuestContent.QUEST_CONTENT_TEAM_DEAD,
                ContentTeamDead.class.getAnnotation(QuestValueContent.class).value());
    }
}
