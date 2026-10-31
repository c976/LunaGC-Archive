package emu.grasscutter.game.dailytask;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScriptSceneData;
import emu.grasscutter.data.excels.DailyTaskData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.managers.blossom.BlossomManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.*;

import it.unimi.dsi.fastutil.ints.*;

import sun.misc.Unsafe;

import java.util.*;
import java.util.concurrent.Future;

/** Real manager and scene lifecycle with in-memory entity, persistence and transport boundaries. */
@org.junit.jupiter.api.extension.ExtendWith(emu.grasscutter.ServerResourceFixture.class)
public final class DailyTaskGroupLifecycleTest {
    @org.junit.jupiter.api.Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void preservesEncountersAcrossProximityChangesAndRestarts() throws Exception {
        var maps =
                List.<Map>of(
                        GameData.getDailyTaskDataMap(),
                        GameData.getScriptSceneDataMap(),
                        GameData.getMonsterDataMap(),
                        GameData.getRewardPreviewDataMap());
        var backups = maps.stream().map(HashMap::new).toList();
        var cacheField = DailyTaskManager.class.getDeclaredField("GROUP_RESOURCE_SUPPORT_CACHE");
        cacheField.setAccessible(true);
        var cache = (Map) cacheField.get(null);
        var oldCache = new HashMap(cache);
        var serverField = emu.grasscutter.Grasscutter.class.getDeclaredField("gameServer");
        serverField.setAccessible(true);
        var oldServer = serverField.get(null);
        try {
            checks = 0;
            nextId = 500;
            main(new String[0]);
        } finally {
            for (int i = 0; i < maps.size(); i++) {
                maps.get(i).clear();
                maps.get(i).putAll(backups.get(i));
            }
            cache.clear();
            cache.putAll(oldCache);
            serverField.set(null, oldServer);
        }
    }

