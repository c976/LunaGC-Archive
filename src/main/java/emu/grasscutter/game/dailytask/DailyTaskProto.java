package emu.grasscutter.game.dailytask;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DailyTaskDataNotifyOuterClass.DailyTaskDataNotify;
import emu.grasscutter.net.proto.DailyTaskInfoOuterClass.DailyTaskInfo;
import emu.grasscutter.net.proto.DailyTaskProgressNotifyOuterClass.DailyTaskProgressNotify;
import emu.grasscutter.net.proto.WorldOwnerDailyTaskNotifyOuterClass.WorldOwnerDailyTaskNotify;
import java.util.List;

/**
 * The daily task messages.
 *
 * <p>6.7 and 7.0 had none of these named, so they were identified by shape and written by hand with
 * partly guessed numbers. 7.1 names all three notifies and DailyTaskInfo, so they are built from the
 * generated classes.
 */
public final class DailyTaskProto {
    private DailyTaskProto() {}

    public static final int PROGRESS_NOTIFY_CMD = PacketOpcodes.DailyTaskProgressNotify;
    public static final int WORLD_OWNER_NOTIFY_CMD = PacketOpcodes.WorldOwnerDailyTaskNotify;
    public static final int DATA_NOTIFY_CMD = PacketOpcodes.DailyTaskDataNotify;

    private static DailyTaskInfo infoProto(DailyTask task) {
        return DailyTaskInfo.newBuilder()
                .setIsFinished(task.isFinished())
                .setRewardId(task.getRewardId())
                .setDailyTaskId(task.getDailyTaskId())
                .setFinishProgress(task.getFinishProgress())
                .setProgress(task.getProgress())
                .build();
    }

    /** One task, as the client reads it. */
    public static byte[] info(DailyTask task) {
        return infoProto(task).toByteArray();
    }

    /** The whole board: every task, the city they were drawn from, and how many are done. */
    public static byte[] worldOwnerNotify(List<DailyTask> tasks, int cityId, int finished) {
        var notify =
                WorldOwnerDailyTaskNotify.newBuilder()
                        .setFilterCityId(cityId)
                        .setFinishedDailyTaskNum(finished);
        for (var task : tasks) notify.addTaskList(infoProto(task));
        return notify.build().toByteArray();
    }

    /** One task moved. */
    public static byte[] progressNotify(DailyTask task) {
        return DailyTaskProgressNotify.newBuilder().setInfo(infoProto(task)).build().toByteArray();
    }

    /** The bonus reward's state, once enough commissions are done. */
    public static byte[] dataNotify(int finished, int scoreRewardId, boolean taken) {
        return DailyTaskDataNotify.newBuilder()
                .setFinishedNum(finished)
                .setScoreRewardId(scoreRewardId)
                .setIsTakenScoreReward(taken)
                .build()
                .toByteArray();
    }
}
