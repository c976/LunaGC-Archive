package emu.grasscutter.game.battlepass;

import emu.grasscutter.data.excels.BattlePassMissionData;
import emu.grasscutter.game.props.BattlePassMissionStatus;
import emu.grasscutter.net.proto.BeyondBattlePassAllDataNotify._BeyondBattlePassAllDataNotify;
import emu.grasscutter.net.proto.BeyondBattlePassCurScheduleUpdateNotify._BeyondBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.net.proto.BeyondBattlePassMission._BeyondBattlePassMission;
import emu.grasscutter.net.proto.BeyondBattlePassMissionUpdateNotify._BeyondBattlePassMissionUpdateNotify;
import emu.grasscutter.net.proto.BeyondBattlePassProduct._BeyondBattlePassProduct;
import emu.grasscutter.net.proto.BeyondBattlePassSchedule._BeyondBattlePassSchedule;
import java.util.Collection;

/**
 * Builds the Miliastra (Beyond) battle pass packets. These used to be hand-encoded with field
 * numbers from an older client; 7.1 names every field, so the generated classes are used.
 */
public final class BeyondBattlePassWireEncoder {
    public static final int FIXED_BEGIN = 1785528000;
    public static final int FIXED_END = 1795982399;
    private static final int SCHEDULE_ID = 6700;

    private BeyondBattlePassWireEncoder() {}

    public static byte[] encodeAllDataNotify(BattlePassManager battlePassManager) {
        return _BeyondBattlePassAllDataNotify.newBuilder()
                .setProductInfo(product())
                .setCurSchedule(schedule(battlePassManager))
                .setHaveCurBeyondSchedule(true)
                .setGOKJFDPPOHF(SCHEDULE_ID)
                .build()
                .toByteArray();
    }

    public static byte[] encodeCurScheduleUpdateNotify(BattlePassManager battlePassManager) {
        return _BeyondBattlePassCurScheduleUpdateNotify.newBuilder()
                .setHaveCurBeyondSchedule(true)
                .setCurSchedule(schedule(battlePassManager))
                .setGOKJFDPPOHF(SCHEDULE_ID)
                .build()
                .toByteArray();
    }

    public static byte[] encodeMissionUpdateNotify(BattlePassMission battlePassMission) {
        BattlePassMissionData data = battlePassMission != null ? battlePassMission.getData() : null;
        return _BeyondBattlePassMissionUpdateNotify.newBuilder()
                .addMissionList(mission(battlePassMission, data))
                .build()
                .toByteArray();
    }

    public static byte[] encodeMissionUpdateNotify(Collection<BattlePassMission> collection) {
        var notify = _BeyondBattlePassMissionUpdateNotify.newBuilder();
        if (collection != null) {
            for (BattlePassMission battlePassMission : collection) {
                if (battlePassMission == null) continue;
                notify.addMissionList(mission(battlePassMission, battlePassMission.getData()));
            }
        }
        return notify.build().toByteArray();
    }

    private static _BeyondBattlePassSchedule schedule(BattlePassManager battlePassManager) {
        int level = battlePassManager != null ? battlePassManager.getLevel() : 0;
        int point = battlePassManager != null ? battlePassManager.getPoint() : 0;
        boolean paid = battlePassManager != null && battlePassManager.isPaid();
        return _BeyondBattlePassSchedule.newBuilder()
                .setPoint(point)
                .setOEGGIOOLJKJ(true)
                .setIsExtraPaidRewardTaken(false)
                .setUnlockStatusValue(paid ? 2 : 1)
                .setGOKJFDPPOHF(SCHEDULE_ID)
                .setLevel(level)
                .setBeginTime(BattlePassCompatHelper.beginTime())
                .setEndTime(BattlePassCompatHelper.endTime())
                .setScheduleId(SCHEDULE_ID)
                .setProductInfo(product())
                .build();
    }

    private static _BeyondBattlePassProduct product() {
        return _BeyondBattlePassProduct.newBuilder()
                .setNBPELODCADF("10201")
                .setNormalProductId("10201")
                .setUpgradeProductId("10203")
                .build();
    }

    private static _BeyondBattlePassMission mission(
            BattlePassMission battlePassMission, BattlePassMissionData data) {
        int id =
                battlePassMission != null
                        ? battlePassMission.getId()
                        : (data != null ? data.getId() : 0);
        int total = data != null ? Math.max(1, data.getProgress()) : 1;
        int progress = battlePassMission != null ? battlePassMission.getProgress() : 0;
        int point = data != null ? data.getAddPoint() : 0;
        int refreshType =
                data != null && data.getRefreshType() != null ? data.getRefreshType().getValue() : 0;
        return _BeyondBattlePassMission.newBuilder()
                .setMissionId(id)
                .setTotalProgress(total)
                .setCurProgress(Math.min(Math.max(0, progress), total))
                .setRewardBattlePassPoint(point)
                .setMissionType(refreshType)
                .setMissionStatusValue(
                        toBeyondStatusValue(battlePassMission != null ? battlePassMission.getStatus() : null))
                .build();
    }

    private static int toBeyondStatusValue(BattlePassMissionStatus battlePassMissionStatus) {
        if (battlePassMissionStatus == null) {
            return 1;
        }
        if (battlePassMissionStatus == BattlePassMissionStatus.MISSION_STATUS_FINISHED) {
            return 2;
        }
        if (battlePassMissionStatus == BattlePassMissionStatus.MISSION_STATUS_POINT_TAKEN) {
            return 3;
        }
        return 1;
    }
}
