package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.common.ItemParamData;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestNativeRewardFallbackTest {
    private static final Gson GSON = new Gson();

    @Test
    void amberIntroRestoresNativeRewardLostByFlattening() {
        var excel = GSON.fromJson(
                """
                {"subId":35402,"mainId":354,"beginExec":[],"finishExec":[],"failExec":[]}
                """, QuestData.class);
        var nativeData = GSON.fromJson(
                """
                {"subId":35402,"gainItems":[{"itemId":1021,"count":1}]}
                """, MainQuestData.SubQuestData.class);

        excel.applyFrom(nativeData);
        assertEquals(1, excel.getGainItems().size());
        assertEquals(1021, excel.getGainItems().get(0).getId());
        assertEquals(1, excel.getGainItems().get(0).getCount());
    }

    @Test
    void existingExcelRewardsStayAuthoritative() {
        var excel = List.of(new ItemParamData(202, 500));
        var nativeReward = List.of(new ItemParamData(1021, 1));
        assertEquals(excel, QuestData.effectiveGainItems(excel, nativeReward));
    }

    @Test
    void noNativeGrantDoesNotInjectCharactersInto353Tutorial() {
        var tutorial = GSON.fromJson(
                """
                {"subId":35301,"gainItems":[]}
                """, MainQuestData.SubQuestData.class);
        assertTrue(QuestData.effectiveGainItems(null, tutorial.getGainItems()).isEmpty());
        assertTrue(QuestData.effectiveGainItems(null, null).isEmpty());
    }

    @Test
    void malformedOrEmptyRewardsCannotGrantItems() {
        var invalid = List.of(new ItemParamData(0, 1), new ItemParamData(1021, 0));
        assertTrue(QuestData.effectiveGainItems(invalid, List.of()).isEmpty());
        assertEquals(1021, QuestData.effectiveGainItems(invalid,
                List.of(new ItemParamData(1021, 1))).get(0).getId());
    }
}
