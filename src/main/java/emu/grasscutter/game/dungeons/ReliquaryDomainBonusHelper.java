/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.data.excels.dungeon.DungeonData
 *  emu.grasscutter.game.dungeons.DungeonManager
 *  emu.grasscutter.game.dungeons.enums.DungeonSubType
 *  emu.grasscutter.game.inventory.GameItem
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.MaterialDomainTripleHelper;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.inventory.GameItem;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ReliquaryDomainBonusHelper {
    private static final int SANCTIFYING_ESSENCE = 105003;
    private static final int SANCTIFYING_UNCTION = 105002;
    private static final int SANCTIFYING_ELIXIR = 105005;

    private ReliquaryDomainBonusHelper() {
    }

    public static void appendToRewards(DungeonManager dungeonManager, List<GameItem> list) {
        if (dungeonManager == null || list == null) {
            return;
        }
        MaterialDomainTripleHelper.multiplyRewards(dungeonManager, list);
        DungeonData dungeonData = dungeonManager.getDungeonData();
        if (dungeonData == null || dungeonData.getSubType() != DungeonSubType.DUNGEON_SUB_RELIQUARY) {
            return;
        }
        list.removeIf(gameItem -> gameItem != null && gameItem.getItemId() == 105005);
        boolean bl = false;
        for (GameItem gameItem2 : list) {
            int n;
            if (gameItem2 == null || (n = gameItem2.getItemId()) != 105003 && n != 105002) continue;
            bl = true;
        }
        if (!bl) {
            list.add(new GameItem(105003, ReliquaryDomainBonusHelper.rollInclusive(6, 12)));
            list.add(new GameItem(105002, ReliquaryDomainBonusHelper.rollInclusive(7, 10)));
        }
    }

    private static int rollInclusive(int n, int n2) {
        return n + ThreadLocalRandom.current().nextInt(n2 - n + 1);
    }
}

