/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.mixins.AbilityMixin;
import emu.grasscutter.game.ability.mixins.AbilityMixinHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.Map;

@AbilityMixin(value=AbilityMixinData.Type.AttachModifierToSelfGlobalValueMixin)
public class AttachModifierToSelfGlobalValueMixin
extends AbilityMixinHandler {
    @Override
    public boolean execute(Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity gameEntity) {
        GameEntity gameEntity2;
        GameEntity gameEntity3 = gameEntity2 = gameEntity != null ? gameEntity : ability.getOwner();
        if (gameEntity2 == null || abilityMixinData == null || abilityMixinData.globalValueKey == null) {
            return false;
        }
        String string = abilityMixinData.globalValueKey;
        Map<String, Float> map = gameEntity2.getGlobalAbilityValues();
        if (map == null) {
            return false;
        }
        if (!map.containsKey(string) || map.get(string) == null) {
            float f = 0.0f;
            if (abilityMixinData.defaultGlobalValueOnCreate != null) {
                f = abilityMixinData.defaultGlobalValueOnCreate.get(ability, 0.0f);
            }
            map.put(string, Float.valueOf(f));
            gameEntity2.onAbilityValueUpdate();
            if (gameEntity2.getScene() != null && gameEntity2.getScene().getHost() != null) {
                gameEntity2.getScene().getHost().sendPacket(new PacketServerGlobalValueChangeNotify(gameEntity2, string, f));
            }
        }
        return true;
    }
}
