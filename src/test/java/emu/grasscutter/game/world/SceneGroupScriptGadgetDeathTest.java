package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class SceneGroupScriptGadgetDeathTest {
    @Test
    void scriptedKillsStayRecordedEvenWhenTargetWasNotLoaded() {
        var group = new SceneGroupInstance();
        assertFalse(group.isScriptGadgetDestroyed(7));
        assertTrue(group.markScriptGadgetDestroyed(7));
        assertTrue(group.isScriptGadgetDestroyed(7));
        assertFalse(group.markScriptGadgetDestroyed(7));
        assertTrue(group.markScriptGadgetDestroyed(300));
        assertTrue(group.isScriptGadgetDestroyed(300));
        assertFalse(group.isScriptGadgetDestroyed(304));
    }

    @Test
    void invalidConfigIdsDoNotCreateTombstones() {
        var group = new SceneGroupInstance();
        assertFalse(group.markScriptGadgetDestroyed(0));
        assertFalse(group.markScriptGadgetDestroyed(-1));
        assertFalse(group.isScriptGadgetDestroyed(0));
    }

    @Test
    void olderSavedGroupsCanInitializeMissingMarkerSet() throws Exception {
        var group = new SceneGroupInstance();
        var field = SceneGroupInstance.class.getDeclaredField("scriptDestroyedGadgets");
        field.setAccessible(true);
        field.set(group, null);
        assertFalse(group.isScriptGadgetDestroyed(1));
        assertTrue(group.markScriptGadgetDestroyed(1));
        assertTrue(group.isScriptGadgetDestroyed(1));
    }
}
