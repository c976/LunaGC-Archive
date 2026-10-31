package emu.grasscutter.game.achievement;

import dev.morphia.annotations.Entity;
import emu.grasscutter.net.proto.AchievementOuterClass;
import emu.grasscutter.net.proto.AchievementOuterClass.Achievement.Status;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Achievement {
    private Status status;
    /**
     * Durable reward redemption history, independent of completion progress.
     * Revoking and later re-granting an achievement must never mint its reward twice.
     */
    private boolean rewardClaimed;
    private int id;
    private int totalProgress;
    @Setter private int curProgress;
    @Setter private int finishTimestampSec;

    public Achievement(
            Status status, int id, int totalProgress, int curProgress, int finishTimestampSec) {
        this.status = status;
        this.rewardClaimed = status == Status.Status_REWARD_TAKEN;
        this.id = id;
        this.totalProgress = totalProgress;
        this.curProgress = curProgress;
        this.finishTimestampSec = finishTimestampSec;
    }

    /** Remember a claim even when the command moves the achievement back to UNFINISHED. */
    public void setStatus(Status nextStatus) {
        if (this.status == Status.Status_REWARD_TAKEN
                || nextStatus == Status.Status_REWARD_TAKEN) {
            this.rewardClaimed = true;
        }
        this.status = nextStatus;
    }

    /** Also recognizes old saves whose only claim marker is the REWARD_TAKEN status. */
    public boolean hasClaimedReward() {
        return this.rewardClaimed || this.status == Status.Status_REWARD_TAKEN;
    }

    /** Re-completion restores the already-claimed state instead of enabling another payout. */
    public Status statusOnCompletion() {
        return this.hasClaimedReward()
                ? Status.Status_REWARD_TAKEN
                : Status.Status_FINISHED;
    }

    public AchievementOuterClass.Achievement toProto() {
        return AchievementOuterClass.Achievement.newBuilder()
                .setStatus(this.getStatus())
                .setId(this.getId())
                .setTotalProgress(this.getTotalProgress())
                .setCurProgress(this.getCurProgress())
                .setFinishTimestamp(this.getFinishTimestampSec())
                .build();
    }
}
