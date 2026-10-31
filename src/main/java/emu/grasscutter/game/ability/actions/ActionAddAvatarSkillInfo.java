package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.ShareCDHelper;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;

@AbilityAction(AbilityModifierAction.Type.AddAvatarSkillInfo)
public final class ActionAddAvatarSkillInfo extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        Player player = ability != null ? ability.getPlayerOwner() : null;
        if (player == null || action == null) {
            return false;
        }
        ShareCDHelper.addAvatarSkillInfo(player, action.skillID);
        return true;
    }
}
