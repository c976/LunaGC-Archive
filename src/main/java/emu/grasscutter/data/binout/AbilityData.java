package emu.grasscutter.data.binout;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.utils.JsonAdapters;
import emu.grasscutter.game.ability.AbilityLocalIdGenerator;
import emu.grasscutter.game.ability.AbilityLocalIdGenerator.ConfigAbilitySubContainerType;
import java.util.*;

public class AbilityData {
    @SerializedName(value = "abilityName", alternate = {"BEAFNCHOJGD"})
    public String abilityName;

    @SerializedName(value = "modifiers", alternate = {"LEKAENNPGMI"})
    public Map<String, AbilityModifier> modifiers;
    public boolean isDynamicAbility;
    @JsonAdapter(JsonAdapters.AbilitySpecialsAdapter.class)
    public Map<String, Float> abilitySpecials;

    public AbilityModifierAction[] onAdded;
    public AbilityModifierAction[] onRemoved;
    public AbilityModifierAction[] onAbilityStart;
    public AbilityModifierAction[] onKill;
    public AbilityModifierAction[] onFieldEnter;
    public AbilityModifierAction[] onExit;
    public AbilityModifierAction[] onAttach;
    public AbilityModifierAction[] onDetach;
    public AbilityModifierAction[] onAvatarIn;
    public AbilityModifierAction[] onAvatarOut;
    public AbilityModifierAction[] onTriggerAvatarRay;
    public AbilityModifierAction[] onVehicleIn;
    public AbilityModifierAction[] onVehicleOut;

    // abilityMixins
    public AbilityMixinData[] abilityMixins;

    public final Map<Integer, AbilityModifierAction> localIdToAction = new HashMap<>();
    public final Map<Integer, AbilityMixinData> localIdToMixin = new HashMap<>();

    /**
     * The modifiers in the order the client numbers them, paired with the names it has no way to send:
     * the order the ability file lists them in.
     *
     * <p>Everything that resolves a {@code modifier_local_id} has to go through here, because two
     * separate things are indexed by it - the local id tables built below, and the number the client
     * puts into an invoke or a modifier change. Deriving the order a second way splits those two: this
     * file numbered local ids by file order while {@code AbilityManager.resolveModifierMapName} looked a
     * modifier up by name-sorted order, so a client attaching the twenty-first modifier of
     * {@code TeamAbility_Natsaurus_Transfer_Vehicle_Skill} got {@code Target_Perform} applied and
     * reported as {@code Remove_Avatar_Perform}. Whatever reads a name from that index - the
     * max-HP-ratio helpers, Lohen's extra art, and releasing a limbo modifier - then acted on a
     * modifier that was never attached, and a limbo mark under a name nobody holds can never be
     * cleared.
     *
     * <p>Order survives decoding because GSON deserializes a {@code Map} into its insertion-ordered
     * {@code LinkedTreeMap}; nothing here configures a different map factory. That is an assumption about
     * a library default, so {@code AbilityModifierOrderTest} pins it against a real decode.
     */
    public List<Map.Entry<String, AbilityModifier>> orderedModifiers() {
        return modifiers == null ? List.of() : new ArrayList<>(modifiers.entrySet());
    }

    /** The modifier the client means when it sends this {@code modifier_local_id}, or null if there is no such modifier. */
    public AbilityModifier modifierAt(int modifierLocalId) {
        var ordered = orderedModifiers();
        if (modifierLocalId < 0 || modifierLocalId >= ordered.size()) {
            return null;
        }
        return ordered.get(modifierLocalId).getValue();
    }

    /** The same modifier's name, which lives on the map entry and not on the modifier itself. */
    public String modifierNameAt(int modifierLocalId) {
        var ordered = orderedModifiers();
        if (modifierLocalId < 0 || modifierLocalId >= ordered.size()) {
            return null;
        }
        return ordered.get(modifierLocalId).getKey();
    }

