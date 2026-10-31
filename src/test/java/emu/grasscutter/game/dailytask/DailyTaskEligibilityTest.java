package emu.grasscutter.game.dailytask;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.DailyTaskData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

/** Automatic AR eligibility only: no inferred quest gate, live inventory or database. */
@ExtendWith(ServerResourceFixture.class)
final class DailyTaskEligibilityTest {
    private RecordingPlayer player;
    private RecordingManager manager;
    private Map<Integer, DailyTaskData> savedResources;
    private Map<Integer, Boolean> savedSupport;
    private ConcurrentMap<Integer, Boolean> support;

    @BeforeAll
    static void reportProductionOrigin() {
        System.out.println(
                "DailyTaskManager production origin: "
                        + DailyTaskManager.class.getProtectionDomain().getCodeSource().getLocation());
    }

    @BeforeEach
    void setup() throws Exception {
        player = allocate(RecordingPlayer.class);
        player.rank = 12; // Ordinary generation fixtures are eligible, never accidentally AR0.
        manager = new RecordingManager(player);
        player.manager = manager;
        savedResources = new HashMap<>(GameData.getDailyTaskDataMap());
        GameData.getDailyTaskDataMap().clear();
        var field = DailyTaskManager.class.getDeclaredField("GROUP_RESOURCE_SUPPORT_CACHE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var cache = (ConcurrentMap<Integer, Boolean>) field.get(null);
        support = cache;
        savedSupport = new HashMap<>(support);
        support.clear();
        // All four admitted resources are fixed; tests isolate generation, not Lua/script coverage.
        for (int i = 0; i < 4; i++) {
            int id = 991000 + i;
            int group = 133001900 + i;
            var data =
                    new Gson()
                            .fromJson(
                                    "{\"id\":"
                                            + id
                                            + ",\"cityId\":1,\"type\":\"DAILY_TASK_SCENE\","
                                            + "\"finishType\":\"DAILY_FINISH_MONSTER_NUM\",\"finishProgress\":1,"
                                            + "\"taskRewardId\":1,\"newGroupVec\":["
                                            + group
                                            + "]}",
                                    DailyTaskData.class);
            GameData.getDailyTaskDataMap().put(id, data);
            support.put(group, true);
        }
    }

    @AfterEach
    void restoreResources() {
        GameData.getDailyTaskDataMap().clear();
        GameData.getDailyTaskDataMap().putAll(savedResources);
        support.clear();
        support.putAll(savedSupport);
    }

    @Test
    void bothNewManagerConstructorsDefaultToMondstadt() {
        assertEquals(1, new DailyTaskManager().getCityId());
        assertEquals(1, manager.getCityId());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void attachingPlayerPreservesExplicitPersistedCityIncludingRandom(int city) {
        var restored = new Gson().fromJson("{\"cityId\":" + city + "}", DailyTaskManager.class);
        restored.setPlayer(player);
        assertEquals(city, restored.getCityId());
    }

    @Test
    void invalidStoredCityUsesTheCurrentMondstadtDefault() {
        var restored = new Gson().fromJson("{\"cityId\":-1}", DailyTaskManager.class);
        restored.setPlayer(player);
        assertEquals(1, restored.getCityId());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 11})
    void automaticGenerationBelowAr12DoesNotIssueTasksSaveOrConsumeQuota(int rank) {
        player.rank = rank;
        assertEquals(0, manager.ensureDailyTasksForToday());
        assertNoGeneration();
        assertEquals(0, manager.resetDailyTasksForNewDay());
        assertNoGeneration();
    }

    @Test
    void loginUsesTheSameAutomaticEligibilityBoundary() {
        player.rank = 11;
        manager.onPlayerLogin();
        assertNoGeneration();
        assertEquals(1, player.packets); // Empty existing notification remains unchanged.
    }

    @Test
    void underRankExistingLegacySetIsNeitherMigratedNorCleared() throws Exception {
        var old = seedFourTasks();
        player.rank = 11;
        assertEquals(0, manager.ensureDailyTasksForToday());
        assertEquals(0, manager.getLastGenerationDate());
        assertEquals(0, manager.saves);
        assertEquals(old, manager.getDailyTasks());
        assertSame(old.get(0), manager.getDailyTasks().get(0));
    }

    @Test
    void underRankCurrentQuotaAndClaimStateArePreservedWithoutSaving() throws Exception {
        var old = seedFourTasks();
        set("lastGenerationDate", today());
        set("scoreRewardTaken", true);
        player.rank = 11;
        assertEquals(0, manager.ensureDailyTasksForToday());
        assertEquals(today(), manager.getLastGenerationDate());
        assertTrue(manager.isScoreRewardTaken());
        assertEquals(old, manager.getDailyTasks());
        assertEquals(0, manager.saves);
    }

    @Test
    void ar12GenerationRemainsAvailableWithoutAnUnprovedQuestGate() {
        assertEquals(4, manager.ensureDailyTasksForToday());
        assertEquals(1, manager.saves);
        assertTrue(manager.getDailyTasks().stream().noneMatch(DailyTask::isFinished));
        assertEquals(4, manager.getDailyTasks().stream().map(DailyTask::getTaskId).distinct().count());
    }

    @Test
    void raisingRankLaterDoesNotFindAnAlreadyConsumedQuota() {
        player.rank = 11;
        assertEquals(0, manager.ensureDailyTasksForToday());
        player.rank = 12;
        assertEquals(4, manager.ensureDailyTasksForToday());
        assertEquals(1, manager.saves);
    }

    @Test
    void eligibleLegacySetMigratesOnceAndThenKeepsSameDayQuota() {
        var old = seedFourTasks();
        assertEquals(4, manager.ensureDailyTasksForToday());
        assertEquals(today(), manager.getLastGenerationDate());
        assertEquals(1, manager.saves);
        assertSame(old.get(0), manager.getDailyTasks().get(0));
        assertEquals(4, manager.ensureDailyTasksForToday());
        assertEquals(1, manager.saves);
        assertSame(old.get(0), manager.getDailyTasks().get(0));
    }

    @Test
    void persistedRandomQuotaAndClaimStateSurviveRoundTripWithoutRegeneration() throws Exception {
        assertTrue(manager.setCityIdAndReset(0));
        set("scoreRewardTaken", true);
        var ids = manager.getDailyTasks().stream().map(DailyTask::getTaskId).sorted().toList();
        var gson = new Gson();
        var restored =
                gson.fromJson(gson.toJson(manager, DailyTaskManager.class), RecordingManager.class);
        restored.setPlayer(player);
        player.manager = restored;
        assertEquals(0, restored.getCityId());
        assertEquals(today(), restored.getLastGenerationDate());
        assertTrue(restored.isScoreRewardTaken());
        assertEquals(4, restored.ensureDailyTasksForToday());
        assertEquals(
                ids, restored.getDailyTasks().stream().map(DailyTask::getTaskId).sorted().toList());
        assertEquals(0, restored.saves);
    }

    @Test
    void explicitOperatorResetStillWorksBelowAr12() {
        player.rank = 11;
        assertEquals(4, manager.resetDailyTasks());
        assertEquals(1, manager.saves);
    }

    @Test
    void explicitOperatorRandomSelectionStillWorksBelowAr12() {
        player.rank = 11;
        assertTrue(manager.setCityIdAndReset(0));
        assertEquals(0, manager.getCityId());
        assertEquals(4, manager.getDailyTasks().size());
        assertEquals(1, manager.saves);
    }

    @Test
    void missingPlayerStillDoesNothing() {
        manager.setPlayer(null);
        assertEquals(0, manager.ensureDailyTasksForToday());
        assertNoGeneration();
    }

    private void assertNoGeneration() {
        assertTrue(manager.getDailyTasks().isEmpty());
        assertEquals(0, manager.getLastGenerationDate());
        assertEquals(0, manager.saves);
        assertFalse(manager.isScoreRewardTaken());
    }

    private List<DailyTask> seedFourTasks() {
        for (int i = 0; i < 4; i++) manager.getDailyTasks().add(DailyTask.create(player, 991000 + i));
        return List.copyOf(manager.getDailyTasks());
    }

    private void set(String field, Object value) throws Exception {
        var target = DailyTaskManager.class.getDeclaredField(field);
        target.setAccessible(true);
        target.set(manager, value);
    }

    private static int today() {
        var date = java.time.LocalDate.now(java.time.ZoneId.systemDefault());
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingManager extends DailyTaskManager {
        int saves;

        RecordingManager(Player player) {
            super(player);
        }

        @Override
        public int getRewardId(int taskRewardId) {
            return 1;
        }

        @Override
        public void save() {
            saves++;
        }

        @Override
        public void syncAll() {}
    }

    private static final class RecordingPlayer extends Player {
        int rank;
        int packets;
        DailyTaskManager manager;

        @Override
        public int getUid() {
            return 10001;
        }

        @Override
        public int getLevel() {
            return rank;
        }

        @Override
        public DailyTaskManager getDailyTaskManager() {
            return manager;
        }

        @Override
        public boolean isOnline() {
            return false;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets++;
        }
    }
}
