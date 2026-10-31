package emu.grasscutter.game.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityMixinData;
import com.google.gson.JsonParser;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.ability.mixins.CheckSubTagScanEntityMixin;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;

/**
 * Natlan's Saurian riding (龙之魂附): the mixins that drive it, and the monster-to-vehicle lookup.
 *
 * <p>The four vehicle mixins exist in the 7.1 resource data but had no constant in
 * {@link AbilityMixinData.Type}, so GSON left their type null and every invoke landed on no handler.
 * {@code TryEnterVehicleMixin} is the one that matters: it mixes into {@code Avatar_Perform}, which the
 * client attaches the moment the mount animation starts, and it is the server's cue to put the player on
 * the Saurian. Dropped, the animation plays out and the camera falls back to the character - exactly the
 * reported symptom.
 *
 * <p>The two tests that pin values out of the extracted 7.1 data assume-skip when {@code resources/} is
 * absent, which it is on CI - the rest of this class needs nothing but its own fixtures.
 */
public final class NatsaurusVehicleTest {

    /** Real rows of GadgetExcelConfigData, so the tribe lookup is exercised against real names. */
    private static final String[] VEHICLE_ROWS = {
        "{\"id\":45003011,\"jsonName\":\"Natsaurus_Drillhead_Vehicle_01\",\"type\":\"Vehicle\"}",
        "{\"id\":45003012,\"jsonName\":\"Natsaurus_Drillhead_Vehicle_HumanDragonCollaboration_01\",\"type\":\"Vehicle\"}",
        "{\"id\":45003021,\"jsonName\":\"Natsaurus_Hookwalker_Vehicle_01\",\"type\":\"Vehicle\"}",
        "{\"id\":45003031,\"jsonName\":\"Natsaurus_Mosasaurus_Vehicle_01\",\"type\":\"Vehicle\"}",
        "{\"id\":45003041,\"jsonName\":\"Natsaurus_Flamingo_Vehicle_01\",\"type\":\"Vehicle\"}",
        "{\"id\":45003051,\"jsonName\":\"Natsaurus_Shamansaurus_Vehicle_01\",\"type\":\"Vehicle\"}",
        "{\"id\":45003060,\"jsonName\":\"Natsaurus_Bisonsaurus_Vehicle_01\",\"type\":\"Vehicle\"}",
        // Skiffs: the same EntityVehicle class and the same isInvincible gadget flag, so anything that
        // tunes Saurian mounts has to be able to tell these apart from those.
        "{\"id\":45001001,\"jsonName\":\"Skiff_Normal_01\",\"type\":\"Vehicle\"}",
        "{\"id\":45001002,\"jsonName\":\"Skiff_Normal_Beidou\",\"type\":\"Vehicle\"}",
        // Not a vehicle, and a vehicle with no jsonName: both must be ignored.
        "{\"id\":70310006,\"jsonName\":\"SceneObj_Gather_Small\",\"type\":\"Gadget\"}",
        "{\"id\":45003010,\"type\":\"Vehicle\"}"
    };

    @BeforeEach
    public void loadVehicleRows() {
        for (String row : VEHICLE_ROWS) {
            var data = JsonUtils.decode(row, GadgetData.class);
            GameData.getGadgetDataMap().put(data.getId(), data);
        }
    }

    @AfterEach
    public void unloadVehicleRows() {
        for (String row : VEHICLE_ROWS) {
            GameData.getGadgetDataMap().remove(JsonUtils.decode(row, GadgetData.class).getId());
        }
    }

    @Test
    @DisplayName("a Saurian monster resolves to its tribe's vehicle gadget")
    public void monsterResolvesToVehicleGadget() {
        // Both spellings occur in MonsterExcelConfigData, and the Small variant shares the tribe.
        assertEquals(45003011, NatsaurusVehicleHelper.vehicleGadgetId("Monster_Natsaurus_Drillhead_Normal_01"));
        assertEquals(45003011, NatsaurusVehicleHelper.vehicleGadgetId("Monster_Natsaurus_Drillhead_Small_01"));
        assertEquals(
                45003060, NatsaurusVehicleHelper.vehicleGadgetId("Monster_Natsaurus_Bisonsaurus_Normal_TheAbyss_01"));
        assertEquals(
                45003031, NatsaurusVehicleHelper.vehicleGadgetId("Natsaurus_Mosasaurus_Normal_TheAbyss_01"));
    }

    @Test
    @DisplayName("anything that is not a Saurian resolves to no vehicle at all")
    public void nonSaurianResolvesToNothing() {
        assertEquals(0, NatsaurusVehicleHelper.vehicleGadgetId("Monster_Zharptitsa_ReturnToBorn"));
        assertEquals(0, NatsaurusVehicleHelper.vehicleGadgetId("Natsaurus_"));
        assertEquals(0, NatsaurusVehicleHelper.vehicleGadgetId(null));
    }

