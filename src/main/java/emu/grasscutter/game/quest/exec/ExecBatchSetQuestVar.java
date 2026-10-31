package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/** ["i1,i2,...", "v1,v2,..."]: sets several quest variables of the main quest at once. */
@QuestValueExec(QuestExec.QUEST_EXEC_BATCH_SET_QUEST_VAR)
public final class ExecBatchSetQuestVar extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr.length < 2) return false;
        String[] indexes = paramStr[0].split(",");
        String[] values = paramStr[1].split(",");
        if (indexes.length != values.length) return false;
        var mainQuest = quest.getMainQuest();
        for (int i = 0; i < indexes.length; i++) {
            mainQuest.setQuestVar(Integer.parseInt(indexes[i].trim()), Integer.parseInt(values[i].trim()));
        }
        return true;
    }
}
