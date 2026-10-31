package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_PLAYER_WORLD_SCENE)
public class ExecUnlockPlayerWorldScene extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr == null || paramStr.length < 1) return false;
        try {
            int sceneId = Integer.parseInt(paramStr[0]);
            // Unlocking access to the open-world scene is not exploration of
            // Mondstadt's first Statue of the Seven region (scene 3, area 1).
            if (sceneId != 3) {
                quest.getOwner().getProgressManager().unlockSceneArea(sceneId, 1);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}