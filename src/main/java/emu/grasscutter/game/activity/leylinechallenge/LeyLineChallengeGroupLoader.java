package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.world.Scene;

/**
 * LeyLineChallenge entrance loader.
 *
 * <p>Placeholder. {@link Scene} already calls {@link #ensureNearby} alongside the other
 * spawn helpers, and {@code SelectWorktopOptionReq} already gates its private-server entry path on
 * {@link #isEntranceGadget} plus {@link #ENTER_OPTION_ID}, but the group / gadget / option ids that
 * identify the entrance were never committed with those call sites. {@code OfferingSpawnHelper} and
 * {@code SnezhnayaExploreSpawnHelper} in {@code game.world} show the shape a real loader takes.
 *
 * <p>Neutral on purpose: nothing spawns and no gadget is recognised as the entrance, which is the
 * behaviour before these hooks existed.
 */
public final class LeyLineChallengeGroupLoader {
    /**
     * Worktop option that opens the challenge from the entrance gadget. Unused while
     * {@link #isEntranceGadget} answers {@code false}; {@code 0} is not a valid option id, so it can
     * never match a real request by accident.
     */
    public static final int ENTER_OPTION_ID = 0;

    private LeyLineChallengeGroupLoader() {}

    /** Spawn the entrance group when the player walks into range. Neutral until ids are known. */
    public static void ensureNearby(Scene scene) {
        // no-op
    }

    /**
     * True for the entrance gadget the worktop option belongs to.
     *
     * @return always {@code false} - no gadget is recognised yet.
     */
    public static boolean isEntranceGadget(EntityGadget gadget) {
        return false;
    }
}
