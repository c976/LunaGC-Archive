package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.tower.TowerLevelRecord;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerFloorRecordChangeNotifyOuterClass.TowerFloorRecordChangeNotify;
import emu.grasscutter.net.proto.TowerFloorRecordOuterClass.TowerFloorRecord;
import emu.grasscutter.net.proto.TowerLevelRecordOuterClass;
import java.util.Map;
import java.util.stream.IntStream;

public class PacketTowerFloorRecordChangeNotify extends BasePacket {

    public PacketTowerFloorRecordChangeNotify(
            int floorId, int stars, boolean canEnterScheduleFloor) {
        this(floorId, stars, canEnterScheduleFloor, null);
    }

    public PacketTowerFloorRecordChangeNotify(
            int floorId,
            int stars,
            boolean canEnterScheduleFloor,
            TowerLevelRecord floorRecord) {
        super(PacketOpcodes.TowerFloorRecordChangeNotify);

        // floorStarRewardProgress = claim cursor (0/3/6/9), NEVER the star total.
        int claimProgress =
                floorRecord != null
                        ? Math.max(0, Math.min(9, floorRecord.getFloorStarRewardProgress()))
                        : 0;
        claimProgress = (claimProgress / 3) * 3;

        TowerFloorRecord.Builder floor =
                TowerFloorRecord.newBuilder()
                        .setFloorId(floorId)
                        .setFloorStarRewardProgress(claimProgress);

        if (floorRecord != null && floorRecord.getPassedLevelMap() != null) {
            Map<Integer, Integer> map = floorRecord.getPassedLevelMap();
            floor.putAllPassedLevelMap(map);
            map.forEach(
                    (levelId, levelStars) -> {
                        if (levelId == null || levelStars == null || levelStars <= 0) return;
                        floor.addPassedLevelRecordList(
                                TowerLevelRecordOuterClass.TowerLevelRecord.newBuilder()
                                        .setLevelId(levelId)
                                        .addAllSatisfiedCondList(
                                                IntStream.range(1, Math.min(3, levelStars) + 1).boxed().toList())
                                        .build());
                    });
        }

        TowerFloorRecordChangeNotify proto =
                TowerFloorRecordChangeNotify.newBuilder()
                        .addTowerFloorRecordList(floor.build())
                        .setIsFinishedEntranceFloor(canEnterScheduleFloor)
                        .build();

        this.setData(proto);
    }
}
