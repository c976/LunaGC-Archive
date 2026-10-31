package emu.grasscutter.game.entity.gadget.chest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldChestGradeTest {
    @Test
    void knownNativeGadgetIdsResolveToTheirGrades() {
        assertEquals(WorldChestLootHelper.Tier.COMMON,
                WorldChestLootHelper.resolveTier(70210001, null));
        assertEquals(WorldChestLootHelper.Tier.EXQUISITE,
                WorldChestLootHelper.resolveTier(70210002, null));
        assertEquals(WorldChestLootHelper.Tier.PRECIOUS,
                WorldChestLootHelper.resolveTier(70210003, null));
        assertEquals(WorldChestLootHelper.Tier.LUXURIOUS,
                WorldChestLootHelper.resolveTier(70210004, null));
    }

    @Test
    void nativeChestPrefabNamesResolveWhenTheirGadgetIdIsNotHardcoded() {
        assertEquals(WorldChestLootHelper.Tier.COMMON,
                WorldChestLootHelper.resolveTier(70211101, "SceneObj_Chest_Default_Lv1"));
        assertEquals(WorldChestLootHelper.Tier.EXQUISITE,
                WorldChestLootHelper.resolveTier(70211111, "SceneObj_Chest_Frozen_Lv2"));
        assertEquals(WorldChestLootHelper.Tier.PRECIOUS,
                WorldChestLootHelper.resolveTier(70211121, "SceneObj_Chest_Bramble_Lv3"));
        assertEquals(WorldChestLootHelper.Tier.LUXURIOUS,
                WorldChestLootHelper.resolveTier(70211131, "SceneObj_Chest_Default_Lv5"));
    }

    @Test
    void unknownGadgetCannotSilentlyBecomeACommonChest() {
        var error = assertThrows(IllegalStateException.class,
                () -> WorldChestLootHelper.resolveTier(70211101, null));
        assertTrue(error.getMessage().contains("70211101"));
        assertThrows(IllegalStateException.class,
                () -> WorldChestLootHelper.resolveTier(12345678, "UnknownChest"));
    }
}
