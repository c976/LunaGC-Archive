package emu.grasscutter.game.achievement;

import static emu.grasscutter.net.proto.AchievementOuterClass.Achievement.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AchievementRewardClaimTest {
    private static Achievement achievement(
            emu.grasscutter.net.proto.AchievementOuterClass.Achievement.Status status) {
        return new Achievement(status, 10001, 1, status == Status_UNFINISHED ? 0 : 1, 42);
    }

    private static Achievements savedAchievements(Achievement item) {
        var values = new HashMap<>(Map.of(item.getId(), item));
        return Achievements.of().achievementList(values).build();
    }

    @Test
    void claimedRewardRemainsClaimedAfterRevokeAndCompletion() {
        var item = achievement(Status_REWARD_TAKEN);
        var record = savedAchievements(item);

        assertTrue(record.isRewardTaken(10001));
        item.setStatus(Status_UNFINISHED);
        item.setCurProgress(0);

        assertTrue(item.hasClaimedReward());
        assertTrue(record.isRewardTaken(10001));
        assertFalse(record.isRewardLeft(10001));

        item.setCurProgress(1);
        assertEquals(Status_REWARD_TAKEN, item.statusOnCompletion());
        item.setStatus(item.statusOnCompletion());

        assertEquals(Status_REWARD_TAKEN, item.getStatus());
        assertFalse(record.isRewardLeft(10001));
    }

    @Test
    void unclaimedCompletedRewardRemainsAvailable() {
        var item = achievement(Status_FINISHED);
        var record = savedAchievements(item);

        assertFalse(item.hasClaimedReward());
        assertTrue(record.isRewardLeft(10001));
        assertEquals(Status_FINISHED, item.statusOnCompletion());
    }

    @Test
    void claimedRewardCannotBeReopenedBySettingFinishedStatus() {
        var item = achievement(Status_REWARD_TAKEN);
        var record = savedAchievements(item);

        item.setStatus(Status_UNFINISHED);
        item.setStatus(Status_FINISHED);

        assertEquals(Status_FINISHED, item.getStatus());
        assertTrue(record.isRewardTaken(10001));
        assertFalse(record.isRewardLeft(10001));
    }

    @Test
    void legacyClaimStatusesAreMigratedOnLoad() throws ReflectiveOperationException {
        var item = achievement(Status_REWARD_TAKEN);
        var record = savedAchievements(item);

        // Emulate an older BSON document that has REWARD_TAKEN but no rewardClaimed field.
        var persistedClaim = Achievement.class.getDeclaredField("rewardClaimed");
        persistedClaim.setAccessible(true);
        persistedClaim.setBoolean(item, false);
        assertFalse(item.isRewardClaimed());

        record.restoreLegacyRewardClaims();
        assertTrue(item.isRewardClaimed());

        item.setStatus(Status_UNFINISHED);
        assertTrue(item.hasClaimedReward());
        assertFalse(record.isRewardLeft(10001));
    }
}
