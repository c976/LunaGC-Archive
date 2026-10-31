package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.scripts.data.SceneGadget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
class NativeInteractDropTest {
    @Test
    void spincrystalAndSearchPointUseAuthoredRewardsAndDeliveryMode() throws Exception {
        var tables = GameData.getDropTableDataMap();
        var items = GameData.getItemDataMap();
        var oldTables = new HashMap<>(tables);
        var oldItems = new HashMap<>(items);
        var gson = new Gson();
        try {
            tables.clear();
            for (var row : JsonParser.parseString(Files.readString(
                    Path.of("src/test/resources/native-snapshots/DropTableExcelConfigData.json"))).getAsJsonArray()) {
                var table = gson.fromJson(row, DropTableData.class);
                tables.put(table.getId(), table);
            }
            // Only item identity and stack counts matter here, not equipment generation.
            for (var table : tables.values()) {
                for (var entry : table.getDropVec()) {
                    int id = entry.getId();
                    if (id > 0 && !tables.containsKey(id)) {
                        items.put(id, gson.fromJson("{\"id\":" + id
                                + ",\"itemType\":\"ITEM_MATERIAL\"}", ItemData.class));
                    }
                }
            }
            var drops = new DropSystem(TestPlayers.server());
            var crystal = new SceneGadget();
            crystal.chest_drop_id = 21920003; // scene3_group133004068, config 68001
            crystal.drop_count = 1;
            var reward = drops.rollNativeInteractDrop(crystal);
            assertFalse(reward.fallToGround());
            assertEquals(1, reward.items().size());
            assertEquals(141004, reward.items().getFirst().getItemId());
            assertEquals(1, reward.items().getFirst().getCount());

            var search = new SceneGadget();
            search.drop_tag = "搜刮点解谜通用蒙德"; // scene3_group133004007, config 38
            search.level = 1;
            var gathered = drops.rollNativeInteractDrop(search);
            assertTrue(gathered.fallToGround());
            assertFalse(gathered.items().isEmpty());
            assertTrue(gathered.items().stream().allMatch(i -> i.getCount() > 0));
            assertThrows(IllegalStateException.class,
                    () -> drops.rollNativeInteractDrop(new SceneGadget()));
        } finally {
            tables.clear();
            tables.putAll(oldTables);
            items.clear();
            items.putAll(oldItems);
        }
    }
}
