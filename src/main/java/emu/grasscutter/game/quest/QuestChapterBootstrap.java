package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import java.util.List;

/** Restores chapter-controller quests linked to active main-quest series by resource data. */
public final class QuestChapterBootstrap {
    private QuestChapterBootstrap() {}

    public static void startForActiveMainQuests(QuestManager questManager) {
        if (questManager == null) return;

        // Starting a controller mutates mainQuests, so snapshot the source quests first.
        List<Integer> sourceMainQuestIds =
                questManager.getMainQuests().values().stream()
                        .filter(mainQuest -> !mainQuest.isFinished())
                        .map(GameMainQuest::getParentQuestId)
                        .toList();

        for (int sourceMainQuestId : sourceMainQuestIds) {
            int controllerMainQuestId = controllerMainQuestFor(sourceMainQuestId);
            if (controllerMainQuestId <= 0 || controllerMainQuestId == sourceMainQuestId) continue;

            if (QuestManager.opensUnlinked(controllerMainQuestId)) {
                var sourceData = GameData.getMainQuestDataMap().get(sourceMainQuestId);
                var chapter =
                        sourceData != null
                                ? GameData.getChapterDataMap().get(sourceData.getSeries())
                                : null;
                Grasscutter.getLogger()
                        .debug(
                                "Chapter bootstrap: uid={} sourceMain={} series={} chapter={} beginSub={} controllerMain={}",
                                questManager.getPlayer().getUid(),
                                sourceMainQuestId,
                                sourceData != null ? sourceData.getSeries() : 0,
                                chapter != null ? chapter.getId() : 0,
                                chapter != null ? chapter.getBeginQuestId() : 0,
                                controllerMainQuestId);
                questManager.startMainQuestIfUnlinked(controllerMainQuestId);
            }
        }
    }

    static int controllerMainQuestFor(int sourceMainQuestId) {
        var sourceData = GameData.getMainQuestDataMap().get(sourceMainQuestId);
        if (sourceData == null || sourceData.getSeries() <= 0) return 0;

        var chapter = GameData.getChapterDataMap().get(sourceData.getSeries());
        if (chapter == null || chapter.getBeginQuestId() <= 0) return 0;

        var beginQuestData = GameData.getQuestDataMap().get(chapter.getBeginQuestId());
        return beginQuestData != null ? beginQuestData.getMainId() : 0;
    }
}
