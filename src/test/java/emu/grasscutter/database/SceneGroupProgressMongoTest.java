package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ASTAPS_SCENE_GROUP_MONGO_TEST", matches = "1")
@ExtendWith(ServerResourceFixture.class)
class SceneGroupProgressMongoTest {
    @Test
    void completedPuzzleLoadsItsSavedSuiteAndVariablesAfterMongoReconnect() {
        String name = "astaps_group_progress_test_" + UUID.randomUUID().toString().replace("-", "");
        String uri = "mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=3000";
        boolean oldScripts = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        try {
            var player = new TestWorld.TestPlayer();
            player.setUid(12345);
            var group = SceneGroup.of(133004061);
            group.init_config = new SceneInitConfig();
            group.init_config.suite = 1;
            var initial = new SceneSuite();
            initial.gadgets = List.of(61001, 61002, 61003);
            var completed = new SceneSuite();
            completed.gadgets = List.of(61001, 61004);
            group.suites = List.of(initial, completed);
            var variable = new SceneVar();
            variable.name = "solved";
            variable.value = 0;
            group.variables = List.of(variable);
            try (var client = MongoClients.create(uri)) {
                var store = Morphia.createDatastore(client, name);
                store.getMapper().map(SceneGroupInstance.class);
                var progress = new SceneGroupInstance(group, player);
                progress.setActiveSuiteId(2);
                progress.getCachedVariables().put("solved", 1);
                progress.setLastTimeRefreshed(123);
                store.save(progress);
            }
            try (var client = MongoClients.create(uri)) {
                var store = Morphia.createDatastore(client, name);
                store.getMapper().map(SceneGroupInstance.class);
                var progress = store.find(SceneGroupInstance.class).first();
                assertNotNull(progress);
                assertNull(progress.getLuaGroup());
                progress.setLuaGroup(group);
                var scene = new TestWorld.TestScene(TestWorld.create(player), 3, SceneType.SCENE_WORLD);
                var created = new ArrayList<Integer>();
                var scripts = new SceneScriptManager(scene) {
                    @Override public void addGroupSuite(SceneGroupInstance state, SceneSuite suite,
                            List<emu.grasscutter.game.entity.GameEntity> entities) {
                        created.addAll(suite.gadgets);
                    }
                    @Override public Future<?> callEvent(ScriptArgs args) {
                        return CompletableFuture.completedFuture(null);
                    }
                };
                assertEquals(2, DailyTaskGroupLifecycle.load(scripts, progress, 0));
                assertEquals(List.of(61001, 61004), created);
                assertEquals(1, progress.getCachedVariables().get("solved"));
                assertEquals(123, progress.getLastTimeRefreshed());
            }
        } finally {
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = oldScripts;
            try (var client = MongoClients.create(uri)) { client.getDatabase(name).drop(); }
        }
    }
}
