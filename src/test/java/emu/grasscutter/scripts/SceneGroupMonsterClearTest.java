package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class SceneGroupMonsterClearTest {
    @Test
    void deadLastMonsterDoesNotBlockGroupClearWhileStillInSceneMap() {
        assertFalse(SceneScriptManager.isLivingGroupMonster(true, 133002233, 133002233));
        assertFalse(SceneScriptManager.isLivingGroupMonster(true, 133004015, 133004015));
    }

    @Test
    void aliveMonsterFromTheGroupStillBlocksCompletion() {
        assertTrue(SceneScriptManager.isLivingGroupMonster(false, 133002233, 133002233));
        assertFalse(SceneScriptManager.isLivingGroupMonster(false, 133004015, 133002233));
    }
}
