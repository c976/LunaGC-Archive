package emu.grasscutter.utils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** 上海时间 04:00 开始的游戏日及游戏周。 */
public final class GameDay {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private GameDay() {}

    public static LocalDate date(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(ZONE).minusHours(4).toLocalDate();
    }

    public static int dateKey(long epochSeconds) {
        var date = date(epochSeconds);
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    public static boolean crossedWeeklyReset(long lastReset, long currentTime) {
        var lastWeek = date(lastReset).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var currentWeek = date(currentTime).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return currentWeek.isAfter(lastWeek);
    }
}
