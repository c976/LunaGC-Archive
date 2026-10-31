package emu.grasscutter.game.quest;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import java.util.Arrays;

/** MQ1009's temporary plot-dungeon entrance is not an exploration reward point. */
public final class LiyueCloudDungeonEntryRecovery {
    private LiyueCloudDungeonEntryRecovery() {}

    public static boolean isEntryAction(GameQuest quest, int sceneId, int pointId) {
        return quest.getMainQuestId() == 1009 && quest.getSubQuestId() == 100912
                && sceneId == 3 && pointId == 187;
    }

    public static boolean enable(GameQuest quest) {
        var main = quest.getMainQuest();
        var ready = main.getChildQuestById(100911);
        var end = main.getChildQuestById(100914);
        if (!QuestManager.isQuestingActive() || !isEntryAction(quest, 3, 187)
                || main.getParentQuestId() != 1009 || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE
                || quest.getState() != QuestState.QUEST_STATE_UNFINISHED
                || ready == null || ready.getState() != QuestState.QUEST_STATE_FINISHED
                || end == null || end.getState() == QuestState.QUEST_STATE_FINISHED)
            return false;

        var data = quest.getQuestData();
        var finish = data != null ? data.getFinishCond() : null;
        if (data == null || data.getMainId() != 1009 || finish == null || finish.size() != 1)
            return false;
        var condition = finish.get(0);
        if (condition.getType() != QuestContent.QUEST_CONTENT_ENTER_DUNGEON
                || condition.getParam() == null || condition.getParam().length < 2
                || condition.getParam()[0] != 1103 || condition.getParam()[1] != 187
                || !"3".equals(condition.getParamStr())) return false;
        var entry = GameData.getScenePointEntryById(3, 187);
        var point = entry != null ? entry.getPointData() : null;
        if (point == null || !"DungeonEntry".equals(point.getType())
                || !point.isForbidSimpleUnlock() || point.getDungeonIds() == null
                || Arrays.stream(point.getDungeonIds()).noneMatch(id -> id == 1103)) return false;

        var player = main.getOwner();
        boolean changed = player.getUnlockedScenePoints(3).add(187);
        changed |= player.getForceLockedScenePoints(3).remove(187);
        // Only update the plot entrance and client state. No inventory, area or exploration events.
        player.sendPacket(new PacketScenePointUnlockNotify(3, 187));
        if (changed) player.save();
        return true;
    }

    static boolean resumeOnLogin(GameMainQuest main) {
        if (main.getParentQuestId() != 1009) return false;
        var quest = main.getChildQuestById(100912);
        // Preserve the finished offerings while restoring a pending dungeon entry, including old saves.
        return quest != null && enable(quest);
    }
}
