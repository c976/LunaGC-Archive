package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.utils.JsonUtils;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
class WorldBossRewardBatchTest {
    @Test
    void repeatedNativeWeaponsAreDistinctButMoraStillStacks() {
        var weapon = JsonUtils.decode(
                "{\"id\": 11101, \"itemType\": \"ITEM_WEAPON\"}", ItemData.class);
        var mora = JsonUtils.decode(
                "{\"id\": 202, \"itemType\": \"ITEM_VIRTUAL\"}", ItemData.class);
        var map = GameData.getItemDataMap();
        var priorWeapon = map.put(11101, weapon);
        var priorMora = map.put(202, mora);
        try {
            var batch = new ArrayList<GameItem>();
            DropSystem.appendNativeDrop(11101, 3, batch);
            DropSystem.appendNativeDrop(202, 2000, batch);
            DropSystem.appendNativeDrop(202, 1000, batch);
            assertEquals(4, batch.size());
            for (int n = 0; n < 3; n++) {
                assertEquals(11101, batch.get(n).getItemId());
                assertEquals(1, batch.get(n).getCount());
                for (int m = 0; m < n; m++) assertNotSame(batch.get(m), batch.get(n));
            }
            assertEquals(202, batch.get(3).getItemId());
            assertEquals(3000, batch.get(3).getCount());
        } finally {
            if (priorWeapon == null) map.remove(11101); else map.put(11101, priorWeapon);
            if (priorMora == null) map.remove(202); else map.put(202, priorMora);
        }
    }
}
