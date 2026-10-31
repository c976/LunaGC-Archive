package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
class SceneGroundRewardPreparationTest {
    private static final int CHEST = 99000011;
    private GadgetData oldChest;
    @BeforeEach void installChest() {
        oldChest = GameData.getGadgetDataMap().put(CHEST,
                JsonUtils.decode("{\"id\":99000011}", GadgetData.class));
    }
    @AfterEach void restoreChest() {
        if (oldChest == null) GameData.getGadgetDataMap().remove(CHEST);
        else GameData.getGadgetDataMap().put(CHEST, oldChest);
    }

    @Test void unknownItemFailsBeforeAnyPickupIsPublished() {
        var player = new TestPlayers.TestPlayer(TestPlayers.server(), 77881);
        var scene = new Scene(new World(player), JsonUtils.decode("{\"id\":3}", SceneData.class));
        var chest = new EntityGadget(scene, CHEST, new Position());
        assertThrows(IllegalArgumentException.class,
                () -> scene.prepareItemEntities(987654321, 1, chest));
        assertTrue(scene.getEntities().isEmpty());
    }

    @Test void preparedGroundBatchRemainsUnpublishedAndKeepsItsFullQuantity() {
        int itemId = 987650001;
        var old = GameData.getItemDataMap().get(itemId);
        try {
            GameData.getItemDataMap().put(itemId, JsonUtils.decode("""
                    {"id":987650001,"itemType":"ITEM_MATERIAL","materialType":"MATERIAL_NONE",
                     "gadgetId":70600001,"stackLimit":9999}
                    """, ItemData.class));
            var player = new TestPlayers.TestPlayer(TestPlayers.server(), 77882);
            var scene = new Scene(new World(player), JsonUtils.decode("{\"id\":3}", SceneData.class));
            var chest = new EntityGadget(scene, CHEST, new Position());
            var drops = scene.prepareItemEntities(itemId, 7, chest);
            assertEquals(1, drops.size());
            assertEquals(7, drops.getFirst().getCount());
            assertNotNull(drops.getFirst().toProto());
            assertTrue(scene.getEntities().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> scene.prepareItemEntities(itemId, 0, chest));
        } finally {
            if (old == null) GameData.getItemDataMap().remove(itemId);
            else GameData.getItemDataMap().put(itemId, old);
        }
    }
}
