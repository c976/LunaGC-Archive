package emu.grasscutter.data.excels.tps;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

/** TpsAmmunitionExcelConfigData: one reserve pool per ammunition, shared by the slots it fills. */
@Getter
@ResourceType(name = "TpsAmmunitionExcelConfigData.json")
public final class TpsAmmunitionData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int tpsAmmoLimit;
    private List<Integer> ammoSlotIds;
}
