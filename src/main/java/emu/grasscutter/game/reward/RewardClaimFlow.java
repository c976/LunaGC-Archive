package emu.grasscutter.game.reward;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.RewardGrantJournal;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Shared claim ordering for configured chest and ley-line rewards.
 * Receipts block ambiguous retries; they are not a cross-resource database transaction.
 */
public final class RewardClaimFlow {
    private RewardClaimFlow() {}

    public interface Receipt {
        boolean completed();
        boolean prepare();
        void confirm();
        void abortUnpaid();
        void complete();
    }

    public interface Delivery {
        /** Preflight must finish before authorize; confirmed must run before any reward writes. */
        boolean admit(BooleanSupplier authorize, Runnable confirmed);
        void publish();
    }

    public static Receipt receipt(RewardGrantJournal journal, RewardGrantJournal.Key key,
            List<RewardGrantJournal.Reward> rewards, List<RewardGrantJournal.Debit> debits) {
        return new Receipt() {
            public boolean completed() { return journal.completed(key); }
            public boolean prepare() { return journal.prepare(key, rewards, debits); }
            public void confirm() { journal.confirm(key); }
            public void abortUnpaid() { journal.abortUnpaid(key); }
            public void complete() { journal.complete(key); }
            @Override public String toString() { return key.storageId(); }
        };
    }

    public static boolean grant(Receipt receipt, Delivery delivery, BooleanSupplier pay,
            Runnable claim, Runnable unpaid) {
        if (receipt.completed()) {
            claim.run();
            return true;
        }
        boolean accepted = delivery.admit(() -> {
            if (!receipt.prepare()) return false;
            if (pay.getAsBoolean()) return true;
            // Only an explicit false result proves that payment did not happen.
            receipt.abortUnpaid();
            unpaid.run();
            return false;
        }, () -> {
            claim.run();
            receipt.confirm();
        });
        if (!accepted) return false;
        delivery.publish();
        try {
            receipt.complete();
        } catch (RuntimeException failure) {
            // Delivery already happened; callers must still finish the claim once.
            Grasscutter.getLogger().error("Reward delivered; receipt needs reconciliation: {}", receipt, failure);
        }
        return true;
    }
}
