package emu.grasscutter.game.inventory;

import emu.grasscutter.data.common.ItemParamData;
import java.util.LinkedHashMap;
import java.util.Map;

/** Combines an entire payment before mutation; null denotes an invalid or overflowing quote. */
final class InventoryPaymentQuote {
    private InventoryPaymentQuote() {}

    static Map<Integer, Integer> calculate(Iterable<ItemParamData> costs, int quantity) {
        if (costs == null || quantity <= 0) {
            return null;
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (ItemParamData cost : costs) {
            if (cost == null || cost.getCount() < 0) {
                return null;
            }
            if (cost.getCount() == 0) {
                continue;
            }
            if (cost.getId() <= 0) {
                return null;
            }
            long multiplied = (long) cost.getCount() * quantity;
            long combined = result.getOrDefault(cost.getId(), 0) + multiplied;
            if (combined > Integer.MAX_VALUE) {
                return null;
            }
            result.put(cost.getId(), (int) combined);
        }
        return result;
    }
}
