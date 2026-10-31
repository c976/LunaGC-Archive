package emu.grasscutter.game.player;

import emu.grasscutter.game.world.Position;

/**
 * The Natlan overworld is part of scene 3, not a separate scene. Use its 7.1
 * world-space footprint when the player enters or moves in the shared Teyvat scene.
 * These conservative bounds follow the Natlan exploration zone used by
 * NodKraiExploreSpawnHelper; refine them when precise region geometry is available.
 */
public final class PhlogistonRegion {
    private PhlogistonRegion() {}

    public static boolean contains(int sceneId, Position position) {
        // Retain the legacy standalone Natlan scene path for compatibility.
        if (sceneId == 101) return true;
        if (sceneId != 3 || position == null) return false;
        return position.getX() >= -4000f && position.getX() <= 2000f
                && position.getZ() >= 6000f && position.getZ() <= 11000f;
    }
}
