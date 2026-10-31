package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ExecSetWeatherGadgetTest {
    @Test
    void activatingAWeatherAreaSelectsItsActualAreaId() {
        assertEquals(3, ExecSetWeatherGadget.nextArea(0, 3, true));
        assertEquals(2150, ExecSetWeatherGadget.nextArea(1, 2150, true));
        assertEquals(1, ExecSetWeatherGadget.nextArea(1, 1, true));
    }

    @Test
    void deactivatingTheActiveAreaClearsWeatherOverride() {
        // Quest 35901: activate storm 3 then finish by deactivating 3 and 1.
        assertEquals(0, ExecSetWeatherGadget.nextArea(3, 3, false));
        assertEquals(0, ExecSetWeatherGadget.nextArea(0, 1, false));
    }

    @Test
    void cleanupOfAnotherAreaDoesNotCancelAStillActiveWeatherArea() {
        assertEquals(3, ExecSetWeatherGadget.nextArea(3, 1, false));
        assertEquals(1, ExecSetWeatherGadget.nextArea(1, 3, false));
    }

    @Test
    void lateCleanupCanClearStormAfterPlayerLeavesMondstadt() {
        assertTrue(ExecSetWeatherGadget.canApply(false, 3, 1004));
        assertEquals(0, ExecSetWeatherGadget.nextArea(3, 3, false));
        assertTrue(ExecSetWeatherGadget.canApply(false, 3, 3));
        assertFalse(ExecSetWeatherGadget.canApply(true, 3, 1004));
        assertTrue(ExecSetWeatherGadget.canApply(true, 3, 3));
    }
}
