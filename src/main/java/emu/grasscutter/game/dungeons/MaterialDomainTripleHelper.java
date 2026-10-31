package emu.grasscutter.game.dungeons;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import java.util.List;
import java.util.Set;

/**
 * Domain reward boost: talent/weapon materials ×3. Reliquary domains use
 * DungeonDrop + claim multipliers only.
 */
public final class MaterialDomainTripleHelper {
    private static final int TALENT_MULTIPLIER = 3;
    private static final int WEAPON_MULTIPLIER = 3;
    private static final Set<Integer> SKIP_ITEM_IDS =
            Set.of(101, 102, 104, 105, 201, 202, 203, 204);

    private MaterialDomainTripleHelper() {}

    public static void multiplyRewards(DungeonManager dungeonManager, List<GameItem> list) {
        if (dungeonManager == null || list == null || list.isEmpty()) {
            return;
        }
        DungeonData dungeonData = dungeonManager.getDungeonData();
        if (dungeonData == null) {
            return;
        }
        DungeonSubType subType = dungeonData.getSubType();
        int multiplier;
        if (subType == DungeonSubType.DUNGEON_SUB_TALENT) {
            multiplier = TALENT_MULTIPLIER;
        } else if (subType == DungeonSubType.DUNGEON_SUB_WEAPON) {
            multiplier = WEAPON_MULTIPLIER;
        } else {
            return;
        }

        for (GameItem gameItem : list) {
            if (gameItem == null || !isMaterialReward(gameItem)) {
                continue;
            }
            int n = gameItem.getCount();
            if (n <= 0) {
                continue;
            }
            gameItem.setCount(n * multiplier);
        }
    }

    private static boolean isMaterialReward(GameItem gameItem) {
        int n = gameItem.getItemId();
        if (SKIP_ITEM_IDS.contains(n)) {
            return false;
        }
        ItemData itemData = gameItem.getItemData();
        if (itemData == null) {
            return n >= 1000;
        }
        return itemData.getItemType() == ItemType.ITEM_MATERIAL;
    }
}
