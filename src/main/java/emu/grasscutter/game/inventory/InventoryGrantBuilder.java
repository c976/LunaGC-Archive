package emu.grasscutter.game.inventory;

import emu.grasscutter.data.excels.ItemData;
import java.util.ArrayList;
import java.util.List;

/** Materialize a grant without silently truncating equipment quantities or material stacks. */
public final class InventoryGrantBuilder {
    private static final int MAX_INDIVIDUAL_ITEMS_PER_BATCH = 10_000;

    private InventoryGrantBuilder() {}

    public static List<GameItem> create(ItemData definition, int count, int level) {
        if (definition == null || definition.getItemType() == null || count <= 0) {
            throw new IllegalArgumentException("Invalid grant item or quantity");
        }
        ItemType type = definition.getItemType();
        if (type == ItemType.ITEM_WEAPON || type == ItemType.ITEM_RELIQUARY
                || type == ItemType.ITEM_TPS_WEAPON || definition.isUseOnGain()) {
            if (count > MAX_INDIVIDUAL_ITEMS_PER_BATCH) {
                throw new IllegalArgumentException("Excessive individual-item grant count " + count);
            }
            List<GameItem> copies = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                GameItem item = new GameItem(definition);
                // Each use-on-gain card applies one effect; never pack several into one card.
                item.setLevel(Math.max(1, level));
                item.setPromoteLevel(GameItem.getMinPromoteLevel(item.getLevel()));
                copies.add(item);
            }
            return copies;
        }
        GameItem item = new GameItem(definition, count);
        item.setLevel(level);
        item.setPromoteLevel(GameItem.getMinPromoteLevel(level));
        return List.of(item);
    }
}
