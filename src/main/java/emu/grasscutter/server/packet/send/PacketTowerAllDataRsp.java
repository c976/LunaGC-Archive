package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.tower.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerAllDataRspOuterClass.TowerAllDataRsp;
import emu.grasscutter.net.proto.TowerFloorRecordOuterClass.TowerFloorRecord;
import emu.grasscutter.net.proto.TowerLevelRecordOuterClass;
import emu.grasscutter.utils.helpers.DateHelper;
import java.util.*;
import java.util.stream.*;

public class PacketTowerAllDataRsp extends BasePacket {

    public PacketTowerAllDataRsp(TowerSystem towerScheduleManager, TowerManager towerManager) {
        super(PacketOpcodes.TowerAllDataRsp);

        var recordList =
                towerManager.getRecordMap().values().stream()
                        .map(PacketTowerAllDataRsp::toFloorProto)
                        .toList();

        var scheduleWindow = towerScheduleManager.getScheduleWindow();
        var scheduleStart = DateHelper.getUnixTime(scheduleWindow.startTime());

        var openTimeMap =
                towerScheduleManager.getScheduleFloors().stream()
                        .collect(Collectors.toMap(x -> x, y -> scheduleStart));

        int validRecordCount =
                (int)
                        recordList.stream()
                                .filter(
                                        rec ->
                                                rec.getPassedLevelMapMap().values().stream()
                                                        .anyMatch(stars -> stars > 0))
                                .count();

        var data = towerManager.getTowerData();
        int skipTo = towerManager.getSkipToFloorIndex();
        var skipState =
                TowerAllDataRsp._TowerSkipFloorState.forNumber(towerManager.getSkipFloorState());
        if (skipState == null) {
            skipState = TowerAllDataRsp._TowerSkipFloorState._TowerSkipFloorState_TOWER_SKIP_FLOOR_STATE_NONE;
        }

        var builder =
                TowerAllDataRsp.newBuilder()
                        .setTowerScheduleId(
                                towerScheduleManager.getCurrentTowerScheduleData().getScheduleId())
                        .addAllTowerFloorRecordList(recordList)
                        .setScheduleStartTime(scheduleStart)
                        .setValidTowerRecordNum(Math.max(validRecordCount, recordList.isEmpty() ? 0 : 1))
                        .setSkipToFloorIndex(skipTo)
                        .setTowerSkipFloorState(skipState)
                        .setNextScheduleChangeTime(
                                DateHelper.getUnixTime(scheduleWindow.endTime()))
                        .putAllFloorOpenTimeMap(openTimeMap)
                        .setIsFinishedEntranceFloor(towerManager.canEnterScheduleFloor());

        var granted = towerManager.getSkipFloorGrantedRewards();
        if (!granted.isEmpty()) {
            builder.putAllSkipFloorGrantedRewardItemMap(granted);
        }

        this.setData(builder.build());
    }

    public static TowerFloorRecord toFloorProto(TowerLevelRecord rec) {
        var clean = cleanStarMap(rec.getPassedLevelMap());
        // Claim cursor only — do not snap this up toward star totals or the client marks chests claimed.
        int progress = Math.max(0, Math.min(9, rec.getFloorStarRewardProgress()));
        progress = (progress / 3) * 3;
        return TowerFloorRecord.newBuilder()
                .setFloorId(rec.getFloorId())
                .setFloorStarRewardProgress(progress)
                .putAllPassedLevelMap(clean)
                .addAllPassedLevelRecordList(buildFromPassedLevelMap(clean))
                .build();
    }

    private static List<TowerLevelRecordOuterClass.TowerLevelRecord> buildFromPassedLevelMap(
            Map<Integer, Integer> map) {
        return map.entrySet().stream()
                .map(
                        item ->
                                TowerLevelRecordOuterClass.TowerLevelRecord.newBuilder()
                                        .setLevelId(item.getKey())
                                        .addAllSatisfiedCondList(
                                                IntStream.range(1, item.getValue() + 1).boxed().toList())
                                        .build())
                .toList();
    }

    private static Map<Integer, Integer> cleanStarMap(Map<Integer, Integer> map) {
        if (map == null || map.isEmpty()) return Map.of();
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .collect(
                        java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> Math.min(3, entry.getValue()),
                                (left, right) -> right,
                                java.util.LinkedHashMap::new));
    }
}
