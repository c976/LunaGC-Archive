package emu.grasscutter.data.binout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the 7.1 names of the special energy and Nightsoul actions.
 *
 * <p>7.1 renamed AddSpecialEnergy to ReviveSpecialEnergy, NyxAdd/NyxSet to AddNyxValue/SetNyxValue
 * and NyxCostMixin to ChangeNyxValueMixin. The old names no longer appear anywhere in the
 * resources, so they parsed as no type at all and Mavuika's and Skirk's burst energy never moved.
 */
public final class NyxActionNamesTest {
    private static AbilityModifierAction action(String json) {
        return JsonUtils.decode(json, AbilityModifierAction.class);
    }

    @Test
    @DisplayName("the 7.1 action names map onto the existing handlers")
    public void renamedActionsParse() {
        assertEquals(
                AbilityModifierAction.Type.AddSpecialEnergy,
                action("{\"$type\":\"ReviveSpecialEnergy\",\"value\":45}").type);
        assertEquals(
                AbilityModifierAction.Type.NyxAdd, action("{\"$type\":\"AddNyxValue\",\"value\":10}").type);
        assertEquals(AbilityModifierAction.Type.NyxSet, action("{\"$type\":\"SetNyxValue\"}").type);
        assertEquals(
                AbilityModifierAction.Type.AddSpecialEnergy,
                action("{\"$type\":\"AddSpecialEnergy\"}").type);
    }

    @Test
    @DisplayName("a SetNyxValue with no value writes zero, not ratio's default of one")
    public void bareSetNyxValueWritesZero() {
        var values = new Object2FloatOpenHashMap<String>();
        assertEquals(0f, action("{\"$type\":\"SetNyxValue\"}").writtenValue().get(values, 0f));
        assertEquals(45f, action("{\"$type\":\"ReviveSpecialEnergy\",\"value\":45}").writtenValue().get(values, 0f));
    }

    @Test
    @DisplayName("ChangeNyxValueMixin parses as NyxCostMixin and keeps its delta")
    public void changeNyxValueMixinParses() {
        var mixin =
                JsonUtils.decode(
                        "{\"$type\":\"ChangeNyxValueMixin\",\"value\":[0,0.8,\"NyxFreeze\",\"MUL\",\"SUB\"]}",
                        AbilityMixinData.class);
        assertEquals(AbilityMixinData.Type.NyxCostMixin, mixin.type);

        var values = new Object2FloatOpenHashMap<String>();
        values.put("NyxFreeze", 1f);
        assertEquals(-0.8f, mixin.value.get(values, 0f), 1e-6f);
    }

    @Test
    @DisplayName("other mixins' value keys (ints and flags) still parse")
    public void otherMixinValuesStillParse() {
        JsonUtils.decode("{\"$type\":\"AttachToStateIDMixin\",\"value\":3}", AbilityMixinData.class);
        JsonUtils.decode("{\"$type\":\"RigidBodyFreezeMixin\",\"value\":true}", AbilityMixinData.class);
    }
}
