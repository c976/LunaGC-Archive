package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.NatsaurusVehicleHelper;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;

/**
 * Puts the player on the Saurian they held the interaction key on (龙之魂附).
 *
 * <p>This mixes into {@code Avatar_Perform} on {@code TeamAbility_Natsaurus_Transfer_Vehicle_Skill}, which
 * the client attaches to the avatar when the hold completes. The mixin's target is therefore the avatar,
 * not the Saurian: what to ride is whatever the scan mixin last picked up.
 *
 * <p>The server runs this itself on the attach rather than waiting for an invoke - see
 * {@code AbilityManager.SERVER_OWNED_MIXIN_ON_ATTACH}.
 */
@AbilityMixin(value = AbilityMixinData.Type.TryEnterVehicleMixin)
public class TryEnterVehicleMixin extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity target) {
        if (ability == null) return false;

        Player player = ability.getPlayerOwner();
        if (player == null || player.getScene() == null) return false;

        GameEntity source = resolveSource(ability, target);
        if (source == null) return false;

        return NatsaurusVehicleHelper.board(player, source) != null;
    }

    /**
     * What to ride: the target if the client pointed at a rideable Saurian, otherwise whatever the scan
     * mixin picked up just before this attach.
     *
     * <p>Deliberately no "nearest Saurian" fallback. The scan names the animal the player held the key on,
     * and boarding the closest one instead rides off somewhere else - the wrong place, and possibly the
     * wrong tribe.
     */
    private GameEntity resolveSource(Ability ability, GameEntity target) {
        if (NatsaurusVehicleHelper.vehicleGadgetIdFor(target) != 0) return target;

        GameEntity scanned = ability.getManager() == null ? null : ability.getManager().getScanTarget();
        if (NatsaurusVehicleHelper.vehicleGadgetIdFor(scanned) != 0) return scanned;

        return null;
    }
}
