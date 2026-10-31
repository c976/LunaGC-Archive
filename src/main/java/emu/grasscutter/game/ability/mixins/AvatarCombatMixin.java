/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.mixins.AbilityMixin;
import emu.grasscutter.game.ability.mixins.AbilityMixinHandler;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(value=AbilityMixinData.Type.AvatarCombatMixin)
public final class AvatarCombatMixin
extends AbilityMixinHandler {
    @Override
    public boolean execute(Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity gameEntity) {
        AbilityModifier.AbilityModifierAction[] abilityModifierActionArray;
        if (gameEntity == null) {
            gameEntity = ability.getOwner();
        }
        if (gameEntity == null) {
            return false;
        }
        boolean bl = AvatarCombatMixin.isInCombatHealState(gameEntity);
        AbilityModifier.AbilityModifierAction[] abilityModifierActionArray2 = abilityModifierActionArray = bl ? abilityMixinData.onExitCombat : abilityMixinData.onEnterCombat;
        if (abilityModifierActionArray == null || abilityModifierActionArray.length == 0) {
            return false;
        }
        AbilityManager abilityManager = ability.getManager();
        if (abilityManager == null) {
            return false;
        }
        Grasscutter.getLogger().debug("[BoL] AvatarCombatMixin {} for entity {} (wasInCombatHeal={})", bl ? "onExitCombat" : "onEnterCombat", gameEntity.getId(), bl);
        for (AbilityModifier.AbilityModifierAction abilityModifierAction : abilityModifierActionArray) {
            if (abilityModifierAction == null) continue;
            abilityManager.executeAction(ability, abilityModifierAction, byteString, gameEntity);
        }
        return true;
    }

    private static boolean isInCombatHealState(GameEntity gameEntity) {
        Float f = gameEntity.getGlobalAbilityValues().get("_ABILITY_Avatar_ForbidFoodHeal");
        return f != null && f.floatValue() > 0.0f;
    }
}
