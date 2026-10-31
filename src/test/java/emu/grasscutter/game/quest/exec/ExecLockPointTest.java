package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import org.junit.jupiter.api.Test;

final class ExecLockPointTest {
    @Test
    void lockPointActionIsRegisteredToOpcode17() {
        var registration = ExecLockPoint.class.getAnnotation(QuestValueExec.class);
        assertNotNull(registration);
        assertEquals(QuestExec.QUEST_EXEC_LOCK_POINT, registration.value());
        assertEquals(17, registration.value().getValue());
    }

    @Test
    void pointValidationRejectsInvalidSceneAndPoint() {
        assertTrue(ExecLockPoint.validPoint(3, 1720));
        assertFalse(ExecLockPoint.validPoint(0, 1720));
        assertFalse(ExecLockPoint.validPoint(3, 0));
        assertFalse(ExecLockPoint.validPoint(-1, 1720));
    }
}
