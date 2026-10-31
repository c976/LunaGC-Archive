package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.PartyReviveHelper;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

/** Fills in ReviveDeadAvatar, charging the Qiqi or Barbara cooldown by which ability owns the revive so
 * merely having Qiqi in the party cannot burn her cooldown. */
@AbilityAction({
    AbilityModifierAction.Type.ReviveAvatar,
    AbilityModifierAction.Type.ReviveDeadAvatar
})
public final class ActionReviveAvatar extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        Player player = ability.getPlayerOwner();
        if (player == null) {
            return false;
        }

        var owner = ability.getOwner();
        if (owner instanceof emu.grasscutter.game.entity.EntityClientGadget ownerGadget) {
            owner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId());
        }
        if (owner == null) {
            owner = ability.getOwner();
        }

        var properties = new Object2FloatOpenHashMap<String>();
        if (owner != null) {
            for (var property : FightProperty.values()) {
                properties.put(property.name(), owner.getFightProperty(property));
            }
            owner.getGlobalAbilityValues().forEach(properties::put);
        }
        properties.putAll(ability.getAbilitySpecials());

        float ratio = action.amountByTargetMaxHPRatio.get(properties, 0.0f);
        if (ratio <= 0.01f) {
            ratio = action.amount.get(properties, 0.0f);
        }
        if (ratio <= 0.01f) {
            ratio = PartyReviveHelper.QIQI_REVIVE_RATIO;
        }

        PartyReviveHelper.reviveFallenFromAbility(ability, ratio);
        return true;
    }
}
