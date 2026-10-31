package emu.grasscutter.game.quest;

/**
 * Stores satisfied quest predicates as cumulative progress.
 *
 * <p>Each event is checked against all predicates of its type. A nonmatching
 * event must not undo a previously matched objective (for example, dungeon
 * 1 must not erase the earlier dungeon 1001 clear in quest 30901). Explicit
 * quest rewind resets these arrays at the owning quest level.
 */
final class QuestProgress {
    private QuestProgress() {}

    /** Rechecks a live prerequisite rather than latching an obsolete quest state. */
    static void recordCurrent(int[] progress, int index, boolean satisfied) {
        progress[index] = satisfied ? 1 : 0;
    }

    /** @return true only when a new objective becomes satisfied. */
    static boolean recordMatch(int[] progress, int index, boolean matched) {
        if (!matched || progress[index] == 1) return false;
        progress[index] = 1;
        return true;
    }
}
