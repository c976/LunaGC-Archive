package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.GameEntity;

/**
 * Runs the tail of a vehicle interaction: what happens once the player is on the Saurian, and once they
 * are off it again.
 *
 * <p>Mixes into {@code Avatar_Perform} next to {@link TryEnterVehicleMixin}. Its {@code onVehicleIn} list is
 * what clears the perform modifier, so it is the half that ends the mount animation; skipping it leaves
 * the client stuck in the animation it started.
 */
@AbilityMixin(value = AbilityMixinData.Type.DoActionOnVehicleInteractPostMixin)
public class DoActionOnVehicleInteractPostMixin extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity target) {
        if (ability == null || abilityMixinData == null) return false;

        GameEntity entity = target != null ? target : ability.getOwner();
        boolean entered = isOnVehicle(ability);

        AbilityModifierAction[] actions = entered ? abilityMixinData.onVehicleIn : abilityMixinData.onVehicleOut;
        if (actions == null) return true;

        var manager = ability.getManager();
        if (manager == null) return false;

        for (AbilityModifierAction action : actions) {
            if (action == null) continue;
            manager.executeActionNow(ability, action, byteString, entity);
        }
        return true;
    }

    /**
     * Whether the player is currently riding, which decides which half of the mixin runs.
     *
     * <p>Checked against the riders rather than the owner: a dismounted Saurian stays in the scene as a
     * soul candle still owned by the player, so an owner check reads "on the vehicle" forever after the
     * first ride and {@code onVehicleOut} never runs.
     */
    private boolean isOnVehicle(Ability ability) {
        var player = ability.getPlayerOwner();
        if (player == null || player.getScene() == null) return false;
        int uid = player.getUid();
        return player.getScene().getEntities().values().stream()
                .filter(entity -> entity instanceof EntityVehicle)
                .anyMatch(
                        entity ->
                                ((EntityVehicle) entity)
                                        .getVehicleMembers().stream()
                                                .anyMatch(member -> member.getUid() == uid));
    }
}
