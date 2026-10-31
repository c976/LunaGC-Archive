package emu.grasscutter.game.dungeons;

import static emu.grasscutter.game.dungeons.DomainStatueClaimHelper.ClaimMode.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class DomainClaimPaymentPreflightTest {
    private static boolean affordable(
            DomainStatueClaimHelper.ClaimMode mode, int costId, int costCount,
            boolean enabled, int resin, int condensed, int fragile, int used, int primos) {
        return DomainClaimPaymentPreflight.affordable(
                mode, costId, costCount, enabled, resin, condensed, fragile, used, primos);
    }

    @Test
    void ordinaryAndDoubleResinMustBeAvailableBeforeJournalPreparation() {
        assertFalse(affordable(NORMAL_1X, 106, 20, true, 19, 0, 0, 0, 0));
        assertTrue(affordable(NORMAL_1X, 106, 20, true, 20, 0, 0, 0, 0));
        assertFalse(affordable(NORMAL_2X, 106, 20, true, 39, 0, 0, 0, 0));
        assertTrue(affordable(NORMAL_2X, 106, 20, true, 40, 0, 0, 0, 0));
        assertTrue(affordable(NORMAL_1X, 106, 20, false, 0, 0, 0, 0, 0));
        assertTrue(affordable(NORMAL_1X, 999, 20, true, 0, 0, 0, 0, 0));
    }

    @Test
    void alternativePaymentPreflightMatchesActualChargeRules() {
        assertFalse(affordable(CONDENSE, 106, 20, true, 0, 0, 0, 0, 0));
        assertTrue(affordable(CONDENSE, 106, 20, true, 0, 1, 0, 0, 0));
        assertFalse(affordable(CONDENSE, 106, 40, true, 0, 1, 0, 0, 0));
        assertFalse(affordable(FRAGILE, 106, 20, false, 0, 0, 0, 0, 0));
        assertTrue(affordable(FRAGILE, 106, 20, true, 0, 0, 1, 0, 0));
        assertFalse(affordable(HCOIN, 106, 20, true, 0, 0, 0, 0, 49));
        assertTrue(affordable(HCOIN, 106, 20, true, 0, 0, 0, 0, 50));
        assertFalse(affordable(HCOIN, 106, 20, true, 0, 0, 0, 15, 10000));
    }
}
