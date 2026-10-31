/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.SpecialEnergyBarHelper;
import emu.grasscutter.game.ability.actions.AbilityAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.Map;

@AbilityAction(value=AbilityModifier.AbilityModifierAction.Type.ChangePlayMode)
public final class ActionChangePlayMode
extends AbilityActionHandler {
    private static final String NYX_INSTANT_KEY = "_ABILITY_NyxInstant_Active";

    @Override
    public boolean execute(Ability ability, AbilityModifier.AbilityModifierAction abilityModifierAction, ByteString byteString, GameEntity gameEntity) {
        GameEntity gameEntity2 = gameEntity != null ? gameEntity : (ability != null ? ability.getOwner() : null);
        if (gameEntity2 instanceof EntityAvatar) {
            EntityAvatar entityAvatar = (EntityAvatar)gameEntity2;
            Map<String, Float> map = entityAvatar.getGlobalAbilityValues();
            if (map != null) {
                // AbilityModifier in the running jar may lack toPlayMode/enable fields, so mark
                // enter here; NightsoulStaminaExempt only honors this while NyxValue > 0.
                map.put(NYX_INSTANT_KEY, Float.valueOf(1.0f));
                entityAvatar.onAbilityValueUpdate();
                if (entityAvatar.getScene() != null && entityAvatar.getScene().getHost() != null) {
                    entityAvatar.getScene().getHost().sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, NYX_INSTANT_KEY, 1.0f));
                }
            }
            try {
                SpecialEnergyBarHelper.ensureAndSync(entityAvatar);
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().debug("ChangePlayMode special bar sync failed", throwable);
            }
        }
        return true;
    }
}
