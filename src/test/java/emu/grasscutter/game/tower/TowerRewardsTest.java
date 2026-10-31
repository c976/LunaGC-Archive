package emu.grasscutter.game.tower;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tower.*;

import org.junit.jupiter.api.*;

/** Pins rotation group, floor-number and chamber-index selection independently of inventory. */
final class TowerRewardsTest {
    private static final Gson GSON = new Gson();
    private static final TowerFloorData FLOOR =
            GSON.fromJson(
                    "{\"floorId\":1044,\"floorIndex\":9,\"rewardIdThreeStars\":11,"
                            + "\"rewardIdSixStars\":12,\"rewardIdNineStars\":13}",
                    TowerFloorData.class);
    private static final TowerLevelData LEVEL = level(2);
    private TowerRewardData previous;

    private static TowerScheduleData schedule(int group) {
        return GSON.fromJson("{\"rewardGroup\":" + group + "}", TowerScheduleData.class);
    }

    private static TowerLevelData level(int index) {
        return GSON.fromJson(
                "{\"levelId\":131,\"levelIndex\":" + index + ",\"firstPassRewardId\":21}",
                TowerLevelData.class);
    }

    private static TowerRewardData row(String rewards) {
        return GSON.fromJson(
                "{\"rewardGroup\":15,\"floor\":9," + rewards + "}", TowerRewardData.class);
    }

    @BeforeEach
    void setup() {
        previous =
                GameData.getTowerRewardDataMap()
                        .put(
                                1509,
                                row(
                                        "\"rewardId3Stars\":31,\"rewardId6Stars\":32,\"rewardId9Stars\":33,"
                                            + "\"rewardIdRoom\":[41,42,43]"));
    }

    @AfterEach
    void restore() {
        if (previous == null) GameData.getTowerRewardDataMap().remove(1509);
        else GameData.getTowerRewardDataMap().put(1509, previous);
    }

    @Test
    void selectsEveryStarTierFromTheRewardGroup() {
        assertEquals(31, TowerRewards.starRewardId(schedule(15), FLOOR, 3));
        assertEquals(32, TowerRewards.starRewardId(schedule(15), FLOOR, 6));
        assertEquals(33, TowerRewards.starRewardId(schedule(15), FLOOR, 9));
    }

    @Test
    void selectsEachChamberByOneBasedIndex() {
        assertEquals(41, TowerRewards.chamberRewardId(schedule(15), FLOOR, level(1)));
        assertEquals(42, TowerRewards.chamberRewardId(schedule(15), FLOOR, LEVEL));
        assertEquals(43, TowerRewards.chamberRewardId(schedule(15), FLOOR, level(3)));
    }

    @Test
    void absentGroupAndScheduleUseLegacyRows() {
        for (var schedule : new TowerScheduleData[] {null, schedule(0), schedule(4)}) {
            assertEquals(13, TowerRewards.starRewardId(schedule, FLOOR, 9));
            assertEquals(21, TowerRewards.chamberRewardId(schedule, FLOOR, LEVEL));
        }
    }

    @Test
    void absentFloorOrLevelCannotSelectAGroupRow() {
        assertEquals(0, TowerRewards.starRewardId(schedule(15), null, 9));
        assertEquals(21, TowerRewards.chamberRewardId(schedule(15), null, LEVEL));
        assertEquals(0, TowerRewards.chamberRewardId(schedule(15), FLOOR, null));
    }

    @Test
    void missingAndNonpositiveGroupEntriesFallBackIndividually() {
        GameData.getTowerRewardDataMap()
                .put(
                        1509,
                        row(
                                "\"rewardId3Stars\":31,\"rewardId6Stars\":0,\"rewardId9Stars\":-1,"
                                        + "\"rewardIdRoom\":[41,null,0]"));
        assertEquals(31, TowerRewards.starRewardId(schedule(15), FLOOR, 3));
        assertEquals(12, TowerRewards.starRewardId(schedule(15), FLOOR, 6));
        assertEquals(13, TowerRewards.starRewardId(schedule(15), FLOOR, 9));
        assertEquals(21, TowerRewards.chamberRewardId(schedule(15), FLOOR, LEVEL));
        assertEquals(21, TowerRewards.chamberRewardId(schedule(15), FLOOR, level(3)));
    }

    @Test
    void absentOrShortRoomListAndInvalidIndicesFallBack() {
        GameData.getTowerRewardDataMap().put(1509, row("\"rewardIdRoom\":[41]"));
        assertEquals(21, TowerRewards.chamberRewardId(schedule(15), FLOOR, LEVEL));
        assertEquals(21, TowerRewards.chamberRewardId(schedule(15), FLOOR, level(0)));
        GameData.getTowerRewardDataMap().put(1509, row("\"rewardId9Stars\":33"));
        assertEquals(21, TowerRewards.chamberRewardId(schedule(15), FLOOR, LEVEL));
    }

    @Test
    void invalidStarTiersHaveNoReward() {
        for (int tier : new int[] {0, 1, 4, 12}) {
            assertEquals(0, TowerRewards.starRewardId(schedule(15), FLOOR, tier));
            assertEquals(0, TowerRewards.starRewardId(null, FLOOR, tier));
        }
    }

    @Test
    void resourceKeyUsesGroupAndFloorNumberAndRegistersExcelName() {
        var row = row("\"rewardId9Stars\":33");
        assertEquals(TowerRewardData.key(15, 9), row.getId());
        assertNotEquals(TowerRewardData.key(14, 9), row.getId());
        assertNotEquals(TowerRewardData.key(15, 10), row.getId());
        assertEquals(
                GameData.getTowerRewardDataMap(),
                GameData.getMapByResourceDef(TowerRewardData.class));
        assertEquals(
                "TowerRewardExcelConfigData.json",
                row.getClass().getAnnotation(emu.grasscutter.data.ResourceType.class).name()[0]);
    }
}
