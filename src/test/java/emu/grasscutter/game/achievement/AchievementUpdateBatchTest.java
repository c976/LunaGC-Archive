package emu.grasscutter.game.achievement;

import static emu.grasscutter.net.proto.AchievementOuterClass.Achievement.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AchievementUpdateBatchTest {
    private static Achievement achievement(int id) {
        return new Achievement(Status_FINISHED, id, 5, 5, 123);
    }

    @Test
    void collectsUniqueUpdatesAndCountsRealStatusChanges() {
        var changes = new Achievements.UpdateBatch();
        var a = achievement(10001);
        var b = achievement(10002);
        changes.record(a, true);
        changes.record(b, false);
        changes.record(a, false); // Linked stages can be reached more than once.

        var packets = new ArrayList<List<Achievement>>();
        changes.flush(packets::add);

        assertEquals(1, changes.changedCount());
        assertEquals(1, packets.size());
        assertEquals(List.of(a, b), packets.getFirst());
    }

    @Test
    void splitsLargeUpdatesIntoBoundedPacketsWithoutDroppingIds() {
        var changes = new Achievements.UpdateBatch();
        for (int id = 1; id <= 270; id++) {
            changes.record(achievement(id), true);
        }

        var packets = new ArrayList<List<Achievement>>();
        changes.flush(packets::add);

        assertEquals(270, changes.changedCount());
        assertEquals(List.of(128, 128, 14), packets.stream().map(List::size).toList());
        assertEquals(
                IntStream.rangeClosed(1, 270).boxed().toList(),
                packets.stream().flatMap(List::stream).map(Achievement::getId).toList());
    }

    @Test
    void grantAllCompletesIndependentIdsEvenWhenGroupFinalStageAlreadyFinished() {
        var earlierStage = new Achievement(Status_UNFINISHED, 10001, 5, 2, 0);
        var middleStage = new Achievement(Status_UNFINISHED, 10002, 10, 0, 0);
        var finalStage = new Achievement(Status_FINISHED, 10003, 20, 20, 100);
        var batch = new Achievements.UpdateBatch();
        var completedIds = new ArrayList<Integer>();

        Achievements.grantUnfinishedStages(
                List.of(earlierStage, middleStage, finalStage),
                stage -> {
                    completedIds.add(stage.getId());
                    stage.setStatus(stage.statusOnCompletion());
                    stage.setFinishTimestampSec(123);
                    return true;
                },
                batch);

        assertEquals(List.of(10001, 10002), completedIds);
        assertEquals(2, batch.changedCount());
        assertEquals(5, earlierStage.getCurProgress());
        assertEquals(10, middleStage.getCurProgress());
        assertEquals(20, finalStage.getCurProgress());
        assertEquals(100, finalStage.getFinishTimestampSec());
        assertEquals(Status_FINISHED, earlierStage.getStatus());
        assertEquals(Status_FINISHED, middleStage.getStatus());
        assertEquals(Status_FINISHED, finalStage.getStatus());
    }

    @Test
    void grantAllPreservesClaimedRewardsWhileRecompletingPreviouslyRevokedId() {
        var claimed = new Achievement(Status_REWARD_TAKEN, 10001, 5, 5, 123);
        claimed.setStatus(Status_UNFINISHED);
        claimed.setCurProgress(0);
        var unclaimed = new Achievement(Status_UNFINISHED, 10002, 3, 0, 0);
        var batch = new Achievements.UpdateBatch();

        Achievements.grantUnfinishedStages(
                List.of(claimed, unclaimed),
                stage -> {
                    stage.setStatus(stage.statusOnCompletion());
                    return true;
                },
                batch);

        assertEquals(2, batch.changedCount());
        assertEquals(5, claimed.getCurProgress());
        assertEquals(Status_REWARD_TAKEN, claimed.getStatus());
        assertTrue(claimed.hasClaimedReward());
        assertEquals(Status_FINISHED, unclaimed.getStatus());
        assertFalse(unclaimed.hasClaimedReward());
    }

    @Test
    void revokeAllResetsEachCompletedStageRegardlessOfParent() {
        var stageOne = achievement(10001);
        var stageTwo = achievement(10002);
        var parent = new Achievement(Status_UNFINISHED, 10003, 10, 4, 0);
        var changes = new Achievements.UpdateBatch();

        for (Achievement stage : List.of(stageOne, stageTwo, parent)) {
            if (Achievements.resetCompletedAchievement(stage)) {
                changes.record(stage, true);
            }
        }

        assertEquals(2, changes.changedCount());
        assertEquals(Status_UNFINISHED, stageOne.getStatus());
        assertEquals(Status_UNFINISHED, stageTwo.getStatus());
        assertEquals(0, stageOne.getCurProgress());
        assertEquals(0, stageTwo.getFinishTimestampSec());
        // An unfinished stage is not a completed achievement to revoke.
        assertEquals(4, parent.getCurProgress());
        assertEquals(Status_UNFINISHED, parent.getStatus());
    }

    @Test
    void revokeAllPreservesAlreadyClaimedRewardHistory() {
        var stage = new Achievement(Status_REWARD_TAKEN, 10001, 5, 5, 123);
        assertTrue(Achievements.resetCompletedAchievement(stage));

        assertEquals(Status_UNFINISHED, stage.getStatus());
        assertEquals(0, stage.getCurProgress());
        assertEquals(0, stage.getFinishTimestampSec());
        assertTrue(stage.hasClaimedReward());
        assertEquals(Status_REWARD_TAKEN, stage.statusOnCompletion());
        assertFalse(Achievements.resetCompletedAchievement(stage));
    }

    @Test
    void revokeAllLeavesUnfinishedAndInvalidStatesUntouched() {
        var unfinished = new Achievement(Status_UNFINISHED, 10001, 5, 3, 0);
        var invalid = new Achievement(Status_INVALID, 10002, 5, 0, 0);

        assertFalse(Achievements.resetCompletedAchievement(unfinished));
        assertFalse(Achievements.resetCompletedAchievement(invalid));
        assertFalse(Achievements.resetCompletedAchievement(null));
        assertEquals(3, unfinished.getCurProgress());
    }

    @Test
    void emptyBatchDoesNotSendPackets() {
        var changes = new Achievements.UpdateBatch();
        var packets = new ArrayList<List<Achievement>>();

        changes.flush(packets::add);

        assertTrue(changes.isEmpty());
        assertEquals(0, changes.changedCount());
        assertTrue(packets.isEmpty());
    }
}
