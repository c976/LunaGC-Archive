package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

final class WeeklyBossClaimStateTest {
    private static final ZoneId SERVER = ZoneId.of("Asia/Shanghai");

    @Test
    void firstThreeClaimsAreDiscountedAndFourthCostsFullPrice() {
        for (int used = 0; used < 3; used++) {
            var quote = WeeklyBossClaimState.decide(used, used, false, 113003 + used, 60);
            assertTrue(quote.allowed());
            assertEquals(30, quote.cost());
            assertEquals(3 - used, quote.remainingDiscounts());
        }
        var fourth = WeeklyBossClaimState.decide(3, 3, false, 113087, 60);
        assertTrue(fourth.allowed());
        assertEquals(60, fourth.cost());
        assertEquals(0, fourth.remainingDiscounts());
    }

    @Test
    void sameBossCannotBeClaimedTwiceAcrossRunsOrDifficulties() {
        Instant now = Instant.parse("2026-10-26T08:00:00Z");
        var state = new WeeklyBossClaimState();
        var first = state.quote(now, 113017, 60);
        state.recordPaidClaim(now, 113017, first);
        assertEquals(WeeklyBossClaimState.Refusal.ALREADY_CLAIMED,
                state.quote(now, 113017, 60).refusal());
        assertEquals(30, state.quote(now, 113013, 60).cost());
        assertEquals(1, state.getTotalClaims(now));
    }

    @Test
    void pricingAloneDoesNotConsumeDiscountOnUnpaidAttempt() {
        Instant now = Instant.parse("2026-10-26T08:00:00Z");
        var state = new WeeklyBossClaimState();
        assertEquals(30, state.quote(now, 113003, 60).cost());
        assertEquals(30, state.quote(now, 113003, 60).cost());
        assertEquals(0, state.getTotalClaims(now));
    }

    @Test
    void mondayFourAmResetsDiscountAndBossClaim() {
        Instant before = Instant.parse("2026-10-25T19:59:59Z");
        Instant after = Instant.parse("2026-10-25T20:00:00Z");
        assertNotEquals(WeeklyBossClaimState.weekStart(before, SERVER),
                WeeklyBossClaimState.weekStart(after, SERVER));
        var state = new WeeklyBossClaimState();
        var quote = state.quote(before, 113003, 60);
        state.recordPaidClaim(before, 113003, quote);
        assertEquals(0, state.getTotalClaims(after));
        assertEquals(30, state.quote(after, 113003, 60).cost());
    }

    @Test
    void invalidCostAndBossFailClosed() {
        assertEquals(WeeklyBossClaimState.Refusal.INVALID_COST,
                WeeklyBossClaimState.decide(0, 0, false, 113003, 20).refusal());
        assertEquals(WeeklyBossClaimState.Refusal.INVALID_BOSS,
                WeeklyBossClaimState.decide(0, 0, false, 0, 60).refusal());
    }
}
