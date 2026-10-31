package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

final class Quest351NativePrerequisiteTest {
    private static final Gson GSON = new Gson();

    private static QuestData.QuestAcceptCondition cond(String type, int id, int state) {
        String json = "{\"type\":\"" + type
                + "\",\"param\":[" + id + "," + state + ",0],\"param_str\":\"\"}";
        return GSON.fromJson(json, QuestData.QuestAcceptCondition.class);
    }

    @Test
    void paimonHandoffUsesNative35100FinishedPrerequisite() {
        var stale = List.of(cond("QUEST_COND_STATE_EQUAL", 0, 3));
        var nativeValue = List.of(cond("QUEST_COND_STATE_EQUAL", 35100, 3));
        var chosen = QuestData.selectNativePrologueAcceptConditions(351, stale, nativeValue);
        assertEquals(35100, chosen.get(0).getParam()[0]);
        assertFalse(QuestData.sameAcceptConditions(stale, chosen));
        assertTrue(QuestData.sameAcceptConditions(nativeValue, chosen));
    }

    @Test
    void otherMainQuestsAndMissingNativeConditionsRemainUntouched() {
        var excel = List.of(cond("QUEST_COND_STATE_EQUAL", 35100, 3));
        var nativeValue = List.of(cond("QUEST_COND_STATE_EQUAL", 35205, 3));
        assertSame(excel, QuestData.selectNativePrologueAcceptConditions(353, excel, nativeValue));
        assertSame(excel, QuestData.selectNativePrologueAcceptConditions(351, excel, null));
        assertSame(excel, QuestData.selectNativePrologueAcceptConditions(351, excel, List.of()));
    }

    @Test
    void native351MultipleConditionsRetainOrderAndSemantics() {
        var nativeValue = List.of(
                cond("QUEST_COND_STATE_EQUAL", 35106, 3),
                cond("QUEST_COND_STATE_NOT_EQUAL", 35105, 3));
        var chosen = QuestData.selectNativePrologueAcceptConditions(351, List.of(), nativeValue);
        assertEquals(2, chosen.size());
        assertEquals("QUEST_COND_STATE_NOT_EQUAL", chosen.get(1).getType().name());
    }
}
