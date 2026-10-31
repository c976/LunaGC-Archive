package emu.grasscutter.data.excels.tps;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

/**
 * TpsWeaponExcelConfigData: the guns and grenades of the 7.x third-person shooter mode. The same
 * file also feeds {@link emu.grasscutter.data.excels.ItemData} so the weapons exist as items.
 */
@Getter
@ResourceType(name = "TpsWeaponExcelConfigData.json")
public final class TpsWeaponData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int gadgetId;
    private String tpsWeaponType;

    /** Ammo slots of the weapon; TpsAmmunitionExcelConfigData says which ammunition fills each. */
    private List<Integer> ammoSlotIds;

    private List<Integer> tpsWeaponBaseAffix;

    /** EquipAffixExcelConfigData affixId of the base affix (affix id * 10 + level). */
    @SerializedName(value = "equipAffixId", alternate = "PIHHHIMPCAM")
    private int equipAffixId;

    /** Wear slot: 1 for guns, 2 for grenades. See CONST_VALUE_TPS_SLOT_WEAR_NUM_LIMIT. */
    @SerializedName(value = "wearSlotType", alternate = "NNJGEAPMJFL")
    private int wearSlotType;
}
