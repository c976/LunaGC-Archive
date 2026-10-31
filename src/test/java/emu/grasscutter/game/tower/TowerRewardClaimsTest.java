package emu.grasscutter.game.tower;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.dungeons.TowerDungeonSettleListener;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.server.game.GameServer;

import org.junit.jupiter.api.*;

import sun.misc.Unsafe;

import java.util.*;

/**
 * Exercises the real star-claim, chamber-settlement loot, and skipped-floor loot paths. Reward ids
 * follow group 15's floor-9 row; synthetic item counts distinguish the selected ids. Server and
 * inventory doubles prevent network, database, and account mutations.
 */
final class TowerRewardClaimsTest {
    private final Map<Integer, TowerFloorData> oldFloors = new HashMap<>();
    private final Map<Integer, TowerLevelData> oldLevels = new HashMap<>();
    private final Map<Integer, RewardData> oldRewards = new HashMap<>();
    private ItemData oldPrimogem;
    private TowerRewardData oldGroup;
    private TestPlayer player;
    private TestTower manager;
    private TowerLevelRecord record;

    @BeforeEach
    void setup() throws Exception {
        oldPrimogem =
                GameData.getItemDataMap()
                        .put(
                                201,
                                json("{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
        oldFloors.putAll(GameData.getTowerFloorDataMap());
        oldLevels.putAll(GameData.getTowerLevelDataMap());
        oldRewards.putAll(GameData.getRewardDataMap());
        GameData.getTowerFloorDataMap().clear();
        GameData.getTowerLevelDataMap().clear();
        GameData.getRewardDataMap().clear();
        GameData.getTowerFloorDataMap()
                .put(
                        1044,
                        json(
                                "{\"floorId\":1044,\"floorIndex\":9,\"levelGroupId\":44,"
                                    + "\"rewardIdThreeStars\":610009,\"rewardIdSixStars\":610031,"
                                    + "\"rewardIdNineStars\":610049}",
                                TowerFloorData.class));
        for (int i = 1; i <= 3; i++) {
            GameData.getTowerLevelDataMap()
                    .put(
                            129 + i,
                            json(
                                    "{\"levelId\":"
                                            + (129 + i)
                                            + ",\"levelIndex\":"
                                            + i
                                            + ",\"levelGroupId\":44,\"firstPassRewardId\":621026}",
                                    TowerLevelData.class));
        }
        for (int id :
                List.of(610009, 610031, 610049, 610029, 610053, 621026, 626301, 626302, 626303)) {
            var reward = new RewardData();
            reward.rewardId = id;
            reward.rewardItemList =
                    List.of(
                            new ItemParamData(
                                    201,
                                    id == 610029 || id == 610053
                                            ? 100
                                            : id >= 626301 ? id - 626300 : 50));
            GameData.getRewardDataMap().put(id, reward);
        }
        loadRotationRewards();
        player = allocate(TestPlayer.class);
        player.inventory = allocate(RecordingInventory.class);
        player.inventory.grants = new ArrayList<>();
        player.server = allocate(GameServer.class);
        player.tower = allocate(TestSystem.class);
        var towerField = GameServer.class.getDeclaredField("towerSystem");
        towerField.setAccessible(true);
        towerField.set(player.server, player.tower);
        player.tower.schedule =
                json("{\"scheduleId\":124,\"rewardGroup\":15}", TowerScheduleData.class);
        manager = new TestTower(player);
        record = new TowerLevelRecord(1044);
        manager.records = new HashMap<>(Map.of(1044, record));
    }

    private void loadRotationRewards() {
        var row =
                json(
                        "{\"rewardGroup\":15,\"floor\":9,\"rewardId3Stars\":610009,"
                                + "\"rewardId6Stars\":610029,\"rewardId9Stars\":610053,"
                                + "\"rewardIdRoom\":[626301,626302,626303]}",
                        TowerRewardData.class);
        oldGroup = GameData.getTowerRewardDataMap().put(row.getId(), row);
    }

    @AfterEach
    void restore() {
        if (oldGroup == null) GameData.getTowerRewardDataMap().remove(1509);
        else GameData.getTowerRewardDataMap().put(1509, oldGroup);
        if (oldPrimogem == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, oldPrimogem);
        GameData.getTowerFloorDataMap().clear();
        GameData.getTowerFloorDataMap().putAll(oldFloors);
        GameData.getTowerLevelDataMap().clear();
        GameData.getTowerLevelDataMap().putAll(oldLevels);
        GameData.getRewardDataMap().clear();
        GameData.getRewardDataMap().putAll(oldRewards);
    }

    @Test
    void starClaimPaysRotationRewardsAndDoesNotPayTwice() {
        record.setLevelStars(130, 3).setLevelStars(131, 3).setLevelStars(132, 3);
        assertTrue(manager.claimFloorStarReward(1044));
        assertEquals(250, player.inventory.total());
        assertEquals(9, record.getFloorStarRewardProgress());
        assertEquals(ActionReason.TowerFloorStarReward, player.inventory.reason);
        assertFalse(manager.claimFloorStarReward(1044));
        assertEquals(250, player.inventory.total());
        assertEquals(1, player.saves);
    }

    @Test
    void earlyClaimOnlyPaysNewlyEarnedTiers() {
        record.setLevelStars(130, 3);
        assertTrue(manager.claimFloorStarReward(1044));
        assertEquals(50, player.inventory.total());
        record.setLevelStars(131, 3);
        assertTrue(manager.claimFloorStarReward(1044));
        assertEquals(150, player.inventory.total());
        assertEquals(6, record.getFloorStarRewardProgress());
    }

    @Test
    void absentGroupFallsBackToFloorRewards() {
        player.tower.schedule = json("{\"rewardGroup\":4}", TowerScheduleData.class);
        record.setLevelStars(130, 3).setLevelStars(131, 3).setLevelStars(132, 3);
        assertTrue(manager.claimFloorStarReward(1044));
        assertEquals(150, player.inventory.total());
    }

    @Test
    void missingSelectedRewardStopsBeforeConsumingItsTier() {
        GameData.getRewardDataMap().remove(610029);
        record.setLevelStars(130, 3).setLevelStars(131, 3).setLevelStars(132, 3);
        assertTrue(manager.claimFloorStarReward(1044));
        assertEquals(3, record.getFloorStarRewardProgress());
        assertEquals(50, player.inventory.total());
    }

    @Test
    void chamberSettlementUsesRotationRoomAndRetainsRepeatGuard() throws Exception {
        var method =
                TowerDungeonSettleListener.class.getDeclaredMethod(
                        "grantFirstPassRewards", TowerManager.class, int.class);
        method.setAccessible(true);
        var items = (List<?>) method.invoke(null, manager, 131);
        assertEquals(1, items.size());
        assertEquals(2, player.inventory.total());
        assertEquals(ActionReason.TowerFirstPassReward, player.inventory.reason);
        record.setLevelStars(131, 3);
        assertTrue(((List<?>) method.invoke(null, manager, 131)).isEmpty());
        assertEquals(2, player.inventory.total());
    }

    @Test
    void skippedFloorLootUsesAllRotationRooms() throws Exception {
        var method =
                TowerManager.class.getDeclaredMethod("mergeFirstPassRewards", int.class, Map.class);
        method.setAccessible(true);
        Map<Integer, Integer> grants = new HashMap<>();
        method.invoke(manager, 1044, grants);
        assertEquals(Map.of(201, 6), grants);
    }

    private static <T> T json(String value, Class<T> type) {
        return new Gson().fromJson(value, type);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class TestTower extends TowerManager {
        Map<Integer, TowerLevelRecord> records;

        TestTower(Player player) {
            super(player);
        }

        @Override
        public Map<Integer, TowerLevelRecord> getRecordMap() {
            return records;
        }

        @Override
        public int getCurrentFloorId() {
            return 1044;
        }
    }

    private static final class TestPlayer extends Player {
        RecordingInventory inventory;
        GameServer server;
        TestSystem tower;
        int saves;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class TestSystem extends TowerSystem {
        TowerScheduleData schedule;

        TestSystem() {
            super(null);
        }

        @Override
        public TowerScheduleData getCurrentTowerScheduleData() {
            return schedule;
        }
    }

    private static final class RecordingInventory extends Inventory {
        List<GameItem> grants;
        ActionReason reason;

        RecordingInventory() {
            super(null);
        }

        @Override
        public InventoryAddResult addItems(Collection<GameItem> items, ActionReason reason) {
            this.reason = reason;
            grants.addAll(items);
            return null;
        }

        int total() {
            return grants.stream().mapToInt(GameItem::getCount).sum();
        }
    }
}
