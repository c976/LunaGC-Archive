package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class SceneScriptGadgetSpawnTest {
    @Test
    void explicitQuestLuaDeathPreventsRespawnOfNormalGadgets() {
        // Q39403 removes normal (not oneoff/persistent) seal gadgets.
        assertFalse(SceneScriptManager.shouldSpawnGadget(false, true, false, false, false));
        assertFalse(SceneScriptManager.shouldSpawnGadget(false, true, true, true, false));
    }

    @Test
    void ordinaryDeathsRetainExistingSpawnAndOneoffRules() {
        assertTrue(SceneScriptManager.shouldSpawnGadget(false, false, false, false, true));
        assertFalse(SceneScriptManager.shouldSpawnGadget(false, false, true, true, true));
        assertTrue(SceneScriptManager.shouldSpawnGadget(false, false, true, true, false));
        assertFalse(SceneScriptManager.shouldSpawnGadget(true, false, false, false, false));
    }
}
