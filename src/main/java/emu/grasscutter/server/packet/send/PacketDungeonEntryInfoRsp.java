package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DungeonEntryInfoOuterClass.DungeonEntryInfo;
import emu.grasscutter.net.proto.DungeonEntryPointInfoOuterClass.DungeonEntryPointInfo;
import emu.grasscutter.net.proto.DungeonEntryInfoRspOuterClass.DungeonEntryInfoRsp;
import java.util.*;

public class PacketDungeonEntryInfoRsp extends BasePacket {

    public PacketDungeonEntryInfoRsp(PointData pointData) {
        this(pointData, List.of());
    }

    public PacketDungeonEntryInfoRsp(PointData pointData, List<Integer> additional) {
        this(Collections.singletonMap(new ScenePointEntry(3, pointData), additional), 0, false);
    }

    /**
     * Both 7.1 request forms use the same entry data. The response flag must match the request:
     * the native handler dispatches single-entry and entry-list responses to different UI events.
     */
    public PacketDungeonEntryInfoRsp(
            Map<ScenePointEntry, List<Integer>> entries, int playerLevel, boolean entryList) {
        super(PacketOpcodes.DungeonEntryInfoRsp);
        var response = DungeonEntryInfoRsp.newBuilder().setHGBKKNDDABP(entryList);
        for (var entry : entries.entrySet()) {
            var point = buildEntryPoint(entry.getKey().getPointData(), entry.getValue(),
                    entry.getKey().getSceneId(), playerLevel);
            response.addDungeonEntryPointList(point);
            if (response.getDungeonEntryPointListCount() == 1) {
                response.setPointId(point.getPointId())
                        .setRecommendDungeonId(point.getRecommendDungeonId())
                        .addAllDungeonEntryList(point.getDungeonEntryListList());
            }
        }
        if (entries.isEmpty()) response.setRetcode(1);
        this.setData(response);
    }

    private static DungeonEntryPointInfo buildEntryPoint(
            PointData pointData, List<Integer> additional, int sceneId, int playerLevel) {
        if (pointData.getDungeonRandomList() != null && pointData.getDungeonRandomList().length > 0) {
            pointData.updateDailyDungeon();
        }
        var dungeonIds = new LinkedHashSet<Integer>();
        if (pointData.getDungeonIds() != null) {
            for (int id : pointData.getDungeonIds()) if (id > 0) dungeonIds.add(id);
        }
        if (additional != null) {
            for (Integer id : additional) if (id != null && id > 0) dungeonIds.add(id);
        }
        var point = DungeonEntryPointInfo.newBuilder()
                .setPointId(pointData.getId())
                .setSceneId(sceneId)
                .setRecommendDungeonId(pickRecommend(dungeonIds, playerLevel));
        for (int id : dungeonIds) {
            point.addDungeonEntryList(DungeonEntryInfo.newBuilder().setDungeonId(id));
        }
        return point.build();
    }

    private static int pickRecommend(Collection<Integer> dungeonIds, int playerLevel) {
        int fallback = 0;
        int best = 0;
        int bestLimit = Integer.MIN_VALUE;
        for (int dungeonId : dungeonIds) {
            fallback = dungeonId;
            DungeonData data = GameData.getDungeonDataMap().get(dungeonId);
            int limit = data == null ? 0 : data.getLimitLevel();
            if (limit <= playerLevel && limit >= bestLimit) {
                best = dungeonId;
                bestLimit = limit;
            }
        }
        return best != 0 ? best : fallback;
    }

    public PacketDungeonEntryInfoRsp() {
        super(PacketOpcodes.DungeonEntryInfoRsp);
        this.setData(DungeonEntryInfoRsp.newBuilder().setRetcode(1));
    }
}
