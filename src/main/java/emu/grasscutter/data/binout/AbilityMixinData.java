package emu.grasscutter.data.binout;

import com.google.gson.*;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import emu.grasscutter.utils.JsonAdapters;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class AbilityMixinData implements Serializable {
    private static final long serialVersionUID = -2001232313615923575L;

    public enum Type {
        AttachToGadgetStateMixin,
        AttachToStateIDMixin,
        SetGadgetStateV2,
        ShieldBarMixin,
        AvatarCombatMixin,
        DoActionByEventMixin,
        DoActionByKillingMixin,
        SkillButtonHoldChargeMixin,
        GlobalSubShieldMixin,
        TileAttackMixin,
        SwitchHealToHPDebtsMixin,
        AttachModifierToGlobalValueMixin,
        DJLJLPAFPGN,
        KENHGCLICPB,
        DoActionOnGlobalValueChangeMixin,
        CurLocalAvatarMixin,
        @SerializedName(value = "NyxCostMixin", alternate = "ChangeNyxValueMixin")
        NyxCostMixin,
        ModifyDamageMixin,
        AvatarChangeSkillMixin,
        KHOENFHDFJE,
        HJKDMEOOBDK,
        FIGCOCJJHCH,
        DMKDPHHJENO,
        LAAJCBLNLDO,
        CameraBlurMixin,
        AttachToNormalizedTimeMixin,
        AttachToMultiNormalizedTimeMixin,
        DLJBCMKDMEK,
        PhlogistonCostMixin,
        FIHACJPNNED,
        JMEOJHGPNMB,
        AttachModifierToSelfGlobalValueMixin,
        AttachActionToModifierMixin,
        AttachModifierToSelfGlobalValueNoInitMixin,
        HPDebtsMixin,
        LimitHpDebtsByTagMixin,
        TileAttackManagerMixin,
        CostStaminaMixin,
        DoActionByEnergyChangeMixin,
        RejectAttackMixin,
        DoActionByTargetsCountMixin,
        AttachToAbilityStateMixin,
        IPIBBIDFDOL,
        EBBCNBHOAIP,
        ReviveElemEnergyMixin,
        HKGPGGJAKGL,
        ENPGGGNLLJG,
        PBOJOFIGPIC,
        IEABBMGDJHC,
        ModifyBeHitDamageMixin,
        DoActionByCreateGadgetMixin,
        MuteHitEffectMixin,
        EntityInVisibleMixin,
        DDCOPGJBHLB,
        IBAMBHPLNNA,
        PCKKGOMJIKL,
        TriggerPostProcessEffectMixin,
        JGOOOFOCJBI,
        OOAMMMJMKPD,
        EFDAMNIDHDC,
        JMJFEPHFFFN,
        AttachToAnimatorStateIDMixin,
        AvatarSteerByCameraMixin,
        ModifyDamageCountMixin,
        AttackCostElementMixin,
        OnAvatarUseSkillMixin,
        DoActionByElementReactionMixin,
        DoActionBySelfElementReactionMixin,
        // Saurians, and the scan that opens it. Each of these names is in the 7.1 resource data with no
        // constant here, so GSON leaves type null; a null type matches no handler, and the mixin is dropped
        // without a sound while the action lists beside it still parse.
        TryEnterVehicleMixin,
        DoActionOnVehicleInteractPostMixin,
        TriggerVehicleOff,
        PhlogistonAreaMixin,
        // The "is a Saurian in range" scan that opens the ride, plus its siblings from the same
        // modifier. Missing here too, so GSON nulled them the same way.
        CheckSubTagScanEntityMixin,
        SubTagScanEntityMixin,
        StageReadyMixin
    }
    public AbilityModifierAction[] idontknowwhattonamethis;
    public AbilityModifierAction[] idontknowwhattonamethis2;

    // Newer ability data uses these generic action/reaction fields. Keep them
    // alongside the legacy names so both data versions deserialize correctly.
    public AbilityModifierAction[] actions;
    public AbilityModifierAction[] actionQueue;
    public List<String> reactionTypes = new ArrayList<>();
    public List<String> entityTypes = new ArrayList<>();
    public List<String> attackTags = new ArrayList<>();

    @SerializedName("onEnterCombat")
    public AbilityModifierAction[] onEnterCombat;

    @SerializedName("onExitCombat")
    public AbilityModifierAction[] onExitCombat;

    @SerializedName("onTriggerSkill")
    public AbilityModifierAction[] onTriggerSkill;

    @SerializedName("onTriggerUltimateSkill")
    public AbilityModifierAction[] onTriggerUltimateSkill;

    public AbilityModifierAction[] IOKPLLOKGGJ;
    
    @SerializedName("onKill")
    public AbilityModifierAction[] onKill;
    
    @SerializedName("successActions")
    public AbilityModifierAction[] successActions;

    @SerializedName("succActions")
    public AbilityModifierAction[] succActions;

    /** DoActionOnVehicleInteractPostMixin: runs once the player is on the vehicle. */
    @SerializedName("onVehicleIn")
    public AbilityModifierAction[] onVehicleIn;

    /** DoActionOnVehicleInteractPostMixin: runs once the player is off the vehicle again. */
    @SerializedName(value = "onVehicleOut", alternate = "NFPAFDMIGFA")
    public AbilityModifierAction[] onVehicleOut;

    /** TryEnterVehicleMixin's action list; the key is still obfuscated in the resource data. */
    public AbilityModifierAction[] AIKNGFLJEDJ;

    /** TryEnterVehicleMixin: which vehicle family this accepts, "Natsaurus" for the Natlan Saurians. */
    public String vehicleType;

    /** TryEnterVehicleMixin's only numeric param (0.6 for the Natlan Saurians); its meaning is not settled. */
    public DynamicFloat FKKOBPIDGPC = DynamicFloat.ZERO;

    /** CheckSubTagScanEntityMixin: runs when the scan first picks up an entity carrying {@link #tag}. */
    @SerializedName("onSelectStart")
    public AbilityModifierAction[] onSelectStart;

    /** CheckSubTagScanEntityMixin: runs when the scan loses its entity. The key is still obfuscated. */
    @SerializedName("IMEDGAOLLEM")
    public AbilityModifierAction[] onSelectEnd;

    /** The area mixins' action list for entering it. */
    @SerializedName("onEnterArea")
    public AbilityModifierAction[] onEnterArea;

    /** The area mixins' action list for leaving it. */
    @SerializedName("onExitArea")
    public AbilityModifierAction[] onExitArea;

    /** StageReadyMixin's action list. */
    @SerializedName("onStageReady")
    public AbilityModifierAction[] onStageReady;

    /** CheckSubTagScanEntityMixin / SubTagScanEntityMixin: the sub-tag the scan filters on. */
    public String tag;

    @SerializedName("$type")
    public Type type;

    public JsonElement modifierName;


    public DynamicFloat speed = DynamicFloat.ZERO;
    /**
     * Snezhnaya's change-Frigid mixin (KOANNHKDPII in the 7.1 data): "Set" or "Add"; absent, a positive
     * speed spends warmth before it builds Frigid. See SnezhnayaFrigid.
     */
    public String costType;
    /** The same mixin's "Level_1".."Level_5": while a higher level is ticking, lower ones are ignored. */
    public String priority;
    /** The same mixin's flag on its clears: the change goes straight to the Frigid meter. */
    @SerializedName("LKALKDPBLJB")
    public boolean frigidOnly;
    public DynamicFloat costStaminaDelta = DynamicFloat.ZERO;
    public DynamicFloat ratio = DynamicFloat.ONE;
    /** ChangeNyxValueMixin's Nightsoul delta. Other mixins reuse the key for state IDs and flags. */
    public DynamicFloat value = DynamicFloat.ZERO;
    public DynamicFloat detectWindow = DynamicFloat.ONE;
    public String globalValueKey;
    public List<String> stateIDs = new ArrayList<>();
    public String stateID;
    public DynamicFloat defaultGlobalValueOnCreate = DynamicFloat.ZERO;
    public List<DynamicFloat> ratioSteps = new ArrayList<>();
    @JsonAdapter(JsonAdapters.ModifierNameStepsAdapter.class)
    public List<String> modifierNameSteps = new ArrayList<>();
    public boolean EJEMBMFPBKF = true;
    public boolean isCheckOnAttach = true;
    public boolean AMFABNCKJNG = true;
    public boolean forceStopWhenRemoved = true;
    public boolean FKAJIEOFOAB = true;
    public List<String> getModifierNames() {
        if (modifierName.isJsonArray()) {
            java.lang.reflect.Type listType = (new TypeToken<List<String>>() {}).getType();
            List<String> list = (new Gson()).fromJson(modifierName, listType);
            return list;
        } else {
            return Arrays.asList(modifierName.getAsString());
        }
    }
}
