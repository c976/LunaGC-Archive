package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.managers.StatueTalkQuests;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_POINT)
public class ExecUnlockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        int sceneId = Integer.parseInt(paramStr[0]);
        int pointId = Integer.parseInt(paramStr[1]);

        if (LiyueActFourEntrySupport.isEntryAction(quest, sceneId, pointId))
            return LiyueActFourEntrySupport.enable(quest);

        if (LiyueGoldenHouseEntrySupport.isEntryAction(quest, sceneId, pointId))
            return LiyueGoldenHouseEntrySupport.enable(quest);

        if (LiyueCloudDungeonEntryRecovery.isEntryAction(quest, sceneId, pointId))
            return LiyueCloudDungeonEntryRecovery.enable(quest);

        var scenePointEntry = GameData.getScenePointEntryById(sceneId, pointId);
        boolean isStatue =
                scenePointEntry != null && isStatuePoint(scenePointEntry.getPointData());

        return quest.getOwner().getProgressManager().unlockTransPoint(sceneId, pointId, isStatue);
    }

    static boolean isStatuePoint(PointData pointData) {
        return StatueTalkQuests.isStatuePoint(pointData);
    }
}
