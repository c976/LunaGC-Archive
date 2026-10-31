package emu.grasscutter.game.dailytask;

import static emu.grasscutter.game.dailytask.DailyTaskGroupLifecycleTest.alloc;
import static emu.grasscutter.game.dailytask.DailyTaskGroupLifecycleTest.set;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

@ExtendWith(emu.grasscutter.ServerResourceFixture.class)
class DailyTaskSnapshotRecoveryTest {
    private static final int GROUP = 133001306;
    private static final String VALID =
            """
            {"version":1,"owner":10001,"date":20261016,"serial":0,"task":10102,"group":133001306,
             "suite":1,"entities":[],"triggers":{},"regions":[]}
            """;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "malformed",
                "oversize",
                "null",
                "version",
                "suite",
                "entities",
                "entity",
                "triggers",
                "regions",
                "tides",
                "owner",
                "date",
                "serial",
                "task",
                "group"
            })
    void invalidSnapshotsClearActiveAndCachedAliasesBeforeOneNormalRefresh(String kind)
            throws Exception {
        var f = fixture();
        var value = JsonParser.parseString(VALID).getAsJsonObject();
        String text;
        switch (kind) {
            case "malformed" -> text = "{";
            case "oversize" -> text = " ".repeat(262145);
            case "null" -> text = "null";
            default -> {
                switch (kind) {
                    case "entities" -> value.remove("entities");
                    case "entity" ->
                            value.add(
                                    "entities",
                                    JsonParser.parseString(
                                            "[{\"kind\":\"M\",\"config\":999,\"hp\":10}]"));
                    case "triggers" ->
                            value.add("triggers", JsonParser.parseString("{\"UNKNOWN\":1}"));
                    case "regions" -> value.add("regions", JsonParser.parseString("[999]"));
                    case "tides" -> value.add("tides", JsonParser.parseString("[null]"));
                    default -> value.addProperty(kind, -1);
                }
                text = value.toString();
            }
        }
        f.instance.setDailyTaskSnapshot(text);
        assertEquals(1, DailyTaskGroupLifecycle.refresh(f.scripts, f.instance, 1, false));
        assertNull(f.instance.getDailyTaskSnapshot());
        assertNull(f.cached.getDailyTaskSnapshot());
        assertNull(f.scripts.getGroupInstanceById(GROUP).getDailyTaskSnapshot());
        assertFalse(f.state.restored.contains(GROUP), "fresh group load must not be suppressed");
        assertEquals(1, f.scripts.refreshes);
        assertEquals(2, f.scene.entities.size());
        assertEquals(
                1, f.scripts.triggers.get(1).size(), "only the clean suite registers its trigger");
    }

    @Test
    void validSnapshotRestoresWithoutNormalRefreshOrCreateCallbacks() throws Exception {
        var f = fixture();
        f.instance.setDailyTaskSnapshot(VALID);
        assertEquals(1, DailyTaskGroupLifecycle.refresh(f.scripts, f.instance, 1, false));
        assertEquals(0, f.scripts.refreshes);
        assertTrue(f.scene.entities.isEmpty());
        assertTrue(f.state.restored.contains(GROUP));
        assertEquals(VALID, f.instance.getDailyTaskSnapshot());
    }

    @Test
    void restoresRegisteredButUnstartedTideWithoutReplayingGroupLoad() throws Exception {
        var f = fixture();
        var saved = JsonParser.parseString(VALID).getAsJsonObject();
        saved.add(
                "tides",
                JsonParser.parseString(
                        """
                        [{"source":"startup","tideCount":2,"killCount":0,"alive":0,"sceneLimit":1,
                          "orders":[1311,1312],"configIds":[1311,1312],"started":false}]
                        """));
        f.instance.setDailyTaskSnapshot(saved.toString());
        DailyTaskGroupLifecycle.refresh(f.scripts, f.instance, 1, false);
        assertEquals(
                1,
                f.scene.entities.size(),
                "an interrupted first turn must resume after hydration");
        assertEquals(0, f.scripts.refreshes);
        assertEquals(0, f.scripts.groupLoadEvents);
        var tide = f.scripts.getMonsterTides(GROUP).get(0);
        assertTrue(tide.snapshot().started);
        assertEquals(1, tide.snapshot().alive);
        assertEquals(1, tide.snapshot().tideCount);
        tide.spawnFirstTurn();
        assertEquals(1, f.scene.entities.size(), "startup publication is idempotent");
    }

    @Test
    void failureInNormalFallbackIsPropagatedRatherThanRetried() throws Exception {
        var f = fixture();
        f.instance.setDailyTaskSnapshot("{");
        f.scripts.refreshFailure = new IllegalStateException("normal refresh failed");
        assertSame(
                f.scripts.refreshFailure,
                assertThrows(
                        IllegalStateException.class,
                        () -> DailyTaskGroupLifecycle.refresh(f.scripts, f.instance, 1, false)));
        assertEquals(1, f.scripts.refreshes);
        assertNull(f.instance.getDailyTaskSnapshot());
    }

    private static Fixture fixture() throws Exception {
        var owner = alloc(DailyTaskGroupLifecycleTest.P.class);
        owner.uid = 10001;
        var scene = alloc(DailyTaskGroupLifecycleTest.S.class);
        var world = alloc(DailyTaskGroupLifecycleTest.W.class);
        world.owner = owner;
        owner.setWorld(world);
        owner.scene = scene;
        scene.world = world;
        scene.players = new ArrayList<>(List.of(owner));
        scene.entities = new LinkedHashMap<>();
        scene.groups = new LinkedHashSet<>();
        scene.packets = new ArrayList<>();
        var manager = new DailyTaskGroupLifecycleTest.M(owner);
        owner.manager = manager;
        var scripts = alloc(DailyTaskGroupLifecycleTest.C.class);
        scripts.scene = scene;
        scripts.instances = new HashMap<>();
        scripts.triggers = new HashMap<>();
        scripts.counts = new HashMap<>();
        scene.scripts = scripts;
        set(SceneScriptManager.class, scripts, "monsterTides", new ArrayList<>());
        set(
                SceneScriptManager.class,
                scripts,
                "scriptMonsterSpawnService",
                new emu.grasscutter.scripts.service.ScriptMonsterSpawnService(scripts));
        var group = SceneGroup.of(GROUP);
        group.block_id = 3001;
        group.monsters = new HashMap<>();
        group.gadgets = new HashMap<>();
        group.npcs = new HashMap<>();
        group.regions = new HashMap<>();
        group.variables = new ArrayList<>();
        group.suites = List.of(new SceneSuite());
        var trigger = new SceneTrigger();
        trigger.setName("WAVE");
        trigger.setEvent(1);
        trigger.setCurrentGroup(group);
        group.triggers = Map.of("WAVE", trigger);
        for (int config : new int[] {1311, 1312}) {
            var monster = new SceneMonster();
            monster.config_id = config;
            monster.pos = new Position();
            group.monsters.put(config, monster);
        }
        var instance = new SceneGroupInstance(group, owner);
        var cached = new SceneGroupInstance(group, owner);
        cached.setDailyTaskSnapshot("stale cached alias");
        scripts.instances.put(GROUP, cached);
        var state = new DailyTaskGroupLifecycle.State(manager, scene);
        state.owned.put(
                GROUP,
                new DailyTaskGroupLifecycle.Plan(
                        10102, GROUP, 20261016, 0, new Position(), 40, 60));
        state.managed.add(GROUP);
        scene.setDailyTaskGroups(state);
        return new Fixture(scene, scripts, instance, cached, state);
    }

    private record Fixture(
            DailyTaskGroupLifecycleTest.S scene,
            DailyTaskGroupLifecycleTest.C scripts,
            SceneGroupInstance instance,
            SceneGroupInstance cached,
            DailyTaskGroupLifecycle.State state) {}
}
