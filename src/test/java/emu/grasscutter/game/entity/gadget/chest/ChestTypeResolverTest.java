package emu.grasscutter.game.entity.gadget.chest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChestTypeResolverTest {
    @Test
    void importedKindsPreserveIdentityEvenWithSharedGadgetIds() {
        assertEquals("normal", ChestTypeResolver.fromExploreKind("chest_common"));
        assertEquals("normal", ChestTypeResolver.fromExploreKind("chest_exquisite"));
        assertEquals("normal", ChestTypeResolver.fromExploreKind("chest_precious"));
        assertEquals("normal", ChestTypeResolver.fromExploreKind("chest_luxurious"));
        assertEquals("normal", ChestTypeResolver.fromExploreKind("chest_puzzle"));
        assertEquals("mora", ChestTypeResolver.fromExploreKind("chest_mora"));
        assertEquals("remarkable", ChestTypeResolver.fromExploreKind("chest_remarkable"));
        assertNull(ChestTypeResolver.fromExploreKind(null));
        assertNull(ChestTypeResolver.fromExploreKind("unknown_chest"));
        assertEquals("common", ChestTypeResolver.gradeFromExploreKind("chest_common"));
        assertEquals("exquisite", ChestTypeResolver.gradeFromExploreKind("chest_exquisite"));
        assertEquals("precious", ChestTypeResolver.gradeFromExploreKind("chest_precious"));
        assertEquals("luxurious", ChestTypeResolver.gradeFromExploreKind("chest_luxurious"));
        assertNull(ChestTypeResolver.gradeFromExploreKind("chest_mora"));
        assertNull(ChestTypeResolver.gradeFromExploreKind("chest_remarkable"));
        assertNull(ChestTypeResolver.gradeFromExploreKind("chest_puzzle"));
    }

    @Test
    void nativeIdentityIsOnlyDerivedFromRecognizedOrdinaryChestPrefabs() {
        for (String name : new String[]{
                "SceneObj_Chest_Default_Lv1",
                "SceneObj_Chest_Locked_Lv2",
                "SceneObj_Chest_Bramble_Lv3",
                "SceneObj_Chest_Frozen_Lv4",
                "SceneObj_Chest_Rock_Lv1",
                "SceneObj_EssenceChest_Default_Lv1"}) {
            assertEquals("normal", ChestTypeResolver.fromNativeJsonName(name), name);
        }
        assertNull(ChestTypeResolver.fromNativeJsonName(null));
        assertNull(ChestTypeResolver.fromNativeJsonName(
                "SceneObj_Area_Dq_Property_Ani_Prop_JunkChest_01"));
        assertNull(ChestTypeResolver.fromNativeJsonName(
                "SceneObj_Area_Common_Property_Ani_Prop_MoonlitBox_01"));
    }
}
