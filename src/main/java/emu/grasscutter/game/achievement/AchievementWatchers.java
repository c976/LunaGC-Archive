package emu.grasscutter.game.achievement;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.achievement.AchievementData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PushTipsManager;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.server.packet.send.PacketWatcherAllDataNotify;
import emu.grasscutter.server.packet.send.PacketWatcherChangeNotify;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Client-side achievement watchers.
 *
 * <p>Some achievement triggers only happen on the client (blowing a dandelion with Anemo, sitting
 * on the Anemo Archon statue's hand, ability-driven combat feats). The server lists the watchers
 * the client should track in WatcherAllDataNotify at login (an official capture carries every
 * unfinished achievement id there), and the client reports progress with WatcherEvent*Notify.
 */
public final class AchievementWatchers {
    private AchievementWatchers() {}

    /** Watcher ids for every achievement the player has not finished yet. */
    public static List<Integer> unfinishedIds(Achievements achievements) {
        var ids = new ArrayList<Integer>();
        for (AchievementData data : GameData.getAchievementDataMap().values()) {
            if (data == null || !data.isUsed()) continue;
            if (!achievements.isFinished(data.getId())) ids.add(data.getId());
        }
        return ids;
    }

    public static void sendAll(Player player) {
        // Push tips watchers ride along: the client only reports triggers it was told to watch.
        var ids = new LinkedHashSet<Integer>(unfinishedIds(player.getAchievements()));
        ids.addAll(PushTipsManager.of(player).pendingWatcherIds());
        player.sendPacket(new PacketWatcherAllDataNotify(new ArrayList<>(ids)));
    }

    /** WatcherEventNotify / WatcherEventStageNotify: progress one watcher by id. */
    public static void onWatcherEvent(Player player, int watcherId, int addProgress) {
        var achievements = player.getAchievements();
        if (achievements.isInvalid(watcherId)) {
            Grasscutter.getLogger()
                    .debug("[Watcher] uid={} event for non-achievement watcher {}", player.getUid(), watcherId);
            PushTipsManager.of(player).onWatcherEvent(watcherId);
            return;
        }
        addProgress(player, achievements, watcherId, addProgress);
    }

    /** WatcherEventTypeNotify: progress every unfinished achievement of that trigger and params. */
    public static void onTypeEvent(Player player, int triggerType, List<Integer> params, int addProgress) {
        var type = WatcherTriggerType.getTypeByValue(triggerType);
        if (type == null || type == WatcherTriggerType.TRIGGER_NONE) {
            Grasscutter.getLogger()
                    .debug("[Watcher] uid={} unknown trigger type {} params={}", player.getUid(), triggerType, params);
            return;
        }
        var achievements = player.getAchievements();
        int matched = 0;
        for (AchievementData data : GameData.getAchievementDataMap().values()) {
            if (data == null || !data.isUsed() || data.getTriggerConfig() == null) continue;
            if (data.getTriggerConfig().getTriggerType() != type) continue;
            if (!paramsMatch(data.getTriggerConfig().getParamList(), params)) continue;
            if (achievements.isFinished(data.getId())) continue;
            addProgress(player, achievements, data.getId(), addProgress);
            matched++;
        }
        // 推送提示（教学）的观察器也会按类型上报，例如靠近风神瞳时的 TRIGGER_WIND_CRYSTAL。
        // 以前只交给成就，提示一直不解锁、观察器不撤，客户端会每秒重复触发「看神瞳」教学并锁住镜头。
        PushTipsManager.of(player).onTypeEvent(type, params, addProgress);
        Grasscutter.getLogger()
                .debug(
                        "[Watcher] uid={} type={}({}) params={} add={} matched {} achievement(s)",
                        player.getUid(),
                        type,
                        triggerType,
                        params,
                        addProgress,
                        matched);
    }

    private static void addProgress(Player player, Achievements achievements, int id, int addProgress) {
        var achievement = achievements.getAchievement(id);
        if (achievement == null || achievements.isFinished(id)) return;
        int add = Math.max(1, addProgress);
        int next = (int) Math.min((long) achievement.getTotalProgress(), (long) achievement.getCurProgress() + add);
        achievements.progress(id, next);
        Grasscutter.getLogger()
                .debug(
                        "[Watcher] uid={} achievement {} progress {}/{}",
                        player.getUid(),
                        id,
                        next,
                        achievement.getTotalProgress());

        // The finished achievement and any linked stage it completed stop being watched.
        var removed = new ArrayList<Integer>();
        if (achievements.isFinished(id)) removed.add(id);
        var data = GameData.getAchievementDataMap().get(id);
        if (data != null && data.getExcludedGroupAchievementIdList() != null) {
            for (int linked : data.getExcludedGroupAchievementIdList()) {
                if (achievements.isFinished(linked)) removed.add(linked);
            }
        }
        if (!removed.isEmpty()) player.sendPacket(new PacketWatcherChangeNotify(List.of(), removed));
    }

    /**
     * Excel params are strings; numeric ones must equal the client's param at the same index.
     * Named params (dummy point names and the like) cannot be sent as uint32 and are skipped.
     */
    public static boolean paramsMatch(String[] excelParams, List<Integer> clientParams) {
        if (excelParams == null) return true;
        for (int i = 0; i < excelParams.length; i++) {
            var param = excelParams[i];
            if (param == null || param.isEmpty()) continue;
            int value;
            try {
                value = Integer.parseInt(param.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (i >= clientParams.size() || clientParams.get(i) != value) return false;
        }
        return true;
    }
}
