package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DomainDropRollerTest {
    private static DungeonDropEntry entry(List<Integer> counts, List<Integer> items) {
        var result = new DungeonDropEntry();
        result.setCounts(counts);
        result.setItems(items);
        return result;
    }

    @Test
    void optionalArtifactRollOfZeroDoesNotEraseGuaranteedRewards() {
        var optional = entry(List.of(0), List.of(20513, 20514));
        var guaranteed = entry(List.of(150), List.of(202));
        var rewards = DomainDropRoller.roll(
                4482, DungeonSubType.DUNGEON_SUB_RELIQUARY,
                List.of(optional, guaranteed), 1, false);
        assertEquals(1, rewards.size());
        assertEquals(202, rewards.get(0).getId());
        assertEquals(150, rewards.get(0).getCount());
    }

    @Test
    void zeroOnlyProxyProducesNoPayableItems() {
        var rewards = DomainDropRoller.roll(
                4482, DungeonSubType.DUNGEON_SUB_RELIQUARY,
                List.of(entry(List.of(0), List.of(20513))), 1, false);
        assertTrue(rewards.isEmpty());
    }

    @Test
    void optionalOneOrZeroNeverEmitsZeroCountItems() {
        var optional = entry(List.of(0, 1), List.of(20513, 20514));
        for (int i = 0; i < 100; i++) {
            var rewards = DomainDropRoller.roll(
                    4482, DungeonSubType.DUNGEON_SUB_RELIQUARY,
                    List.of(optional), 1, false);
            assertTrue(rewards.size() <= 1);
            assertTrue(rewards.stream().allMatch(item -> item.getCount() == 1));
        }
    }

    @Test
    void appliesRollAndMultiplayerMultipliers() {
        var friendship = entry(List.of(12), List.of(105));
        friendship.setMpDouble(true);
        var rewards = DomainDropRoller.roll(
                4210, DungeonSubType.DUNGEON_SUB_TALENT,
                List.of(friendship), 2, true);
        assertEquals(1, rewards.size());
        assertEquals(105, rewards.get(0).getId());
        assertEquals(48, rewards.get(0).getCount());
    }

    @Test
    void rejectsMalformedPoolBeforeChoosingReward() {
        assertThrows(IllegalArgumentException.class, () ->
                DomainDropRoller.roll(
                        5000, DungeonSubType.DUNGEON_SUB_RELIQUARY,
                        List.of(entry(List.of(1), List.of())), 1, false));
        assertThrows(IllegalArgumentException.class, () ->
                DomainDropRoller.roll(
                        5000, DungeonSubType.DUNGEON_SUB_RELIQUARY,
                        List.of(entry(List.of(1), List.of(20513))), 4, false));
    }
}
