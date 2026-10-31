package emu.grasscutter.data.excels.avatar;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.game.props.ElementType;
import lombok.Getter;

@ResourceType(name = "AvatarSkillExcelConfigData.json", loadPriority = LoadPriority.HIGHEST)
@Getter
public class AvatarSkillData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private float cdTime;
    private int costElemVal;
    private int maxChargeNum;
    private int triggerID;
    private boolean isAttackCameraLock;
    private int proudSkillGroupId;
    private ElementType costElemType;
    private long nameTextMapHash;
    private long descTextMapHash;
    private String abilityName;
    // Despite the names, specialEnergyMin is the size of the bar (Mavuika 200, Skirk 100) and
    // specialEnergyMax what a burst needs (100, 50). 7.1 spells them specialEnergyCostMax and
    // specialEnergyCostStart; read as zero, Mavuika had no bar and her burst drained elemental energy.
    @SerializedName(
            value = "specialEnergyMin",
            alternate = {"LNEGGCDIDCP", "specialEnergyCostMax"})
    public float specialEnergyMin;
    @SerializedName(
            value = "specialEnergyMax",
            alternate = {"GHCNBMAPHML", "specialEnergyCostStart"})
    public float specialEnergyMax;
}