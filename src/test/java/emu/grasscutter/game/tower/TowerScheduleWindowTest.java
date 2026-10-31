package emu.grasscutter.game.tower;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.config.ConfigContainer.GameOptions.TowerOptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@ExtendWith(ServerResourceFixture.class)
@ResourceLock("GameData")
class TowerScheduleWindowTest {
    private TowerOptions originalOptions;
    private TowerSystem tower;
    private TowerScheduleConfig config;

    @BeforeEach
    void configureTower() {
        originalOptions = GAME_OPTIONS.tower;
        GAME_OPTIONS.tower = new TowerOptions();
        tower = new TowerSystem(null);
        config = tower.getTowerScheduleConfig();
        assertNotNull(config);
        config.setScheduleStartTime(null);
        config.setNextScheduleChangeTime(null);
    }

    @AfterEach
    void restoreTowerOptions() {
        GAME_OPTIONS.tower = originalOptions;
    }

    @Test
    void preservesAnActiveConfiguredWindow() {
        Date start = date(2026, 10, 1, 0, 0);
        Date end = date(2026, 11, 1, 0, 0);
        configureWindow(start, end);

        var window = tower.getScheduleWindow(date(2026, 10, 7, 12, 0));

        assertEquals(start, window.startTime());
        assertEquals(end, window.endTime());
    }

    @Test
    void preservesAConfiguredWindowThatStartsInTheFuture() {
        Date start = date(2026, 10, 15, 0, 0);
        Date end = date(2026, 11, 1, 0, 0);
        configureWindow(start, end);

        var window = tower.getScheduleWindow(date(2026, 10, 7, 12, 0));

        assertEquals(start, window.startTime());
        assertEquals(end, window.endTime());
    }

    @Test
    void missingEitherOrBothEndpointsUsesTheCurrentDailyWindow() {
        Date now = date(2026, 10, 7, 12, 0);
        Date start = date(2026, 10, 1, 0, 0);
        Date end = date(2026, 11, 1, 0, 0);

        configureWindow(null, end);
        assertWindow(now, date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
        configureWindow(start, null);
        assertWindow(now, date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
        configureWindow(null, null);
        assertWindow(now, date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
    }

    @Test
    void anExpiredWindowUsesTheCurrentDailyWindow() {
        configureWindow(date(2022, 6, 1, 0, 0), date(2022, 6, 30, 0, 0));

        assertWindow(date(2026, 10, 7, 12, 0), date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
    }

    @Test
    void aWindowEndingAtNowUsesTheCurrentDailyWindow() {
        Date now = date(2026, 10, 7, 12, 0);
        configureWindow(date(2026, 10, 1, 0, 0), now);

        assertWindow(now, date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
    }

    @Test
    void equalEndpointsUseTheCurrentDailyWindow() {
        Date endpoint = date(2026, 10, 15, 0, 0);
        configureWindow(endpoint, endpoint);

        assertWindow(date(2026, 10, 7, 12, 0), date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
    }

    @Test
    void reversedEndpointsUseTheCurrentDailyWindow() {
        configureWindow(date(2026, 11, 1, 0, 0), date(2026, 10, 15, 0, 0));

        assertWindow(date(2026, 10, 7, 12, 0), date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
    }

    @Test
    void theMinuteBeforeRolloverBelongsToThePreviousDay() {
        assertWindow(date(2026, 10, 7, 3, 59), date(2026, 10, 6, 4, 0), date(2026, 10, 7, 4, 0));
    }

    @Test
    void rolloverStartsTheNewDailyWindow() {
        assertWindow(date(2026, 10, 7, 4, 0), date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
    }

    @Test
    void aDailyWindowCanCrossTheYearBoundary() {
        assertWindow(date(2027, 1, 1, 3, 59), date(2026, 12, 31, 4, 0), date(2027, 1, 1, 4, 0));
        assertWindow(date(2027, 1, 1, 4, 0), date(2027, 1, 1, 4, 0), date(2027, 1, 2, 4, 0));
    }

    @Test
    void rotationUsesTheDailyWindowInsteadOfTheStaticConfig() {
        GAME_OPTIONS.tower.rotate = true;
        configureWindow(date(2022, 6, 1, 0, 0), date(2030, 6, 30, 0, 0));

        assertWindow(date(2026, 10, 7, 12, 0), date(2026, 10, 7, 4, 0), date(2026, 10, 8, 4, 0));
    }

    @Test
    void aPinnedScheduleKeepsItsConfiguredWindowWhenRotationIsEnabled() {
        GAME_OPTIONS.tower.scheduleId = 45;
        GAME_OPTIONS.tower.rotate = true;
        Date start = date(2022, 6, 1, 0, 0);
        Date end = date(2030, 6, 30, 0, 0);
        configureWindow(start, end);

        var window = tower.getScheduleWindow(date(2026, 10, 7, 12, 0));

        assertEquals(start, window.startTime());
        assertEquals(end, window.endTime());
        assertEquals(45, GAME_OPTIONS.tower.scheduleId);
    }

    private void configureWindow(Date start, Date end) {
        config.setScheduleStartTime(start);
        config.setNextScheduleChangeTime(end);
    }

    private void assertWindow(Date now, Date start, Date end) {
        var window = tower.getScheduleWindow(now);
        assertEquals(start, window.startTime());
        assertEquals(end, window.endTime());
        assertFalse(window.startTime().after(now));
        assertTrue(window.endTime().after(now));
    }

    private static Date date(int year, int month, int day, int hour, int minute) {
        return Date.from(
                LocalDateTime.of(year, month, day, hour, minute)
                        .atZone(ZoneId.systemDefault())
                        .toInstant());
    }
}
