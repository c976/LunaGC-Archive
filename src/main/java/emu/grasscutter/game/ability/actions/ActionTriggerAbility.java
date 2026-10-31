package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.LaumaC1HealHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.Grasscutter;

@AbilityAction(AbilityModifierAction.Type.TriggerAbility)
public final class ActionTriggerAbility extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        Grasscutter.getLogger().debug("[Ability] TriggerAbility: {}", action.abilityName);

        var player = ability.getPlayerOwner();
        if (player == null) {
            Grasscutter.getLogger().error("No player owner found for ability {}", ability);
            return false;
        }
        if (action.abilityName == null || action.abilityName.isEmpty() || target == null) {
            return false;
        }

        var world = player.getWorld();
        if (world == null || world.getHost() == null) return false;
        AbilityManager manager = world.getHost().getAbilityManager();
        // Re-triggering an already-instanced ability (e.g. Skirk Pickable_Handler on absorb) must
        // run onAbilityStart. Blindly stacking another copy only fires onAdded and skips energy.
        Ability existing = findAbility(target, action.abilityName);
        if (existing == null) {
            manager.addAbilityToEntity(target, action.abilityName);
            existing = findAbility(target, action.abilityName);
        }
        if (existing != null) {
            manager.fireAbilityOnAbilityStart(existing, target);
        }

        if (LaumaC1HealHelper.isLaumaC1Ability(action.abilityName)) {
            try {
                LaumaC1HealHelper.onThreadOfLife(player);
            } catch (Throwable ignored) {
            }
        }

        return true;
    }

    private static Ability findAbility(GameEntity entity, String abilityName) {
        if (entity == null || abilityName == null) {
            return null;
        }
        var list = entity.getInstancedAbilities();
        if (list == null) {
            return null;
        }
        for (Ability candidate : new java.util.ArrayList<>(list)) { // 副本遍历，避免并发修改崩溃
            if (candidate == null || candidate.getData() == null) {
                continue;
            }
            if (abilityName.equals(candidate.getData().abilityName)) {
                return candidate;
            }
        }
        return null;
    }
}
