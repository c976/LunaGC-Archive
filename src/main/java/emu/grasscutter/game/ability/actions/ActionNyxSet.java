/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.MavuikaSpiritHelper;
import emu.grasscutter.game.ability.actions.AbilityAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.Map;

@AbilityAction(value=AbilityModifier.AbilityModifierAction.Type.NyxSet)
public final class ActionNyxSet
extends AbilityActionHandler {
    private static final String NYX_KEY = "NyxValue";

    @Override
    public boolean execute(Ability ability, AbilityModifier.AbilityModifierAction abilityModifierAction, ByteString byteString, GameEntity gameEntity) {
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
        for (Map.Entry entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            object2FloatOpenHashMap.put((String)entry.getKey(), (Float)entry.getValue());
        }
        // A SetNyxValue with no value clears it; ratio's unset default is one.
        float f = abilityModifierAction.writtenValue().get(object2FloatOpenHashMap, 0.0f);
        float f2 = ActionNyxSet.resolveBound(object2FloatOpenHashMap, abilityModifierAction.maxValue, "NyxValueMax", 120.0f);
        float f3 = ActionNyxSet.resolveBound(object2FloatOpenHashMap, abilityModifierAction.minValue, "NyxValueMin", 0.0f);
        f = Math.max(f3, Math.min(f2, f));
        if (ability.getPlayerOwner() != null) {
            MavuikaSpiritHelper.beforeGlobalFloatPut(ability.getPlayerOwner(), gameEntity2, NYX_KEY, f);
        }
        map.put(NYX_KEY, Float.valueOf(f));
        gameEntity2.onAbilityValueUpdate();
        if (gameEntity2.getScene() != null && gameEntity2.getScene().getHost() != null) {
            gameEntity2.getScene().getHost().sendPacket(new PacketServerGlobalValueChangeNotify(gameEntity2, NYX_KEY, f));
        }
        return true;
    }

    private static float resolveBound(Object2FloatOpenHashMap<String> object2FloatOpenHashMap, DynamicFloat dynamicFloat, String string, float f) {
        float f2;
        if (dynamicFloat != null && !Float.isNaN(f2 = dynamicFloat.get(object2FloatOpenHashMap, Float.NaN))) {
            return f2;
        }
        if (object2FloatOpenHashMap.containsKey(string)) {
            return object2FloatOpenHashMap.getFloat(string);
        }
        return f;
    }
}
