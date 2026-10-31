/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.AbilityAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(value=AbilityModifier.AbilityModifierAction.Type.DoWatcherSystemAction)
public final class ActionDoWatcherSystemAction
extends AbilityActionHandler {
    @Override
    public boolean execute(Ability ability, AbilityModifier.AbilityModifierAction abilityModifierAction, ByteString byteString, GameEntity gameEntity) {
        return true;
    }
}
