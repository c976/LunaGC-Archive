package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.KillGadget)
public class ActionKillGadget extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var owner = ability.getOwner();
        if (owner == null) return false;

        int configId =
                (action.gadgetInfo != null && action.gadgetInfo.configID > 0)
                        ? action.gadgetInfo.configID
                        : action.gadgetID;
        if (configId == 0) return false;

        var scene = owner.getScene();
        if (scene == null) return false;

        var doomed =
                scene.getEntities().values().stream()
                        .filter(EntityGadget.class::isInstance)
                        .map(EntityGadget.class::cast)
                        .filter(gadget -> gadget.getGadgetId() == configId)
                        .filter(gadget -> gadget.getOwner() != null)
                        .filter(gadget -> gadget.getOwner().getId() == owner.getId())
                        .toList();

        doomed.forEach(scene::removeEntity);
        return true;
    }
}