    @Test
    @DisplayName("the real Saurian transfer ability registers its vehicle mixins under a local id")
    public void transferAbilityRegistersVehicleMixins() throws Exception {
        // Local id 513 is what the client actually sent 37 times while standing next to a Saurian;
        // an invoke whose head carries a local id goes straight to handleServerInvoke, which
        // dispatches on this map. So this is the assertion that says whether the invoke can land.
        var path =
                Path.of("resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Natsaurus_V5_0_Dragon.json");
        assumeTrue(Files.exists(path), "needs the extracted 7.1 resource data");

        AbilityData transfer = null;
        for (var element : JsonParser.parseReader(Files.newBufferedReader(path)).getAsJsonArray()) {
            var data = JsonUtils.decode(element.getAsJsonObject().getAsJsonObject("Default"), AbilityData.class);
            if ("TeamAbility_Natsaurus_Transfer_Vehicle_Skill".equals(data.abilityName)) {
                transfer = data;
                break;
            }
        }
        assertNotNull(transfer, "TeamAbility_Natsaurus_Transfer_Vehicle_Skill must be in the ability file");
        transfer.initialize();

        var byType = new HashMap<AbilityMixinData.Type, Integer>();
        transfer.localIdToMixin.forEach((id, mixin) -> byType.put(mixin.type, id));

        assertTrue(
                byType.containsKey(AbilityMixinData.Type.TryEnterVehicleMixin),
                () -> "TryEnterVehicleMixin must be registered, got " + byType);
        assertTrue(
                byType.containsKey(AbilityMixinData.Type.DoActionOnVehicleInteractPostMixin),
                () -> "DoActionOnVehicleInteractPostMixin must be registered, got " + byType);

        // Registration is not the same as the invoke arriving. In a full capture beside a Saurian the
        // only non-zero head localId the client ever sent was 513, which decodes as an ACTION id
        // (1 + 1<<9), not a MODIFIER_MIXIN id (4 + modifierIndex<<3 + mixinIndex<<9). So the mixin
        // above is reachable but was never invoked - pinned so nobody reads this test as proof that
        // the client asks for it.
        assertFalse(transfer.localIdToMixin.containsKey(513));

        // The client numbers modifiers by file order and keeps one mixin counter across the whole
        // ability, so the "is a Saurian in range" scan - the 34th mixin of the ability - is 17156.
        // The server used to sort modifiers by name and restart the counter on each one, which left
        // 17156 out of the map entirely and dropped the invoke before any handler could run.
        var scan = transfer.localIdToMixin.get(17156);
        assertNotNull(scan, "localId 17156 must resolve - it is the Saurian range scan");
        assertEquals("CheckSubTagScanEntityMixin", String.valueOf(scan.type));

        // Avatar_Perform is the first modifier in the file and TryEnterVehicleMixin its first mixin, so
        // it lands on the container's base id. Pinned because the server now fires this one off the
        // modifier attach rather than an invoke: if the id ever moves, the attach-driven path and any
        // capture decoded against 4 both go stale at once.
        assertEquals(
                AbilityMixinData.Type.TryEnterVehicleMixin,
                transfer.localIdToMixin.get(4).type,
                "TryEnterVehicleMixin must be localId 4");
    }

    @Test
    void scanInvokePayloadCarriesTheSelectedSaurian() {
        // Byte-for-byte from a capture: the client held the interaction key beside Saurian 4194499 and
        // this was the whole ABILITY_MIXIN_CHECK_SCAN_ENTITY payload. The trailing 00 is padding the
        // client writes after the id, so a decoder that took the first varint would read the tag byte.
        var selected = com.google.protobuf.ByteString.copyFrom(
                hex("1205c381800200"));
        assertEquals(
                4194499,
                CheckSubTagScanEntityMixin.firstScannedEntityId(selected),
                "the scanned Saurian must come out of the real payload");

        // The release half of the same hold arrives with no payload at all. Treating that as "still
        // selected" would leave a stale target for the next attach to board.
        assertEquals(0, CheckSubTagScanEntityMixin.firstScannedEntityId(
                com.google.protobuf.ByteString.EMPTY));
        assertEquals(0, CheckSubTagScanEntityMixin.firstScannedEntityId(null));

        // Reverse check: the same varint under a different field number is not a scan result, and a
        // truncated length must not run off the end.
        assertEquals(0, CheckSubTagScanEntityMixin.firstScannedEntityId(
                com.google.protobuf.ByteString.copyFrom(hex("18c381800200"))));
        assertEquals(0, CheckSubTagScanEntityMixin.firstScannedEntityId(
                com.google.protobuf.ByteString.copyFrom(hex("1205c381"))));
    }

