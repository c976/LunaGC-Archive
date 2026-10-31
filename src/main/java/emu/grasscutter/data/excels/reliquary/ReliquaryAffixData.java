package emu.grasscutter.data.excels.reliquary;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.game.props.FightProperty;
import lombok.Getter;

@ResourceType(name = "ReliquaryAffixExcelConfigData.json")
@Getter
public class ReliquaryAffixData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int depotId;
    private int groupId;

    @SerializedName("propType")
    private FightProperty fightProp;

    private float propValue;
    private int weight;
    private int upgradeWeight;

    @Override
    public void onLoad() {
        if (this.weight <= 0) {
            this.weight = RelicWeightDefaults.affix(this.fightProp);
        }
        if (this.upgradeWeight <= 0) {
            this.upgradeWeight = RelicWeightDefaults.UPGRADE_WEIGHT;
        }
    }
}
