package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import lombok.val;

@QuestValueExec(QuestExec.QUEST_EXEC_UNREGISTER_DYNAMIC_GROUP)
public class ExecUnregisterDynamicGroup extends QuestExecHandler {

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        int groupId = parseGroupId(paramStr);
        if (groupId <= 0 || quest == null || quest.getOwner() == null) return false;
        // Native actions may omit the unused second flag, including MQ18000 cleanup.
        val scene = quest.getOwner().getScene();
        if (scene == null) return false;

        Grasscutter.getLogger().debug("Unregistering group {}", groupId);

        if (!scene.unregisterDynamicGroup(groupId)) {
            return false;
        }

        // Remove suites if they are registered
        quest
                .getMainQuest()
                .getQuestGroupSuites()
                .removeIf(gs -> gs.getGroup() == groupId && gs.getScene() == scene.getId());

        Grasscutter.getLogger().debug("Unregistered group {} in scene {}", groupId, scene.getId());

        return true;
    }

    static int parseGroupId(String... params) {
        if (params == null || params.length == 0) return 0;
        try {
            int id = Integer.parseInt(params[0]);
            return id > 0 ? id : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
