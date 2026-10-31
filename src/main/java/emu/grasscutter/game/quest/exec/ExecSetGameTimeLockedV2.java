package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/** [locked]: the V2 form of QUEST_EXEC_SET_IS_GAME_TIME_LOCKED. */
@QuestValueExec(QuestExec.QUEST_EXEC_SET_IS_GAME_TIME_LOCKED_V2)
public final class ExecSetGameTimeLockedV2 extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr.length < 1) return false;
        quest.getOwner().getWorld().lockTime("1".equals(paramStr[0].trim()));
        return true;
    }
}
