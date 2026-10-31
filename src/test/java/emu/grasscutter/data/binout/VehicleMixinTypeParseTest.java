package emu.grasscutter.data.binout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mixin {@code $type} names that 7.1 ships but {@link AbilityMixinData.Type} had no constant for.
 *
 * <p>GSON leaves the type null for a name it cannot resolve, and a null type matches no handler, so the
 * mixin is dropped without a sound: the action lists next to it still parse, which is why this reads as a
 * working ability right up until the one thing it asked for never happens.
 */
public final class VehicleMixinTypeParseTest {

    @Test
    @DisplayName("the vehicle mixin names parse onto a type instead of nulling out")
    void vehicleMixinsParse() {
        assertEquals(
                AbilityMixinData.Type.TryEnterVehicleMixin,
                JsonUtils.decode(
                                "{\"$type\":\"TryEnterVehicleMixin\",\"vehicleType\":\"Natsaurus\","
                                        + "\"FKKOBPIDGPC\":0.6}",
                                AbilityMixinData.class)
                        .type);
        assertEquals(
                AbilityMixinData.Type.DoActionOnVehicleInteractPostMixin,
                JsonUtils.decode(
                                "{\"$type\":\"DoActionOnVehicleInteractPostMixin\",\"onVehicleIn\":"
                                        + "[{\"$type\":\"AttachModifier\",\"modifierName\":\"Remove_Avatar_Perform\"}]}",
                                AbilityMixinData.class)
                        .type);
        assertEquals(
                AbilityMixinData.Type.TriggerVehicleOff,
                JsonUtils.decode("{\"$type\":\"TriggerVehicleOff\"}", AbilityMixinData.class).type);
        assertEquals(
                AbilityMixinData.Type.PhlogistonAreaMixin,
                JsonUtils.decode("{\"$type\":\"PhlogistonAreaMixin\"}", AbilityMixinData.class).type);
    }

    @Test
    @DisplayName("the Saurian range scan and its siblings parse too")
    void scanMixinsParse() {
        assertEquals(
                AbilityMixinData.Type.CheckSubTagScanEntityMixin,
                JsonUtils.decode(
                                "{\"$type\":\"CheckSubTagScanEntityMixin\",\"tag\":\"Entity_Natsaurus_Group\"}",
                                AbilityMixinData.class)
                        .type);
        assertEquals(
                AbilityMixinData.Type.SubTagScanEntityMixin,
                JsonUtils.decode("{\"$type\":\"SubTagScanEntityMixin\"}", AbilityMixinData.class).type);
        assertEquals(
                AbilityMixinData.Type.StageReadyMixin,
                JsonUtils.decode("{\"$type\":\"StageReadyMixin\"}", AbilityMixinData.class).type);
    }

    @Test
    @DisplayName("DoActionOnVehicleInteractPostMixin keeps both of its action halves")
    void vehicleInteractActionsParse() {
        var mixin =
                JsonUtils.decode(
                        "{\"$type\":\"DoActionOnVehicleInteractPostMixin\","
                                + "\"onVehicleIn\":[{\"$type\":\"AttachModifier\",\"modifierName\":\"Remove_Avatar_Perform\"}],"
                                + "\"NFPAFDMIGFA\":[{\"$type\":\"SetGlobalValue\",\"key\":\"Disable_Eff_SaurTotem\",\"value\":1}]}",
                        AbilityMixinData.class);

        assertEquals(1, mixin.onVehicleIn.length);
        assertEquals("Remove_Avatar_Perform", mixin.onVehicleIn[0].modifierName);
        assertEquals(1, mixin.onVehicleOut.length);
        assertEquals("Disable_Eff_SaurTotem", mixin.onVehicleOut[0].key);
    }

    @Test
    @DisplayName("the scan's two halves survive, one under an obfuscated key")
    void scanActionsParse() {
        var mixin =
                JsonUtils.decode(
                        "{\"$type\":\"CheckSubTagScanEntityMixin\",\"tag\":\"Entity_Natsaurus_Group\","
                                + "\"onSelectStart\":[{\"$type\":\"SetGlobalValue\",\"key\":\"Eff_Saur_Follow\",\"value\":1}],"
                                + "\"IMEDGAOLLEM\":[]}",
                        AbilityMixinData.class);

        assertEquals("Entity_Natsaurus_Group", mixin.tag);
        assertEquals(1, mixin.onSelectStart.length);
        assertEquals("Eff_Saur_Follow", mixin.onSelectStart[0].key);
        assertEquals(0, mixin.onSelectEnd.length);
    }

    @Test
    @DisplayName("an unknown mixin name still parses as no type - the failure mode being pinned")
    void unknownMixinStillNulls() {
        // GSON leaves the type null for a name with no constant, which is why the vehicle mixins
        // never reached a handler. Keeping this pinned shows the parse tests above are not vacuous.
        assertNull(JsonUtils.decode("{\"$type\":\"NoSuchMixinAtAll\"}", AbilityMixinData.class).type);
    }
}
