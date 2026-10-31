package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DailyDungeonEntryInfoOuterClass.DailyDungeonEntryInfo;
import emu.grasscutter.net.proto.DungeonEntryInfoOuterClass.DungeonEntryInfo;
import emu.grasscutter.net.proto.GetDailyDungeonEntryInfoRspOuterClass.GetDailyDungeonEntryInfoRsp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adventurer Handbook → Domains.
 *
 * <p>7.0 was encoded by hand in two candidate layouts because the generated 7.0 class was
 * unreliable; the 7.1 classes carry the same fields by name, so there is one layout again.
 */
public class PacketGetDailyDungeonEntryInfoRsp extends BasePacket {

    public PacketGetDailyDungeonEntryInfoRsp(Integer sceneID) {
        this(null, sceneID);
    }

    public PacketGetDailyDungeonEntryInfoRsp(Player player, Integer sceneID) {
        super(PacketOpcodes.GetDailyDungeonEntryInfoRsp);
        this.setData(buildPayload(player, sceneID));
    }

    /** Kept for the callers that used to push the two 7.0 candidate layouts; sends the 7.1 one. */
    public static void sendBothLayouts(Player player, Integer sceneID) {
        if (player == null || player.getSession() == null) {
            return;
        }
        player.getSession().send(new PacketGetDailyDungeonEntryInfoRsp(player, sceneID));
    }

    private static GetDailyDungeonEntryInfoRsp buildPayload(Player player, Integer sceneID) {
        int sceneId = sceneID == null ? 3 : sceneID;
        int playerLevel = player != null ? player.getLevel() : 60;
        var rsp = GetDailyDungeonEntryInfoRsp.newBuilder();

        for (DungeonEntryData data : GameData.getDungeonEntryDataMap().values()) {
            if (data.getSceneId() != sceneId) {
                continue;
            }
            // Listing eligibility is independent of whether the map point is unlocked.
            if (!data.isShowInAdvHandbook()) {
                continue;
            }
            var entry = buildEntry(data, player, playerLevel);
            if (entry != null) {
                rsp.addDailyDungeonInfoList(entry);
            }
        }

        Grasscutter.getLogger()
                .debug(
                        "GetDailyDungeonEntryInfoRsp sceneId={} level={} entries={}",
                        sceneId,
                        playerLevel,
                        rsp.getDailyDungeonInfoListCount());
        return rsp.build();
    }

    private static DailyDungeonEntryInfo buildEntry(DungeonEntryData data, Player player, int playerLevel) {
        int[] dungeonIds = resolveDungeonIds(data);
        if (dungeonIds == null || dungeonIds.length == 0) {
            return null;
        }
        int recommend = pickRecommendDungeonId(dungeonIds, playerLevel);

        List<Integer> ordered = new ArrayList<>(dungeonIds.length);
        ordered.add(recommend);
        for (int id : dungeonIds) {
            if (id != recommend) {
                ordered.add(id);
            }
        }

        // Do not advertise an unexplored entrance as quick-open or an uncleared
        // dungeon as completed just to make the handbook page populate.
        boolean unlocked = player != null
                && player.getUnlockedScenePoints(data.getSceneId()).contains(data.getDungeonEntryId())
                && !player.isScenePointForceLocked(data.getSceneId(), data.getDungeonEntryId());
        boolean passed = player != null
                && player.getPlayerProgress() != null
                && player.getPlayerProgress().getCompletedDungeons() != null
                && player.getPlayerProgress().getCompletedDungeons().contains(recommend);

        return DailyDungeonEntryInfo.newBuilder()
                .setEDEOIILGGJC(true)
                .setIsQuickOpen(unlocked)
                .setRecommendDungeonId(recommend)
                .setRecommendDungeonEntryInfo(
                        DungeonEntryInfo.newBuilder().setDungeonId(recommend).setIsPassed(passed))
                .setAKNPDDFHEKO(true)
                .setDungeonEntryId(data.getDungeonEntryId())
                .setDungeonEntryConfigId(data.getId())
                .addAllODDLACNLJOF(ordered)
                .build();
    }

    private static int pickRecommendDungeonId(int[] dungeonIds, int playerLevel) {
        int bestId = dungeonIds[dungeonIds.length - 1];
        int bestLimit = -1;
        for (int id : dungeonIds) {
            DungeonData dungeon = GameData.getDungeonDataMap().get(id);
            int limit = dungeon != null ? dungeon.getLimitLevel() : 0;
            if (limit <= playerLevel && limit >= bestLimit) {
                bestLimit = limit;
                bestId = id;
            }
        }
        return bestId;
    }

    private static int[] resolveDungeonIds(DungeonEntryData data) {
        try {
            ScenePointEntry point =
                    GameData.getScenePointEntryById(data.getSceneId(), data.getDungeonEntryId());
            if (point == null || point.getPointData() == null) {
                return null;
            }
            PointData pd = point.getPointData();
            if (pd.getDungeonRandomList() != null && pd.getDungeonRandomList().length > 0) {
                pd.updateDailyDungeon();
            }
            int[] ids = pd.getDungeonIds();
            if (ids != null && ids.length > 0) {
                return Arrays.copyOf(ids, ids.length);
            }
            return pd.getDungeonRandomList();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
