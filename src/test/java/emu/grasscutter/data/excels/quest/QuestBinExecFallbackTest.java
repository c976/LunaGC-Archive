package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.binout.MainQuestData;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestBinExecFallbackTest {
    private static final Gson GSON = new Gson();

    private static QuestData.QuestExecParam exec(String type) {
        return GSON.fromJson(
                "{\"type\":\"" + type + "\",\"param\":[\"3\",\"133003002,2\"]}",
                QuestData.QuestExecParam.class);
    }

    @Test
    void missingExcelActionsUseNativeSlimeSuiteRefresh() {
        var bin = exec("QUEST_EXEC_REFRESH_GROUP_SUITE");
        var result = QuestData.effectiveExecList(List.of(), List.of(bin));
        assertEquals(1, result.size());
        assertEquals("133003002,2", result.get(0).getParam()[1]);
    }

    @Test
    void actualSubQuestMappingRestoresTravelerWindTutorialWithoutEarlyAmber() {
        // 35301 is still the Traveler's wind-element tutorial. The unwanted trial
        // grant was injected into the 7.1 resources by commit c98b896710.
        var dialogueExcel = GSON.fromJson(
                "{\"subId\":35301,\"mainId\":353,\"beginExec\":[],\"finishExec\":[],\"failExec\":[]}",
                QuestData.class);
        var dialogueNative = GSON.fromJson(
                "{\"subId\":35301,\"beginExec\":[{\"type\":\"QUEST_EXEC_REFRESH_GROUP_SUITE\","
                        + "\"param\":[\"3\",\"133003002,1\"]}]}",
                MainQuestData.SubQuestData.class);
        var dialogueBegin = QuestData.effectiveExecList(
                dialogueExcel.getBeginExec(), dialogueNative.getBeginExec());
        assertEquals(1, dialogueBegin.size());
        assertEquals("133003002,1", dialogueBegin.get(0).getParam()[1]);
        assertTrue(QuestData.effectiveExecList(
                dialogueExcel.getFinishExec(), dialogueNative.getFinishExec()).isEmpty());

        var slimeExcel = GSON.fromJson(
                "{\"subId\":35302,\"mainId\":353,\"beginExec\":[],\"finishExec\":[],\"failExec\":[]}",
                QuestData.class);
        var slimeNative = GSON.fromJson(
                "{\"subId\":35302,\"beginExec\":[{\"type\":\"QUEST_EXEC_REFRESH_GROUP_SUITE\","
                        + "\"param\":[\"3\",\"133003002,2\"]}]}",
                MainQuestData.SubQuestData.class);
        var slimeBegin = QuestData.effectiveExecList(
                slimeExcel.getBeginExec(), slimeNative.getBeginExec());
        assertEquals(1, slimeBegin.size());
        assertEquals("QUEST_EXEC_REFRESH_GROUP_SUITE", slimeBegin.get(0).getType().name());
        assertEquals(List.of("3", "133003002,2"), List.of(slimeBegin.get(0).getParam()));
    }

    @Test
    void amberBowTutorialRestoresNativeLuaNotification() {
        // The native Quest 35404 start event reaches scene group 133003439.
        // That group's QUEST_START -> timer -> gadget-3834 script drives the target.
        var excel = GSON.fromJson(
                "{\"subId\":35404,\"mainId\":354,\"beginExec\":[],\"finishExec\":[],\"failExec\":[]}",
                QuestData.class);
        var nativeData = GSON.fromJson(
                "{\"subId\":35404,\"beginExec\":[{\"type\":\"QUEST_EXEC_NOTIFY_GROUP_LUA\","
                        + "\"param\":[\"3\",\"133003439\"]}]}",
                MainQuestData.SubQuestData.class);
        var actions = QuestData.effectiveExecList(
                excel.getBeginExec(), nativeData.getBeginExec());

        assertEquals(1, actions.size());
        assertEquals("QUEST_EXEC_NOTIFY_GROUP_LUA", actions.get(0).getType().name());
        assertEquals(List.of("3", "133003439"), List.of(actions.get(0).getParam()));
    }

    @Test
    void nonemptyExcelActionsStayAuthoritative() {
        var excel = exec("QUEST_EXEC_GRANT_TRIAL_AVATAR");
        var bin = exec("QUEST_EXEC_REFRESH_GROUP_SUITE");
        assertEquals(List.of(excel), QuestData.effectiveExecList(List.of(excel), List.of(bin)));
    }

    @Test
    void parsesNative353QuestActionsWithoutInjectingAmber() {
        // Mirrors the original 7.1 resource before the erroneous c98b896710 grant,
        // corroborated by GCResource 3700 and 4000.
        String nativeSubquests =
                """
                {"id":353,"subQuests":[
                    {"subId":35301,
                     "beginExec":[{"type":"QUEST_EXEC_REFRESH_GROUP_SUITE",
                                   "param":["3","133003002,1"]}]},
                    {"subId":35302,
                     "beginExec":[{"type":"QUEST_EXEC_REFRESH_GROUP_SUITE",
                                   "param":["3","133003002,2"]}]}
                ]}
                """;
        var nativeMain =
                GSON.fromJson(
                        nativeSubquests, emu.grasscutter.data.binout.MainQuestData.class);
        var subs = nativeMain.getSubQuests();
        assertEquals(35301, subs[0].getSubId());
        assertTrue(QuestData.effectiveExecList(List.of(), subs[0].getFinishExec()).isEmpty());
        assertEquals("133003002,1", subs[0].getBeginExec().get(0).getParam()[1]);
        assertEquals(35302, subs[1].getSubId());

        var recoveredBegin = QuestData.effectiveExecList(List.of(), subs[1].getBeginExec());
        assertEquals(1, recoveredBegin.size());
        assertEquals("QUEST_EXEC_REFRESH_GROUP_SUITE", recoveredBegin.get(0).getType().name());
        assertEquals("133003002,2", recoveredBegin.get(0).getParam()[1]);
    }

    @Test
    void invalidOrAbsentActionsAreIgnored() {
        var unknown = exec("NOT_AN_EXEC_TYPE");
        assertTrue(QuestData.effectiveExecList(List.of(), List.of(unknown)).isEmpty());
        assertTrue(QuestData.effectiveExecList(null, null).isEmpty());
    }
}
