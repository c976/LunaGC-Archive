package emu.grasscutter.game.dungeons;

import static emu.grasscutter.game.dungeons.DomainStatueClaimHelper.ClaimMode.*;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.RewardGrantJournal.Debit;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DomainClaimPaymentSnapshotTest {
    @Test
    void normalAndDoubleClaimsRecordExpectedDebits() {
        assertEquals(List.of(new Debit(106, 20)),
                DomainClaimPaymentSnapshot.debits(NORMAL_1X, 106, 20, true, 0));
        assertEquals(List.of(new Debit(106, 40)),
                DomainClaimPaymentSnapshot.debits(NORMAL_2X, 106, 20, true, 0));
        assertTrue(DomainClaimPaymentSnapshot.debits(NORMAL_1X, 999, 20, true, 0).isEmpty());
    }

    @Test
    void alternatePaymentAccountsForResinConfiguration() {
        assertEquals(List.of(new Debit(220007, 1)),
                DomainClaimPaymentSnapshot.debits(CONDENSE, 106, 20, true, 0));
        assertEquals(List.of(new Debit(107009, 1)),
                DomainClaimPaymentSnapshot.debits(FRAGILE, 106, 20, false, 0));
        assertEquals(List.of(new Debit(201, 100)),
                DomainClaimPaymentSnapshot.debits(HCOIN, 106, 20, true, 1));
        assertTrue(DomainClaimPaymentSnapshot.debits(NORMAL_2X, 106, 20, false, 0).isEmpty());
        assertTrue(DomainClaimPaymentSnapshot.debits(HCOIN, 106, 20, false, 0).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> DomainClaimPaymentSnapshot.debits(HCOIN, 106, 20, true, 15));
    }
}
