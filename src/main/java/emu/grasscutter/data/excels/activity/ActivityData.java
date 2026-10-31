package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.*;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(
        name = "NewActivityExcelConfigData.json",
        loadPriority = ResourceType.LoadPriority.LOW)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ActivityData extends GameResource {
    int activityId;
    String activityType;
    List<Integer> condGroupId;
    List<Integer> watcherId;
    List<ActivityWatcherData> watcherDataList;

    @Override
    public int getId() {
        return this.activityId;
    }

    @Override
    public void onLoad() {
        // Banners and story entries carry no condition groups.
        if (this.condGroupId == null) {
            this.condGroupId = List.of();
        }

        // An activity with no watchers at all is ordinary. Throwing on it took every activity
        // in the file down with it.
        if (this.watcherId == null) {
            this.watcherDataList = List.of();
            return;
        }

        this.watcherDataList =
                watcherId.stream()
                        .map(item -> GameData.getActivityWatcherDataMap().get(item.intValue()))
                        .filter(Objects::nonNull)
                        .toList();
    }
}
