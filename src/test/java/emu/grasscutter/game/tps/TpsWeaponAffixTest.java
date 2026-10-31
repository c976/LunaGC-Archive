package emu.grasscutter.game.tps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import org.junit.jupiter.api.*;

/** A TPS weapon's affixes: its own, plus one per unlocked accessory of that weapon. */
public final class TpsWeaponAffixTest {
    @BeforeEach
    public void load() {
        // Rows of TpsWeaponExcelConfigData / TpsWeaponAccessoryExcelConfigData, obfuscated keys kept.
        GameData.getTpsWeaponDataMap()
                .put(224001, JsonUtils.decode("{\"id\": 224001, \"gadgetId\": 50016001, \"ammoSlotIds\": [101, 102], \"tpsWeaponBaseAffix\": [224001], \"PIHHHIMPCAM\": 2240010, \"NNJGEAPMJFL\": 1}", TpsWeaponData.class));
        GameData.getTpsWeaponAccessoryDataMap()
                .put(224506, JsonUtils.decode("{\"id\": 224506, \"tpsWeaponId\": 224001, \"tpsWeaponBaseAffix\": [224012], \"PIHHHIMPCAM\": 2240121, \"KOHCBGDKMPG\": 223112}", TpsWeaponAccessoryData.class));
        GameData.getTpsWeaponAccessoryDataMap()
                .put(224521, JsonUtils.decode("{\"id\": 224521, \"tpsWeaponId\": 224002, \"tpsWeaponBaseAffix\": [224021], \"PIHHHIMPCAM\": 2240211}", TpsWeaponAccessoryData.class));
    }

    @AfterEach
    public void unload() {
        GameData.getTpsWeaponDataMap().remove(224001);
        GameData.getTpsWeaponAccessoryDataMap().remove(224506);
        GameData.getTpsWeaponAccessoryDataMap().remove(224521);
    }

    @Test
    public void excelKeysAreRead() {
        var weapon = GameData.getTpsWeaponDataMap().get(224001);
        assertEquals(2240010, weapon.getEquipAffixId());
        assertEquals(1, weapon.getWearSlotType());
        assertEquals(223112, GameData.getTpsWeaponAccessoryDataMap().get(224506).getUnlockMaterialId());
    }

    @Test
    public void accessoriesAddTheirAffixAtTheirLevel() {
        var item = new GameItem();
        item.setItemId(224001);
        // 224521 belongs to another gun and must not count.
        item.setTpsAccessoryIds(new ArrayList<>(List.of(224506, 224521)));

        var affixes = TpsWeaponSystem.getAffixLevels(item);

        assertEquals(Map.of(224001, 0, 224012, 1), new HashMap<>(affixes));
        assertEquals(
                List.of(224506, 224521), TpsWeaponSystem.toTpsWeaponProto(item).getAccessoryIdListList());
    }
}
