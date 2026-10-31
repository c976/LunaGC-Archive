package emu.grasscutter.game.tower;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.tower.TowerFloorData;
import emu.grasscutter.data.excels.tower.TowerLevelData;
import emu.grasscutter.data.excels.tower.TowerScheduleData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.TowerAllDataRspOuterClass.TowerAllDataRsp;
import emu.grasscutter.net.proto.TowerFloorRecordOuterClass.TowerFloorRecord;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.PacketTowerAllDataRsp;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ExtendWith(ServerResourceFixture.class)
@ResourceLock("GameData")
public final class TowerScheduleAvailabilityTest {
    private static final int SCHEDULE_ID = 45;
    private static final List<Integer> ENTRANCE_FLOORS =
            List.of(1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008);
    private static final List<Integer> SCHEDULE_FLOORS = List.of(2009, 2010, 2011, 2012);
    private final Map<Map<Integer, ?>, Map<Integer, ?>> originalResources = new IdentityHashMap<>();
    private int originalScheduleId;
    private boolean originalRotate;
    private boolean originalSkipEntranceFloors;

    @BeforeEach
    void prepareResources() {
        originalScheduleId = GAME_OPTIONS.tower.scheduleId;
        originalRotate = GAME_OPTIONS.tower.rotate;
        originalSkipEntranceFloors = GAME_OPTIONS.tower.skipEntranceFloors;
        GAME_OPTIONS.tower.scheduleId = SCHEDULE_ID;
        GAME_OPTIONS.tower.rotate = false;
        GAME_OPTIONS.tower.skipEntranceFloors = true;
        isolate(GameData.getTowerScheduleDataMap());
        isolate(GameData.getTowerFloorDataMap());
        isolate(GameData.getTowerLevelDataMap());
        isolate(GameData.getDungeonDataMap());

        for (int index = 1; index <= 12; index++) {
            int floorId = index <= 8 ? 1000 + index : 2000 + index;
            int groupId = 3000 + index;
            int dungeonId = 4000 + index;
            var floor =
                    JsonUtils.decode(
                            "{\"floorId\":%d,\"floorIndex\":%d,\"levelGroupId\":%d}"
                                    .formatted(floorId, index, groupId),
                            TowerFloorData.class);
            GameData.getTowerFloorDataMap().put(floorId, floor);
            for (int chamber = 1; chamber <= 3; chamber++) {
                int levelId = index * 100 + chamber;
                var level =
                        JsonUtils.decode(
                                "{\"levelId\":%d,\"levelIndex\":%d,\"levelGroupId\":%d,\"dungeonId\":%d}"
                                        .formatted(levelId, chamber, groupId, dungeonId),
                                TowerLevelData.class);
                GameData.getTowerLevelDataMap().put(levelId, level);
            }
            var dungeon =
                    JsonUtils.decode(
                            "{\"id\":%d,\"sceneId\":%d}".formatted(dungeonId, 5000 + index),
                            DungeonData.class);
            GameData.getDungeonDataMap().put(dungeonId, dungeon);
        }
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void restoreResources() {
        originalResources.forEach(
                (resources, original) -> {
                    resources.clear();
                    ((Map) resources).putAll(original);
                });
        GAME_OPTIONS.tower.scheduleId = originalScheduleId;
        GAME_OPTIONS.tower.rotate = originalRotate;
        GAME_OPTIONS.tower.skipEntranceFloors = originalSkipEntranceFloors;
    }

    @ParameterizedTest
    @ValueSource(strings = {"schedules", "LHOGNLPBILP"})
    void decodesMoonFloorsAndDropsEmptyScheduleSlots(String scheduleKey) {
        var schedule = schedule(scheduleKey);

        assertEquals(SCHEDULE_ID, schedule.getId());
        assertEquals(ENTRANCE_FLOORS, schedule.getEntranceFloorId());
        assertEquals(1, schedule.getSchedules().size());
        assertEquals(SCHEDULE_FLOORS, schedule.getSchedules().get(0).getFloorList());
    }

    @Test
    void obfuscatedScheduleOpensMoonFloorsInTheAllDataResponse() throws Exception {
        var system = system(schedule("LHOGNLPBILP"));
        var player = player(system);
        var manager = new TowerManager(player);

        var response =
                TowerAllDataRsp.parseFrom(new PacketTowerAllDataRsp(system, manager).getData());
        var records = records(response);

        assertTrue(response.getIsFinishedEntranceFloor());
        assertEquals(SCHEDULE_ID, response.getTowerScheduleId());
        assertEquals(4, response.getFloorOpenTimeMapCount());
        assertEquals(
                SCHEDULE_FLOORS.stream().collect(Collectors.toSet()),
                response.getFloorOpenTimeMapMap().keySet());
        for (int floorId : ENTRANCE_FLOORS) {
            var record = records.get(floorId);
            assertNotNull(record);
            assertEquals(3, record.getPassedLevelMapCount());
            assertEquals(
                    9,
                    record.getPassedLevelMapMap().values().stream()
                            .mapToInt(Integer::intValue)
                            .sum());
        }
        var firstMoonFloor = records.get(2009);
        assertNotNull(firstMoonFloor);
        assertEquals(0, firstMoonFloor.getPassedLevelMapCount());
        assertEquals(0, firstMoonFloor.getFloorStarRewardProgress());
        assertEquals(9, response.getSkipToFloorIndex());
        assertEquals(2009, system.getNextFloorId(1008));
        assertEquals(2010, system.getNextFloorId(2009));
        assertEquals(2011, system.getNextFloorId(2010));
        assertEquals(2012, system.getNextFloorId(2011));
        assertEquals(0, system.getNextFloorId(2012));
    }

    @Test
    void existingScheduleRepairsMalformedEntranceRecordsAndSeedsFloorNine() throws Exception {
        var system = system(schedule("LHOGNLPBILP"));
        var player = player(system);
        var data = player.getTowerData();
        data.lastScheduleId = SCHEDULE_ID;
        data.recordMap = new HashMap<>();
        var oldRecord = new TowerLevelRecord(1008).setLevelStars(0, 6);
        oldRecord.setFloorStarRewardProgress(9);
        data.recordMap.put(1008, oldRecord);
        var missingMap = new TowerLevelRecord(1007);
        missingMap.setPassedLevelMap(null);
        data.recordMap.put(1007, missingMap);
        data.currentFloorId = 2009;
        data.currentLevel = 2;

        var manager = new TowerManager(player);
        var records = manager.getRecordMap();

        assertEquals(9, records.get(1008).getStarCount());
        assertEquals(Map.of(801, 3, 802, 3, 803, 3), records.get(1008).getPassedLevelMap());
        assertEquals(9, records.get(1007).getStarCount());
        assertTrue(records.containsKey(2009));
        assertTrue(records.get(2009).getPassedLevelMap().isEmpty());
        assertTrue(manager.canEnterScheduleFloor());
        assertEquals(2009, data.currentFloorId);
        assertEquals(2, data.currentLevel);
        assertEquals(0, player.saves);
    }

    @Test
    void expiredConfiguredWindowIsActiveOnWireAndKeepsSameScheduleProgress() throws Exception {
        var system = system(schedule("LHOGNLPBILP"));
        var config = system.getTowerScheduleConfig();
        config.setScheduleStartTime(Date.from(Instant.parse("2022-06-01T00:00:00Z")));
        config.setNextScheduleChangeTime(Date.from(Instant.parse("2022-06-16T00:00:00Z")));
        var player = player(system);
        var data = player.getTowerData();
        data.lastScheduleId = SCHEDULE_ID;
        data.recordMap = new HashMap<>();
        var previousProgress = new TowerLevelRecord(2009).setLevelStars(901, 3);
        previousProgress.setFloorStarRewardProgress(3);
        data.recordMap.put(2009, previousProgress);
        data.currentFloorId = 2009;
        data.currentLevel = 1;
        var manager = new TowerManager(player);

        var response =
                TowerAllDataRsp.parseFrom(new PacketTowerAllDataRsp(system, manager).getData());
        long now = Instant.now().getEpochSecond();

        assertTrue(response.getScheduleStartTime() <= now);
        assertTrue(now < response.getNextScheduleChangeTime());
        assertEquals(4, response.getFloorOpenTimeMapCount());
        for (int openTime : response.getFloorOpenTimeMapMap().values()) {
            assertEquals(response.getScheduleStartTime(), openTime);
        }
        var moonRecord = records(response).get(2009);
        assertNotNull(moonRecord);
        assertEquals(Map.of(901, 3), moonRecord.getPassedLevelMapMap());
        assertEquals(3, moonRecord.getFloorStarRewardProgress());
        assertEquals(SCHEDULE_ID, data.lastScheduleId);
        assertEquals(2009, data.currentFloorId);
        assertEquals(1, data.currentLevel);
        assertEquals(0, player.saves);
    }

    @Test
    void playingEntranceFloorsStillRequiresSixStarsOnFloorEight() throws Exception {
        GAME_OPTIONS.tower.skipEntranceFloors = false;
        var system = system(schedule("schedules"));
        var player = player(system);
        var data = player.getTowerData();
        data.lastScheduleId = SCHEDULE_ID;
        data.recordMap = new HashMap<>();
        var manager = new TowerManager(player);
        assertFalse(manager.canEnterScheduleFloor());

        var entranceRecord = new TowerLevelRecord(1008).setLevelStars(801, 3).setLevelStars(802, 2);
        data.recordMap.put(1008, entranceRecord);
        assertFalse(manager.canEnterScheduleFloor());

        entranceRecord.setLevelStars(802, 3);
        assertTrue(manager.canEnterScheduleFloor());
    }

    @Test
    void scriptedEntranceFloorsDoNotMakeAnEmptyMoonSchedulePlayable() throws Exception {
        var empty =
                JsonUtils.decode(
                        "{\"scheduleId\":45,\"entranceFloorId\":[1001,1002,1003,1004,1005,1006,1007,1008]}",
                        TowerScheduleData.class);
        empty.onLoad();
        var system = system(empty);
        var scriptedScenes = field(TowerSystem.class, "scriptedScenes");
        @SuppressWarnings("unchecked")
        var scenes = (Map<Integer, Boolean>) scriptedScenes.get(system);
        for (int index = 1; index <= 12; index++) scenes.put(5000 + index, true);
        var isPlayable = TowerSystem.class.getDeclaredMethod("isPlayable", TowerScheduleData.class);
        isPlayable.setAccessible(true);

        assertEquals(true, isPlayable.invoke(system, schedule("schedules")));
        assertEquals(false, isPlayable.invoke(system, empty));
    }

    private static TowerScheduleData schedule(String key) {
        var data =
                JsonUtils.decode(
                        """
{"scheduleId":45,"entranceFloorId":[1001,1002,1003,1004,1005,1006,1007,1008],
 "%s":[null,{}, {"floorList":[]}, {"floorList":[2009,2010,2011,2012]}]}
"""
                                .formatted(key),
                        TowerScheduleData.class);
        data.onLoad();
        return data;
    }

    private static TowerSystem system(TowerScheduleData schedule) {
        GameData.getTowerScheduleDataMap().put(schedule.getId(), schedule);
        var system = new TowerSystem(null);
        var config = system.getTowerScheduleConfig();
        config.setScheduleStartTime(new Date(0));
        config.setNextScheduleChangeTime(new Date(2_000_000_000_000L));
        return system;
    }

    private static RecordingPlayer player(TowerSystem system) throws Exception {
        var server = allocate(GameServer.class);
        field(GameServer.class, "towerSystem").set(server, system);
        var player = allocate(RecordingPlayer.class);
        player.server = server;
        return player;
    }

    private static Map<Integer, TowerFloorRecord> records(TowerAllDataRsp response) {
        return response.getTowerFloorRecordListList().stream()
                .collect(Collectors.toMap(TowerFloorRecord::getFloorId, record -> record));
    }

    private void isolate(Map<Integer, ?> resources) {
        originalResources.put(resources, new HashMap<>(resources));
        resources.clear();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        return type.cast(unsafe.allocateInstance(type));
    }

    private static final class RecordingPlayer extends Player {
        private GameServer server;
        private int saves;

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void save() {
            saves++;
        }
    }
}