    @Test
    @DisplayName("a parked Saurian mount is recognised as a soul candle")
    public void parkedVehicleGadgetIsRideable() {
        // What stands in the world as a soul candle is the vehicle gadget itself, un-ridden - so the
        // gadget id has to be accepted as an attach target, or the scan picks the candle up and the ride
        // falls through to whichever wild dragon is closest.
        assertTrue(NatsaurusVehicleHelper.isNatsaurusVehicleGadget(45003060));

        // Reverse checks: a Vehicle row with no jsonName carries no tribe, and a non-vehicle gadget is
        // not a mount however Saurian-shaped it looks.
        assertFalse(NatsaurusVehicleHelper.isNatsaurusVehicleGadget(45003010));
        assertFalse(NatsaurusVehicleHelper.isNatsaurusVehicleGadget(70310006));
        assertFalse(NatsaurusVehicleHelper.isNatsaurusVehicleGadget(0));

        // A skiff is a Vehicle gadget too and its config carries the very same isInvincible flag, so
        // "is a rideable gadget" is not a usable gate for the Natlan tuning. Widening that gate to every
        // vehicle would silently retune boats - their HP pool, their death removal, even clearing them
        // away on a teleport.
        assertFalse(NatsaurusVehicleHelper.isNatsaurusVehicleGadget(45001001), "Skiff_Normal_01");
        assertFalse(NatsaurusVehicleHelper.isNatsaurusVehicleGadget(45001002), "Skiff_Normal_Beidou");
    }

    @Test
    void soulCandleTribesMatchTheResourceData() throws Exception {
        // NatsaurusVehicleHelper hardcodes candle gadget id -> tribe, because nothing in the gadget rows
        // names it. This re-derives the pairing the long way round and fails if the data moves:
        //   Dragon_Gadget_NN (gadget row)  ->  id
        //   Dragon_Gadget_NN (gadget config) -> carries ability Prop_SaurTotem_NN
        //   Prop_SaurTotem_NN (ability)      -> fires Eff_Vehicle_Natsaurus_<tribe>_SaurTotem
        for (var required :
                List.of(
                        "resources/ExcelBinOutput/GadgetExcelConfigData.json",
                        "resources/BinOutput/Gadget/ConfigGadget_Scene_V5_0_WhiteBox.json",
                        "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_V5_0_WhiteBox.json")) {
            assumeTrue(Files.exists(Path.of(required)), "needs the extracted 7.1 resource data");
        }

        var candles = new HashMap<Integer, String>();

        var gadgetRows = JsonParser.parseReader(Files.newBufferedReader(
                Path.of("resources/ExcelBinOutput/GadgetExcelConfigData.json"))).getAsJsonArray();
        var indexById = new HashMap<String, Integer>();
        for (var row : gadgetRows) {
            var o = row.getAsJsonObject();
            if (!o.has("jsonName") || !o.has("id")) continue;
            var name = o.get("jsonName").getAsString();
            var m = java.util.regex.Pattern
                    .compile("SceneObj_Area_Nt_Property_Ani_Prop_Dragon_Gadget_(\\d+)$")
                    .matcher(name);
            if (m.matches()) indexById.put(m.group(1), o.get("id").getAsInt());
        }
        assertEquals(6, indexById.size(), () -> "expected six soul candles, got " + indexById);

        var gadgetConfig = JsonParser.parseReader(Files.newBufferedReader(Path.of(
                "resources/BinOutput/Gadget/ConfigGadget_Scene_V5_0_WhiteBox.json"))).getAsJsonObject();
        var abilities = JsonParser.parseReader(Files.newBufferedReader(Path.of(
                "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_V5_0_WhiteBox.json")))
                .getAsJsonArray();

        for (var index : indexById.keySet()) {
            var config = gadgetConfig
                    .getAsJsonObject("SceneObj_Area_Nt_Property_Ani_Prop_Dragon_Gadget_" + index);
            assertNotNull(config, "no gadget config for Dragon_Gadget_" + index);

            String totemAbility = null;
            var carried = config.getAsJsonArray("abilities");
            assertNotNull(carried, "Dragon_Gadget_" + index + " has no abilities array");
            for (var ability : carried) {
                var name = ability.getAsJsonObject().get("abilityName").getAsString();
                if (name.endsWith("Prop_SaurTotem_" + index)) totemAbility = name;
            }
            assertNotNull(totemAbility, "Dragon_Gadget_" + index + " carries no matching SaurTotem ability");

            String tribe = null;
            for (var group : abilities) {
                for (var entry : group.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    var body = entry.getValue().getAsJsonObject();
                    if (!body.has("abilityName")) continue;
                    if (!totemAbility.equals(body.get("abilityName").getAsString())) continue;
                    var found = java.util.regex.Pattern
                            .compile("Eff_Vehicle_Natsaurus_(\\w+?)_SaurTotem")
                            .matcher(body.toString());
                    if (found.find()) tribe = found.group(1);
                }
            }
            assertNotNull(tribe, totemAbility + " names no tribe");

            candles.put(indexById.get(index), tribe);
        }

        for (var entry : candles.entrySet()) {
            assertTrue(
                    NatsaurusVehicleHelper.isSoulCandle(entry.getKey()),
                    () -> "candle " + entry.getKey() + " is not recognised as one");
            assertEquals(
                    entry.getValue(),
                    NatsaurusVehicleHelper.soulCandleTribe(entry.getKey()),
                    () -> "candle " + entry.getKey() + " is mapped to the wrong tribe");
        }

        // Reverse check: a gadget that is not a candle must not resolve to one, or every scene prop in
        // Natlan would read as rideable.
        assertFalse(NatsaurusVehicleHelper.isSoulCandle(70801014));
        assertNull(NatsaurusVehicleHelper.soulCandleTribe(70801021));
    }

    private static byte[] hex(String s) {
        var out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
