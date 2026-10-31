package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.LohenExtraArtSkillLevelHelper;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.RemoveModifier)
public final class ActionRemoveModifier extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        Grasscutter.getLogger().debug("[Ability] Removing Modifier: {}", action.modifierName);

        if (!ability.getModifiers().containsKey(action.modifierName)) {
            Grasscutter.getLogger().debug("modifier {} not found for removal", action.modifierName);
            return false;
        }

        ability.getModifiers().remove(action.modifierName);
        // The limbo gate is sticky on the entity, so a modifier that pinned HP has to release it
        // here or the entity stays damage-proof below its threshold for the rest of its life.
        target.onLimboModifierRemoved(ability, action.modifierName);
        if (LohenExtraArtSkillLevelHelper.isExtraArtModifier(action.modifierName)) {
            LohenExtraArtSkillLevelHelper.onModifierRemoved(ability);
        }
        return true;
    }
}
