package emu.grasscutter.data.excels.tps;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

/** TpsWeaponAccessoryExcelConfigData: unlockable parts that add an affix to one TPS weapon. */
@Getter
@ResourceType(name = "TpsWeaponAccessoryExcelConfigData.json")
public final class TpsWeaponAccessoryData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int tpsWeaponId;
    private List<Integer> tpsWeaponBaseAffix;

    /** EquipAffixExcelConfigData affixId of the accessory affix (affix id * 10 + level). */
    @SerializedName(value = "equipAffixId", alternate = "PIHHHIMPCAM")
    private int equipAffixId;

    /** The MATERIAL_TPS_ACCESSORY item that unlocks this accessory. */
    @SerializedName(value = "unlockMaterialId", alternate = "KOHCBGDKMPG")
    private int unlockMaterialId;
}
