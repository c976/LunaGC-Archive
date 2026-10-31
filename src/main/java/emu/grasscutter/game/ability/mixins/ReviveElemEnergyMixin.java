package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(value = AbilityMixinData.Type.ReviveElemEnergyMixin)
public class ReviveElemEnergyMixin extends AbilityMixinHandler {
    @Override
    public boolean execute(Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        if (!(target instanceof EntityAvatar avatar)) return false;
        avatar.addAbilityEnergy(mixinData.ratio.get(ability));
        return true;
    }
}
