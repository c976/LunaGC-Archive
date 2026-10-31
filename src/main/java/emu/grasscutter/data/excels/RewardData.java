package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import java.util.ArrayList;
import java.util.List;

@ResourceType(name = "RewardExcelConfigData.json")
public class RewardData extends GameResource {
    public int rewardId;
    public List<ItemParamData> rewardItemList;
    public int scoin;
    public int playerExp;
    public int hcoin;

    @Override
    public int getId() {
        return rewardId;
    }

    public List<ItemParamData> getRewardItemList() {
        return rewardItemList;
    }

    /** Includes the currency and adventure EXP fields stored outside rewardItemList. */
    public List<ItemParamData> getAllRewardItems() {
        var items = new ArrayList<ItemParamData>();
        if (rewardItemList != null) items.addAll(rewardItemList);
        if (scoin > 0) items.add(new ItemParamData(202, scoin));
        if (playerExp > 0) items.add(new ItemParamData(102, playerExp));
        if (hcoin > 0) items.add(new ItemParamData(201, hcoin));
        return items;
    }

    @Override
    public void onLoad() {
        rewardItemList = rewardItemList.stream().filter(i -> i.getId() > 0).toList();
    }
}
