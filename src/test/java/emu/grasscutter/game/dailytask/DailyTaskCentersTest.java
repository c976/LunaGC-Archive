package emu.grasscutter.game.dailytask;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScriptSceneData;
import emu.grasscutter.data.excels.DailyTaskData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@ExtendWith(emu.grasscutter.ServerResourceFixture.class)
class DailyTaskCentersTest {
    private static final Gson JSON = new Gson();

    @Test
    void readsAuthoredLuaCentersWithBareAndQuotedNames() {
        var points =
                DailyTaskCenters.read(
                        new StringReader(
                                """
                                dummy_points = {
                                    Event_10100V2 = { pos = { x = 2457.819, y = 210.615, z = -1106.816 } },
                                    ["Event_32250"] = { pos = { z = 3, x = -1.5e2, y = 2 } },
                                    bad = { pos = { x = 1, y = 2 } },
                                    infinite = { pos = { x = 1/0, y = 2, z = 3 } },
                                    wrong_type = { pos = { x = "1", y = 2, z = 3 } },
                                    overflow = { pos = { x = 1e100, y = 2, z = 3 } }
                                }
                                """));
        assertEquals(2, points.size());
        assertEquals(2457.819f, points.get("Event_10100V2").getX());
        assertEquals(210.615f, points.get("Event_10100V2").getY());
        assertEquals(-1106.816f, points.get("Event_10100V2").getZ());
        assertEquals(-150f, points.get("Event_32250").getX());
    }

    @Test
    void fallsBackToLuaWhileKeepingJsonPriorityAndDistanceValidation() {
        var map = GameData.getScriptSceneDataMap();
        var backup = new HashMap<>(map);
        try {
            map.clear();
            var state = new DailyTaskGroupLifecycle.State(null, null);
            state.luaCenters =
                    DailyTaskCenters.read(
                            new StringReader(
                                    "dummy_points = { Event_10100V2 = { pos = { x = 10, y = 20, z ="
                                        + " 30 } } }"));
            var row =
                    JSON.fromJson(
                            """
                            {"centerPosition":"Event_10100V2","enterDistance":40,"exitDistance":60}
                            """,
                            DailyTaskData.class);
            assertEquals(10f, DailyTaskGroupLifecycle.center(row, state).getX());
            map.put(
                    "flat.luas.scenes.full_globals.lua.json",
                    JSON.fromJson(
                            """
                            {"scriptObjectList":{"3/scene3_dummy_points.lua":{"dummy_points":{"Event_10100V2.pos":[1,2,3]}}}}
                            """,
                            ScriptSceneData.class));
            assertEquals(1f, DailyTaskGroupLifecycle.center(row, state).getX());
            map.clear();
            var invalid =
                    JSON.fromJson(
                            """
                            {"centerPosition":"Event_10100V2","enterDistance":60,"exitDistance":40}
                            """,
                            DailyTaskData.class);
            assertNull(DailyTaskGroupLifecycle.center(invalid, state));
            state.luaCenters = Map.of();
            assertNull(
                    DailyTaskGroupLifecycle.center(row, state),
                    "missing coordinates never become an origin");
        } finally {
            map.clear();
            map.putAll(backup);
        }
    }

    @Test
    void missingOrMalformedFileFailsClosedAndNoScriptLibrariesAreExposed() throws Exception {
        assertTrue(
                DailyTaskCenters.load(Path.of("resources", "absent-commission-centers.lua"))
                        .isEmpty());
        assertThrows(
                RuntimeException.class,
                () -> DailyTaskCenters.read(new StringReader("dummy_points = {")));
        assertThrows(
                RuntimeException.class,
                () -> DailyTaskCenters.read(new StringReader("io.open('anything')")));
        assertTrue(DailyTaskCenters.read(new StringReader("unused = {} ")).isEmpty());
    }
}
