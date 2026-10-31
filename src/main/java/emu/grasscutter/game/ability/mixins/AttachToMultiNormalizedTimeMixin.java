/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.ability.mixins.AbilityMixin;
import emu.grasscutter.game.ability.mixins.AbilityMixinHandler;
import emu.grasscutter.game.entity.GameEntity;
import java.util.List;

@AbilityMixin(value=AbilityMixinData.Type.AttachToMultiNormalizedTimeMixin)
public class AttachToMultiNormalizedTimeMixin
extends AbilityMixinHandler {
    @Override
    public boolean execute(Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity gameEntity) {
        if (ability == null || abilityMixinData == null) {
            return false;
        }
        GameEntity gameEntity2 = gameEntity != null ? gameEntity : ability.getOwner();
        AbilityData abilityData = ability.getData();
        if (gameEntity2 == null || abilityData == null || abilityData.modifiers == null) {
            return false;
        }
        List<String> list = abilityMixinData.getModifierNames();
        if (list == null || list.isEmpty()) {
            return true;
        }
        AbilityManager abilityManager = ability.getManager();
        for (String string : list) {
            if (string == null || string.isEmpty()) continue;
            AbilityModifier abilityModifier = abilityData.modifiers.get(string);
            if (abilityModifier == null) {
                Grasscutter.getLogger().debug("AttachToMultiNormalizedTimeMixin missing modifier {}", (Object)string);
                continue;
            }
            try {
                if ("Unique".equals(abilityModifier.stacking) && ability.getModifiers().containsKey(string)) {
                    ability.getModifiers().remove(string);
                }
                if (ability.getModifiers().containsKey(string) && !"Multiple".equals(abilityModifier.stacking)) continue;
                AbilityModifierController abilityModifierController = new AbilityModifierController(ability, abilityData, abilityModifier);
                ability.getModifiers().put(string, abilityModifierController);
                Grasscutter.getLogger().info("AttachToMultiNormalizedTimeMixin attached {}", (Object)string);
                if (abilityModifier.onAdded != null && abilityManager != null) {
                    for (AbilityModifier.AbilityModifierAction abilityModifierAction : abilityModifier.onAdded) {
                        if (abilityModifierAction == null) continue;
                        abilityManager.executeAction(ability, abilityModifierAction, byteString, gameEntity2);
                    }
                }
                gameEntity2.onAddAbilityModifier(abilityModifier);
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().warn("AttachToMultiNormalizedTimeMixin apply {} failed", (Object)string, (Object)throwable);
            }
        }
        return true;
    }
}
