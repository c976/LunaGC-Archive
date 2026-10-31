package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.world.Position;
import java.util.List;

/** Recover a fall from MQ1021's original Jade Chamber while its exterior Ningguang talk is pending. */
final class LiyueJadeChamberRecovery {
    private LiyueJadeChamberRecovery() {}

    static boolean isBelowPlatform(Position position) {
        if (position == null || !Float.isFinite(position.getY()) || position.getY() >= 440) return false;
        double dx = position.getX() + 815.692, dz = position.getZ() - 434.674;
        return Double.isFinite(dx) && Double.isFinite(dz) && dx * dx + dz * dz <= 120 * 120;
    }

    static List<Position> resumeOnLogin(GameMainQuest main) {
        if (main.getParentQuestId() != 1021 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE
                || main.getOwner().getSceneId() != 3 || !isBelowPlatform(main.getOwner().getPosition()))
            return null;
        var arrived = main.getChildQuestById(102104);
        var talk = main.getChildQuestById(102113);
        if (arrived == null || arrived.getState() != QuestState.QUEST_STATE_FINISHED
                || talk == null || talk.getState() != QuestState.QUEST_STATE_UNFINISHED
                || talk.getQuestData() == null || talk.getQuestData().getMainId() != 1021
                || talk.getQuestData().getGuide() == null
                || talk.getQuestData().getGuide().getGuideScene() != 3) return null;
        for (int id : new int[] {102105, 102106, 102112}) {
            var later = main.getChildQuestById(id);
            if (later != null && later.getState() != QuestState.QUEST_STATE_UNSTARTED) return null;
        }
        // Restart only the still-pending native talk so its client quest actors are recreated.
        // The completed ascent and material branches retain their normal saved state.
        talk.rewind(false);
        Grasscutter.getLogger().info("Recovered Jade Chamber exterior talk after fall: uid={} quest=102113",
                main.getOwner().getUid());
        // Q102104Born from the native scene-3 dummy points, beside Q102104Ningguang.
        return List.of(new Position(-815.692f, 464.284f, 434.674f), new Position(0, 0, 0));
    }
}
