package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.List;
import java.util.Map;

@AbilityAction(AbilityModifierAction.Type.AddGlobalValue)
public final class ActionAddGlobalValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action.predicates != null && !action.predicates.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> preds = (List<Map<String, Object>>) (List<?>) action.predicates;
            if (!PredicateEvaluator.all(preds, ability, ability.getOwner(), target, action)) return true;
        }
        var properties = propertiesFor(ability);
        target = resolveTarget(ability, target, action.target);
        if (target == null) return false;
        String valueKey = action.key;
        float valueToAdd = action.writtenValue().get(properties, 0f);
        float maxValue = action.maxValue.get(properties, 0f);
        float minValue = action.minValue.get(properties, 0f);

        float currentGlobalValue = target.getGlobalAbilityValues().getOrDefault(valueKey, 0f);

        float newValue = currentGlobalValue + valueToAdd;
        if (action.useLimitRange) {
            newValue = Math.max(minValue, Math.min(maxValue, newValue));
        }

        target.getGlobalAbilityValues().put(valueKey, newValue);

        target.onAbilityValueUpdate();
        if (!AbilityManager.isServerOwnedChain()) {
            // Team entities are created before scene entry and may have no scene reference.
            var scene = target.getScene();
            var recipient = scene == null ? ability.getPlayerOwner() : scene.getHost();
            if (recipient != null) {
                recipient.sendPacket(new PacketServerGlobalValueChangeNotify(target, valueKey, newValue));
            }
        }

        return true;
    }
}
