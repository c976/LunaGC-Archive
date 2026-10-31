package emu.grasscutter.data.binout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The order {@code modifier_local_id} indexes, pinned without touching {@code resources/} so it runs
 * anywhere - including CI, which checks out code only.
 *
 * <p>Two things are numbered by that index and they have to agree: the local id tables
 * {@link AbilityData#initialize()} builds, and the number the client puts into an invoke. They were
 * derived two different ways once - file order for the tables, name-sorted order for resolving a
 * modifier by {@code modifier_local_id} - which applied one modifier while naming another. Every
 * modifier list in the shipped ability data is close enough to alphabetical that only a deliberately
 * out-of-order fixture separates the two conventions, which is what this file is.
 */
class AbilityModifierOrderTest {

    /**
     * Listed Zeta, Alpha, Middle. Sorted that is Alpha, Middle, Zeta, so index 0 alone tells the two
     * conventions apart - and each modifier carries a different mixin so the local ids read back too. Those
     * three mixin types already exist upstream, so nothing here depends on a feature's enum additions.
     */
    private static final String ABILITY =
            """
            {
              "abilityName": "TestOrder_Ability",
              "modifiers": {
                "Zeta_First":   {"elementDurability": 100,
                                 "modifierMixins": [{"$type": "AvatarCombatMixin"}]},
                "Alpha_Second": {"elementDurability": 100,
                                 "modifierMixins": [{"$type": "ModifyDamageMixin"}]},
                "Middle_Third": {"elementDurability": 100,
                                 "modifierMixins": [{"$type": "CostStaminaMixin"}]}
              }
            }
            """;

    private static AbilityData decoded() {
        var data = JsonUtils.decode(ABILITY, AbilityData.class);
        assertNotNull(data, "the fixture must decode at all");
        data.initialize();
        return data;
    }

    private static List<String> listedNames(AbilityData data) {
        return data.orderedModifiers().stream().map(Map.Entry::getKey).toList();
    }

    @Test
    @DisplayName("GSON hands the modifiers back in file order, not sorted")
    void decodingPreservesFileOrder() {
        // Everything below leans on this, and it leans on a library default - GSON deserializes a Map
        // into its insertion-ordered LinkedTreeMap, and nothing here configures a different one. If that
        // ever changes, this is the assertion that says so.
        assertEquals(List.of("Zeta_First", "Alpha_Second", "Middle_Third"), listedNames(decoded()));
    }

    @Test
    @DisplayName("modifier_local_id indexes file order, and a name and its modifier stay paired")
    void localIdIndexesFileOrder() {
        var data = decoded();

        assertEquals("Zeta_First", data.modifierNameAt(0));
        assertEquals("Alpha_Second", data.modifierNameAt(1));
        assertEquals("Middle_Third", data.modifierNameAt(2));

        // Not just the same length: a lookup that sorted names while reading values off the map would
        // hand back Zeta_First's modifier beside Alpha_Second's name and still look well-formed.
        assertSame(data.modifiers.get("Zeta_First"), data.modifierAt(0));
        assertSame(data.modifiers.get("Middle_Third"), data.modifierAt(2));

        assertNull(data.modifierNameAt(3), "past the end must resolve to nothing");
        assertNull(data.modifierAt(-1));
        assertEquals(3, data.modifierCount());
    }

    @Test
    @DisplayName("a mixin's local id is numbered by file order and one counter across the ability")
    void mixinLocalIdsFollowFileOrder() {
        var data = decoded();

        // MODIFIER_MIXIN is 4 + (modifierIndex << 3) + (mixinIndex << 9), with the mixin counter run
        // across the whole ability. File order puts Zeta_First at modifier 0, its mixin at 0.
        assertEquals(
                AbilityMixinData.Type.AvatarCombatMixin,
                data.localIdToMixin.get(4).type,
                "the first modifier's mixin must own id 4");
        assertEquals(
                AbilityMixinData.Type.ModifyDamageMixin,
                data.localIdToMixin.get(4 + (1 << 3) + (1 << 9)).type);
        assertEquals(
                AbilityMixinData.Type.CostStaminaMixin,
                data.localIdToMixin.get(4 + (2 << 3) + (2 << 9)).type);

        // Numbered by sorted name instead, id 4 would belong to Alpha_Second's mixin.
        assertNotNull(data.localIdToMixin.get(4));
    }
}
