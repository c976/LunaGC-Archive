package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.RewardGrantJournal.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RewardGrantJournalTest {
    private static final Key CLAIM = new Key(Source.MAIL, 10001, "deadbeef01");
    private static final List<Reward> REWARDS = List.of(new Reward(202, 100, 1));

    private static final class FakeStore implements RewardGrantJournal.Store {
        private final Map<Key, Receipt> receipts = new HashMap<>();

        @Override
        public boolean completed(Key key) {
            return receipts.containsKey(key) && receipts.get(key).stage() == Stage.COMPLETED;
        }

        @Override
        public boolean insert(Receipt receipt) {
            return receipts.putIfAbsent(receipt.key(), receipt) == null;
        }

        @Override
        public boolean transition(Key key, Stage from, Stage to) {
            Receipt old = receipts.get(key);
            if (old == null || old.stage() != from) return false;
            receipts.put(key, new Receipt(key, to, old.rewards(), old.debits()));
            return true;
        }

        @Override
        public List<Receipt> unresolved() {
            return receipts.values().stream()
                    .filter(r -> r.stage() == Stage.PREPARED || r.stage() == Stage.CONFIRMED)
                    .toList();
        }
    }

    @Test
    void startsAsPendingAndRejectsDuplicateClaims() {
        var journal = new RewardGrantJournal(new FakeStore());
        assertTrue(journal.prepare(CLAIM, REWARDS));
        assertFalse(journal.prepare(CLAIM, List.of(new Reward(201, 1, 1))));
        assertEquals(REWARDS, journal.unresolved().getFirst().rewards());
        assertEquals(Stage.PREPARED, journal.unresolved().getFirst().stage());
    }

    @Test
    void confirmedGrantIsVisibleForReconciliationUntilCompleted() {
        var journal = new RewardGrantJournal(new FakeStore());
        assertFalse(journal.completed(CLAIM));
        journal.prepare(CLAIM, REWARDS);
        assertFalse(journal.completed(CLAIM));
        journal.confirm(CLAIM);
        assertFalse(journal.completed(CLAIM));
        assertEquals(Stage.CONFIRMED, journal.unresolved().getFirst().stage());
        journal.complete(CLAIM);
        assertTrue(journal.unresolved().isEmpty());
        assertTrue(journal.completed(CLAIM));
        assertFalse(journal.prepare(CLAIM, REWARDS));
    }

    @Test
    void rejectsOutOfOrderAndRepeatedStateTransitions() {
        var journal = new RewardGrantJournal(new FakeStore());
        assertThrows(IllegalStateException.class, () -> journal.confirm(CLAIM));
        journal.prepare(CLAIM, REWARDS);
        assertThrows(IllegalStateException.class, () -> journal.complete(CLAIM));
        journal.confirm(CLAIM);
        assertThrows(IllegalStateException.class, () -> journal.confirm(CLAIM));
        journal.complete(CLAIM);
        assertThrows(IllegalStateException.class, () -> journal.complete(CLAIM));
    }

    @Test
    void rejectsUnsafeIdentitiesAndRewards() {
        assertThrows(IllegalArgumentException.class, () -> new Key(Source.MAIL, 0, "id"));
        assertThrows(IllegalArgumentException.class, () -> new Key(Source.MAIL, 10, "../../x"));
        assertThrows(IllegalArgumentException.class, () -> new Reward(202, -5, 1));
        assertThrows(IllegalArgumentException.class, () -> new Reward(202, 1, -1));
        assertThrows(IllegalArgumentException.class, () ->
                new Receipt(CLAIM, Stage.PREPARED, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Receipt(CLAIM, Stage.PREPARED, new ArrayList<>()));
    }

    @Test
    void zeroPayoutClaimKeepsAReceiptAndCannotBeReopened() {
        var journal = new RewardGrantJournal(new FakeStore());
        var key = new Key(Source.TRANSPOINT, 10001, "point.3.6");
        assertTrue(journal.prepare(key, List.of()));
        assertEquals(List.of(), journal.unresolved().getFirst().rewards());
        journal.confirm(key);
        journal.complete(key);
        assertTrue(journal.completed(key));
        assertFalse(journal.prepare(key, REWARDS));
    }

    @Test
    void copiesTheRewardSnapshot() {
        var input = new ArrayList<>(REWARDS);
        var journal = new RewardGrantJournal(new FakeStore());
        journal.prepare(CLAIM, input);
        input.clear();
        assertEquals(REWARDS, journal.unresolved().getFirst().rewards());
        assertEquals("mail:10001:deadbeef01", CLAIM.storageId());
    }
    @Test
    void preservesPaymentSnapshotThroughAllStages() {
        var journal = new RewardGrantJournal(new FakeStore());
        var debits = new ArrayList<>(List.of(new Debit(202, 200), new Debit(201, 5)));
        assertTrue(journal.prepare(CLAIM, REWARDS, debits));
        debits.clear();
        journal.confirm(CLAIM);
        var receipt = journal.unresolved().getFirst();
        assertEquals(Stage.CONFIRMED, receipt.stage());
        assertEquals(List.of(new Debit(202, 200), new Debit(201, 5)), receipt.debits());
        assertThrows(UnsupportedOperationException.class, () -> receipt.debits().clear());
        journal.complete(CLAIM);
        assertTrue(journal.unresolved().isEmpty());
    }

    @Test
    void rejectsMalformedDebitAndRetainsOldReceiptConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new Debit(202, 0));
        assertThrows(IllegalArgumentException.class, () -> new Debit(-1, 1));
        assertTrue(new Receipt(CLAIM, Stage.PREPARED, REWARDS).debits().isEmpty());
    }

    @Test
    void knownUnpaidAttemptCanAbortButCannotReuseReceiptIdentity() {
        var journal = new RewardGrantJournal(new FakeStore());
        assertTrue(journal.prepare(CLAIM, REWARDS));
        journal.abortUnpaid(CLAIM);
        assertTrue(journal.unresolved().isEmpty());
        assertFalse(journal.completed(CLAIM));
        assertFalse(journal.prepare(CLAIM, REWARDS));
        assertThrows(IllegalStateException.class, () -> journal.confirm(CLAIM));
        assertThrows(IllegalStateException.class, () -> journal.abortUnpaid(CLAIM));
    }

    @Test
    void confirmedAttemptCannotBeAborted() {
        var journal = new RewardGrantJournal(new FakeStore());
        assertTrue(journal.prepare(CLAIM, REWARDS));
        journal.confirm(CLAIM);
        assertThrows(IllegalStateException.class, () -> journal.abortUnpaid(CLAIM));
        assertEquals(Stage.CONFIRMED, journal.unresolved().getFirst().stage());
    }

    @Test
    void failedDatabaseConfirmationLeavesUnresolvedIntentAndBlocksReuse() {
        var stored = new FakeStore();
        var journal = new RewardGrantJournal(new RewardGrantJournal.Store() {
            public boolean insert(Receipt receipt) { return stored.insert(receipt); }
            public boolean transition(Key key, Stage from, Stage to) {
                throw new IllegalStateException("database unavailable");
            }
            public List<Receipt> unresolved() { return stored.unresolved(); }
            public boolean completed(Key key) { return stored.completed(key); }
        });
        assertTrue(journal.prepare(CLAIM, REWARDS));
        assertThrows(IllegalStateException.class, () -> journal.confirm(CLAIM));
        assertEquals(Stage.PREPARED, journal.unresolved().getFirst().stage());
        assertFalse(journal.prepare(CLAIM, REWARDS));
    }

    @Test
    void failedDatabaseCompletionRetainsConfirmedReceiptWithoutReplayingGrant() {
        var stored = new FakeStore();
        var journal = new RewardGrantJournal(new RewardGrantJournal.Store() {
            public boolean insert(Receipt receipt) { return stored.insert(receipt); }
            public boolean transition(Key key, Stage from, Stage to) {
                if (to == Stage.COMPLETED) throw new IllegalStateException("database unavailable");
                return stored.transition(key, from, to);
            }
            public List<Receipt> unresolved() { return stored.unresolved(); }
            public boolean completed(Key key) { return stored.completed(key); }
        });
        assertTrue(journal.prepare(CLAIM, REWARDS));
        journal.confirm(CLAIM);
        assertThrows(IllegalStateException.class, () -> journal.complete(CLAIM));
        assertEquals(Stage.CONFIRMED, journal.unresolved().getFirst().stage());
        assertFalse(journal.prepare(CLAIM, REWARDS));
    }

}
