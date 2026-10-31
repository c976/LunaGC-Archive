package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.NatsaurusVehicleHelper;
import emu.grasscutter.game.entity.GameEntity;

/**
 * Takes the player back off the Saurian.
 *
 * <p>Rides on the Natlan-area team modifier's {@code onRemoved} and on the per-tribe
 * {@code TribeCheck_Is_In_Vehicle_*} modifiers, i.e. wherever the client decides the ride is over - leaving
 * the area, switching character, or the vehicle being killed. Without it the {@code EntityVehicle} this
 * server spawned stays in the scene forever and the player keeps its stamina state.
 */
@AbilityMixin(value = AbilityMixinData.Type.TriggerVehicleOff)
public class TriggerVehicleOff extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity target) {
        var player = ability == null ? null : ability.getPlayerOwner();
        if (player == null) return false;

        NatsaurusVehicleHelper.eject(player);
        return true;
    }
}
