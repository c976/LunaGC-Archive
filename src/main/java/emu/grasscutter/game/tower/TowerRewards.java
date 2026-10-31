package emu.grasscutter.game.tower;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tower.TowerFloorData;
import emu.grasscutter.data.excels.tower.TowerLevelData;
import emu.grasscutter.data.excels.tower.TowerRewardData;
import emu.grasscutter.data.excels.tower.TowerScheduleData;

/**
 * Resolves Spiral Abyss rewards from the rotation's reward group, falling back to the floor and
 * chamber rows when the group has no reward for that tier or chamber.
 */
public final class TowerRewards {
    private TowerRewards() {}

    private static TowerRewardData groupRow(TowerScheduleData schedule, TowerFloorData floor) {
        if (schedule == null || floor == null || schedule.getRewardGroup() <= 0) return null;
        return GameData.getTowerRewardDataMap()
                .get(TowerRewardData.key(schedule.getRewardGroup(), floor.getFloorIndex()));
    }

    /** Reward id of the 3, 6 or 9 star chest, or 0 when there is none. */
    public static int starRewardId(TowerScheduleData schedule, TowerFloorData floor, int tier) {
        var row = groupRow(schedule, floor);
        if (row != null) {
            int id =
                    switch (tier) {
                        case 3 -> row.getRewardId3Stars();
                        case 6 -> row.getRewardId6Stars();
                        case 9 -> row.getRewardId9Stars();
                        default -> 0;
                    };
            if (id > 0) return id;
        }
        if (floor == null) return 0;
        return switch (tier) {
            case 3 -> floor.getRewardIdThreeStars();
            case 6 -> floor.getRewardIdSixStars();
            case 9 -> floor.getRewardIdNineStars();
            default -> 0;
        };
    }

    /** Reward id of a chamber's first clear, or 0 when there is none. */
    public static int chamberRewardId(
            TowerScheduleData schedule, TowerFloorData floor, TowerLevelData level) {
        if (level == null) return 0;
        var row = groupRow(schedule, floor);
        var rooms = row != null ? row.getRewardIdRoom() : null;
        int index = level.getLevelIndex() - 1;
        if (rooms != null && index >= 0 && index < rooms.size()) {
            var id = rooms.get(index);
            if (id != null && id > 0) return id;
        }
        return level.getFirstPassRewardId();
    }
}
