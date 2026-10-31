package emu.grasscutter.game.dailytask;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScriptSceneData;
import emu.grasscutter.data.excels.DailyTaskData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneGroup;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

/** The real manager must not enter the group loader while the host is far from the task. */
@ExtendWith(ServerResourceFixture.class)
class DailyTaskRemoteActivationTest {
    private static final int TASK = 991001, GROUP = 133001999;
    private Owner player;
    private RecordingScene scene;
    private DailyTaskManager manager;
    private Map<Integer, DailyTaskData> resources;
    private Map<String, ScriptSceneData> scripts;
    private Map<Integer, Boolean> support, oldSupport;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() throws Exception {
        resources = new HashMap<>(GameData.getDailyTaskDataMap());
        scripts = new HashMap<>(GameData.getScriptSceneDataMap());
        var cache = DailyTaskManager.class.getDeclaredField("GROUP_RESOURCE_SUPPORT_CACHE");
        cache.setAccessible(true);
        support = (Map<Integer, Boolean>) cache.get(null);
        oldSupport = new HashMap<>(support);
        var gson = new Gson();
        GameData.getDailyTaskDataMap()
                .put(
                        TASK,
                        gson.fromJson(
                                "{\"id\":"
                                        + TASK
                                        + ",\"type\":\"DAILY_TASK_SCENE\",\"finishType\":\"DAILY_FINISH_MONSTER_NUM\","
                                        + "\"finishProgress\":1,\"taskRewardId\":1,\"newGroupVec\":["
                                        + GROUP
                                        + "],\"centerPosition\":\"TestCommission\",\"enterDistance\":40,\"exitDistance\":60}",
                                DailyTaskData.class));
        support.put(GROUP, true);
        GameData.getScriptSceneDataMap()
                .put(
                        "flat.luas.scenes.full_globals.lua.json",
                        gson.fromJson(
                                "{\"scriptObjectList\":{\"3/scene3_dummy_points.lua\":{\"dummy_points\":{\"TestCommission.pos\":[0,10,0]}}}}",
                                ScriptSceneData.class));
        player = allocate(Owner.class);
        player.rank = 12;
        player.position = new Position(500, 10, 0);
        player.setSceneLoadState(Player.SceneLoadState.LOADED);
        var world = allocate(HostWorld.class);
        world.host = player;
        player.setWorld(world);
        scene = allocate(RecordingScene.class);
        scene.world = world;
        scene.player = player;
        scene.scripts = allocate(ReadyScripts.class);
        player.scene = scene;
        manager = new DailyTaskManager(player);
        player.manager = manager;
        manager
                .getDailyTasks()
                .add(gson.fromJson("{\"taskId\":" + TASK + ",\"finishProgress\":1}", DailyTask.class));
        assertEquals(
                Set.of(GROUP), manager.getActiveGroupIds(), "Fixture must admit the real loader boundary");
    }

    @AfterEach
    void restore() {
        GameData.getDailyTaskDataMap().clear();
        GameData.getDailyTaskDataMap().putAll(resources);
        GameData.getScriptSceneDataMap().clear();
        GameData.getScriptSceneDataMap().putAll(scripts);
        support.clear();
        support.putAll(oldSupport);
    }

    @Test
    void farHostDoesNotActivateOrMutateTheIssuedTask() {
        assertEquals(0, manager.loadActiveGroups(scene));
        assertEquals(0, scene.loaderCalls);
        assertEquals(0, manager.getDailyTasks().getFirst().getProgress());
        assertFalse(manager.getDailyTasks().getFirst().isFinished());
    }

    @Test
    void verticalDistanceAlsoPreventsActivation() {
        player.position = new Position(0, 51, 0);
        assertEquals(0, manager.loadActiveGroups(scene));
        assertEquals(0, scene.loaderCalls);
    }

    @Test
    void upstreamMinimumRankGuardRemainsInForceAtTheCenter() {
        player.rank = 11;
        player.position = new Position(0, 10, 0);
        assertEquals(0, manager.loadActiveGroups(scene));
        assertEquals(0, scene.loaderCalls);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    static final class Owner extends Player {
        int rank;
        Position position;
        Scene scene;
        DailyTaskManager manager;

        @Override
        public int getUid() {
            return 10001;
        }

        @Override
        public int getLevel() {
            return rank;
        }

        @Override
        public Position getPosition() {
            return position;
        }

        @Override
        public Scene getScene() {
            return scene;
        }

        @Override
        public DailyTaskManager getDailyTaskManager() {
            return manager;
        }
    }

    static final class HostWorld extends World {
        Player host;

        HostWorld() {
            super((Player) null);
        }

        @Override
        public Player getHost() {
            return host;
        }
    }

    static final class ReadyScripts extends SceneScriptManager {
        ReadyScripts() {
            super(null);
        }

        @Override
        public boolean isInit() {
            return true;
        }
    }

    static final class RecordingScene extends Scene {
        HostWorld world;
        Player player;
        ReadyScripts scripts;
        int loaderCalls;

        RecordingScene() {
            super(null, null);
        }

        @Override
        public int getId() {
            return 3;
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts;
        }

        @Override
        public List<Player> getPlayers() {
            return List.of(player);
        }

        @Override
        public Set<SceneGroup> getLoadedGroups() {
            return Set.of();
        }

        @Override
        public int loadDynamicGroup(int group) {
            loaderCalls++;
            return 1;
        }
    }
}
