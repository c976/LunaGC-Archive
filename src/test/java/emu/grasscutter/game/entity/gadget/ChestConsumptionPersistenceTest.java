package emu.grasscutter.game.entity.gadget;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.morphia.Datastore;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.*;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("integration") // Temporarily replaces the process-wide datastore, as AccountPersistenceTest does.
@ExtendWith(ServerResourceFixture.class)
class ChestConsumptionPersistenceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "SCENE_WORLD,SceneObj_Chest_Default_Lv1,false,false,true",
        "SCENE_DUNGEON,SceneObj_Chest_Default_Lv1,false,false,false",
        "SCENE_WORLD,SearchPoint,false,false,false",
        "SCENE_WORLD,SceneObj_Area_Common_Property_Ani_Prop_BGM_01,true,false,true",
        "SCENE_WORLD,SceneObj_Chest_Default_Lv1,false,true,false"
    })
    @SuppressWarnings("unchecked")
    void consumptionSurvivesUnloadOnlyForPermanentWorldPickups(
            String type, String name, boolean oneoff, boolean boss, boolean permanent) throws Exception {
        var config = Grasscutter.getConfig().server.game;
        boolean oldScripts = config.enableScriptInBigWorld;
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var previous = field.get(null);
        var snapshot = new CompletableFuture<SceneGroupInstance>();
        var gson = new Gson();
        var store = (Datastore) Proxy.newProxyInstance(Datastore.class.getClassLoader(),
                new Class<?>[] {Datastore.class}, (proxy, method, args) -> {
                    if (method.getName().equals("save") && args[0] instanceof SceneGroupInstance group) {
                        snapshot.complete(gson.fromJson(gson.toJson(group), SceneGroupInstance.class));
                        return group;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        int id = 70210001;
        var oldData = GameData.getGadgetDataMap().get(id);
        try {
            field.set(null, store);
            config.enableScriptInBigWorld = false;
            var player = new TestPlayers.TestPlayer(TestPlayers.server(), 9999);
            var scene = new Scene(new World(player), gson.fromJson("{\"id\":3,\"type\":\"" + type + "\"}", SceneData.class));
            var group = new SceneGroup();
            group.id = 133004007;
            var instance = new SceneGroupInstance(group, player);
            var active = SceneScriptManager.class.getDeclaredField("sceneGroupsInstances");
            active.setAccessible(true);
            ((Map<Integer, SceneGroupInstance>) active.get(scene.getScriptManager())).put(group.id, instance);
            GameData.getGadgetDataMap().put(id, gson.fromJson(
                    "{\"id\":70210001,\"jsonName\":\"" + name + "\"}", GadgetData.class));
            var gadget = new EntityGadget(scene, id, new Position()) {
                @Override public void updateState(int state) { setState(state); }
            };
            gadget.setGroupId(group.id);
            gadget.setConfigId(38);
            var meta = new SceneGadget();
            meta.config_id = 38;
            meta.isOneoff = oneoff;
            if (boss) meta.boss_chest = new SceneBossChest();
            // Neither persistent nor oneoff: ordinary chest consumption must still survive.
            gadget.setMetaGadget(meta);
            var finish = GadgetChest.class.getDeclaredMethod("finishOpen",
                    emu.grasscutter.game.player.Player.class, SceneGadget.class, boolean.class);
            finish.setAccessible(true);
            finish.invoke(new GadgetChest(gadget), player, meta, false);
            assertEquals(102, gadget.getState());
            if (permanent) {
                var restored = snapshot.get(5, TimeUnit.SECONDS);
                assertTrue(restored.isScriptGadgetDestroyed(38));
                assertFalse(restored.isScriptGadgetDestroyed(39));
            } else {
                assertFalse(instance.isScriptGadgetDestroyed(38));
                assertFalse(snapshot.isDone());
            }
        } finally {
            field.set(null, previous);
            config.enableScriptInBigWorld = oldScripts;
            if (oldData == null) GameData.getGadgetDataMap().remove(id);
            else GameData.getGadgetDataMap().put(id, oldData);
        }
    }
}