    /** How many modifiers there are to index, for the bounds message at the resolve site. */
    public int modifierCount() {
        return modifiers == null ? 0 : modifiers.size();
    }

    private boolean _initialized = false;

    public void initialize() {
        if (_initialized) return;
        _initialized = true;

        initializeMixins();
        initializeModifiers();
        initializeActions();
    }

    private void initializeActions() {
        AbilityLocalIdGenerator generator =
                new AbilityLocalIdGenerator(ConfigAbilitySubContainerType.ACTION);
        generator.configIndex = 0;

        generator.initializeActionLocalIds(onAdded, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onRemoved, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onAbilityStart, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onKill, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onFieldEnter, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onExit, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onAttach, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onDetach, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onAvatarIn, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onAvatarOut, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onTriggerAvatarRay, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onVehicleIn, localIdToAction);
        generator.configIndex++;
        generator.initializeActionLocalIds(onVehicleOut, localIdToAction);
    }

    private void initializeMixins() {
        if (abilityMixins != null) {
            AbilityLocalIdGenerator generator =
                    new AbilityLocalIdGenerator(ConfigAbilitySubContainerType.MIXIN);
            generator.modifierIndex = 0;
            generator.configIndex = 0;

            generator.initializeMixinsLocalIds(abilityMixins, localIdToMixin);
        }
    }

    private void initializeModifiers() {
        if (modifiers == null) {
            this.modifiers = new HashMap<>();
            return;
        }

        // The client numbers a modifier by its position in the order the ability file lists them,
        // and it keeps one running mixin counter across every modifier in the ability. Sorting by
        // name and handing each modifier a fresh counter made every mixin local id past the first
        // modifier diverge from the client's, so its invokes landed on ids this map does not hold
        // and were dropped in handleServerInvoke before any handler could run. Natlan's Saurian
        // ride dies there: the "is a Saurian in range" scan is mixin 33 of the ability, and the
        // server only ever offered 4, 12, 20, 28, ... - one per modifier, restarting each time.
        //
        // See orderedModifiers() for why this is also the order modifier_local_id indexes.
        var _modifiers = orderedModifiers().stream().map(Map.Entry::getValue).toList();

        var modifierIndex = 0;
        // One generator for the whole ability: modifierIndex moves per modifier, mixinIndex keeps
        // counting. See initializeMixinsLocalIds, which no longer resets it.
        var mixinGenerator = new AbilityLocalIdGenerator(ConfigAbilitySubContainerType.MODIFIER_MIXIN);
        for (AbilityModifier abilityModifier : _modifiers) {
            long configIndex = 0L;
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onAdded, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onRemoved, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onBeingHit, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onAttackLanded, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onHittingOther, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onThinkInterval, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onKill, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onCrash, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onAvatarIn, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onAvatarOut, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onReconnect, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onChangeAuthority, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onVehicleIn, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onVehicleOut, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onZoneEnter, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onZoneExit, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onHeal, localIdToAction);
            this.initializeActionSubCategory(
                    modifierIndex, configIndex++, abilityModifier.onBeingHealed, localIdToAction);

            if (abilityModifier.modifierMixins != null) {
                mixinGenerator.modifierIndex = modifierIndex;
                mixinGenerator.configIndex = 0;

                mixinGenerator.initializeMixinsLocalIds(abilityModifier.modifierMixins, localIdToMixin);
            }

            modifierIndex++;
        }
    }

    private void initializeActionSubCategory(
            long modifierIndex,
            long configIndex,
            AbilityModifierAction[] actions,
            Map<Integer, AbilityModifierAction> localIdToAction) {
        if (actions == null) return;

        var generator = new AbilityLocalIdGenerator(ConfigAbilitySubContainerType.MODIFIER_ACTION);
        generator.modifierIndex = modifierIndex;
        generator.configIndex = configIndex;

        generator.initializeActionLocalIds(actions, localIdToAction);
    }
}
