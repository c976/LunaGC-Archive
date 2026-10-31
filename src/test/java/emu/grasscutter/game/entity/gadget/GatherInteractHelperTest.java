package emu.grasscutter.game.entity.gadget;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public final class GatherInteractHelperTest {
    @Test
    @DisplayName("subfield drops without gather metadata do not synthesize loot")
    public void emptySubfieldHasNoSyntheticLoot() {
        assertFalse(GatherInteractHelper.hasSubfieldGatherLoot(0, 0));
    }

    @Test
    @DisplayName("subfield drops with GatherData item ids synthesize loot")
    public void gatherDataItemEnablesSyntheticLoot() {
        assertTrue(GatherInteractHelper.hasSubfieldGatherLoot(100033, 0));
    }

    @Test
    @DisplayName("subfield drops with spawn gather item ids synthesize loot")
    public void spawnItemEnablesSyntheticLoot() {
        assertTrue(GatherInteractHelper.hasSubfieldGatherLoot(0, 100054));
    }

    @Test
    @DisplayName("non-positive item ids do not count as gather loot")
    public void nonPositiveIdsAreRejected() {
        assertFalse(GatherInteractHelper.hasSubfieldGatherLoot(-1, 0));
        assertFalse(GatherInteractHelper.hasSubfieldGatherLoot(0, -1));
    }
}