package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.game.player.Player;

/**
 * LeyLineChallenge entry flow.
 *
 * <p>Placeholder that pairs with {@link LeyLineChallengeGroupLoader}. The only caller is the
 * worktop-option fallback in {@code SelectWorktopOptionReq}, which is itself gated on
 * {@link LeyLineChallengeGroupLoader#isEntranceGadget} - unreachable while that answers
 * {@code false}, so this never runs today.
 *
 * <p>A real implementation has to pick the dungeon for the difficulty and hand the player to
 * {@code DungeonManager}, which needs the activity's dungeon table first.
 */
public final class LeyLineChallengeEnterFlow {

    private LeyLineChallengeEnterFlow() {}

    /**
     * Enter the challenge.
     *
     * @param player the entering player
     * @param difficulty 1-based difficulty tier chosen on the activity page
     * @param fromWorktop {@code true} when this is the private-server worktop fallback rather than
     *     the official activity-page {@code EnterReq}
     */
    public static void enter(Player player, int difficulty, boolean fromWorktop) {
        // no-op
    }
}
