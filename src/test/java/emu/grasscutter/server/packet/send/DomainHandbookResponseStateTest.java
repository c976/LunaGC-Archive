package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GetDailyDungeonEntryInfoRspOuterClass.GetDailyDungeonEntryInfoRsp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("GameData")
@ExtendWith(ServerResourceFixture.class)
final class DomainHandbookResponseStateTest {
    private static final Gson GSON = new Gson();

    @Test
    void missingHandbookFlagDoesNotListDomain() {
        var unflagged = GSON.fromJson(
                "{\"id\":99001,\"sceneId\":991,\"dungeonEntryId\":77}", DungeonEntryData.class);
        var flagged = GSON.fromJson(
                "{\"id\":99002,\"sceneId\":991,\"dungeonEntryId\":78,"
                        + "\"isShowInAdvHandbook\":true}", DungeonEntryData.class);
        assertFalse(unflagged.isShowInAdvHandbook());
        assertTrue(flagged.isShowInAdvHandbook());
    }

    @Test
    void handbookReportsUnlockAndDungeonCompletionIndependently() throws Exception {
        int configId = 990882, sceneId = 991, pointId = 773, dungeonId = 990883;
        int pointKey = (sceneId << 16) + pointId;
        var entries = GameData.getDungeonEntryDataMap();
        var points = GameData.getScenePointEntryMap();
        var previousEntry = entries.put(configId, GSON.fromJson(
                "{\"id\":990882,\"sceneId\":991,\"dungeonEntryId\":773,"
                        + "\"isShowInAdvHandbook\":true}", DungeonEntryData.class));
        var previousPoint = points.put(pointKey, new ScenePointEntry(
                sceneId, GSON.fromJson(
                        "{\"id\":773,\"$type\":\"DungeonEntry\",\"dungeonIds\":[990883]}",
                        PointData.class)));
        try {
            var player = new Player();
            var locked = response(player, sceneId);
            assertEquals(1, locked.getDailyDungeonInfoListCount());
            assertFalse(locked.getDailyDungeonInfoList(0).getIsQuickOpen());
            assertFalse(locked.getDailyDungeonInfoList(0)
                    .getRecommendDungeonEntryInfo().getIsPassed());

            player.getUnlockedScenePoints(sceneId).add(pointId);
            var unlocked = response(player, sceneId);
            assertTrue(unlocked.getDailyDungeonInfoList(0).getIsQuickOpen());
            assertFalse(unlocked.getDailyDungeonInfoList(0)
                    .getRecommendDungeonEntryInfo().getIsPassed());

            player.getPlayerProgress().getCompletedDungeons().add(dungeonId);
            var cleared = response(player, sceneId);
            assertTrue(cleared.getDailyDungeonInfoList(0).getIsQuickOpen());
            assertTrue(cleared.getDailyDungeonInfoList(0)
                    .getRecommendDungeonEntryInfo().getIsPassed());

            player.getForceLockedScenePoints(sceneId).add(pointId);
            assertFalse(response(player, sceneId).getDailyDungeonInfoList(0).getIsQuickOpen());
        } finally {
            if (previousEntry == null) entries.remove(configId);
            else entries.put(configId, previousEntry);
            if (previousPoint == null) points.remove(pointKey);
            else points.put(pointKey, previousPoint);
        }
    }

    private static GetDailyDungeonEntryInfoRsp response(Player player, int sceneId) throws Exception {
        return GetDailyDungeonEntryInfoRsp.parseFrom(
                new PacketGetDailyDungeonEntryInfoRsp(player, sceneId).getData());
    }
}
