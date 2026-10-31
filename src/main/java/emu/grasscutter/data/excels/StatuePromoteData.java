package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import lombok.*;

// 7.x clients no longer ship this table; statue levels come from CityLevelupConfigData instead.
@ResourceType(name = "StatuePromoteExcelConfigData.json", optional = true)
public class StatuePromoteData extends GameResource {
    @Getter @Setter private int level;
    @Getter @Setter private int cityId;
    @Getter @Setter private ItemParamData[] costItems;
    @Getter @Setter private int[] rewardIdList;
    @Getter @Setter private int stamina;

    @Override
    public int getId() {
        return (cityId << 8) + level;
    }
}
