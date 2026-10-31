package emu.grasscutter.game.dungeons;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Rolls a validated DungeonDrop.json proxy without constructing zero-count rewards. */
final class DomainDropRoller {
    private DomainDropRoller() {}

    static List<ItemParamData> roll(
            int dungeonId,
            DungeonSubType subType,
            List<DungeonDropEntry> entries,
            int times,
            boolean multiplayer) {
        if (times < 1 || times > 3) {
            throw new IllegalArgumentException("Unsupported domain roll count: " + times);
        }
        DomainDropSafety.validatePool(dungeonId, entries, subType);
        var rewards = new ArrayList<ItemParamData>();
        for (var entry : entries) {
            int start = entry.getCounts().get(0);
            int end = entry.getCounts().get(entry.getCounts().size() - 1);
            var amounts = IntStream.rangeClosed(start, end).boxed().toList();
            long amount = 0;
            for (int i = 0; i < times; i++) {
                amount += Utils.drawRandomListElement(amounts, entry.getProbabilities());
            }
            if (entry.isMpDouble() && multiplayer) {
                amount *= 2;
            }
            if (amount == 0) {
                continue;
            }
            if (amount < 0 || amount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid rolled quantity in dungeon " + dungeonId);
            }
            if (entry.getItems().size() == 1) {
                rewards.add(new ItemParamData(entry.getItems().get(0), (int) amount));
            } else {
                for (int i = 0; i < (int) amount; i++) {
                    int itemId = Utils.drawRandomListElement(
                            entry.getItems(), entry.getItemProbabilities());
                    rewards.add(new ItemParamData(itemId, 1));
                }
            }
        }
        return rewards;
    }
}
