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
import emu.grasscutter.game.ability.LaumaC1HealHelper;
import emu.grasscutter.game.ability.mixins.AbilityMixin;
import emu.grasscutter.game.ability.mixins.AbilityMixinHandler;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(value=AbilityMixinData.Type.DoActionByElementReactionMixin)
public class DoActionByElementReactionMixin
extends AbilityMixinHandler {
    @Override
    public boolean execute(Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        return this.runActions(ability, mixinData, abilityData, target);
    }

    protected boolean runActions(Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        if (ability != null
                && mixinData != null
                && mixinData.reactionTypes != null
                && mixinData.reactionTypes.contains("MoonOvergrow")) {
            try {
                if (LaumaC1HealHelper.isLaumaC1(ability)) {
                    LaumaC1HealHelper.tryHeal(ability);
                } else if (ability.getPlayerOwner() != null) {
                    LaumaC1HealHelper.onMoonBloom(ability.getPlayerOwner());
                }
            } catch (Throwable ignored) {
            }
        }

        AbilityModifier.AbilityModifierAction[] actions = mixinData.actions;
        if (actions == null || actions.length == 0) {
            actions = mixinData.actionQueue;
        }
        if (actions == null || actions.length == 0) {
            return false;
        }
        AbilityManager manager = ability.getManager();
        if (manager == null || target == null) {
            return false;
        }
        // Sequential: ApplyModifier → GetFightProperty → HealHP must not race on the pool.
        for (AbilityModifier.AbilityModifierAction action : actions) {
            if (action == null) continue;
            manager.executeActionNow(ability, action, abilityData, target);
        }
        Grasscutter.getLogger().trace("Element reaction mixin {} ran {} action(s) for {} (reactions: {})", new Object[]{mixinData.type, actions.length, ability.getData() != null ? ability.getData().abilityName : "?", mixinData.reactionTypes});
        return true;
    }
}
