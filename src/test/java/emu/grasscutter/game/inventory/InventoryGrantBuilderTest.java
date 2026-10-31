package emu.grasscutter.game.inventory;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.ItemData;
import org.junit.jupiter.api.Test;

final class InventoryGrantBuilderTest {
    private static final Gson GSON = new Gson();

    private static ItemData item(int id, String type, int stackLimit) {
        return GSON.fromJson(
                "{\"id\":" + id + ",\"itemType\":\"" + type
                        + "\",\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":"
                        + stackLimit + "}",
                ItemData.class);
    }

    @Test
    void equipmentGrantCreatesIndependentInstances() {
        var weapons = InventoryGrantBuilder.create(item(11501, "ITEM_WEAPON", 1), 3, 50);
        assertEquals(3, weapons.size());
        assertNotSame(weapons.get(0), weapons.get(1));
        assertEquals(50, weapons.get(2).getLevel());
        assertEquals(2, weapons.get(2).getPromoteLevel());
    }

    @Test
    void materialQuantityIsNotTruncatedDuringPreparation() {
        var material = InventoryGrantBuilder.create(item(2001, "ITEM_MATERIAL", 10), 12, 1);
        assertEquals(1, material.size());
        assertEquals(12, material.get(0).getCount());
    }

    @Test
    void invalidQuantityAndMissingDefinitionAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryGrantBuilder.create(null, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryGrantBuilder.create(item(2001, "ITEM_MATERIAL", 10), 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryGrantBuilder.create(item(11501, "ITEM_WEAPON", 1), 10001, 1));
    }

    @Test
    void useOnGainQuantityCreatesOneEffectPerCard() {
        ItemData data = GSON.fromJson(
                "{\"id\":210001,\"itemType\":\"ITEM_MATERIAL\","
                        + "\"materialType\":\"MATERIAL_NAMECARD\",\"useOnGain\":true}",
                ItemData.class);
        var cards = InventoryGrantBuilder.create(data, 3, 1);
        assertEquals(3, cards.size());
        assertNotSame(cards.get(0), cards.get(1));
        assertEquals(1, cards.get(0).getCount());
        assertEquals(1, cards.get(2).getCount());
        assertThrows(IllegalArgumentException.class,
                () -> InventoryGrantBuilder.create(data, 10_001, 1));
    }
}
