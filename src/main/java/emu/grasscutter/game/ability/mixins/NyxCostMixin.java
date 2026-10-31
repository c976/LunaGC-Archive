/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.MavuikaSpiritHelper;
import emu.grasscutter.game.ability.mixins.AbilityMixin;
import emu.grasscutter.game.ability.mixins.AbilityMixinHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.Map;

@AbilityMixin(value=AbilityMixinData.Type.NyxCostMixin)
public class NyxCostMixin
extends AbilityMixinHandler {
    private static final String NYX_KEY = "NyxValue";
    private static final String NYX_MIN = "NyxValueMin";
    private static final String NYX_MAX = "NyxValueMax";

    @Override
    public boolean execute(Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity gameEntity) {
        float f;
        GameEntity gameEntity2;
        GameEntity gameEntity3 = gameEntity2 = gameEntity != null ? gameEntity : ability.getOwner();
        if (gameEntity2 == null) {
            return false;
        }
        Object2FloatOpenHashMap<String> object2FloatOpenHashMap = new Object2FloatOpenHashMap<String>();
        for (FightProperty fightProperty : FightProperty.values()) {
            object2FloatOpenHashMap.put(fightProperty.name(), gameEntity2.getFightProperty(fightProperty));
        }
        object2FloatOpenHashMap.putAll((Map<String, Float>)ability.getAbilitySpecials());
        Map<String, Float> map = gameEntity2.getGlobalAbilityValues();
        if (map != null) {
            for (Map.Entry entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                object2FloatOpenHashMap.put((String)entry.getKey(), ((Float)entry.getValue()).floatValue());
            }
        }
        float f2 = 0.0f;
        if (abilityMixinData.speed != null && (f2 = abilityMixinData.speed.get(object2FloatOpenHashMap, 0.0f)) == 0.0f) {
            f2 = abilityMixinData.speed.get(ability, 0.0f);
        }
        if (f2 == 0.0f && abilityMixinData.value != null && abilityMixinData.value != DynamicFloat.ZERO) {
            // ChangeNyxValueMixin: a delta that works out to zero (NyxFreeze off) means no change,
            // not "fall back to ratio", whose unset default of one would add a point every time.
            f2 = abilityMixinData.value.get(object2FloatOpenHashMap, 0.0f);
            if (f2 == 0.0f) {
                return true;
            }
        }
        if (f2 == 0.0f && abilityMixinData.ratio != null) {
            f2 = abilityMixinData.ratio.get(object2FloatOpenHashMap, 0.0f);
        }
        if (f2 == 0.0f) {
            return true;
        }
        float f3 = 0.0f;
        if (map != null && map.containsKey(NYX_KEY) && map.get(NYX_KEY) != null) {
            f3 = ((Float)map.get(NYX_KEY)).floatValue();
        }
        float f4 = map != null && map.containsKey(NYX_MIN) && map.get(NYX_MIN) != null ? ((Float)map.get(NYX_MIN)).floatValue() : 0.0f;
        float f5 = f = map != null && map.containsKey(NYX_MAX) && map.get(NYX_MAX) != null ? ((Float)map.get(NYX_MAX)).floatValue() : 120.0f;
        if (object2FloatOpenHashMap.containsKey(NYX_MIN)) {
            f4 = object2FloatOpenHashMap.getFloat(NYX_MIN);
        }
        if (object2FloatOpenHashMap.containsKey(NYX_MAX)) {
            f = object2FloatOpenHashMap.getFloat(NYX_MAX);
        }
        float f6 = Math.max(f4, Math.min(f, f3 + f2));
        // Negative Nyx delta = nightsoul exploration drain → suppress parallel stamina briefly.
        if (f2 < 0.0f || f6 < f3) {
            emu.grasscutter.game.ability.NightsoulStaminaExempt.markNyxCostActive(gameEntity2);
        }
        Player player = ability.getPlayerOwner();
        if (player != null) {
            try {
                MavuikaSpiritHelper.beforeGlobalFloatPut(player, gameEntity2, NYX_KEY, f6);
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().debug("NyxCost spirit hook failed", throwable);
            }
        }
        if (map == null) {
            return false;
        }
        map.put(NYX_KEY, Float.valueOf(f6));
        gameEntity2.onAbilityValueUpdate();
        if (gameEntity2.getScene() != null && gameEntity2.getScene().getHost() != null) {
            gameEntity2.getScene().getHost().sendPacket(new PacketServerGlobalValueChangeNotify(gameEntity2, NYX_KEY, f6));
        }
        return true;
    }
}
