package emu.grasscutter.data.excels.reliquary;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.game.props.FightProperty;
import lombok.Getter;

@ResourceType(name = "ReliquaryMainPropExcelConfigData.json")
@Getter
public class ReliquaryMainPropData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int propDepotId;

    @SerializedName("propType")
    private FightProperty fightProp;

    private int weight;

    @Override
    public void onLoad() {
        if (this.weight <= 0) {
            this.weight = RelicWeightDefaults.mainProp(this.propDepotId, this.fightProp);
        }
    }
}
