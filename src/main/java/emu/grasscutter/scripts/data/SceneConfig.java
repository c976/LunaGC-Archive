package emu.grasscutter.scripts.data;

import emu.grasscutter.game.world.Position;
import lombok.*;

@ToString
@Setter
public class SceneConfig {
    public Position vision_anchor;
    public Position born_pos;
    public Position born_rot;
    public Position begin_pos;
    public Position size;
    public float die_y;

    /**
     * Spawn point for a player entering the scene with no saved position.
     *
     * <p>Not every {@code scene<id>_config.lua} declares {@code born_pos} - some only carry
     * {@code begin_pos}, and a few only {@code vision_anchor}. Reading {@code born_pos} directly
     * left those scenes with a null default, which drops the player at whatever position they
     * carried in from the previous scene. Fall through the other anchors instead.
     *
     * @return the best available spawn point, or {@code null} when the config declares none
     */
    public Position resolveBornPos() {
        if (born_pos != null) {
            return born_pos;
        }
        if (begin_pos != null) {
            return begin_pos;
        }
        return vision_anchor;
    }

    /**
     * Spawn rotation matching {@link #resolveBornPos()}.
     *
     * @return {@code born_rot}, or {@code null} when the config declares none - the caller keeps the
     *     player's current rotation, which is the right answer for a scene that never specified one
     */
    public Position resolveBornRot() {
        return born_rot;
    }
}
