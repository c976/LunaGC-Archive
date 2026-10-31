package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.game.quest.enums.LogicType;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestBinCombinatorFallbackTest {
    private static final Gson GSON = new Gson();

    private static QuestData quest(String json) {
        return GSON.fromJson(json, QuestData.class);
    }

    private static final String DUNGEONS = """
            {"subId":30901,"mainId":309,
             "finishCond":[
               {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1001,0]},
               {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1,0]},
               {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1003,0]}],
             "failCond":[]}
            """;

    @Test
    void threeDistinctDungeonClearsRequireAndNotTheFirstCompletedEvent() {
        var excel = quest(DUNGEONS);
        var bin = GSON.fromJson(
                "{\"subId\":30901,\"finishCondComb\":\"LOGIC_AND\"}",
                MainQuestData.SubQuestData.class);
        excel.applyFrom(bin);

        assertEquals(LogicType.LOGIC_AND, excel.getFinishCondComb());
        assertFalse(LogicType.calculate(excel.getFinishCondComb(), new int[]{1, 0, 0}));
        assertFalse(LogicType.calculate(excel.getFinishCondComb(), new int[]{1, 1, 0}));
        assertTrue(LogicType.calculate(excel.getFinishCondComb(), new int[]{1, 1, 1}));
    }

    @Test
    void meaningfulExcelRuleIsAuthoritative() {
        var conditions = quest(DUNGEONS).getFinishCond();
        assertEquals(LogicType.LOGIC_OR,
                QuestData.effectiveConditionCombinator(
                        LogicType.LOGIC_OR, LogicType.LOGIC_AND, conditions));
        assertEquals(LogicType.LOGIC_NOT,
                QuestData.effectiveConditionCombinator(
                        LogicType.LOGIC_NOT, LogicType.LOGIC_AND, conditions));
    }

    @Test
    void missingOrSingleObjectiveRulesRemainUntouched() {
        var conditions = quest(DUNGEONS).getFinishCond();
        assertEquals(LogicType.LOGIC_NONE,
                QuestData.effectiveConditionCombinator(
                        LogicType.LOGIC_NONE, null, conditions));
        assertEquals(LogicType.LOGIC_NONE,
                QuestData.effectiveConditionCombinator(
                        LogicType.LOGIC_NONE, LogicType.LOGIC_OR, conditions.subList(0, 1)));
        assertNull(QuestData.effectiveConditionCombinator(
                null, LogicType.LOGIC_AND, List.of()));
    }

    @Test
    void failConditionsCanUseTheSameBinOutputRule() {
        var excel = quest("""
                {"subId":35203,"mainId":352,"finishCond":[],
                 "failCond":[
                   {"type":"QUEST_CONTENT_NOT_FINISH_PLOT","param":[35203,0]},
                   {"type":"QUEST_CONTENT_TEAM_DEAD","param":[0,0]}]}
                """);
        var bin = GSON.fromJson(
                "{\"subId\":35203,\"failCondComb\":\"LOGIC_OR\"}",
                MainQuestData.SubQuestData.class);
        excel.applyFrom(bin);
        assertEquals(LogicType.LOGIC_OR, excel.getFailCondComb());
        assertTrue(LogicType.calculate(excel.getFailCondComb(), new int[]{0, 1}));
    }
}
