package emu.grasscutter.game.player;

import dev.morphia.annotations.Entity;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

/** 玩家跨副本的周本领取记录。 */
@Entity
public final class WeeklyBossClaimState {
    // 7.1 ConstValueExcelConfigData: CONST_VALUE_WEEKLY_BOSS_RESIN_DISCOUNT_PARAM = [3, 0.5]。
    public static final int DISCOUNT_LIMIT = 3;
    // 7.1 周本 boss_chest.resin 为 60；原版按首领每周限领一次。
    public static final int ORIGINAL_RESIN_COST = 60;
    // 与 ShopSystem 的每周刷新采用同一服务器时区和 4 点切换。
    public static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");

    private long weekStart;
    private int discountedClaims;
    private int totalClaims;
    private Set<Integer> claimedBosses = new HashSet<>();

    public enum Refusal { NONE, ALREADY_CLAIMED, INVALID_BOSS, INVALID_COST }

    public record Quote(int cost, int originalCost, int remainingDiscounts, Refusal refusal) {
        public boolean allowed() { return refusal == Refusal.NONE; }
        public boolean discounted() { return allowed() && cost < originalCost; }
    }

    public static long weekStart(Instant instant, ZoneId zone) {
        var local = instant.atZone(zone).minusHours(4);
        return local.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atTime(4, 0).atZone(zone).toEpochSecond();
    }

    public static Quote decide(int discountedClaims, int totalClaims, boolean claimedBoss,
                               int bossId, int originalCost) {
        if (bossId <= 0) return new Quote(0, originalCost, 0, Refusal.INVALID_BOSS);
        if (originalCost != ORIGINAL_RESIN_COST || discountedClaims < 0 || totalClaims < 0
                || discountedClaims > totalClaims) {
            return new Quote(0, originalCost, 0, Refusal.INVALID_COST);
        }
        int remaining = Math.max(0, DISCOUNT_LIMIT - discountedClaims);
        if (claimedBoss) return new Quote(0, originalCost, remaining, Refusal.ALREADY_CLAIMED);
        return new Quote(remaining > 0 ? originalCost / 2 : originalCost,
                originalCost, remaining, Refusal.NONE);
    }

    public synchronized Quote quote(Instant now, int bossId, int originalCost) {
        resetIfNeeded(now);
        return decide(discountedClaims, totalClaims, claimedBosses.contains(bossId),
                bossId, originalCost);
    }

    public synchronized void recordPaidClaim(Instant now, int bossId, Quote quote) {
        Quote current = quote(now, bossId, quote.originalCost());
        if (!current.equals(quote) || !current.allowed()) {
            throw new IllegalStateException("Weekly boss claim changed before confirmation");
        }
        claimedBosses.add(bossId);
        totalClaims++;
        if (quote.discounted()) discountedClaims++;
    }

    public synchronized int getTotalClaims(Instant now) {
        resetIfNeeded(now);
        return totalClaims;
    }

    private void resetIfNeeded(Instant now) {
        long currentWeek = weekStart(now, SERVER_ZONE);
        if (weekStart != currentWeek || claimedBosses == null) {
            weekStart = currentWeek;
            discountedClaims = 0;
            totalClaims = 0;
            claimedBosses = new HashSet<>();
        }
    }
}
