package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.utils.JsonUtils;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class WeeklyBossDropResolverTest {
    @Test
    void requiresUniqueBossMaterialFamilyAndKnownNativeLeaves() {
        var items = GameData.getItemDataMap();
        var oldAdventureExp = items.put(102, JsonUtils.decode(
                "{\"id\":102,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
        var oldBossMaterial = items.put(113013, JsonUtils.decode(
                "{\"id\":113013,\"itemType\":\"ITEM_MATERIAL\"}", ItemData.class));
        try {
            var roots = Map.of("weekly_a", 100, "weekly_b", 200);
            var leaves = Map.of(
                    100, Set.of(113013),
                    200, Set.of(113017));
            assertEquals(100, WeeklyBossDropResolver.matchRoot(
                    roots, leaves, Set.of(102, 113013)));
            assertEquals(0, WeeklyBossDropResolver.matchRoot(
                    roots, leaves, Set.of(102)));
            assertEquals(0, WeeklyBossDropResolver.matchRoot(
                    Map.of("a", 100, "b", 300),
                    Map.of(100, Set.of(113013), 300, Set.of(113013)),
                    Set.of(113013)));
            assertEquals(0, WeeklyBossDropResolver.matchRoot(
                    roots, leaves, Set.of(113014)));
        } finally {
            if (oldBossMaterial == null) items.remove(113013);
            else items.put(113013, oldBossMaterial);
            if (oldAdventureExp == null) items.remove(102);
            else items.put(102, oldAdventureExp);
        }
    }
}
