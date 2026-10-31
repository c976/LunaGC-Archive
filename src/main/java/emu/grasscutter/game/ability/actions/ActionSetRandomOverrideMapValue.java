package emu.grasscutter.game.ability.actions;

import com.google.protobuf.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.AbilityActionSetRandomOverrideMapValueOuterClass.AbilityActionSetRandomOverrideMapValue;

@AbilityAction(AbilityModifierAction.Type.SetRandomOverrideMapValue)
public class ActionSetRandomOverrideMapValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        AbilityActionSetRandomOverrideMapValue valueProto;
        try {
            valueProto = AbilityActionSetRandomOverrideMapValue.parseFrom(abilityData);
        } catch (InvalidProtocolBufferException e) {
            return false;
        }

        float value = valueProto.getRandomValue();
        if (!isWithinRange(ability, action, value)) {
            Grasscutter.getLogger()
                    .warn(
                            "Tried setting value out of range: {} inside [{}, {}]",
                            value,
                            action.valueRangeMin.get(ability),
                            action.valueRangeMax.get(ability));
            return true;
        }

        ability.getAbilitySpecials().put(action.overrideMapKey, value);

        return true;
    }

    /**
     * Checks the client's roll against the bounds the ability declares.
     *
     * <p>Plenty of abilities -- Animal_Fox_Random among them -- declare no bounds at all, which
     * leaves both ranges null. A missing bound means "unconstrained", so the roll is accepted. It
     * must never be read as zero: that would reject every non-zero roll and leave the entity
     * without its random value, which is the same visible bug without the stack trace.
     */
    static boolean isWithinRange(Ability ability, AbilityModifierAction action, float value) {
        if (action.valueRangeMin == null || action.valueRangeMax == null) {
            return true;
        }

        float valueRangeMin = action.valueRangeMin.get(ability);
        float valueRangeMax = action.valueRangeMax.get(ability);

        return value >= valueRangeMin && value <= valueRangeMax;
    }
}
