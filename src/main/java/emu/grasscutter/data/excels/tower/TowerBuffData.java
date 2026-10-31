package emu.grasscutter.data.excels.tower;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "TowerBuffExcelConfigData.json")
@Getter
public class TowerBuffData extends GameResource {
    private int towerBuffId;
    private int buffId;

    @Override
    public int getId() {
        return towerBuffId;
    }
}
