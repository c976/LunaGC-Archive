package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One tick of each change-Frigid mixin shape found in ConfigAbility_Scene_V7_0_Frigid. */
class SnezhnayaFrigidStateTest {

    @Test
    void coldTicksSpendWarmthBeforeBuildingFrigid() {
        var state = new SnezhnayaFrigid.State();
        assertEquals(SnezhnayaFrigid.MAX_ENERGY, state.getEnergy());

        // SnezhnayaFrigid_FrigidDoTick_Lv1: speed 1, no priority.
        assertTrue(state.apply(1, null, null, false, 0));
        assertEquals(SnezhnayaFrigid.MAX_ENERGY - 1, state.getEnergy());
        assertEquals(0, state.getFrigid());

        state.energy = 2;
        // SnezhnayaFrigid_FrigidDoTick_Lv2: speed 3 - two from warmth, the third becomes Frigid.
        assertTrue(state.apply(3, null, "Level_1", false, 2000));
        assertEquals(0, state.getEnergy());
        assertEquals(1, state.getFrigid());
    }

    @Test
    void frigidIsCappedAtTheMeterMaximum() {
        var state = new SnezhnayaFrigid.State();
        state.energy = 0;
        // SnezhnayaFrigid_FrigidDoTick_Lv3: speed 200 at Level_5.
        state.apply(200, null, "Level_5", false, 0);
        assertEquals(SnezhnayaFrigid.MAX_FRIGID, state.getFrigid());
    }

    @Test
    void warmthAndClearsLowerFrigidWithoutTouchingWarmth() {
        var state = new SnezhnayaFrigid.State();
        state.energy = 100;
        state.frigid = 60;

        // SnezhnayaFrigid_WarmArea: speed -25 at Level_5.
        state.apply(-25, null, "Level_5", false, 0);
        assertEquals(35, state.getFrigid());
        assertEquals(100, state.getEnergy());

        // SnezhnayaFrigid_Basic_Team_Clear: speed -999, Level_5, frigidOnly.
        state.apply(-999, null, "Level_5", true, 2000);
        assertEquals(0, state.getFrigid());
        assertEquals(100, state.getEnergy());
    }

    @Test
    void addTopsUpWarmthAndSetWritesTheFlaggedValue() {
        var state = new SnezhnayaFrigid.State();
        state.energy = 30;
        // SnezhnayaFrigid_WarmSearch_CrystalBuff_Func: speed 5, costType Add.
        state.apply(5, "Add", "Level_5", false, 0);
        assertEquals(35, state.getEnergy());

        state.frigid = 40;
        // ClearSnezhnayaEnergy: costType Set, speed unset (0), frigidOnly.
        state.apply(0, "Set", "Level_3", true, 5000);
        assertEquals(0, state.getFrigid());
        assertEquals(35, state.getEnergy());
    }

    @Test
    void aHigherPriorityTickHoldsOffLowerOnesUntilItLapses() {
        var state = new SnezhnayaFrigid.State();
        state.energy = 0;
        state.frigid = 50;

        state.apply(-25, null, "Level_5", false, 1000); // warm area
        assertFalse(state.apply(1, null, null, false, 1500), "the base cold tick is overridden");
        assertEquals(25, state.getFrigid());

        // Once the warm ticks stop, the cold tick applies again.
        assertTrue(state.apply(1, null, null, false, 3000));
        assertEquals(26, state.getFrigid());
    }

    @Test
    void priorityLevelsParse() {
        assertEquals(5, SnezhnayaFrigid.priorityLevel("Level_5"));
        assertEquals(0, SnezhnayaFrigid.priorityLevel(null));
        assertEquals(0, SnezhnayaFrigid.priorityLevel("High"));
    }
}