    @org.junit.jupiter.api.Test
    void periodicPollingIsThrottledPerSceneButExplicitLoadsAreImmediate() throws Exception {
        var owner = alloc(P.class);
        owner.uid = 10001;
        var world = alloc(W.class);
        world.owner = owner;
        owner.setWorld(world);
        var scene = alloc(S.class);
        scene.world = world;
        scene.scripts =
                alloc(C.class); // Not initialized: this test measures polling, not resources.
        var manager = new M(owner);
        owner.manager = manager;
        DailyTaskGroupLifecycle.tick(scene, 0);
        DailyTaskGroupLifecycle.tick(scene, 200_000_000L);
        DailyTaskGroupLifecycle.tick(scene, 999_999_999L);
        org.junit.jupiter.api.Assertions.assertEquals(1, manager.polls);
        manager.loadActiveGroups(scene);
        org.junit.jupiter.api.Assertions.assertEquals(2, manager.polls);
        DailyTaskGroupLifecycle.tick(scene, 1_000_000_000L);
        org.junit.jupiter.api.Assertions.assertEquals(3, manager.polls);
        var second = alloc(S.class);
        second.world = world;
        second.scripts = scene.scripts;
        DailyTaskGroupLifecycle.tick(second, 1_000_000_001L);
        org.junit.jupiter.api.Assertions.assertEquals(4, manager.polls);
        owner.manager = new M(owner);
        DailyTaskGroupLifecycle.tick(scene, 1_000_000_002L);
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                owner.manager.polls,
                "a new host manager does not inherit the previous lifecycle's throttle");
    }

    static int checks;
    static int nextId = 500;

    static void check(boolean okay, String msg) {
        checks++;
        if (!okay) throw new AssertionError(msg);
    }

    static void set(Class<?> c, Object target, String name, Object value) throws Exception {
        var f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static <T> T alloc(Class<T> c) throws Exception {
        var f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return c.cast(((Unsafe) f.get(null)).allocateInstance(c));
    }

    public static void main(String[] args) throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var gson = new Gson();
        var owner = alloc(P.class);
        owner.uid = 10001;
        set(Player.class, owner, "id", 10001);
        owner.position = new Position(1141.7009f, 349.2042f, 1572.0726f);
        owner.setSceneLoadState(Player.SceneLoadState.LOADED);
        var scene = alloc(S.class);
        scene.players = new ArrayList<>(List.of(owner));
        scene.entities = new LinkedHashMap<>();
        scene.groups = new LinkedHashSet<>();
        scene.packets = new ArrayList<>();
        var world = alloc(W.class);
        world.owner = owner;
        scene.world = world;
        owner.scene = scene;
        owner.setWorld(world);
        var scripts = alloc(C.class);
        set(SceneScriptManager.class, scripts, "monsterTides", new ArrayList<>());
        scripts.initialized = true;
        scripts.scene = scene;
        scripts.groups = new LinkedHashMap<>();
        scripts.instances = new HashMap<>();
        scripts.triggers = new HashMap<>();
        scripts.counts = new HashMap<>();
        scripts.blocks = new HashMap<>();
        scripts.perBlock = new HashMap<>();
        scene.scripts = scripts;
        set(Scene.class, scene, "loadedGroups", scene.groups);
        set(Scene.class, scene, "entities", scene.entities);
        set(Scene.class, scene, "players", scene.players);
        set(Scene.class, scene, "scriptManager", scripts);
        set(Scene.class, scene, "blossomManager", new BlossomManager(scene));
        var manager = new M(owner);
        owner.manager = manager;
        set(DailyTaskManager.class, manager, "lastGenerationDate", 20261016);
        var row =
                gson.fromJson(
                        "{\"id\":10102,\"type\":\"DAILY_TASK_SCENE\",\"finishType\":\"DAILY_FINISH_MONSTER_NUM\",\"finishProgress\":8,\"taskRewardId\":1,\"newGroupVec\":[133001306],\"centerPosition\":\"Event_10102\",\"enterDistance\":40,\"exitDistance\":60}",
                        DailyTaskData.class);
        GameData.getDailyTaskDataMap().put(10102, row);
        manager.getDailyTasks()
                .add(gson.fromJson("{\"taskId\":10102,\"finishProgress\":8}", DailyTask.class));
        var f = DailyTaskManager.class.getDeclaredField("GROUP_RESOURCE_SUPPORT_CACHE");
        f.setAccessible(true);
        ((Map<Integer, Boolean>) f.get(null)).put(133001306, true);
        GameData.getScriptSceneDataMap()
                .put(
                        "flat.luas.scenes.full_globals.lua.json",
                        gson.fromJson(
                                "{\"scriptObjectList\":{\"3/scene3_dummy_points.lua\":{\"dummy_points\":{\"Event_10102.pos\":[1878.097,196.546,-1546.443]}}}}",
                                ScriptSceneData.class));
        var group = SceneGroup.of(133001306);
        group.block_id = 3001;
        group.dynamic_load = true;
        group.triggers = new LinkedHashMap<>();
        group.regions = new LinkedHashMap<>();
        group.gadgets = new LinkedHashMap<>();
        group.npcs = new LinkedHashMap<>();
        group.monsters = new LinkedHashMap<>();
        group.variables = new ArrayList<>();
        group.suites = List.of(new SceneSuite());
        group.init_config = new SceneInitConfig();
        group.init_config.suite = 1;
        for (int id : new int[] {1311, 1312, 1317}) {
            var monster = new SceneMonster();
            monster.monster_id = 21010201;
            monster.config_id = id;
            monster.pos = new Position(1881.463f, 195.773f, -1548.085f);
            monster.rot = new Position();
            group.monsters.put(id, monster);
        }
        var trigger = new SceneTrigger();
        trigger.setName("WAVE");
        trigger.setEvent(1);
        trigger.setCurrentGroup(group);
        group.triggers.put("WAVE", trigger);
        var block = new SceneBlock();
        block.id = 3001;
        block.groups = new HashMap<>(Map.of(group.id, group));
        scripts.blocks.put(3001, block);
        scripts.groups.put(group.id, group);
        check(
                manager.loadActiveGroups(scene) == 0
                        && scene.groups.isEmpty()
                        && scene.entities.isEmpty(),
                "far real manager must not activate remote groups");
        check(
                manager.saves == 0 && manager.getDailyTasks().get(0).getProgress() == 0,
                "far activation does not save/generate/reward or increment");
        var center = new Position(1878.097f, 196.546f, -1546.443f);
        owner.position = center.clone().addY(41);
        check(
                manager.loadActiveGroups(scene) == 0,
                "documented XYZ policy does not ignore vertical separation");
        owner.position = center.clone().addX(40);
        DailyTaskGroupLifecycle.tick(scene);
        check(
                scripts.refreshes == 1,
                "periodic callback uses authoritative host manager for approach activation");
        check(
                manager.loadActiveGroups(scene) == 1
                        && scripts.refreshes == 1
                        && scene.entities.size() == 2,
                "entry at local-policy inclusive40 loads once through real scene loader");
        manager.loadActiveGroups(scene);
        check(
                scripts.refreshes == 1 && scene.entities.size() == 2,
                "repeated nearby calls do not duplicate group/suite");
        var quietInstance = scripts.instances.get(group.id);
        String quietSnapshot = quietInstance.getDailyTaskSnapshot();
        var quietMonster = scene.entities.values().iterator().next();
        quietMonster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 91);
        manager.loadActiveGroups(scene);
        check(
                quietSnapshot.equals(quietInstance.getDailyTaskSnapshot()),
                "ordinary nearby update does not serialize changed HP without a"
                        + " progress/save/suspend boundary");
        quietMonster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        manager.loadActiveGroups(scene);
        owner.position = center.clone().addX(60);
        manager.loadActiveGroups(scene);
        check(
                scene.entities.size() == 2 && scripts.unloads == 0,
                "inclusive60 retains active group");
        var task = manager.getDailyTasks().get(0);
        task.addProgress(1);
        var first = scene.entities.values().iterator().next();
        scene.entities.remove(
                first.getId()); // Simulated already-processed combat removal; no reward/control
        // commands.
        var survivor = (E) scene.entities.values().iterator().next();
        survivor.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 37);
        var instance = scripts.instances.get(group.id);
        instance.getCachedVariables().put("wave", 7);
        trigger.setTrigger_count(3);
        scripts.counts.put("WAVE", 1);
        owner.position = center.clone().addX(61);
        survivor.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        manager.loadActiveGroups(scene);
        check(
                scene.groups.contains(group) && scripts.unloads == 0,
                "zero-HP dying population is treated as settling, not a live wave to suspend");
        DailyTaskGroupLifecycle.captureAll(scene);
        check(
                gson.fromJson(
                                instance.getDailyTaskSnapshot(),
                                DailyTaskGroupLifecycle.Snapshot.class)
                        .entities
                        .isEmpty(),
                "dying zero-HP monster can never be serialized and revived as an alive zombie");
        survivor.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 37);
        set(SceneScriptManager.class, scripts, "executingEvents", 1);
        manager.loadActiveGroups(scene);
        check(
                scene.entities.size() == 1 && scripts.unloads == 0,
                "executing Lua event is not interrupted by proximity suspension");
        set(SceneScriptManager.class, scripts, "executingEvents", 0);
        manager.loadActiveGroups(scene);
        check(
                scene.groups.isEmpty() && scene.entities.isEmpty() && scripts.unloads == 1,
                "exit beyond60 uses actual nonlethal Scene.unloadGroup once");
        check(
                scene.deaths == 0 && task.getProgress() == 1,
                "exit never calls onDeath or manufactures progress");
        manager.loadActiveGroups(scene);
        check(scripts.unloads == 1, "repeated far tick does not unload again");
        check(
                !DailyTaskGroupLifecycle.isActive(manager, scene, 10102),
                "delayed recovery eligibility false while dormant");
        check(
                !DailyTaskGroupLifecycle.maySpawn(scripts, group.id),
                "late Lua spawn into owned dormant group rejected before resource creation");
        var guardScripts = alloc(GuardScripts.class);
        guardScripts.scene = scene;
        check(
                guardScripts.createMonster(group.id, 3001, group.monsters.get(1311)) == null,
                "actual staged monster creation prefix rejects dormant callback");
        check(
                guardScripts.createGadget(group.id, 3001, null, 0) == null,
                "actual staged gadget creation prefix rejects dormant callback before dereferencing"
                        + " metadata");
        check(
                guardScripts.createMonsterByConfigIdByPos(group, 1311, center, new Position())
                        == null,
                "actual staged positioned-monster prefix rejects dormant callback");
        check(
                DailyTaskGroupLifecycle.maySpawn(scripts, 133999999),
                "unrelated quest/Lua group spawning remains unchanged");
        check(
                !DailyTaskGroupLifecycle.unregisterOwned(scene, group.id, new M(owner)),
                "a different manager instance cannot unload the host encounter");
        owner.position = center.clone().addX(50);
        manager.loadActiveGroups(scene);
        check(scene.entities.isEmpty(), "dormant group does not enter in hysteresis band");
        // Persist/rehydrate the actual group instance DTO, including versioned candidate snapshot.
        var saved = gson.toJson(instance);
        var restored = gson.fromJson(saved, SceneGroupInstance.class);
        restored.setLuaGroup(group);
        scripts.instances.put(group.id, restored);
        owner.position = center.clone();
        manager.loadActiveGroups(scene);
        check(
                scripts.refreshes == 1 && scene.entities.size() == 1,
                "exit/reentry/reloaded instance restores only survivor, never initial killed"
                        + " population");
        check(
                scene.entities.values().iterator().next().getConfigId() == survivor.getConfigId()
                        && scene.entities
                                        .values()
                                        .iterator()
                                        .next()
                                        .getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                                == 37,
                "survivor config and exact HP preserved");
        check(
                restored.getCachedVariables().get("wave") == 7 && task.getProgress() == 1,
                "cached Lua variable and saved board progress preserved");
        check(
                scripts.counts.get("WAVE") == 1,
                "partially consumed multi-count trigger does not reset on suspension/restoration");
        scene.entities
                .values()
                .iterator()
                .next()
                .setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 36);
        DailyTaskGroupLifecycle.captureAll(scene);
        var preSave =
                gson.fromJson(
                        restored.getDailyTaskSnapshot(), DailyTaskGroupLifecycle.Snapshot.class);
        check(
                preSave.entities.get(0).hp == 36 && manager.saves == 0,
                "existing scene-save boundary captures latest HP without quota/account save");
        check(scripts.groupLoadEvents == 1, "restoring encounter does not replay group-load event");
        check(
                DailyTaskGroupLifecycle.isActive(manager, scene, 10102),
                "near active encounter allows ordinary delayed recovery");
        check(
                DailyTaskGroupLifecycle.maySpawn(scripts, group.id),
                "near owned Lua wave creation remains allowed");
        GameData.getMonsterDataMap()
                .put(
                        21010201,
                        gson.fromJson(
                                "{\"id\":21010201}",
                                emu.grasscutter.data.excels.monster.MonsterData.class));
        scene.creationSentinel = true;
        try {
            guardScripts.createMonster(group.id, 3001, group.monsters.get(1311));
            throw new AssertionError("original creation path not reached");
        } catch (CreationReached expected) {
            check(true, "near actual creation guard admits unchanged original body");
        }
        scene.creationSentinel = false;
        var settlingPopulation = new LinkedHashMap<>(scene.entities);
        scene.entities.clear();
        owner.position = center.clone().addX(61);
        int beforeSettle = scripts.unloads;
        manager.loadActiveGroups(scene);
        check(
                scene.groups.contains(group) && scripts.unloads == beforeSettle,
                "empty unfinished wave definition is not reset during an in-flight transition");
        check(
                DailyTaskGroupLifecycle.maySpawn(scripts, group.id),
                "first settling-wave creation is allowed before nonlethal suspension");
        scene.entities.putAll(settlingPopulation);
        check(
                DailyTaskGroupLifecycle.maySpawn(scripts, group.id),
                "one Lua settling batch is not truncated after its first monster");
        manager.loadActiveGroups(scene);
        check(
                scene.entities.isEmpty() && !DailyTaskGroupLifecycle.maySpawn(scripts, group.id),
                "settled out-of-range population suspends once and rejects late callbacks");
        owner.position = center.clone();
        manager.loadActiveGroups(scene);
        var repairPopulation = new LinkedHashMap<>(scene.entities);
        scene.entities.clear();
        int requested = manager.recoveryRequests;
        manager.loadActiveGroups(scene);
        manager.loadActiveGroups(scene);
        check(
                manager.recoveryRequests == requested + 1,
                "near empty encounter requests existing bounded repair once, not every tick");
        scene.entities.putAll(repairPopulation);
        manager.loadActiveGroups(scene);
        scene.players.clear();
        manager.loadActiveGroups(scene);
        check(scene.entities.isEmpty(), "zero visitors triggers nonlethal suspension");
        scene.players.add(owner);
        scripts.initialized = false;
        manager.loadActiveGroups(scene);
        check(scene.entities.isEmpty(), "uninitialized script manager cannot activate groups");
        scripts.initialized = true;
        manager.loadActiveGroups(scene);
        check(scene.entities.size() == 1, "reentry after script initialization preserves survivor");
        owner.position = center.clone().addX(61);
        manager.loadActiveGroups(scene);
        restored.setDailyTaskSnapshot("{");
        owner.position = center.clone();
        int priorRefresh = scripts.refreshes;
        manager.loadActiveGroups(scene);
        check(
                scene.entities.size() == 2
                        && scripts.refreshes == priorRefresh + 1
                        && task.getProgress() == 1,
                "malformed stored snapshot refreshes a playable encounter without changing task"
                        + " progress");
        String recovered = restored.getDailyTaskSnapshot();
        manager.loadActiveGroups(scene);
        check(
                scene.entities.size() == 2
                        && scripts.refreshes == priorRefresh + 1
                        && recovered.equals(restored.getDailyTaskSnapshot()),
                "corrupt snapshot is replaced once, not retried on every nearby update");
        // A direct-created second-wave config not in the initial population must survive
        // suspension.
        scene.entities.clear();
        var wave = scripts.createMonster(group.id, 3001, group.monsters.get(1317));
        scene.entities.put(wave.getId(), wave);
        scripts.triggers.get(1).clear();
        owner.position = center.clone().addX(61);
        manager.loadActiveGroups(scene);
        owner.position = center.clone();
        manager.loadActiveGroups(scene);
        check(
                scene.entities.size() == 1
                        && scene.entities.values().iterator().next().getConfigId() == 1317,
                "direct-created later wave retained without resetting initial suite population");
        check(
                scripts.triggers.get(1).isEmpty(),
                "consumed wave trigger is not replayed on restore");
        var guest = alloc(P.class);
        guest.uid = 10002;
        set(Player.class, guest, "id", 10002);
        guest.position = center.clone();
        guest.scene = scene;
        guest.setWorld(world);
        guest.setSceneLoadState(Player.SceneLoadState.LOADED);
        guest.manager = new M(guest);
        scene.players.add(guest);
        owner.position = center.clone().addX(1000);
        manager.loadActiveGroups(scene);
        check(
                !scene.entities.isEmpty(),
                "near visitor retains authoritative host encounter while owner is far");
        check(
                guest.manager.loadActiveGroups(scene) == 0,
                "guest manager cannot activate its own board in host world");
        scene.players.remove(guest);
        manager.loadActiveGroups(scene);
        check(
                scene.entities.isEmpty(),
                "last nearby visitor departure permits nonlethal suspension");
        owner.position = center.clone();
        manager.loadActiveGroups(scene);
        task.finish();
        manager.loadActiveGroups(scene);
        check(
                scene.entities.isEmpty() && manager.saves == 0,
                "completed task cannot reload and proximity never saves quota/board");
        check(
                DailyTaskGroupLifecycle.center(
                                gson.fromJson(
                                        "{\"centerPosition\":\"missing\",\"enterDistance\":40,\"exitDistance\":60}",
                                        DailyTaskData.class))
                        == null,
                "missing center never becomes origin or monster position");
        check(
                DailyTaskGroupLifecycle.center(
                                gson.fromJson(
                                        "{\"centerPosition\":\"Event_10102\",\"enterDistance\":60,\"exitDistance\":40}",
                                        DailyTaskData.class))
                        == null,
                "invalid hysteresis resource rejected");
        // Legitimate day rollover gets a fresh encounter, not a previous day's persisted wave.
        manager.getDailyTasks().clear();
        var combatTask =
                gson.fromJson(
                        "{\"taskId\":10102,\"finishProgress\":8,\"rewardId\":991001}",
                        DailyTask.class);
        manager.getDailyTasks().add(combatTask);
        set(DailyTaskManager.class, manager, "lastGenerationDate", 20261017);
        owner.position = center.clone();
        int initialRefreshes = scripts.refreshes;
        manager.loadActiveGroups(scene);
        check(
                scene.entities.size() == 2
                        && scripts.refreshes == initialRefreshes + 1
                        && combatTask.getProgress() == 0,
                "new quota date never reuses prior-date wave snapshot");
        var otherScene = alloc(S.class);
        otherScene.world = world;
        owner.scene = otherScene;
        check(
                !DailyTaskGroupLifecycle.maySpawn(scripts, group.id),
                "stale old-scene Lua callback is denied after visitor changes scene");
        manager.loadActiveGroups(scene);
        check(
                scene.entities.isEmpty()
                        && scene.getDailyTaskGroups() != otherScene.getDailyTaskGroups(),
                "scene transition suspends old population without sharing lifecycle state");
        owner.scene = scene;
        owner.position = center.clone();
        manager.loadActiveGroups(scene);
        var otherWorld = alloc(W.class);
        otherWorld.owner = owner;
        owner.setWorld(otherWorld);
        check(
                !DailyTaskGroupLifecycle.maySpawn(scripts, group.id)
                        && manager.loadActiveGroups(scene) == 0,
                "retired world's manager cannot spawn or activate after ownership-world"
                        + " transition");
        owner.setWorld(world);
        // Actual QuestManager scene-suite lookup, without constructors/database.
        var q = alloc(emu.grasscutter.game.quest.QuestManager.class);
        var main = alloc(emu.grasscutter.game.quest.GameMainQuest.class);
        set(
                emu.grasscutter.game.quest.GameMainQuest.class,
                main,
                "state",
                emu.grasscutter.game.quest.enums.ParentQuestState.PARENT_QUEST_STATE_NONE);
        set(
                emu.grasscutter.game.quest.GameMainQuest.class,
                main,
                "questGroupSuites",
                new ArrayList<>(
                        List.of(
                                emu.grasscutter.game.quest.QuestGroupSuite.of()
                                        .scene(3)
                                        .group(group.id)
                                        .suite(1)
                                        .build())));
        var mains = new Int2ObjectOpenHashMap<emu.grasscutter.game.quest.GameMainQuest>();
        mains.put(99, main);
        set(emu.grasscutter.game.quest.QuestManager.class, q, "mainQuests", mains);
        owner.quests = q;
        owner.position = center.clone().addX(61);
        int beforeQuest = scripts.unloads;
        manager.loadActiveGroups(scene);
        check(
                scene.entities.size() == 2 && scripts.unloads == beforeQuest,
                "quest-owned overlapping group is never proximity-unloaded");
        check(
                !DailyTaskGroupLifecycle.unregisterOwned(scene, group.id, manager),
                "commission reset/finish cleanup cannot unregister quest-owned group");
        owner.quests = null;
        scene.unregisterDynamicGroup(group.id);
        owner.position = center.clone();
        manager.loadActiveGroups(scene);
        var server = alloc(emu.grasscutter.server.game.GameServer.class);
        var scheduler = new emu.grasscutter.server.scheduler.ServerTaskScheduler();
        set(emu.grasscutter.server.game.GameServer.class, server, "scheduler", scheduler);
        set(emu.grasscutter.Grasscutter.class, null, "gameServer", server);
        owner.inventory = alloc(RInventory.class);
        owner.saves = 0;
        manager.saves = 0;
        GameData.getRewardPreviewDataMap()
                .put(
                        991001,
                        gson.fromJson(
                                "{\"id\":991001,\"previewItemsArray\":[{\"id\":201,\"count\":10}]}",
                                emu.grasscutter.data.excels.RewardPreviewData.class));
        scene.entities.remove(scene.entities.keySet().iterator().next());
        manager.onMonsterDeath(scene, group.id, owner.position);
        check(
                combatTask.getProgress() == 1 && manager.saves == 1,
                "actual normal death-notification body advances eligible commission");
        var afterProgress =
                gson.fromJson(
                        scripts.instances.get(group.id).getDailyTaskSnapshot(),
                        DailyTaskGroupLifecycle.Snapshot.class);
        check(
                afterProgress.entities.size() == 1,
                "post-Lua normal progress updates encounter snapshot immediately, not only on a"
                        + " later tick");
        owner.position = center.clone().addX(61);
        manager.loadActiveGroups(scene);
        int refreshBeforeCallback = scripts.refreshes;
        scheduler.getTask(0).run();
        check(
                scene.entities.isEmpty()
                        && scripts.refreshes == refreshBeforeCallback
                        && combatTask.getProgress() == 1,
                "actual queued two-second recovery callback cannot recreate dormant remote wave");
        owner.position = center.clone();
        manager.loadActiveGroups(scene);
        for (int kill = 1; kill < 8; kill++)
            manager.onMonsterDeath(scene, group.id, owner.position);
        check(
                combatTask.isFinished() && combatTask.getProgress() == 8 && manager.saves == 8,
                "actual normal death-notification chain reaches capped8/8");
        check(
                owner.inventory.receipts == 1 && owner.inventory.count == 10 && owner.saves == 1,
                "actual preserved preview-reward body pays recording inventory once and persists"
                        + " virtual-reward owner once");
        manager.onMonsterDeath(scene, group.id, owner.position);
        manager.onMonsterDeath(scene, 999, owner.position);
        check(
                manager.saves == 8
                        && owner.inventory.receipts == 1
                        && combatTask.getProgress() == 8,
                "duplicate/foreign group deaths cannot exceed cap or pay twice");
        check(
                scene.entities.isEmpty() && scene.groups.isEmpty(),
                "normal completed task unloads only its owned group nonlethally");
        var unrelated = SceneGroup.of(133001307);
        scene.groups.add(unrelated);
        DailyTaskGroupLifecycle.tick(scene);
        check(
                scene.groups.contains(unrelated)
                        && !DailyTaskGroupLifecycle.unregisterOwned(scene, unrelated.id, manager),
                "unrelated preloaded dynamic group cannot be adopted or removed");
        var dormant = scripts.instances.get(group.id);
        dormant.setDailyTaskSnapshot("{\"stale\":true}");
        DailyTaskGroupLifecycle.discardDormant(scene, group.id, manager);
        check(
                dormant.getDailyTaskSnapshot() == null,
                "same-day reroll discards an unloaded encounter's saved snapshot");
        scene.groups.add(group);
        dormant.setDailyTaskSnapshot("{\"live\":true}");
        DailyTaskGroupLifecycle.discardDormant(scene, group.id, manager);
        check(
                "{\"live\":true}".equals(dormant.getDailyTaskSnapshot()),
                "dormant discard never touches a loaded encounter");
        scene.groups.remove(group);
        System.out.println(
                "PASS "
                        + checks
                        + " production lifecycle assertions with in-memory"
                        + " entity/persistence/transport boundaries");
    }

    static final class M extends DailyTaskManager {
        int saves, recoveryRequests, polls;

        @Override
        public synchronized int loadActiveGroups(Scene scene) {
            polls++;
            return super.loadActiveGroups(scene);
        }

        M(Player p) {
            super(p);
        }

        @Override
        public void save() {
            saves++;
        }

        @Override
        public synchronized void scheduleActiveWaveRecovery(Scene s, int task) {
            recoveryRequests++;
        }
    }

    static final class P extends Player {
        int uid, saves;
        Position position;
        S scene;
        M manager;
        RInventory inventory;
        emu.grasscutter.game.quest.QuestManager quests;

        @Override
        public boolean isOnline() {
            return false;
        }

        @Override
        public void save() {
            saves++;
        }

        @Override
        public emu.grasscutter.game.inventory.Inventory getInventory() {
            return inventory;
        }

        @Override
        public int getUid() {
            return uid;
        }

        @Override
        public int getLevel() {
            return 12;
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

        @Override
        public emu.grasscutter.game.quest.QuestManager getQuestManager() {
            return quests;
        }
    }

    static final class W extends World {
        P owner;

        W() {
            super((Player) null);
        }

        @Override
        public Player getHost() {
            return owner;
        }
    }

    static final class S extends Scene {
        W world;
        C scripts;
        boolean creationSentinel;
        List<Player> players;
        Map<Integer, GameEntity> entities;
        Set<SceneGroup> groups;
        List<BasePacket> packets;
        int deaths;

        S() {
            super(null, null);
        }

        @Override
        public int getId() {
            return 3;
        }

        @Override
        public SceneType getSceneType() {
            return SceneType.SCENE_WORLD;
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
            return players;
        }

        @Override
        public Map<Integer, GameEntity> getEntities() {
            return entities;
        }

        @Override
        public Set<SceneGroup> getLoadedGroups() {
            return groups;
        }

        @Override
        public void broadcastPacket(BasePacket p) {
            packets.add(p);
        }

        @Override
        public int getLevelForMonster(int config, int level) {
            if (creationSentinel) throw new CreationReached();
            return 1;
        }

        @Override
        public boolean loadBlock(SceneBlock b) {
            return true;
        }

        @Override
        public void onLoadBlock(SceneBlock b, List<Player> p) {
            scripts.perBlock.put(b.id, new HashSet<>());
        }
    }

    static final class C extends SceneScriptManager {
        S scene;
        boolean initialized;
        Map<Integer, SceneGroup> groups;
        Map<Integer, SceneGroupInstance> instances;
        Map<Integer, Set<SceneTrigger>> triggers;
        Map<String, Integer> counts;
        Map<Integer, SceneBlock> blocks;
        Map<Integer, Set<SceneGroup>> perBlock;
        int refreshes, unloads, groupLoadEvents;
        RuntimeException refreshFailure;

        C() {
            super(null);
        }

        @Override
        public Map<Integer, SceneGroupInstance> getCachedGroupInstances() {
            return instances;
        }

        @Override
        public boolean isInit() {
            return initialized;
        }

        @Override
        public Scene getScene() {
            return scene;
        }

        @Override
        public SceneGroup getGroupById(int id) {
            return groups.get(id);
        }

        @Override
        public SceneGroupInstance getGroupInstanceById(int id) {
            return instances.get(id);
        }

        @Override
        public SceneGroupInstance getCachedGroupInstanceById(int id) {
            return instances.get(id);
        }

        @Override
        public Map<Integer, SceneBlock> getBlocks() {
            return blocks;
        }

        @Override
        public Map<Integer, Set<SceneGroup>> getLoadedGroupSetPerBlock() {
            return perBlock;
        }

        @Override
        public void loadGroupFromScript(SceneGroup g) {
            instances.computeIfAbsent(g.id, id -> new SceneGroupInstance(g, scene.world.owner));
        }

        @Override
        public int refreshGroup(SceneGroupInstance i, int suite, boolean exclude) {
            refreshes++;
            if (refreshFailure != null) throw refreshFailure;
            i.setActiveSuiteId(1);
            for (int cfg : new int[] {1311, 1312}) {
                var e = createMonster(i.getGroupId(), 3001, i.getLuaGroup().monsters.get(cfg));
                scene.entities.put(e.getId(), e);
            }
            registerTrigger(i.getLuaGroup().triggers.get("WAVE"));
            return 1;
        }

        @Override
        public EntityMonster createMonster(int group, int block, SceneMonster meta) {
            try {
                var e = alloc(E.class);
                e.scene = scene;
                e.id = nextId++;
                e.position = meta.pos.clone();
                e.rotation = new Position();
                e.props = new Int2FloatOpenHashMap();
                e.setGroupId(group);
                e.setBlockId(block);
                e.setConfigId(meta.config_id);
                e.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
                var spawns = getScriptMonsterSpawnService();
                if (spawns != null && !isRestoringMonster())
                    spawns.onMonsterCreatedListener.forEach(listener -> listener.onNotify(e));
                return e;
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        }

        @Override
        public void addEntity(GameEntity entity) {
            scene.entities.put(entity.getId(), entity);
        }

        @Override
        public Set<SceneTrigger> getTriggersByEvent(int event) {
            return triggers.computeIfAbsent(event, id -> new HashSet<>());
        }

        @Override
        public void registerTrigger(SceneTrigger t) {
            getTriggersByEvent(t.getEvent()).add(t);
            counts.put(t.getName(), 0);
        }

        @Override
        public void deregisterTrigger(SceneTrigger t) {
            getTriggersByEvent(t.getEvent()).remove(t);
        }

        @Override
        public int getTriggerInvocationCount(String name) {
            return counts.getOrDefault(name, 0);
        }

        @Override
        public void restoreTriggerInvocation(SceneTrigger trigger, int count) {
            registerTrigger(trigger);
            counts.put(trigger.getName(), count);
        }

        @Override
        public Set<Integer> getRegisteredRegionConfigs(int group) {
            return Set.of();
        }

        @Override
        public void unregisterGroup(SceneGroup g) {
            unloads++;
        }

        @Override
        public Future<?> callEvent(ScriptArgs a) {
            groupLoadEvents++;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    static final class CreationReached extends RuntimeException {}

    static final class GuardScripts extends SceneScriptManager {
        S scene;

        GuardScripts() {
            super(null);
        }

        @Override
        public Scene getScene() {
            return scene;
        }
    }

    static final class RInventory extends emu.grasscutter.game.inventory.Inventory {
        int receipts, count;

        RInventory() {
            super(null);
        }

        @Override
        public void addItemParamDatas(
                Collection<emu.grasscutter.data.common.ItemParamData> items,
                emu.grasscutter.game.props.ActionReason reason) {
            receipts++;
            for (var i : items) count += i.getCount();
        }
    }

    static final class E extends EntityMonster {
        S scene;
        int id;
        Position position, rotation;
        Int2FloatOpenHashMap props;

        E() {
            super(null, null, null, null, 1);
        }

        @Override
        public Scene getScene() {
            return scene;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public Position getPosition() {
            return position;
        }

        @Override
        public Position getRotation() {
            return rotation;
        }

        @Override
        public Int2FloatOpenHashMap getFightProperties() {
            return props;
        }

        @Override
        public emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo toProto() {
            return emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo.newBuilder()
                    .setEntityId(id)
                    .build();
        }

        @Override
        public void onDeath(int killer) {
            scene.deaths++;
            throw new AssertionError("suspension must never kill");
        }
    }
}
