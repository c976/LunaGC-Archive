package emu.grasscutter.game.dungeons;

import static emu.grasscutter.game.dungeons.DomainStatueClaimHelper.ClaimMode.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class WeeklyBossPaymentTest {
    @Test
    void variantPreviewsShareOneBossIdentity() {
        assertEquals(113017, WeeklyBossDropResolver.bossIdentity(
                java.util.Set.of(113017, 113018, 113019, 113021)));
        assertEquals(113017, WeeklyBossDropResolver.bossIdentity(
                java.util.Set.of(113019, 113017, 113021)));
        assertEquals(0, WeeklyBossDropResolver.bossIdentity(java.util.Set.of(113021)));
    }

    @Test
    void onlyAcceptsExplicitNativeThirtyOrSixtyResinSingleClaims() {
        assertTrue(DomainStatueDropService.validWeeklyBossPayment(NORMAL_1X, 106, 30));
        assertTrue(DomainStatueDropService.validWeeklyBossPayment(NORMAL_1X, 106, 60));
        assertFalse(DomainStatueDropService.validWeeklyBossPayment(NORMAL_1X, 0, 0));
        assertFalse(DomainStatueDropService.validWeeklyBossPayment(NORMAL_1X, 106, 20));
        assertFalse(DomainStatueDropService.validWeeklyBossPayment(NORMAL_2X, 106, 30));
        assertFalse(DomainStatueDropService.validWeeklyBossPayment(CONDENSE, 106, 30));
        assertFalse(DomainStatueDropService.validWeeklyBossPayment(FRAGILE, 106, 60));
    }
}
