package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

final class PlayerProgressManagerTransPointStateTest {
    @Test
    void questLockPreservesPermanentUnlockState() {
        var unlocked = new HashSet<Integer>();
        var forceLocked = new HashSet<Integer>();
        unlocked.add(1720);

        assertTrue(PlayerProgressManager.applyQuestPointLock(forceLocked, 1720));
        assertTrue(unlocked.contains(1720));
        assertTrue(forceLocked.contains(1720));
    }

    @Test
    void releasingQuestLockDoesNotCreateASecondFirstUnlock() {
        var unlocked = new HashSet<Integer>();
        var forceLocked = new HashSet<Integer>();
        unlocked.add(1720);
        forceLocked.add(1720);

        var transition =
                PlayerProgressManager.applyTransPointUnlock(unlocked, forceLocked, 1720);

        assertTrue(transition.changed());
        assertFalse(transition.newlyUnlocked());
        assertTrue(transition.wasForceLocked());
        assertTrue(unlocked.contains(1720));
        assertFalse(forceLocked.contains(1720));
    }

    @Test
    void failedRewardDoesNotRevokeFirstUnlockAndCanRetry() {
        var unlocked = new HashSet<Integer>();
        var locked = new HashSet<Integer>();
        locked.add(7);
        var change = PlayerProgressManager.applyTransPointUnlock(unlocked, locked, 7);
        assertTrue(change.newlyUnlocked());
        var pending = new HashSet<Integer>();
        pending.add(7);

        assertFalse(PlayerProgressManager.settlePendingTransPointReward(pending, 7, () -> false));
        assertTrue(unlocked.contains(7));
        assertFalse(locked.contains(7));
        assertTrue(pending.contains(7));

        assertTrue(PlayerProgressManager.settlePendingTransPointReward(pending, 7, () -> true));
        assertTrue(unlocked.contains(7));
        assertFalse(pending.contains(7));
    }

    @Test
    void ambiguousRewardExceptionLeavesPendingForJournalInspection() {
        var pending = new HashSet<Integer>();
        pending.add(42);
        assertThrows(IllegalStateException.class,
                () -> PlayerProgressManager.settlePendingTransPointReward(
                        pending, 42, () -> { throw new IllegalStateException("delivery uncertain"); }));
        assertTrue(pending.contains(42));
    }

    @Test
    void successfulClaimCannotBeReissuedWithoutPendingState() {
        var pending = new HashSet<Integer>();
        pending.add(9);
        var deliveryCount = new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(PlayerProgressManager.settlePendingTransPointReward(
                pending, 9, () -> { deliveryCount.incrementAndGet(); return true; }));
        assertFalse(PlayerProgressManager.settlePendingTransPointReward(
                pending, 9, () -> { deliveryCount.incrementAndGet(); return true; }));
        assertFalse(PlayerProgressManager.settlePendingTransPointReward(
                pending, 99, () -> { deliveryCount.incrementAndGet(); return true; }));
        assertEquals(1, deliveryCount.get());
    }

    @Test
    void frozenSnapshotKeysAreSafeBsonFieldNames() {
        assertEquals("3:7", PlayerProgressManager.transPointSnapshotKey(3, 7));
        assertEquals("3:20", PlayerProgressManager.transPointSnapshotKey(3, 20));
        assertFalse(PlayerProgressManager.transPointSnapshotKey(3, 7).contains("."));
        assertNotEquals(PlayerProgressManager.transPointSnapshotKey(3, 7),
                PlayerProgressManager.transPointSnapshotKey(3, 20));
    }

    @Test
    void firstActivationSnapshotSurvivesConfigChangesAndFailedAdmission() {
        var snapshots = new java.util.HashMap<String, java.util.Map<Integer, Integer>>();
        var rolls = new java.util.concurrent.atomic.AtomicInteger();
        var original = PlayerProgressManager.freezeRewardSnapshot(snapshots, PlayerProgressManager.transPointSnapshotKey(3, 6), () -> {
            rolls.incrementAndGet();
            return java.util.Map.of(102, 10);
        });
        assertEquals(java.util.Map.of(102, 10), original);
        assertEquals(original, PlayerProgressManager.freezeRewardSnapshot(snapshots, PlayerProgressManager.transPointSnapshotKey(3, 6), () -> {
            rolls.incrementAndGet();
            return java.util.Map.of(201, 999);
        }));
        assertEquals(1, rolls.get());
        assertThrows(UnsupportedOperationException.class, () -> original.put(201, 999));
    }

    @Test
    void intentionalZeroPayoutIsFrozenAndNotRerolled() {
        var snapshots = new java.util.HashMap<String, java.util.Map<Integer, Integer>>();
        var rolls = new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(PlayerProgressManager.freezeRewardSnapshot(snapshots, PlayerProgressManager.transPointSnapshotKey(3, 7), () -> {
            rolls.incrementAndGet();
            return java.util.Map.of();
        }).isEmpty());
        assertTrue(PlayerProgressManager.freezeRewardSnapshot(snapshots, PlayerProgressManager.transPointSnapshotKey(3, 7), () -> {
            rolls.incrementAndGet();
            return java.util.Map.of(201, 5);
        }).isEmpty());
        assertEquals(1, rolls.get());
    }

    @Test
    void failedRollLeavesSnapshotAbsentForLaterValidConfiguration() {
        var snapshots = new java.util.HashMap<String, java.util.Map<Integer, Integer>>();
        assertThrows(IllegalStateException.class, () ->
                PlayerProgressManager.freezeRewardSnapshot(snapshots, PlayerProgressManager.transPointSnapshotKey(3, 8), () -> {
                    throw new IllegalStateException("configuration unavailable");
                }));
        assertFalse(snapshots.containsKey(PlayerProgressManager.transPointSnapshotKey(3, 8)));
        assertEquals(java.util.Map.of(201, 5), PlayerProgressManager.freezeRewardSnapshot(
                snapshots, PlayerProgressManager.transPointSnapshotKey(3, 8), () -> java.util.Map.of(201, 5)));
    }

    @Test
    void genuineFirstUnlockIsStillReportedAsNew() {
        var unlocked = new HashSet<Integer>();
        var forceLocked = new HashSet<Integer>();

        var transition =
                PlayerProgressManager.applyTransPointUnlock(unlocked, forceLocked, 6);

        assertTrue(transition.changed());
        assertTrue(transition.newlyUnlocked());
        assertFalse(transition.wasForceLocked());
        assertTrue(unlocked.contains(6));
    }
}
