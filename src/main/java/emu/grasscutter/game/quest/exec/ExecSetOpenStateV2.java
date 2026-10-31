package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/** [openStateId, value]: the V2 form of QUEST_EXEC_SET_OPEN_STATE. */
@QuestValueExec(QuestExec.QUEST_EXEC_SET_OPEN_STATE_V2)
public final class ExecSetOpenStateV2 extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr.length < 2) return false;
        quest.getOwner()
                .getProgressManager()
                .forceSetOpenState(Integer.parseInt(paramStr[0].trim()), Integer.parseInt(paramStr[1].trim()));
        return true;
    }
}
