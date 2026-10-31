package emu.grasscutter.game.quest;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.game.quest.enums.QuestState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Preserve reviewed parallel dialogue and investigation stages across login. */
final class LiyueParallelQuestResume {
    private static final List<Phase> PHASES = List.of(
            new Phase(1000, 100001, 100007,
                    List.of(100002, 100003, 100004, 100005, 100006, 100016),
                    Set.of(100002, 100003, 100004), List.of(100005, 100006), 100006),
            new Phase(1000, 100007, 100026,
                    List.of(100021, 100022, 100023, 100024, 100025),
                    Set.of(100021, 100022), List.of(100023, 100024), 100024),
            new Phase(18000, 1800028, 1800016,
                    List.of(1800012, 1800013, 1800014, 1800015),
                    Set.of(1800012, 1800014), List.of(1800015), 1800015));

    private LiyueParallelQuestResume() {}

    record Recovery(List<GameQuest> quests, Phase phase) {
        int completedTalks() {
            return (int) quests.stream().filter(q -> phase.talks().contains(q.getSubQuestId())
                    && q.getState() == QuestState.QUEST_STATE_FINISHED).count();
        }
    }

    record Phase(int mainId, int prerequisite, int next, List<Integer> quests, Set<Integer> talks,
                 List<Integer> counters, int completedCounter) {}

    static Recovery selectPhase(GameMainQuest main) {
        if ((main.getParentQuestId() != 1000 && main.getParentQuestId() != 18000) || main.isFinished()
                || (main.getState() != null
                    && main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE)) return null;
        for (var phase : PHASES) {
            if (phase.mainId() != main.getParentQuestId()) continue;
            var prerequisite = main.getChildQuestById(phase.prerequisite());
            var next = main.getChildQuestById(phase.next());
            if (prerequisite == null || prerequisite.getState() != QuestState.QUEST_STATE_FINISHED
                    || next == null || next.getState() != QuestState.QUEST_STATE_UNSTARTED) continue;

            var quests = new ArrayList<GameQuest>();
            for (int id : phase.quests()) {
                var quest = main.getChildQuestById(id);
                if (quest == null || (quest.getState() != QuestState.QUEST_STATE_UNSTARTED
                        && quest.getState() != QuestState.QUEST_STATE_UNFINISHED
                        && quest.getState() != QuestState.QUEST_STATE_FINISHED)
                        || !hasParallelPrerequisite(quest.getQuestData(), phase.mainId(), phase.prerequisite())) {
                    return null;
                }
                quests.add(quest);
            }
            // Later progress belongs to its own stage, even if a legacy save lost the entry quest.
            int lastOrder = quests.stream().mapToInt(q -> q.getQuestData().getOrder()).max().orElse(0);
            if (main.getChildQuests().values().stream().anyMatch(q -> q.getQuestData() != null
                    // MQ18000's independent hidden controller has order 31, not later story progress.
                    && !(phase.mainId() == 18000 && q.getSubQuestId() == 1800029)
                    && q.getQuestData().getOrder() > lastOrder
                    && q.getState() != QuestState.QUEST_STATE_UNSTARTED)) return null;
            return new Recovery(quests, phase);
        }
        return null;
    }

    private static boolean hasParallelPrerequisite(QuestData data, int mainId, int prerequisite) {
        if (data == null || data.getMainId() != mainId || data.getAcceptCond() == null
                || data.getAcceptCond().size() != 1) return false;
        var condition = data.getAcceptCond().get(0);
        var params = condition.getParam();
        return condition.getType() == QuestCond.QUEST_COND_STATE_EQUAL
                && params != null && params.length >= 2 && params[0] == prerequisite
                && params[1] == QuestState.QUEST_STATE_FINISHED.getValue();
    }
}
