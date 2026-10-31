package emu.grasscutter.game.quest;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import java.util.Arrays;

/** MQ1023 uses a temporary plot entrance distinct from the Golden House weekly boss. */
public final class LiyueGoldenHouseEntrySupport {
    private LiyueGoldenHouseEntrySupport() {}

    public static boolean isEntryAction(GameQuest quest, int sceneId, int pointId) {
        return quest.getMainQuestId() == 1023 && quest.getSubQuestId() == 102301
                && sceneId == 3 && pointId == 183;
    }

    public static boolean enable(GameQuest quest) {
        var main = quest.getMainQuest();
        var player = main.getOwner();
        var ready = player.getQuestManager().getQuestById(102208);
        var end = main.getChildQuestById(102305);
        if (!QuestManager.isQuestingActive() || !isEntryAction(quest, 3, 183)
                || main.isFinished() || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE
                || quest.getState() != QuestState.QUEST_STATE_UNFINISHED
                || ready == null || ready.getState() != QuestState.QUEST_STATE_FINISHED
                || end == null || end.getState() == QuestState.QUEST_STATE_FINISHED) return false;

        var data = quest.getQuestData();
        var finish = data != null ? data.getFinishCond() : null;
        if (data == null || data.getMainId() != 1023 || finish == null || finish.size() != 1) return false;
        var condition = finish.get(0);
        if (condition.getType() != QuestContent.QUEST_CONTENT_ENTER_DUNGEON
                || condition.getParam() == null || condition.getParam().length < 2
                || condition.getParam()[0] != 101 || condition.getParam()[1] != 183
                || !"3".equals(condition.getParamStr())) return false;
        var entry = GameData.getScenePointEntryById(3, 183);
        var point = entry != null ? entry.getPointData() : null;
        if (point == null || !"DungeonEntry".equals(point.getType()) || !point.isForbidSimpleUnlock()
                || point.getDungeonIds() == null
                || Arrays.stream(point.getDungeonIds()).noneMatch(id -> id == 101)) return false;

        boolean changed = player.getUnlockedScenePoints(3).add(183);
        changed |= player.getForceLockedScenePoints(3).remove(183);
        player.sendPacket(new PacketScenePointUnlockNotify(3, 183));
        // A plot entrance is not an exploration point; do not grant waypoint rewards or events.
        if (changed) player.save();
        return true;
    }

    static boolean resumeOnLogin(GameMainQuest main) {
        if (main.getParentQuestId() != 1023) return false;
        var quest = main.getChildQuestById(102301);
        return quest != null && enable(quest);
    }
}
