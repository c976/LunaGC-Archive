package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemUseData;
import emu.grasscutter.game.home.SpecialFurnitureType;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.ItemUseAction;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import lombok.Getter;

@ResourceType(
        name = {
            "MaterialExcelConfigData.json",
            "WeaponExcelConfigData.json",
            "ReliquaryExcelConfigData.json",
            "HomeWorldFurnitureExcelConfigData.json",
            "TpsWeaponExcelConfigData.json"
        })
@Getter
public class ItemData extends GameResource {
    // Main
    @Getter(onMethod_ = @Override)
    private int id;

    private int stackLimit = 1;
    private int maxUseCount;
    private int rankLevel;
    private String effectName;
    private int rank;
    private int weight;
    private int gadgetId;
    private String icon;

    private int[] destroyReturnMaterial;
    private int[] destroyReturnMaterialCount;

    // Enums
    private ItemType itemType = ItemType.ITEM_NONE;
    private MaterialType materialType = MaterialType.MATERIAL_NONE;
    private EquipType equipType = EquipType.EQUIP_NONE;
    private String effectType;
    private String destroyRule;

    // Food
    private String foodQuality;
    private int[] satiationParams;

    // Usable item
    private ItemUseTarget useTarget = ItemUseTarget.ITEM_USE_TARGET_NONE;
    private List<ItemUseData> itemUse;
    private List<ItemUseAction> itemUseActions;
    private boolean useOnGain = false;

    // Relic
    private int mainPropDepotId;
    private int appendPropDepotId;
    private int appendPropNum;
    @SerializedName(value = "setId", alternate = "setID")
    private int setId;
    private int[] addPropLevels;
    private int baseConvExp;
    private int maxLevel;

    // Weapon
    private int weaponPromoteId;
    private int weaponBaseExp;
    private int storyId;
    private int avatarPromoteId;
    private int awakenMaterial;
    private int[] awakenCosts;
    private int[] skillAffix;
    private WeaponProperty[] weaponProp;

    // Hash
    private long nameTextMapHash;

    // Furniture
    private int comfort;
    private List<Integer> furnType;
    private List<Integer> furnitureGadgetID;
    private SpecialFurnitureType specialFurnitureType = SpecialFurnitureType.NOT_SPECIAL;

    @SerializedName(
            value = "roomSceneId",
            alternate = {
                "BMEPAMCNABE",
                "DANFGGLKLNO",
                "JFDLJGDFIGL",
                "OHIANNAEEAK",
                "MFGACDIOHGF",
                "roomSceneID"
            })
    private int roomSceneId;

    // Custom
    private transient IntSet addPropLevelSet;

    public WeaponProperty[] getWeaponProperties() {
        return this.weaponProp;
    }

    public boolean canAddRelicProp(int level) {
        return this.addPropLevelSet != null && this.addPropLevelSet.contains(level);
    }

    public boolean isEquip() {
        return this.itemType == ItemType.ITEM_RELIQUARY || this.itemType == ItemType.ITEM_WEAPON;
    }

    @Override
    public void onLoad() {
        if (this.itemType == ItemType.ITEM_RELIQUARY) {
            if (this.addPropLevels != null && this.addPropLevels.length > 0) {
                this.addPropLevelSet = new IntOpenHashSet(this.addPropLevels);
            }
        } else if (this.itemType == ItemType.ITEM_WEAPON) {
            this.equipType = EquipType.EQUIP_WEAPON;
        } else {
            this.equipType = EquipType.EQUIP_NONE;
        }

        if (this.weaponProp != null) {
            this.weaponProp =
                    Arrays.stream(this.weaponProp)
                            .filter(prop -> prop.getPropType() != null)
                            .toArray(WeaponProperty[]::new);
        }

        if (this.getFurnType() != null) {
            this.furnType = this.furnType.stream().filter(x -> x > 0).toList();
        }
        if (this.getFurnitureGadgetID() != null) {
            this.furnitureGadgetID = this.furnitureGadgetID.stream().filter(x -> x > 0).toList();
        }

        // Prevent material type from being null
        this.materialType = this.materialType == null ? MaterialType.MATERIAL_NONE : this.materialType;

        if (this.itemUse != null && !this.itemUse.isEmpty()) {
            this.itemUseActions =
                    this.itemUse.stream()
                            .filter(x -> x.getUseOp() != ItemUseOp.ITEM_USE_NONE)
                            .map(ItemUseAction::fromItemUseData)
                            .filter(Objects::nonNull)
                            .toList();
        }

        // 7.1 data leaves useOnGain off every avatar card and costume and most gliders, so they fell
        // through to the "lacks isUseOnGain" branch and were thrown away: a character pulled from a
        // wish never arrived. A card whose only job is to grant the thing is always used on gain.
        if (!this.useOnGain && this.itemUseActions != null) {
            var gainOp =
                    switch (this.materialType) {
                        case MATERIAL_AVATAR -> ItemUseOp.ITEM_USE_GAIN_AVATAR;
                        case MATERIAL_FLYCLOAK -> ItemUseOp.ITEM_USE_GAIN_FLYCLOAK;
                        case MATERIAL_COSTUME -> ItemUseOp.ITEM_USE_GAIN_COSTUME;
                        case MATERIAL_NAMECARD -> ItemUseOp.ITEM_USE_GAIN_NAME_CARD;
                        default -> null;
                    };
            if (gainOp != null
                    && this.itemUseActions.stream().anyMatch(a -> a.getItemUseOp() == gainOp)) {
                this.useOnGain = true;
            }
        }

        // Elemental particles (MATERIAL_ADSORBATE: 元素球 / 燃素球) lost useOnGain in 7.1 data too, so
        // collecting one stored it as a bag material and the burst never charged. Their useTarget is
        // already ITEM_USE_TARGET_CUR_TEAM, which is exactly the on-field/off-field split that
        // ItemUseAddEnergy applies. Only rows whose action really grants energy get flipped.
        if (!this.useOnGain
                && this.materialType == MaterialType.MATERIAL_ADSORBATE
                && this.itemUseActions != null
                && this.itemUseActions.stream()
                        .anyMatch(
                                a ->
                                        a.getItemUseOp() == ItemUseOp.ITEM_USE_ADD_ELEM_ENERGY
                                                || a.getItemUseOp()
                                                        == ItemUseOp.ITEM_USE_ADD_ALL_ENERGY)) {
            this.useOnGain = true;
        }
    }

    @Getter
    public static class WeaponProperty {
        private FightProperty propType;
        private float initValue;
        private String type;
    }
}
