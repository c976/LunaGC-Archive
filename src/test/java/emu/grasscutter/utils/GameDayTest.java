package emu.grasscutter.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class GameDayTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void dailyQuotaAndPlayerResetKeepPreviousDateUntilFourAm() {
        int sunday = 20261025;
        assertEquals(sunday, GameDay.dateKey(at(2026, 10, 25, 23, 59)));
        assertEquals(sunday, GameDay.dateKey(at(2026, 10, 26, 0, 0)));
        assertEquals(sunday, GameDay.dateKey(at(2026, 10, 26, 3, 59)));
        assertEquals(20261026, GameDay.dateKey(at(2026, 10, 26, 4, 0)));
    }

    @Test
    void wholeWeekOfflineStillCrossesMondayFourAm() {
        long sunday = at(2026, 10, 25, 20, 0);
        assertFalse(GameDay.crossedWeeklyReset(sunday, at(2026, 10, 26, 3, 59)));
        assertTrue(GameDay.crossedWeeklyReset(sunday, at(2026, 10, 26, 4, 0)));
        assertTrue(GameDay.crossedWeeklyReset(sunday, at(2026, 10, 27, 12, 0)));
        assertFalse(GameDay.crossedWeeklyReset(at(2026, 10, 26, 5, 0), at(2026, 10, 27, 12, 0)));
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, SHANGHAI).toEpochSecond();
    }
}
