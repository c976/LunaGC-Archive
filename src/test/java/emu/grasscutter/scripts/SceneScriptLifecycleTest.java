package emu.grasscutter.scripts;

import static emu.grasscutter.scripts.constants.EventType.EVENT_TIMER_EVENT;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.game.world.TestWorld.TestPlayer;
import emu.grasscutter.game.world.TestWorld.TestScene;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@ExtendWith(ServerResourceFixture.class)
final class SceneScriptLifecycleTest {
    private static boolean bigWorldScripts;
    private TestPlayer player;
    private TestWorld world;
    private TestScene scene;

    @BeforeAll
    static void loadScriptEngine() throws Exception {
        ScriptLoader.init();
        // Scene 3 then skips loading Lua resources the test checkout does not have.
        bigWorldScripts = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
    }

    @AfterAll
    static void restoreConfig() {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = bigWorldScripts;
    }

    @BeforeEach
    void createScene() {
        player = new TestPlayer();
        world = TestWorld.create(player);
        scene = new TestScene(world, 3, SceneType.SCENE_WORLD);
    }

    @Test
    void destroySavesGroupProgressBeforeClearingIt() {
        var manager = scene.getScriptManager();
        manager.getCachedGroupInstances().put(7, new SceneGroupInstance(group(), player));

        manager.onDestroy();

        assertEquals(1, scene.saves);
        assertEquals(1, scene.cachedGroupsAtSave);
        assertTrue(manager.getCachedGroupInstances().isEmpty());
    }

    @Test
    void destroyDuringAnEventSavesWhenTheEventReturns() throws Exception {
        var savesDuringEvent = new AtomicInteger(-1);
        var manager =
                new SceneScriptManager(scene) {
                    @Override
                    public Set<SceneTrigger> getTriggersByEvent(int eventId) {
                        onDestroy(); // e.g. the event's Lua moved the last player out
                        savesDuringEvent.set(scene.saves);
                        return new HashSet<>();
                    }
                };

        manager.callEvent(new ScriptArgs(7, EventType.EVENT_GADGET_CREATE)).get();

        assertEquals(0, savesDuringEvent.get());
        assertEquals(1, scene.saves);
    }

    @Test
    void groupTimerFiresOnceAfterItsSeconds() {
        var events = new ArrayList<ScriptArgs>();
        var manager = timerManager(events);

        assertEquals(0, manager.createGroupTimerEvent(7, "wave", 1.0));
        int ticks = (int) Math.ceil(1000d / Configuration.GAME_INFO.tickRateMs);
        tick(ticks - 1);
        assertTrue(events.isEmpty(), "fired before one second of ticks");
        tick(1);
        assertEquals(1, events.size());
        assertEquals("wave", events.get(0).getEventSource());
        assertEquals(7, events.get(0).getGroupId());
        tick(3 * ticks);
        assertEquals(1, events.size(), "a group timer is one-shot");
    }

    @Test
    void cancelledGroupTimerNeverFires() {
        var events = new ArrayList<ScriptArgs>();
        var manager = timerManager(events);

        manager.createGroupTimerEvent(7, "wave", 1.0);
        manager.cancelGroupTimerEvent(7, "wave");
        tick(30);

        assertTrue(events.isEmpty());
    }

    @Test
    void luaTeleportGoesThroughTheWorldAndKeepsTheScene() {
        world.transferResult = false;
        scene.getPlayers().add(player);
        var lib = ScriptLoader.getScriptLib();
        lib.setSceneScriptManager(scene.getScriptManager());
        try {
            var pos = new LuaTable();
            pos.set("x", 1);
            pos.set("y", 2);
            pos.set("z", 3);
            var args = new LuaTable();
            args.set("uid_list", LuaValue.listOf(new LuaValue[] {LuaValue.valueOf(player.getUid())}));
            args.set("pos", pos);

            assertNotEquals(0, lib.TransPlayerToPos(args), "a rejected transfer must report failure");
            world.transferResult = true;
            assertEquals(0, lib.TransPlayerToPos(args));
        } finally {
            lib.removeSceneScriptManager();
        }

        var transfer = world.transfers.get(0);
        assertEquals(3, transfer.getSceneId());
        assertEquals(TeleportType.SCRIPT, transfer.getTeleportType());
        assertEquals(2f, transfer.getTeleportTo().getY());
        assertTrue(scene.getPlayers().contains(player));
        assertFalse(scene.getScriptManager().isDestroyed());
    }

    private SceneScriptManager timerManager(List<ScriptArgs> events) {
        var group = group();
        var trigger = new SceneTrigger();
        trigger.setName("TIMER_EVENT_wave");
        trigger.setEvent(EVENT_TIMER_EVENT);
        trigger.setSource("wave");
        trigger.currentGroup = group;
        group.triggers = Map.of(trigger.getName(), trigger);
        return new SceneScriptManager(scene) {
            @Override
            public SceneGroup getGroupById(int groupId) {
                return groupId == group.id ? group : null;
            }

            @Override
            public Future<?> callEvent(ScriptArgs params) {
                events.add(params);
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private void tick(int count) {
        for (int i = 0; i < count; i++) scene.getScheduler().runTasks();
    }

    private static SceneGroup group() {
        var group = new SceneGroup();
        group.id = 7;
        return group;
    }
}
