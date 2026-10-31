package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.entity.GameEntity;
import java.util.List;
import java.util.Map;

@AbilityAction(AbilityModifierAction.Type.ExecuteGadgetLua)
public class ActionExecuteGadgetLua extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action.predicates != null && !action.predicates.isEmpty()) {
            @SuppressWarnings("unchecked")
            var predicates = (List<Map<String, Object>>) (List<?>) action.predicates;
            if (!PredicateEvaluator.all(predicates, ability, ability.getOwner(), target, action)) {
                return true;
            }
        }
        GameEntity owner = ability.getOwner();

        // Investigate if we need to use target

        if (owner.getEntityController() != null) {
            var param1 = action.param1.getInt(ability);
            var param2 = action.param2.getInt(ability);
            var param3 = action.param3.getInt(ability);

            owner.getEntityController().onClientExecuteRequest(owner, param1, param2, param3);
            return true;
        }

        return false;
    }
}
