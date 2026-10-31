package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.enums.LogicType;
import org.junit.jupiter.api.Test;

final class QuestProgressTest {
    @Test
    void threeSeparateDungeonEventsAccumulateIntoAnAndObjective() {
        var progress = new int[3];

        assertTrue(QuestProgress.recordMatch(progress, 0, true));
        assertFalse(QuestProgress.recordMatch(progress, 1, false));
        assertFalse(QuestProgress.recordMatch(progress, 2, false));
        assertArrayEquals(new int[]{1, 0, 0}, progress);
        assertFalse(LogicType.calculate(LogicType.LOGIC_AND, progress));

        assertFalse(QuestProgress.recordMatch(progress, 0, false));
        assertTrue(QuestProgress.recordMatch(progress, 1, true));
        assertArrayEquals(new int[]{1, 1, 0}, progress);
        assertFalse(LogicType.calculate(LogicType.LOGIC_AND, progress));

        assertTrue(QuestProgress.recordMatch(progress, 2, true));
        assertArrayEquals(new int[]{1, 1, 1}, progress);
        assertTrue(LogicType.calculate(LogicType.LOGIC_AND, progress));
    }

    @Test
    void unrelatedOrRepeatedEventsDoNotEraseOrResendCompletion() {
        var progress = new int[2];
        assertTrue(QuestProgress.recordMatch(progress, 0, true));
        assertFalse(QuestProgress.recordMatch(progress, 0, false));
        assertFalse(QuestProgress.recordMatch(progress, 0, true));
        assertFalse(QuestProgress.recordMatch(progress, 1, false));
        assertArrayEquals(new int[]{1, 0}, progress);
        assertTrue(LogicType.calculate(LogicType.LOGIC_OR, progress));
    }

    @Test
    void twoAcceptancePredicatesOfTheSameTypeRemainSatisfied() {
        // Two required quest-state transitions arrive as separate events.
        var accepted = new int[2];
        QuestProgress.recordCurrent(accepted, 0, true);
        QuestProgress.recordCurrent(accepted, 1, false);
        assertFalse(LogicType.calculate(LogicType.LOGIC_AND, accepted));
        QuestProgress.recordCurrent(accepted, 1, true);
        assertTrue(LogicType.calculate(LogicType.LOGIC_AND, accepted));
    }

    @Test
    void liveNotEqualGateCanBecomeFalseBeforeAnotherPrerequisiteFinishes() {
        // 35103: prerequisite 35105 must still be unfinished when 35106 finishes.
        var accepted = new int[2];
        QuestProgress.recordCurrent(accepted, 1, true);
        QuestProgress.recordCurrent(accepted, 1, false);
        QuestProgress.recordCurrent(accepted, 0, true);
        assertArrayEquals(new int[]{1, 0}, accepted);
        assertFalse(LogicType.calculate(LogicType.LOGIC_AND, accepted));
    }

    @Test
    void explicitRewindStartsFreshProgress() {
        var oldProgress = new int[]{1, 1};
        var fresh = new int[oldProgress.length];
        assertFalse(LogicType.calculate(LogicType.LOGIC_AND, fresh));
        assertArrayEquals(new int[]{1, 1}, oldProgress);
        assertArrayEquals(new int[]{0, 0}, fresh);
    }
}
