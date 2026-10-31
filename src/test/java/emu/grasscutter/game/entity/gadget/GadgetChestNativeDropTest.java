package emu.grasscutter.game.entity.gadget;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.game.props.SceneType;
import org.junit.jupiter.api.Test;

/** 蒙德调查点 133003104/2527（SearchPoint，chest_drop_id 1010100）按 F 无反应，2026-10-25 01:09。 */
final class GadgetChestNativeDropTest {
    @Test
    void unknownChestKindUsesItsNativeDropTable() {
        assertTrue(GadgetChest.usesNativeDrop(SceneType.SCENE_WORLD, 133003104, false, null));
    }

    @Test
    void knownWorldChestKeepsConfiguredRules() {
        assertFalse(GadgetChest.usesNativeDrop(SceneType.SCENE_WORLD, 133003104, false, "normal"));
        assertTrue(GadgetChest.usesNativeDrop(SceneType.SCENE_DUNGEON, 250010001, false, "normal"));
        assertTrue(GadgetChest.usesNativeDrop(SceneType.SCENE_WORLD, 133003104, true, "normal"));
    }

    @Test
    void headerOnlyDropRowHasAnEmptyVector() {
        var row = new Gson().fromJson(
                "{\"id\":1010100,\"randomType\":1,\"fallToGround\":true,\"historyLimit\":1}",
                DropTableData.class);
        row.onLoad();
        assertNotNull(row.getDropVec());
        assertTrue(row.getDropVec().isEmpty());
    }
}
