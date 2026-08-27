package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(value = AbilityMixinData.Type.AttachModifierToGlobalValueMixin)
public class AttachModifierToGlobalValueMixin extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        var manager = ability.getManager();
        if (manager == null || target == null) return false;

        manager.evaluateGlobalValueMixin(target, ability, mixinData, abilityData, 0);
        return true;
    }
}
