package emu.grasscutter.game.achievement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class AchievementWatchersTest {
    @Test
    void numericParamsMustMatchByIndex() {
        assertTrue(AchievementWatchers.paramsMatch(new String[] {"70510009"}, List.of(70510009)));
        assertFalse(AchievementWatchers.paramsMatch(new String[] {"70510009"}, List.of(70510010)));
        assertFalse(AchievementWatchers.paramsMatch(new String[] {"70510009"}, List.of()));
    }

    @Test
    void namedAndEmptyParamsAreSkipped() {
        assertTrue(
                AchievementWatchers.paramsMatch(
                        new String[] {"3", "SitPointOnStatue_ForAchievement"}, List.of(3)));
        assertTrue(AchievementWatchers.paramsMatch(new String[] {"", ""}, List.of()));
        assertTrue(AchievementWatchers.paramsMatch(null, List.of()));
    }
}
