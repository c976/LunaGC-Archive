package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.world.WeatherData;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestWeatherRestoreTest {
    private static final Gson GSON = new Gson();
    private static QuestData quest(String begin, String finish) {
        return GSON.fromJson(
                "{\"subId\":35901,\"mainId\":359,\"beginExec\":" + begin
                + ",\"finishExec\":" + finish + "}", QuestData.class);
    }

    private static WeatherData weather(int scene, int gadget) {
        return GSON.fromJson(
                "{\"areaID\":3,\"sceneID\":" + scene
                + ",\"gadgetID\":" + gadget + "}", WeatherData.class);
    }

    @Test
    void active35901StormReturnsAfterRelogInMondstadt() {
        var active = quest(
                "[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"3\",\"1\"]}]",
                "[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"3\",\"0\"]},"
                    + "{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"1\",\"0\"]}]");
        assertEquals(3, QuestWeatherRestore.selectArea(
                List.of(active), 3, id -> id == 3 ? weather(3, 70020003) : null));
        assertEquals(0, QuestWeatherRestore.selectArea(
                List.of(active), 4, id -> id == 3 ? weather(3, 70020003) : null));
    }

    @Test
    void oneShotOrMalformedWeatherActionsAreNotReplayed() {
        var begin = "[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"3\",\"1\"]}]";
        var finish = "[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"3\",\"0\"]}]";
        assertEquals(0, QuestWeatherRestore.selectArea(
                List.of(quest(begin, "[]")), 3, id -> weather(3, 70020003)));
        assertEquals(0, QuestWeatherRestore.selectArea(
                List.of(quest(begin, finish)), 3, id -> weather(3, 0)));
        assertEquals(0, QuestWeatherRestore.selectArea(
                List.of(quest(begin, finish)), 3, id -> weather(4, 70020003)));
        assertEquals(0, QuestWeatherRestore.selectArea(
                List.of(quest("[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"x\",\"1\"]}]",
                        finish)), 3, id -> weather(3, 70020003)));
    }

    @Test
    void conflictingActiveAreasHaveNoInventedWinner() {
        var first = quest(
                "[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"3\",\"1\"]}]",
                "[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"3\",\"0\"]}]");
        var second = quest(
                "[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"1\",\"1\"]}]",
                "[{\"type\":\"QUEST_EXEC_SET_WEATHER_GADGET\",\"param\":[\"1\",\"0\"]}]");
        assertEquals(0, QuestWeatherRestore.selectArea(
                List.of(first, second), 3,
                id -> id == 3 ? weather(3, 70020003) : weather(3, 70020001)));
        assertEquals(0, QuestWeatherRestore.selectArea(
                List.of(), 3, id -> weather(3, 70020003)));
    }
}
