/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.mixins.AbilityMixin;
import emu.grasscutter.game.ability.mixins.AbilityMixinHandler;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(value=AbilityMixinData.Type.AttachActionToModifierMixin)
public class AttachActionToModifierMixin
extends AbilityMixinHandler {
    @Override
    public boolean execute(Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity gameEntity) {
        if (ability == null || abilityMixinData == null) {
            return false;
        }
        AbilityModifier.AbilityModifierAction[] abilityModifierActionArray = abilityMixinData.actions;
        if (abilityModifierActionArray == null || abilityModifierActionArray.length == 0) {
            abilityModifierActionArray = abilityMixinData.actionQueue;
        }
        if (abilityModifierActionArray == null || abilityModifierActionArray.length == 0) {
            return true;
        }
        AbilityManager abilityManager = ability.getManager();
        if (abilityManager == null) {
            return false;
        }
        GameEntity gameEntity2 = gameEntity != null ? gameEntity : ability.getOwner();
        for (AbilityModifier.AbilityModifierAction abilityModifierAction : abilityModifierActionArray) {
            if (abilityModifierAction == null) continue;
            abilityManager.executeAction(ability, abilityModifierAction, byteString, gameEntity2);
        }
        return true;
    }
}
