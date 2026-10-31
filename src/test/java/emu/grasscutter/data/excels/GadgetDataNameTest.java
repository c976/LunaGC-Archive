package emu.grasscutter.data.excels;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins how a gadget with no name is compared against.
 *
 * <p>7345 of the 30557 rows in GadgetExcelConfigData.json omit {@code jsonName} and 13001 omit
 * {@code itemJsonName}, so both getters return null for a large share of gadgets. Calling equals on
 * the getter throws, and on the spawn path that throw escapes into the world tick - the world then
 * stops advancing and a player entering sees a white screen.
 *
 * <p>The fix is to compare constant-first. This test is about that ordering, so it asserts on the
 * comparison rather than on the data class.
 */
public final class GadgetDataNameTest {
    private static GadgetData nameless() {
        var data = JsonUtils.decode("{\"id\":70900001}", GadgetData.class);
        assertNull(data.getJsonName(), "the row under test must have no jsonName");
        return data;
    }

    @Test
    @DisplayName("a gadget with no jsonName compares false instead of throwing")
    public void namelessGadgetComparesFalse() {
        var data = nameless();

        assertDoesNotThrow(
                () -> assertFalse("SceneObj_Gear_Operator_Mamolu_Entity".equals(data.getJsonName())));
    }

    @Test
    @DisplayName("a gadget with no itemJsonName compares false instead of throwing")
    public void namelessItemComparesFalse() {
        var data = nameless();

        assertDoesNotThrow(() -> assertFalse("Default_MonsterWeapon".equals(data.getItemJsonName())));
    }

    @Test
    @DisplayName("a named gadget still matches")
    public void namedGadgetStillMatches() {
        var data =
                JsonUtils.decode(
                        "{\"id\":70900001,\"jsonName\":\"SceneObj_Gear_Operator_Mamolu_Entity\"}",
                        GadgetData.class);

        assertTrue("SceneObj_Gear_Operator_Mamolu_Entity".equals(data.getJsonName()));
    }
}
