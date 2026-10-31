package emu.grasscutter.game.quest;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.ChapterData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.LogicType;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import java.util.Arrays;

/** Chapter 1104 entrances are temporary plot points, not exploration rewards. */
public final class LiyueActFourEntrySupport {
    private LiyueActFourEntrySupport() {}

    private record Entry(int mainId, int questId, int prerequisite, int end, int point, int dungeon) {}

    private static final Entry[] ENTRIES = {
        new Entry(8000, 800002, 800001, 800007, 233, 1114),
        new Entry(8003, 800309, 800307, 800311, 240, 1115)
    };

    private static Entry entryFor(GameQuest quest) {
        if (quest == null) return null;
        for (var entry : ENTRIES) {
            if (quest.getMainQuestId() == entry.mainId() && quest.getSubQuestId() == entry.questId())
                return entry;
        }
        return null;
    }

    public static boolean isEntryAction(GameQuest quest, int sceneId, int pointId) {
        var entry = entryFor(quest);
        return entry != null && sceneId == 3 && pointId == entry.point();
    }

    static boolean matchesEntryData(GameQuest quest, QuestData data, PointData point) {
        var entry = entryFor(quest);
        if (entry == null || data == null || data.getMainId() != entry.mainId()
                || data.getId() != entry.questId() || data.getFinishCond() == null
                || data.getFinishCond().size() != 1 || point == null
                || !"DungeonEntry".equals(point.getType()) || !point.isForbidSimpleUnlock()
                || point.getDungeonIds() == null
                || Arrays.stream(point.getDungeonIds()).noneMatch(id -> id == entry.dungeon())) return false;
        var condition = data.getFinishCond().get(0);
        return condition.getType() == QuestContent.QUEST_CONTENT_ENTER_DUNGEON
                && condition.getParam() != null && condition.getParam().length >= 2
                && condition.getParam()[0] == entry.dungeon()
                && condition.getParam()[1] == entry.point() && "3".equals(condition.getParamStr());
    }

    public static boolean enable(GameQuest quest) {
        var entry = entryFor(quest);
        if (entry == null || !QuestManager.isQuestingActive()) return false;
        var main = quest.getMainQuest();
        if (main == null || main.getParentQuestId() != entry.mainId() || main.isFinished()
                || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE
                || quest.getState() != QuestState.QUEST_STATE_UNFINISHED) return false;
        var ready = main.getChildQuestById(entry.prerequisite());
        var end = main.getChildQuestById(entry.end());
        if (ready == null || ready.getState() != QuestState.QUEST_STATE_FINISHED
                || end == null || end.getState() == QuestState.QUEST_STATE_FINISHED) return false;
        var scenePoint = GameData.getScenePointEntryById(3, entry.point());
        if (!matchesEntryData(quest, quest.getQuestData(),
                scenePoint != null ? scenePoint.getPointData() : null)) return false;
        var player = main.getOwner();
        if (player == null) return false;
        boolean changed = player.getUnlockedScenePoints(3).add(entry.point());
        changed |= player.getForceLockedScenePoints(3).remove(entry.point());
        player.sendPacket(new PacketScenePointUnlockNotify(3, entry.point()));
        if (changed) player.save();
        return true;
    }

    /** Recheck only the next chapter's entry after Act IV has actually finished. */
    static void resumeNextChapter(QuestManager manager) {
        if (!QuestManager.isQuestingActive()) return;
        var end = manager.getQuestById(800311);
        var previous = manager.getMainQuestById(8003);
        var chapter = ChapterData.getBeginQuestChapterMap().get(200003);
        if (end == null || end.getState() != QuestState.QUEST_STATE_FINISHED
                || previous == null || !previous.isFinished()
                || chapter == null || chapter.getId() != 1201
                || manager.getPlayer().getLevel() < chapter.getNeedPlayerLevel()) return;

        var data = GameData.getQuestDataMap().get(200001);
        var next = manager.getMainQuestById(2000);
        if (data == null || data.getMainId() != 2000 || manager.wasSubQuestStarted(data)
                || (next != null && next.isFinished())) return;
        var conditions = data.getAcceptCond();
        if (conditions == null || conditions.isEmpty()) return;
        int[] progress = new int[conditions.size()];
        for (int i = 0; i < conditions.size(); i++) {
            var condition = conditions.get(i);
            var params = condition.getParam();
            if (condition.getType() == null || params == null || params.length == 0) return;
            progress[i] = manager.getPlayer().getServer().getQuestSystem()
                    .triggerCondition(manager.getPlayer(), data, condition,
                            condition.getParamStr(), params) ? 1 : 0;
        }
        // An already-ended, unconfigured activity emits no event to fill the cached AND gate.
        if (LogicType.calculate(data.getAcceptCondComb(), progress)) {
            manager.getAcceptProgressLists().put(data.getId(), progress);
            manager.addQuest(data);
        }
    }

    static void onConditionEvent(QuestManager manager, QuestCond type, int[] params) {
        if (params == null || params.length == 0) return;
        if ((type == QuestCond.QUEST_COND_QUEST_GLOBAL_VAR_EQUAL && params[0] == 10010)
                || (type == QuestCond.QUEST_COND_STATE_EQUAL && params[0] == 800311)
                || (type == QuestCond.QUEST_COND_ACTIVITY_END && params[0] == 2005001)
                || type == QuestCond.QUEST_COND_PLAYER_LEVEL_EQUAL_GREATER) {
            resumeNextChapter(manager);
        }
    }

    static boolean resumeOnLogin(GameMainQuest main) {
        for (var entry : ENTRIES) {
            if (main.getParentQuestId() != entry.mainId()) continue;
            var quest = main.getChildQuestById(entry.questId());
            return quest != null && enable(quest);
        }
        return false;
    }
}
