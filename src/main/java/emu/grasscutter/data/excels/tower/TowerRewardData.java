package emu.grasscutter.data.excels.tower;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.List;

/** Star chest and chamber rewards per rotation reward group and floor number. */
@ResourceType(name = "TowerRewardExcelConfigData.json")
@Getter
public class TowerRewardData extends GameResource {
    private int rewardGroup;
    private int floor;
    private int rewardId3Stars;
    private int rewardId6Stars;
    private int rewardId9Stars;

    /** Chamber first-clear rewards, in chamber order. */
    private List<Integer> rewardIdRoom;

    public static int key(int rewardGroup, int floor) {
        return rewardGroup * 100 + floor;
    }

    @Override
    public int getId() {
        return key(this.rewardGroup, this.floor);
    }
}
