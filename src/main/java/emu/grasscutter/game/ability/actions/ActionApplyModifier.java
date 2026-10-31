package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.ability.AbilityTargetSelector;
import emu.grasscutter.game.ability.ColumbinaMountainDew;
import emu.grasscutter.game.ability.EscoffierHealUtil;
import emu.grasscutter.game.ability.LaumaC1HealHelper;
import emu.grasscutter.game.ability.LohenExtraArtSkillLevelHelper;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.GameEntity;
import java.util.List;
import java.util.Map;

@AbilityAction(AbilityModifierAction.Type.ApplyModifier)
public final class ActionApplyModifier extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        target = resolveTarget(ability, target, action.target);
        if (target == null) return false;
        var selected = AbilityTargetSelector.select(action.otherTargets, ability, target);
        if (selected != null) {
            for (var candidate : selected) {
                apply(ability, action, abilityData, candidate);
            }
            return true;
        }
        return apply(ability, action, abilityData, target);
    }

    private boolean apply(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action.predicates != null && !action.predicates.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> preds = (List<Map<String, Object>>) (List<?>) action.predicates;
            if (!PredicateEvaluator.all(preds, ability, ability.getOwner(), target, action)) return true;
        }
        var modifierData = ability.getData().modifiers.get(action.modifierName);
        if (modifierData == null) return false;

        if ("Unique".equals(modifierData.stacking)) {
            ability.getModifiers().remove(action.modifierName);
        }

        AbilityModifierController modifier = new AbilityModifierController(ability, ability.getData(), modifierData);
        ability.getModifiers().put(action.modifierName, modifier);
        var manager = ability.getManager();

        // Lauma C1: settle the mastery-scaled heal before running onAdded, avoiding a GetFightProperty and
        // HealHP race plus target-resolution issues.
        boolean laumaC1Healed = LaumaC1HealHelper.isHealModifier(action.modifierName)
                && LaumaC1HealHelper.tryHeal(ability);

        if (modifierData.onAdded != null) {
            for (var a : modifierData.onAdded) {
                if (a == null) continue;
                // Skip HealHP when the helper already settled it, to avoid double healing; GetFightProperty
                // and the VFX still run.
                if (laumaC1Healed && a.type == AbilityModifierAction.Type.HealHP) {
                    continue;
                }
                // onAdded is a sequence: a global value written by one step must be readable by the next.
                manager.executeActionNow(ability, a, abilityData, target);
            }
        }
        // Defer Lisa's tap-orb hit callbacks until impact; preserve other abilities.
        if (modifierData.onAttackLanded != null && !isLisaTapProjectileSetup(ability, action, target)) {
            for (var b : modifierData.onAttackLanded) {
                if (b == null) continue;
                manager.executeActionNow(ability, b, abilityData, target);
            }
        }

        if (EscoffierHealUtil.isStrikeModifier(action.modifierName)) {
            EscoffierHealUtil.onStrikeModifier(ability);
        }

        // The dew-point UI is driven by RGV_TempMoonOvergrowPoint - grant when the talent's
        // AddMoonOverGrowCount modifier lands (Lunar Bloom inside the lunar field).
        if (ColumbinaMountainDew.isAddMoonOverGrowModifier(action.modifierName)) {
            ColumbinaMountainDew.grantOne(ability, target);
        }

        if (LohenExtraArtSkillLevelHelper.isExtraArtModifier(action.modifierName)) {
            LohenExtraArtSkillLevelHelper.onModifierApplied(ability);
        }

            return true;
        }

    static boolean isLisaTapProjectileSetup(
            Ability ability, AbilityModifierAction action, GameEntity target) {
        return "Avatar_Lisa_FlickerWaveDamage".equals(ability.getData().abilityName)
                && "StaticElec_Pre".equals(action.modifierName)
                && target == ability.getOwner()
                && target instanceof EntityClientGadget gadget
                && gadget.getGadgetId() == 41006009;
    }
}
