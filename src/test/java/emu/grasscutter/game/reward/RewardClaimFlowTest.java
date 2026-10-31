package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
class RewardClaimFlowTest {
    static final class Attempt implements RewardClaimFlow.Receipt, RewardClaimFlow.Delivery {
        final List<String> events = new ArrayList<>();
        boolean full, noFunds, paymentFailure, writeFailure, publishFailure, completionFailure;
        boolean prepared, confirmed, completed, claimed;
        int charges, writes;
        public boolean completed() { return completed; }
        public boolean prepare() {
            if (prepared) return false;
            events.add("prepare"); prepared = true; return true;
        }
        public void confirm() { events.add("confirm"); confirmed = true; }
        public void abortUnpaid() { events.add("abort"); }
        public void complete() {
            if (completionFailure) throw new IllegalStateException("receipt unavailable");
            events.add("complete"); completed = true;
        }
        public boolean admit(java.util.function.BooleanSupplier authorize, Runnable confirm) {
            events.add("preflight");
            if (full || !authorize.getAsBoolean()) return false;
            confirm.run();
            if (writeFailure) throw new IllegalStateException("partial inventory failure");
            events.add("write"); writes++; return true;
        }
        public void publish() {
            if (publishFailure) throw new IllegalStateException("partial scene failure");
            events.add("publish");
        }
        boolean run() {
            return RewardClaimFlow.grant(this, this, () -> {
                events.add("pay");
                if (noFunds) return false;
                charges++;
                if (paymentFailure) throw new IllegalStateException("paid but notification failed");
                return true;
            }, () -> { events.add("claim"); claimed = true; },
                    () -> { events.add("new-attempt"); prepared = false; });
        }
    }

    @Test void successfulClaimHasOneOrderingForInventoryAndGroundDelivery() {
        var attempt = new Attempt();
        assertTrue(attempt.run());
        assertEquals(List.of("preflight", "prepare", "pay", "claim", "confirm", "write", "publish", "complete"),
                attempt.events);
        assertEquals(1, attempt.charges);
        assertEquals(1, attempt.writes);
    }

    @Test void fullInventoryDoesNotPrepareChargeOrClaim() {
        var attempt = new Attempt(); attempt.full = true;
        assertFalse(attempt.run());
        assertEquals(List.of("preflight"), attempt.events);
        assertFalse(attempt.claimed);
        assertEquals(0, attempt.charges);
    }

    @Test void provenUnpaidFailureCanStartANewAttempt() {
        var attempt = new Attempt(); attempt.noFunds = true;
        assertFalse(attempt.run());
        assertFalse(attempt.claimed);
        assertTrue(attempt.events.contains("abort"));
        attempt.noFunds = false;
        assertTrue(attempt.run());
        assertEquals(1, attempt.charges);
    }

    @Test void ambiguousPaymentFailureNeverAbortsOrChargesAgain() {
        var attempt = new Attempt(); attempt.paymentFailure = true;
        assertThrows(IllegalStateException.class, attempt::run);
        assertTrue(attempt.prepared);
        assertFalse(attempt.events.contains("abort"));
        attempt.paymentFailure = false;
        assertFalse(attempt.run());
        assertEquals(1, attempt.charges);
        assertEquals(0, attempt.writes);
    }

    @Test void inventoryFailureKeepsConfirmationAndBlocksReplay() {
        var attempt = new Attempt(); attempt.writeFailure = true;
        assertThrows(IllegalStateException.class, attempt::run);
        assertTrue(attempt.claimed);
        assertTrue(attempt.confirmed);
        attempt.writeFailure = false;
        assertFalse(attempt.run());
        assertEquals(1, attempt.charges);
        assertFalse(attempt.completed);
    }

    @Test void groundFailureDoesNotReplayAlreadyDeliveredInventory() {
        var attempt = new Attempt(); attempt.publishFailure = true;
        assertThrows(IllegalStateException.class, attempt::run);
        attempt.publishFailure = false;
        assertFalse(attempt.run());
        assertEquals(1, attempt.writes);
        assertEquals(1, attempt.charges);
    }

    @Test void completedReceiptFinishesRecreatedChestWithoutNewPayout() {
        var attempt = new Attempt(); assertTrue(attempt.run());
        attempt.events.clear(); attempt.claimed = false;
        assertTrue(attempt.run());
        assertTrue(attempt.claimed);
        assertEquals(List.of("claim"), attempt.events);
        assertEquals(1, attempt.writes);
        assertEquals(1, attempt.charges);
    }

    @Test void receiptCompletionFailureStillReportsDeliveredAndPreventsReplay() {
        var attempt = new Attempt(); attempt.completionFailure = true;
        assertTrue(attempt.run());
        assertTrue(attempt.claimed);
        assertTrue(attempt.confirmed);
        assertFalse(attempt.completed);
        assertFalse(attempt.run());
        assertEquals(1, attempt.writes);
    }
}
