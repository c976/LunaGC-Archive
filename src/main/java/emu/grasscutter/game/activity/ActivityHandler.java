package emu.grasscutter.game.activity;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.ActivityData;
import emu.grasscutter.data.server.ActivityCondGroup;
import emu.grasscutter.game.activity.condition.ActivityConditionExecutor;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.net.proto.ActivityInfoOuterClass;
import emu.grasscutter.utils.helpers.DateHelper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class ActivityHandler {
    /** Must set before initWatchers */
    @Getter ActivityConfigItem activityConfigItem;

    @Getter ActivityData activityData;
    Map<WatcherTriggerType, List<ActivityWatcher>> watchersMap = new HashMap<>();

    public abstract void onProtoBuild(
            PlayerActivityData playerActivityData,
            ActivityInfoOuterClass.ActivityInfo.Builder activityInfo);

    public abstract void onInitPlayerActivityData(PlayerActivityData playerActivityData);

    public void initWatchers(Map<WatcherTriggerType, ConstructorAccess<?>> activityWatcherTypeMap) {
        activityData = GameData.getActivityDataMap().get(activityConfigItem.getActivityId());

        // add watcher to map by id
        activityData
                .getWatcherDataList()
                .forEach(
                        watcherData -> {
                            var watcherType =
                                    activityWatcherTypeMap.get(
                                            watcherData.getTriggerConfig().getWatcherTriggerType());
                            ActivityWatcher watcher;
                            if (watcherType != null) {
                                watcher = (ActivityWatcher) watcherType.newInstance();
                            } else {
                                watcher = new DefaultWatcher();
                            }

                            watcher.setWatcherId(watcherData.getId());
                            watcher.setActivityHandler(this);
                            watcher.setActivityWatcherData(watcherData);
                            watchersMap.computeIfAbsent(
                                    watcherData.getTriggerConfig().getWatcherTriggerType(), k -> new ArrayList<>());
                            watchersMap.get(watcherData.getTriggerConfig().getWatcherTriggerType()).add(watcher);
                        });
    }

    protected void triggerCondEvents(Player player) {
        var questManager = player.getQuestManager();
        getActivityConditions()
                .forEach(condition -> questManager.queueEvent(QuestCond.QUEST_COND_ACTIVITY_COND, condition));
    }

    private List<Integer> getActivityConditions() {
        if (activityData == null) {
            return new ArrayList<>();
        }

        var groups =
                activityData.getCondGroupId().stream()
                        .map(condGroupId -> GameData.getActivityCondGroupMap().get((int) condGroupId))
                        .toList();
        if (groups.contains(null)) {
            // Server/ActivityCondGroups.json stops short of the newest activities. Their condition
            // ids are the activity id followed by three digits, the same rule
            // PlayerActivityDataMappingBuilder uses to tie a condition to its activity.
            int activityId = activityData.getId();
            return GameData.getActivityCondExcelConfigDataMap().keySet().stream()
                    .filter(condId -> condId / 1000 == activityId)
                    .sorted()
                    .toList();
        }

        return groups.stream().map(ActivityCondGroup::getCondIds).flatMap(Collection::stream).toList();
    }

    // TODO handle possible overwrites
    private List<Integer> getMeetConditions(ActivityConditionExecutor conditionExecutor) {
        return conditionExecutor.getMeetActivitiesConditions(getActivityConditions());
    }

    private Map<Integer, PlayerActivityData.WatcherInfo> initWatchersDataForPlayer() {
        return watchersMap.values().stream()
                .flatMap(Collection::stream)
                .map(PlayerActivityData.WatcherInfo::init)
                .collect(Collectors.toMap(PlayerActivityData.WatcherInfo::getWatcherId, y -> y));
    }

    public PlayerActivityData initPlayerActivityData(Player player) {
        PlayerActivityData playerActivityData =
                PlayerActivityData.of()
                        .activityId(activityConfigItem.getActivityId())
                        .uid(player.getUid())
                        .watcherInfoMap(initWatchersDataForPlayer())
                        .build();

        onInitPlayerActivityData(playerActivityData);
        return playerActivityData;
    }

    public ActivityInfoOuterClass.ActivityInfo toProto(
            PlayerActivityData playerActivityData, ActivityConditionExecutor conditionExecutor) {
        var proto = ActivityInfoOuterClass.ActivityInfo.newBuilder();
        proto
                .setActivityId(activityConfigItem.getActivityId())
                .setActivityType(activityConfigItem.getActivityType())
                .setScheduleId(activityConfigItem.getScheduleId())
                .setBeginTime(DateHelper.getUnixTime(activityConfigItem.getBeginTime()))
                .setFirstDayStartTime(DateHelper.getUnixTime(activityConfigItem.getBeginTime()))
                .setEndTime(DateHelper.getUnixTime(activityConfigItem.getEndTime()))
                .addAllMeetCondList(getMeetConditions(conditionExecutor));

        if (playerActivityData != null) {
            proto.addAllWatcherInfoList(playerActivityData.getAllWatcherInfoList());
        }

        onProtoBuild(playerActivityData, proto);

        return proto.build();
    }
}
