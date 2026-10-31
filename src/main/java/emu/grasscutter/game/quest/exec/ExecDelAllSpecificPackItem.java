package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/** The reviewed MQ1013 fallback branches exhaust their special purse before item errands. */
@QuestValueExec(QuestExec.QUEST_EXEC_DEL_ALL_SPECIFIC_PACK_ITEM)
public final class ExecDelAllSpecificPackItem extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        int id = quest.getSubQuestId();
        if (quest.getMainQuestId() != 1013 || (id != 101304 && id != 101308 && id != 101312)
                || quest.getState() != QuestState.QUEST_STATE_FAILED
                || params.length != 1 || !"100496".equals(params[0])) return false;
        var inventory = quest.getOwner().getInventory();
        int count = inventory.getItemCountById(100496);
        return count <= 0 || inventory.removeItemById(100496, count);
    }
}
