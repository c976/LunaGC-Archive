package emu.grasscutter.data.excels.tower;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;

@ResourceType(name = "TowerScheduleExcelConfigData.json")
public class TowerScheduleData extends GameResource {
    private int scheduleId;
    private List<Integer> entranceFloorId;
    // The 7.1 resources name this list LHOGNLPBILP; reading it as empty hides floors 9-12.
    @SerializedName(value = "schedules", alternate = {"LHOGNLPBILP"})
    private List<ScheduleDetail> schedules;
    private int monthlyLevelConfigId;
    private int rewardGroup;

    @Override
    public int getId() {
        return scheduleId;
    }

    @Override
    public void onLoad() {
        super.onLoad();

        // Throwing here costs the whole file, not the row: onLoad runs inside the resource
        // loader's per-file pass, so one schedule without a floor list left the server with no
        // tower schedules at all and every abyss lookup failing. Drop the unusable entries and
        // keep the rest.
        if (this.schedules == null) {
            this.schedules = List.of();
            return;
        }

        this.schedules =
                this.schedules.stream()
                        .filter(item -> item != null && item.getFloorList() != null)
                        .filter(item -> !item.getFloorList().isEmpty())
                        .toList();
    }

    public int getScheduleId() {
        return scheduleId;
    }

    public List<Integer> getEntranceFloorId() {
        return entranceFloorId;
    }

    public List<ScheduleDetail> getSchedules() {
        return schedules;
    }

    public int getMonthlyLevelConfigId() {
        return monthlyLevelConfigId;
    }

    public int getRewardGroup() {
        return rewardGroup;
    }

    public static class ScheduleDetail {
        private List<Integer> floorList;

        public List<Integer> getFloorList() {
            return floorList;
        }
    }
}
