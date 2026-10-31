package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
class SceneGroupRestoreTest {
    private boolean scriptsEnabled;
    private TestWorld.TestPlayer player;
    private TestWorld world;
    private SceneGroup group;
    private SceneGroupInstance instance;
    private final List<SceneSuite> created = new ArrayList<>();
    private final List<ScriptArgs> events = new ArrayList<>();

    @BeforeEach
    void setup() {
        scriptsEnabled = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        player = new TestWorld.TestPlayer();
        world = TestWorld.create(player);
        // Native scene3_group133004061: suite 1 has the traveling seelie,
        // suite 2 has the court and reward chest. The court itself is not persistent.
        group = SceneGroup.of(133004061);
        group.init_config = new SceneInitConfig();
        group.init_config.suite = 1;
        group.init_config.end_suite = 2;
        var traveling = new SceneSuite();
        traveling.gadgets = List.of(61001, 61002, 61003);
        var solved = new SceneSuite();
        solved.gadgets = List.of(61001, 61004);
        group.suites = List.of(traveling, solved);
        var variable = new SceneVar();
        variable.name = "solved";
        variable.value = 0;
        group.variables = List.of(variable);
        instance = new SceneGroupInstance(group, player);
        instance.setActiveSuiteId(2);
        instance.setLastTimeRefreshed(123);
        instance.getCachedVariables().put("solved", 1);
    }

    @AfterEach
    void restoreConfig() {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = scriptsEnabled;
    }

    private SceneScriptManager manager(SceneType type) {
        var scene = new TestWorld.TestScene(world, 3, type);
        return new SceneScriptManager(scene) {
            @Override public void loadGroupFromScript(SceneGroup loaded) {}
            @Override public SceneGroupInstance getGroupInstanceById(int id) { return instance; }
            @Override public SceneGroupInstance getCachedGroupInstanceById(int id) { return instance; }
            @Override public void addGroupSuite(SceneGroupInstance state, SceneSuite suite,
                    List<emu.grasscutter.game.entity.GameEntity> entities) { created.add(suite); }
            @Override public void removeGroupSuite(SceneGroup ignored, SceneSuite suite) {}
            @Override public Future<?> callEvent(ScriptArgs args) {
                events.add(args);
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    @Test
    void sceneLoaderRestoresSavedProgressInsteadOfRefreshingTheInitialSuite() throws Exception {
        var scripts = manager(SceneType.SCENE_WORLD);
        var scene = scripts.getScene();
        var field = emu.grasscutter.game.world.Scene.class.getDeclaredField("scriptManager");
        field.setAccessible(true);
        field.set(scene, scripts);
        scripts.getLoadedGroupSetPerBlock().put(group.block_id, new HashSet<>());

        scene.onLoadGroup(List.of(group));

        assertEquals(List.of(group.suites.get(1)), created);
        assertEquals(2, instance.getActiveSuiteId());
        assertEquals(1, instance.getCachedVariables().get("solved"));
        assertTrue(scene.getLoadedGroups().contains(group));
        assertTrue(events.stream().anyMatch(e -> e.type == EventType.EVENT_GROUP_LOAD));
    }

    @Test
    void sceneReloadKeepsCompletedSeelieSuiteAndPuzzleVariables() {
        assertEquals(2, DailyTaskGroupLifecycle.load(manager(SceneType.SCENE_WORLD), instance, 0));
        assertEquals(List.of(group.suites.get(1)), created);
        assertFalse(created.getFirst().gadgets.contains(61002), "Do not respawn the traveling seelie");
        assertEquals(1, instance.getCachedVariables().get("solved"));
        assertEquals(123, instance.getLastTimeRefreshed());
        assertEquals(1, events.size());
        assertEquals(EventType.EVENT_GROUP_REFRESH, events.getFirst().type,
                "Keep native runtime initialization hooks on group load");
    }

    @Test
    void explicitLuaRefreshStillResetsSuiteAndRefreshableVariables() {
        var scripts = manager(SceneType.SCENE_WORLD);
        assertEquals(1, DailyTaskGroupLifecycle.refresh(scripts, instance, 0, false));
        assertEquals(1, instance.getActiveSuiteId());
        assertEquals(0, instance.getCachedVariables().get("solved"));
        assertEquals(EventType.EVENT_GROUP_REFRESH, events.getFirst().type);
    }

    @Test
    void explicitQuestOverrideStillWinsOverSavedPuzzleSuite() {
        assertEquals(1, DailyTaskGroupLifecycle.load(manager(SceneType.SCENE_WORLD), instance, 1));
        assertEquals(List.of(group.suites.getFirst()), created);
        assertEquals(1, instance.getActiveSuiteId());
        assertEquals(0, instance.getCachedVariables().get("solved"));
    }

    @Test
    void dungeonReloadStartsFromNativeInitialSuite() {
        assertEquals(1, DailyTaskGroupLifecycle.load(manager(SceneType.SCENE_DUNGEON), instance, 0));
        assertEquals(1, instance.getActiveSuiteId());
        assertEquals(0, instance.getCachedVariables().get("solved"));
    }

    @Test
    void absentOrObsoleteSavedSuiteFallsBackToNativeInitialSuite() {
        for (int saved : List.of(0, 99, -1)) {
            instance.setActiveSuiteId(saved);
            assertEquals(1, DailyTaskGroupLifecycle.load(manager(SceneType.SCENE_WORLD), instance, 0));
            assertEquals(1, instance.getActiveSuiteId());
        }
    }
}
