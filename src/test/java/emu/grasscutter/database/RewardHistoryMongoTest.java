package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;
import static emu.grasscutter.net.proto.AchievementOuterClass.Achievement.Status.*;

import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.achievement.Achievement;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.player.PlayerProgress;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;

/** Uses a disposable database, never the configured player's database. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ASTAPS_REWARD_HISTORY_MONGO_TEST", matches = "1")
@ExtendWith(ServerResourceFixture.class)
class RewardHistoryMongoTest {
    @Test
    void claimedAchievementsAndHandbookSurviveFreshMongoClient() {
        String name = "astaps_reward_history_test_" + UUID.randomUUID().toString().replace("-", "");
        String uri = "mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=3000";
        try {
            try (var client = MongoClients.create(uri)) {
                var store = Morphia.createDatastore(client, name);
                store.getMapper().map(Achievements.class, HandbookOwner.class);
                var claimed = new Achievement(Status_REWARD_TAKEN, 10001, 5, 5, 1234);
                // Revoking progress must not erase the redemption history on disk.
                var revoked = new Achievement(Status_REWARD_TAKEN, 10002, 3, 3, 1234);
                revoked.setStatus(Status_UNFINISHED);
                store.save(Achievements.of().uid(12345)
                        .achievementList(new HashMap<>(Map.of(10001, claimed, 10002, revoked)))
                        .finishedAchievementNum(1).takenGoalRewardIdList(new ArrayList<>(List.of(80001)))
                        .build());
                var owner = new HandbookOwner();
                owner.id = 12345;
                owner.progress = new PlayerProgress();
                owner.progress.setInvestigationTargetProgress(60001, 5);
                owner.progress.setInvestigationTargetState(60001, 3);
                owner.progress.setInvestigationChapterState(10001, 3);
                owner.progress.addHandbookChestOpen(12);
                owner.progress.raiseHandbookReliquaryHistoryCount(5, 8);
                store.save(owner);
            }
            // A new client/datastore forces BSON decoding, rather than reusing Java objects.
            try (var client = MongoClients.create(uri)) {
                var store = Morphia.createDatastore(client, name);
                store.getMapper().map(Achievements.class, HandbookOwner.class);
                var saved = store.find(Achievements.class).first();
                assertNotNull(saved);
                assertEquals(Status_REWARD_TAKEN, saved.getStatus(10001));
                assertTrue(saved.getAchievementList().get(10001).hasClaimedReward());
                assertTrue(saved.getAchievementList().get(10002).hasClaimedReward());
                assertEquals(Status_REWARD_TAKEN,
                        saved.getAchievementList().get(10002).statusOnCompletion());
                assertEquals(List.of(80001), saved.getTakenGoalRewardIdList());
                var owner = store.find(HandbookOwner.class).first();
                assertNotNull(owner);
                assertEquals(5, owner.progress.getInvestigationTargetProgress(60001));
                assertEquals(3, owner.progress.getInvestigationTargetState(60001));
                assertEquals(3, owner.progress.getInvestigationChapterState(10001));
                assertEquals(12, owner.progress.getHandbookChestOpenCount());
                assertEquals(8, owner.progress.getHandbookReliquaryHistoryCount(5));
            }
        } finally {
            try (var client = MongoClients.create(uri)) { client.getDatabase(name).drop(); }
        }
    }

    @Entity(value = "handbook_test_owners", useDiscriminator = false)
    public static class HandbookOwner {
        @Id int id;
        PlayerProgress progress;
        public HandbookOwner() {}
    }
}
