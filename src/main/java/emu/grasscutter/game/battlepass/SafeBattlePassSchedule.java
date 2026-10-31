package emu.grasscutter.game.battlepass;

import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BattlePassReward;
import emu.grasscutter.net.proto.BattlePassCycleOuterClass.BattlePassCycle;
import emu.grasscutter.net.proto.BattlePassProductOuterClass.BattlePassProduct;
import emu.grasscutter.net.proto.BattlePassRewardPlanOption._BattlePassRewardPlanOption;
import emu.grasscutter.net.proto.BattlePassScheduleOuterClass.BattlePassSchedule;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Builds the compatibility schedule with generated protobuf classes only. */
public final class SafeBattlePassSchedule {
    public static final int SCHEDULE_ID = 6700;
    public static final int BEGIN_TIME = 1785528000;
    public static final int END_TIME = 1795982399;

    private SafeBattlePassSchedule() {}

    public static BattlePassSchedule build(BattlePassManager battlePassManager) {
        int level = battlePassManager != null ? battlePassManager.getLevel() : 0;
        int point = battlePassManager != null ? battlePassManager.getPoint() : 0;
        int cyclePoints = battlePassManager != null ? battlePassManager.getCyclePoints() : 0;
        boolean paid = battlePassManager != null && battlePassManager.isPaid();
        int plan =
                battlePassManager != null && battlePassManager.getPlayer() != null
                        ? Math.max(1, BattlePassCompatHelper.getSelectedPlan(battlePassManager.getPlayer()))
                        : 1;

        LocalDate today = LocalDate.now();
        LocalDate nextSunday =
                today.getDayOfWeek() == DayOfWeek.SUNDAY
                        ? today
                        : today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        int cycleEnd =
                (int)
                        LocalDateTime.of(nextSunday, java.time.LocalTime.of(23, 59, 59))
                                .atZone(ZoneId.systemDefault())
                                .toEpochSecond();

        BattlePassProduct product =
                BattlePassProduct.newBuilder()
                        // Legacy 6.x fields retained for clients that still read them.
                        .setNormalProductId("201")
                        .setExtraProductId("202")
                        .setUpgradeProductId("203")
                        .build();

        BattlePassSchedule.Builder builder =
                BattlePassSchedule.newBuilder()
                        .setProductInfo(product)
                        .setIsViewed(true)
                        .setUnlockStatus(
                                paid
                                        ? BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID
                                        : BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_FREE)
                        .setEndTime(END_TIME)
                        .setCurCycle(
                                BattlePassCycle.newBuilder()
                                        .setBeginTime(BEGIN_TIME)
                                        .setEndTime(cycleEnd)
                                        .setCycleIdx(1)
                                        .build())
                        .setScheduleId(SCHEDULE_ID)
                        .setBeginTime(BEGIN_TIME)
                        .setLevel(level)
                        .setPaidPlatformFlags(paid ? 3 : 0)
                        .setCurCyclePoints(cyclePoints)
                        .setPoint(point);

        for (int i = 1; i <= 5; i++) {
            builder.addRewardPlanOptionList(
                    _BattlePassRewardPlanOption.newBuilder()
                            .setBattlePassPlan(plan)
                            .setFBHFDJJIDBD(i)
                            .setENGHPDCKACD(false)
                            .build());
        }

        if (battlePassManager != null && battlePassManager.getTakenRewards() != null) {
            for (BattlePassReward reward : battlePassManager.getTakenRewards().values()) {
                if (reward == null || !paid && reward.isPaid()) continue;
                builder.addRewardTakenList(reward.toProto());
            }
        }
        return builder.build();
    }
}
