package emu.grasscutter.game.entity.gadget.chest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.inventory.ItemType;
import org.junit.jupiter.api.Test;

class WorldChestAutoCollectTest {
    @Test
    void nativeAutopickAndVirtualCurrencyAreDeliveredDirectly() {
        assertTrue(WorldChestLootHelper.shouldAutoCollect(ItemType.ITEM_VIRTUAL, 0, false));
        assertFalse(WorldChestLootHelper.shouldAutoCollect(ItemType.ITEM_VIRTUAL, 123, false));
        assertFalse(WorldChestLootHelper.shouldAutoCollect(ItemType.ITEM_WEAPON, 0, false));
        assertTrue(WorldChestLootHelper.shouldAutoCollect(ItemType.ITEM_WEAPON, 123, true));
    }
}
