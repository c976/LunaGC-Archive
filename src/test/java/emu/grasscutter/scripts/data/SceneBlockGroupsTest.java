package emu.grasscutter.scripts.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins that a block always has a group map.
 *
 * <p>Scene.checkGroups dereferences this on every tick. When a block's script was missing or failed
 * to parse the field stayed null while the block was already marked loaded, so it was never
 * retried: the scene's tick then threw every frame and the whole world stopped moving. A partial
 * resource pack is an ordinary thing to run, so a missing script has to cost that block and
 * nothing else.
 */
public final class SceneBlockGroupsTest {
    @Test
    @DisplayName("a block starts with a group map rather than null")
    public void groupsAreNeverNull() {
        var block = new SceneBlock();

        assertNotNull(block.groups, "the tick dereferences this every frame");
        assertTrue(block.groups.isEmpty());
    }

    @Test
    @DisplayName("the map can be written to, which the boss and investigation spawners rely on")
    public void groupsAreMutable() {
        // WorldBossSpawnHelper and InvestigationSpawnHelper both put() into this map, so an
        // immutable empty default would trade the null for an UnsupportedOperationException.
        var block = new SceneBlock();

        assertDoesNotThrow(() -> block.groups.put(1, new SceneGroup()));
        assertTrue(block.groups.containsKey(1));
    }
}
