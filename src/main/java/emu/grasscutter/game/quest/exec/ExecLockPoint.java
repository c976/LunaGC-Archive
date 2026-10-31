package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/** Re-locks scene points named by quest execution data, including the 35106 3/1720 lock. */
@QuestValueExec(QuestExec.QUEST_EXEC_LOCK_POINT)
public final class ExecLockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (params == null || params.length < 2) return false;
        int sceneId;
        int pointId;
        try {
            sceneId = Integer.parseInt(params[0]);
            pointId = Integer.parseInt(params[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (!validPoint(sceneId, pointId)) return false;
        return quest.getOwner().getProgressManager().lockTransPoint(sceneId, pointId);
    }

    static boolean validPoint(int sceneId, int pointId) {
        return sceneId > 0 && pointId > 0;
    }
}
