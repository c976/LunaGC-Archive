package emu.grasscutter.scripts;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;
import static emu.grasscutter.scripts.constants.EventType.EVENT_TIMER_EVENT;

import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.server.Grid;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.platform.BaseRoute;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.VisionTypeOuterClass;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.service.*;
import emu.grasscutter.server.event.game.SceneMetaLoadEvent;
import emu.grasscutter.server.packet.send.PacketGroupSuiteNotify;
import emu.grasscutter.utils.*;
import io.netty.util.concurrent.FastThreadLocalThread;
import it.unimi.dsi.fastutil.ints.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.*;
import kotlin.Pair;
import lombok.val;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public class SceneScriptManager {
    private final Scene scene;
    private final SealBattleManager sealBattleManager;
    private final Map<String, Integer> variables;
    /** Mutable fallbacks when a group instance is not cached yet (tower stage/TPL_TIME writes). */
    private final Map<Integer, Map<String, Integer>> orphanGroupVariables = new ConcurrentHashMap<>();
    private SceneMeta meta;
    private volatile boolean isInit;
    private volatile boolean initAttempted;
    private volatile boolean destroyed;
    private int executingEvents;
    private final SceneScriptInitGate initialization = new SceneScriptInitGate();
    private boolean noCacheGroupGridsToDisk;

    private final Map<String, SceneTimeAxis> timeAxis = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Integer>> pendingCutsceneGroups = new HashMap<>();

    /** current triggers controlled by RefreshGroup */
    private final Map<Integer, Set<SceneTrigger>> currentTriggers;

    private final Set<SceneTrigger> ongoingTriggers;
    private final Map<String, Set<SceneTrigger>> triggersByGroupScene;
    private final Map<Integer, Set<Pair<String, Integer>>> activeGroupTimers;
    private final LiyueCloudDungeonTimers liyueDungeonTimers = new LiyueCloudDungeonTimers(this);
    private CompletableFuture<Void> liyueFireEvents = CompletableFuture.completedFuture(null);
    private final Map<Integer, CompletableFuture<Void>> amberQuestEvents = new HashMap<>();
    private final Map<String, AtomicInteger> triggerInvocations;
    private final Map<Integer, EntityRegion> regions; // <EntityId-Region>
    private final Map<Integer, SceneGroup> sceneGroups;
    private final Map<Integer, SceneGroupInstance> sceneGroupsInstances;
    private final Map<Integer, SceneGroupInstance> cachedSceneGroupsInstances;
    private ScriptMonsterTideService scriptMonsterTideService;
    private final List<ScriptMonsterTideService> monsterTides = new ArrayList<>();
    private boolean restoringMonster;
    private ScriptMonsterSpawnService scriptMonsterSpawnService;
    /** blockid - loaded groupSet */
    private final Map<Integer, Set<SceneGroup>> loadedGroupSetPerBlock;

    private static final Int2ObjectMap<List<Grid>> groupGridsCache =
            Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>()); // scenes init on their own threads
    public static final ExecutorService eventExecutor;

    static {
        eventExecutor =
                new ThreadPoolExecutor(
                        4,
                        4,
                        60,
                        TimeUnit.SECONDS,
                        new LinkedBlockingDeque<>(10000),
                        FastThreadLocalThread::new,
                        new ThreadPoolExecutor.AbortPolicy());
    }

    public SceneScriptManager(Scene scene) {
        this.scene = scene;
        this.sealBattleManager = new SealBattleManager(this);
        this.currentTriggers = new ConcurrentHashMap<>();
        this.ongoingTriggers = ConcurrentHashMap.newKeySet();
        this.triggersByGroupScene = new ConcurrentHashMap<>();
        this.activeGroupTimers = new ConcurrentHashMap<>();
        this.triggerInvocations = new ConcurrentHashMap<>();

        this.regions = new ConcurrentHashMap<>();
        this.variables = new ConcurrentHashMap<>();
        this.sceneGroups = new ConcurrentHashMap<>();
        this.sceneGroupsInstances = new ConcurrentHashMap<>();
        this.cachedSceneGroupsInstances = new ConcurrentHashMap<>();
        this.scriptMonsterSpawnService = new ScriptMonsterSpawnService(this);
        this.loadedGroupSetPerBlock = new ConcurrentHashMap<>();

        // TEMPORARY
        if (this.getScene().getId() < 10
                && !Grasscutter.getConfig().server.game.enableScriptInBigWorld) {
            this.initAttempted = true;
            this.initialization.complete(false);
            return;
        }

        // Create
        new Thread(this::init).start();
    }

    public Scene getScene() {
        return scene;
    }

    /** Used under this monitor before nonlethal commission suspension. */
    public synchronized boolean isExecutingScripts() {
        return executingEvents != 0;
    }

    public int getTriggerInvocationCount(String name) {
        var count = triggerInvocations.get(name);
        return count == null ? 0 : count.get();
    }

    public void restoreTriggerInvocation(SceneTrigger trigger, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative trigger invocation count");
        registerTrigger(trigger);
        triggerInvocations.get(trigger.getName()).set(count);
    }

    public Set<Integer> getRegisteredRegionConfigs(int groupId) {
        Set<Integer> result = new HashSet<>();
        for (var region : regions.values()) {
            if (region.getGroupId() == groupId) result.add(region.getMetaRegion().config_id);
        }
        return result;
    }

    public SealBattleManager getSealBattleManager() {
        return sealBattleManager;
    }

    public SceneConfig getConfig() {
        for (int i = 0; i < 10; ++i) {
            if (this.isInit) {
                return this.meta.config;
            }
            if (this.initAttempted) {
                return null;
            }
            Utils.sleep(100);
        }
        return null;
    }

    public Map<Integer, SceneBlock> getBlocks() {
        // init() leaves meta null when the scene has no script meta on disk, and getConfig()
        // already reports that case gracefully. Iterating blocks must not be the thing that throws.
        return this.meta == null ? Collections.emptyMap() : this.meta.blocks;
    }

    /**
     * Per-group Lua variables. Never return an immutable empty map: tower scripts write {@code
     * stage}/{@code TPL_TIME} across groups, and {@link Collections#emptyMap()} throws on put.
     */
    public Map<String, Integer> getVariables(int group_id) {
        var instance = this.getCachedGroupInstanceById(group_id);
        if (instance == null) {
            instance = this.getGroupInstanceById(group_id);
        }
        if (instance != null) {
            var vars = instance.getCachedVariables();
            if (vars != null) {
                return vars;
            }
            // Morphia can leave the map null on older documents.
            instance.ensureCachedVariables();
            return instance.getCachedVariables();
        }
        // Group not loaded yet — still allow cross-group writes (e.g. stage on 233717005).
        return this.orphanGroupVariables.computeIfAbsent(group_id, id -> new ConcurrentHashMap<>());
    }

    public Set<SceneTrigger> getTriggersByEvent(int eventId) {
        return currentTriggers.computeIfAbsent(eventId, e -> ConcurrentHashMap.newKeySet());
    }

    public int getTriggerCount() {
        return currentTriggers.size();
    }

    public void registerTrigger(List<SceneTrigger> triggers) {
        triggers.forEach(this::registerTrigger);
    }

    public void registerTrigger(SceneTrigger trigger) {
        this.triggerInvocations.put(trigger.getName(), new AtomicInteger(0));
        this.getTriggersByEvent(trigger.getEvent()).add(trigger);
        Grasscutter.getLogger()
                .trace(
                        "Registered trigger {} from group {}", trigger.getName(), trigger.getCurrentGroup().id);
    }

    public void deregisterTrigger(List<SceneTrigger> triggers) {
        triggers.forEach(this::deregisterTrigger);
    }

    public void deregisterTrigger(SceneTrigger trigger) {
        this.getTriggersByEvent(trigger.getEvent()).remove(trigger);
        Grasscutter.getLogger()
                .trace(
                        "deregistered trigger {} from group {}",
                        trigger.getName(),
                        trigger.getCurrentGroup().id);
    }

    public void resetTriggers(int eventId) {
        currentTriggers.put(eventId, ConcurrentHashMap.newKeySet());
    }

    public void resetTriggersForGroupSuite(SceneGroup group, int suiteIndex) {
        Grasscutter.getLogger().debug("reset triggers for group {} suite {}", group.id, suiteIndex);
        var suite = group.getSuiteByIndex(suiteIndex);
        if (suite == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Trying to load null suite Triggers for group {} with suiteindex {}",
                            group.id,
                            suiteIndex);
            return;
        }

        var groupSceneTriggers = triggersByGroupScene.get(group.id + "_" + suiteIndex);
        if (groupSceneTriggers == null) {
            groupSceneTriggers = new HashSet<>();
        }

        if (!groupSceneTriggers.isEmpty()) {
            for (var trigger : groupSceneTriggers) {
                currentTriggers.get(trigger.getEvent()).remove(trigger);
            }
            groupSceneTriggers.clear();
        }

        if (!suite.sceneTriggers.isEmpty()) {
            groupSceneTriggers.addAll(suite.sceneTriggers);
            for (var trigger : groupSceneTriggers) {
                registerTrigger(trigger);
                /*this.currentTriggers.computeIfAbsent(trigger.event, k -> ConcurrentHashMap.newKeySet())
                .add(trigger);*/
            }
        }
        triggersByGroupScene.put(group.id + "_" + suiteIndex, groupSceneTriggers);
    }

    /** Reloading an open-world group is not a request to reset its puzzle progress. */
    public int restoreGroup(SceneGroupInstance instance, int overrideSuite) {
        var group = instance.getLuaGroup();
        int savedSuite = instance.getActiveSuiteId();
        if (getScene() != null && getScene().getSceneType() == SceneType.SCENE_WORLD && overrideSuite == 0
                && group != null && group.suites != null
                && group.getSuiteByIndex(savedSuite) != null) {
            // The previous scene's entities/triggers are gone. Recreate the saved suite,
            // without resetting variables or refresh timestamps. Keep the loader's
            // existing refresh notification so scripts can initialize their runtime hooks.
            addGroupSuite(instance, group.getSuiteByIndex(savedSuite));
            callEvent(new ScriptArgs(instance.getGroupId(), EventType.EVENT_GROUP_REFRESH));
            return savedSuite;
        }
        return refreshGroup(instance, overrideSuite, false);
    }

    public void refreshGroup(SceneGroupInstance groupInstance) {
        if (groupInstance == null || groupInstance.getLuaGroup().suites == null) {
            return;
        }
        // for (int i = 1; i<= group.suites.size();i++){
        // refreshGroup(group, i);
        refreshGroup(
                groupInstance, groupInstance.getActiveSuiteId(), false); // Refresh the last group triggers
        // }
    }

    public int refreshGroup(
            SceneGroupInstance groupInstance, int suiteIndex, boolean excludePrevSuite) {
        return this.refreshGroup(groupInstance, suiteIndex, excludePrevSuite, null);
    }

    public int refreshGroup(
            SceneGroupInstance groupInstance,
            int suiteIndex,
            boolean excludePrevSuite,
            List<GameEntity> entitiesAdded) {
        SceneGroup group = groupInstance.getLuaGroup();
        if (LiyueCloudDungeonSupport.isTimerGroup(getScene(), group.id))
            liyueDungeonTimers.cancelGroup(group.id);
        if (suiteIndex == 0) {
            if (excludePrevSuite) {
                suiteIndex = group.findInitSuiteIndex(groupInstance.getActiveSuiteId());
            } else suiteIndex = group.findInitSuiteIndex(0);
        }
        if (suiteIndex == 0) return 0;

        var suiteData = group.getSuiteByIndex(suiteIndex);
        if (suiteData == null) {
            Grasscutter.getLogger().warn("Group {} suite {} not found", group.id, suiteIndex);
            group.setLoaded(false);
            group.load(this.scene.getId());
            suiteData = group.getSuiteByIndex(suiteIndex);
            if (suiteData == null) {
                return 0;
            }
            Grasscutter.getLogger()
                    .error("Group {} suite {} nvm, I found it. This is BAD", group.id, suiteIndex);
        }

        int prevSuiteIndex = groupInstance.getActiveSuiteId();
        boolean waitForOne = false;
        SceneSuite prevSuiteData = null;
        if (prevSuiteIndex != 0) {
            prevSuiteData = group.getSuiteByIndex(prevSuiteIndex);
            if (prevSuiteData != null) {
                if (prevSuiteData.ban_refresh && !suiteData.ban_refresh) {
                    waitForOne = true;
                }
            }
        }

        if (waitForOne
                && (groupInstance.getTargetSuiteId() == 0
                        || prevSuiteIndex != groupInstance.getTargetSuiteId())) {
            groupInstance.setTargetSuiteId(suiteIndex);
            Grasscutter.getLogger()
                    .debug("Group {} suite {} wating one more refresh", group.id, suiteIndex);
            return 0;
        }

        groupInstance.setTargetSuiteId(0);

        if (prevSuiteData != null) {
            removeGroupSuite(group, prevSuiteData);
        } // Remove old group suite

        this.addGroupSuite(groupInstance, suiteData, entitiesAdded);

        // refreshGroup may be called by a trigger.
        // If that trigger has been refreshed, ensure it does not get
        // deregistered anyway when the trigger completes its invocation.
        for (var triggerSet : currentTriggers.values()) {
            var toSave = new HashSet<SceneTrigger>(triggerSet);
            toSave.retainAll(ongoingTriggers);
            toSave.forEach(t -> t.setPreserved(true));
        }

        // Refesh variables here
        group.variables.forEach(
                variable -> {
                    if (!variable.no_refresh)
                        groupInstance.getCachedVariables().put(variable.name, variable.value);
                });

        groupInstance.setActiveSuiteId(suiteIndex);
        emu.grasscutter.game.quest.LiyueSceneGroupRecovery.onSuiteChanged(getScene(), groupInstance);
        groupInstance.setLastTimeRefreshed(getScene().getWorld().getGameTime());

        // Call EVENT_GROUP_REFRESH for any action trigger waiting for it
        callEvent(new ScriptArgs(groupInstance.getGroupId(), EventType.EVENT_GROUP_REFRESH));

        return suiteIndex;
    }

    public boolean refreshGroupSuite(int groupId, int suiteId) {
        var targetGroupInstance = getGroupInstanceById(groupId);
        if (targetGroupInstance == null) {
            getGroupById(
                    groupId); // Load the group, this ensures an instance is created and the if neccesary
            // unloaded, but the suite data is stored
            targetGroupInstance = getGroupInstanceById(groupId);
            Grasscutter.getLogger()
                    .debug(
                            "Tried to refresh group suite {} in an unloaded and uncached group {} in scene {}.",
                            suiteId,
                            groupId,
                            getScene().getId());
            if (targetGroupInstance == null) return false;
        }
        Grasscutter.getLogger().debug("Refreshing group {} suite {}", groupId, suiteId);
        suiteId =
                refreshGroup(
                        targetGroupInstance,
                        suiteId,
                        false); // If suiteId is zero, the value of suiteId changes
        scene.broadcastPacket(new PacketGroupSuiteNotify(groupId, suiteId));

        return true;
    }

    /**
     * Store only the latest quest-owned scene suite for a (scene, group) pair.
     * A successful reset to suite 0 drops the override entirely.
     */
    static boolean rememberQuestGroupSuite(
            List<QuestGroupSuite> saved, int sceneId, int groupId, int suiteId) {
        if (saved == null || sceneId <= 0 || groupId <= 0 || suiteId < 0) return false;
        synchronized (saved) {
            int matches = 0;
            int oldSuite = -1;
            for (var entry : saved) {
                if (entry != null && entry.getScene() == sceneId && entry.getGroup() == groupId) {
                    matches++;
                    oldSuite = entry.getSuite();
                }
            }
            if ((suiteId == 0 && matches == 0)
                    || (suiteId > 0 && matches == 1 && oldSuite == suiteId)) return false;
            saved.removeIf(
                    entry -> entry != null && entry.getScene() == sceneId && entry.getGroup() == groupId);
            if (suiteId > 0) {
                saved.add(QuestGroupSuite.of().scene(sceneId).group(groupId).suite(suiteId).build());
            }
            return true;
        }
    }

    public boolean refreshGroupSuite(int groupId, int suiteId, GameQuest quest) {
        var result = refreshGroupSuite(groupId, suiteId);
        if (result && quest != null) {
            boolean changed =
                    rememberQuestGroupSuite(
                            quest.getMainQuest().getQuestGroupSuites(),
                            getScene().getId(), groupId, suiteId);
            // The task's beginExec may execute after the initial GameQuest.start save.
            // Persist an applied suite transition so relog cannot replay stale waves.
            if (changed) quest.save();
        }
        return result;
    }

    public boolean refreshGroupMonster(int groupId) {
        var groupInstance = getGroupInstanceById(groupId);
        if (groupInstance == null) {
            Grasscutter.getLogger()
                    .warn(
                            "trying to refesh monster group in unloaded and uncached group {} in scene {}",
                            groupId,
                            getScene().getId());
            return false;
        }

        var group = groupInstance.getLuaGroup();
        var monstersToSpawn =
                group.monsters.values().stream()
                        .filter(
                                m -> {
                                    var entity = scene.getEntityByConfigId(m.config_id, groupId);
                                    return (entity == null
                                            || entity.getGroupId()
                                                    != group
                                                            .id); /*&& !groupInstance.getDeadEntities().contains(entity); */ // TODO: Investigate the usage of deadEntities
                                })
                        .map(mob -> createMonster(group.id, group.block_id, mob))
                        .toList(); // TODO check if it interferes with bigworld or anything else
        this.addEntities(monstersToSpawn);

        return true;
    }

    public EntityRegion getRegionById(int id) {
        return regions.get(id);
    }

    public void registerRegion(EntityRegion region) {
        regions.put(region.getId(), region);
        Grasscutter.getLogger()
                .trace(
                        "Registered region {} from group {}",
                        region.getMetaRegion().config_id,
                        region.getGroupId());
    }

    public void registerRegionInGroupSuite(SceneGroup group, SceneSuite suite) {
        suite.sceneRegions.stream()
                .map(region -> new EntityRegion(this.getScene(), region))
                .forEach(this::registerRegion);
    }

    public synchronized void deregisterRegion(SceneRegion region) {
        this.regions.values().stream()
                .filter(r -> r.getMetaRegion().equals(region))
                .findFirst()
                .ifPresent(entityRegion -> this.regions.remove(entityRegion.getId()));
    }

    public Map<Integer, Set<SceneGroup>> getLoadedGroupSetPerBlock() {
        return loadedGroupSetPerBlock;
    }

    // TODO optimize
    public SceneGroup getGroupById(int groupId) {
        for (var block : getBlocks().values()) {
            this.getScene().loadBlock(block);
            if (block.groups == null) continue;

            var group = block.groups.get(groupId);
            if (group == null) {
                continue;
            }

            if (!this.sceneGroupsInstances.containsKey(groupId)) {
                this.getScene().onLoadGroup(List.of(group));
                this.getScene().onRegisterGroups();
            }
            return group;
        }
        return null;
    }

    /**
     * Looks a group up in the block metadata WITHOUT loading it.
     *
     * <p>getGroupById loads any group it finds that is not already instanced. Calling it from
     * inside the load path therefore re-entered the load for the group being loaded, and every
     * monster and chest in it was created twice.
     */
    private SceneGroup findGroupById(int groupId) {
        for (var block : getBlocks().values()) {
            this.getScene().loadBlock(block);
            if (block.groups == null) continue;

            var group = block.groups.get(groupId);
            if (group != null) return group;
        }
        return null;
    }

    public SceneGroupInstance getGroupInstanceById(int groupId) {
        return sceneGroupsInstances.getOrDefault(groupId, null);
    }

    public Map<Integer, SceneGroupInstance> getCachedGroupInstances() {
        return cachedSceneGroupsInstances;
    }

    /** Reset tower mid-half carry vars so a retry starts the chamber from the upper half again. */
    public void resetTowerStageVariables() {
        java.util.function.Consumer<Map<String, Integer>> reset =
                vars -> {
                    if (vars == null) return;
                    if (vars.containsKey("stage") || vars.containsKey("TPL_TIME")) {
                        vars.put("stage", 0);
                        vars.put("TPL_TIME", 0);
                    }
                };
        for (var instance : sceneGroupsInstances.values()) {
            if (instance != null) reset.accept(instance.getCachedVariables());
        }
        for (var instance : cachedSceneGroupsInstances.values()) {
            if (instance != null) reset.accept(instance.getCachedVariables());
        }
        orphanGroupVariables.values().forEach(reset);
    }

    public SceneGroupInstance getCachedGroupInstanceById(int groupId) {
        var instance = cachedSceneGroupsInstances.getOrDefault(groupId, null);
        if (instance == null) {
            instance = DatabaseHelper.loadGroupInstance(groupId, scene.getWorld().getHost());
            if (instance != null) {
                cachedSceneGroupsInstances.put(groupId, instance);
                this.cachedSceneGroupsInstances.get(groupId).setCached(false);
                this.cachedSceneGroupsInstances.get(groupId).setLuaGroup(findGroupById(groupId));
            }
        }

        return instance;
    }

    private static void addGridPositionToMap(
            Map<GridPosition, Set<Integer>> map, int group_id, int vision_level, Position position) {
        // Convert position to grid position
        GridPosition gridPos;
        int width = Grasscutter.getConfig().server.game.visionOptions[vision_level].gridWidth;
        gridPos =
                new GridPosition((int) (position.getX() / width), (int) (position.getZ() / width), width);

        Set<Integer> groups = map.getOrDefault(gridPos, new HashSet<>());
        groups.add(group_id);
        map.put(gridPos, groups);
    }

    private static int getGadgetVisionLevel(int gadget_id) {
        var gadget = GameData.getGadgetDataMap().get(gadget_id);
        if (gadget == null || gadget.getVisionLevel() == null) return 0;

        var visionOptions = Grasscutter.getConfig().server.game.visionOptions;
        for (int i = 0; i < visionOptions.length; i++)
            if (visionOptions[i].name.compareTo(gadget.getVisionLevel()) == 0) {
                return i;
            }

        return 0;
    }

    private void init() {
        try {
            if (this.destroyed) return;
            var event = new SceneMetaLoadEvent(getScene());
            event.call();
            if (event.isOverride()) {
                noCacheGroupGridsToDisk = true;
            }
            if (!this.destroyed) {
                var loadedMeta = ScriptLoader.getSceneMeta(getScene().getId());
                if (loadedMeta != null) {
                    this.meta = loadedMeta;
                    this.isInit = true;
                }
            }
        } catch (RuntimeException exception) {
            Grasscutter.getLogger().error(
                    "Scene {} script initialization failed", scene.getId(), exception);
        } finally {
            this.initAttempted = true;
            this.initialization.complete(this.isInit && !this.destroyed);
        }
    }

    public List<Grid> getGroupGrids() {
        int sceneId = scene.getId();
        if (groupGridsCache.containsKey(sceneId) && groupGridsCache.get(sceneId) != null) {
            Grasscutter.getLogger().trace("Hit cache for scene {}", sceneId);
            return groupGridsCache.get(sceneId);
        } else {
            var path = FileUtils.getCachePath("scene" + sceneId + "_grid.json");
            if (path.toFile().isFile()
                    && !Grasscutter.config.server.game.cacheSceneEntitiesEveryRun
                    && !noCacheGroupGridsToDisk) {
                try {
                    var groupGrids = JsonUtils.loadToList(path, Grid.class);
                    groupGridsCache.put(sceneId, groupGrids);
                    if (groupGrids != null) return groupGrids;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // otherwise generate the grids
            List<Map<GridPosition, Set<Integer>>> groupPositions = new ArrayList<>();
            for (int i = 0; i < 6; i++) groupPositions.add(new HashMap<>());

            var visionOptions = Grasscutter.config.server.game.visionOptions;
            meta.blocks
                    .values()
                    .forEach(
                            block -> {
                                block.load(sceneId, meta.context);
                                block.groups.values().stream()
                                        .filter(g -> !g.dynamic_load)
                                        .forEach(
                                                group -> {
                                                    group.load(this.scene.getId());

                                                    // Add all entities here
                                                    Set<Integer> vision_levels = new HashSet<>();

                                                    if (group.monsters != null) {
                                                        group
                                                                .monsters
                                                                .values()
                                                                .forEach(
                                                                        m -> {
                                                                            addGridPositionToMap(
                                                                                    groupPositions.get(m.vision_level),
                                                                                    group.id,
                                                                                    m.vision_level,
                                                                                    m.pos);
                                                                            vision_levels.add(m.vision_level);
                                                                        });
                                                    } else {
                                                        Grasscutter.getLogger()
                                                                .error("group.monsters null for group {}", group.id);
                                                    }
                                                    if (group.gadgets != null) {
                                                        group
                                                                .gadgets
                                                                .values()
                                                                .forEach(
                                                                        g -> {
                                                                            int vision_level =
                                                                                    Math.max(
                                                                                            getGadgetVisionLevel(g.gadget_id), g.vision_level);
                                                                            addGridPositionToMap(
                                                                                    groupPositions.get(vision_level),
                                                                                    group.id,
                                                                                    vision_level,
                                                                                    g.pos);
                                                                            vision_levels.add(vision_level);
                                                                        });
                                                    } else {
                                                        Grasscutter.getLogger()
                                                                .error("group.gadgets null for group {}", group.id);
                                                    }

                                                    if (group.npcs != null) {
                                                        group
                                                                .npcs
                                                                .values()
                                                                .forEach(
                                                                        n ->
                                                                                addGridPositionToMap(
                                                                                        groupPositions.get(n.vision_level),
                                                                                        group.id,
                                                                                        n.vision_level,
                                                                                        n.pos));
                                                    } else {
                                                        Grasscutter.getLogger().error("group.npcs null for group {}", group.id);
                                                    }

                                                    if (group.regions != null) {
                                                        group
                                                                .regions
                                                                .values()
                                                                .forEach(
                                                                        r ->
                                                                                addGridPositionToMap(
                                                                                        groupPositions.get(0), group.id, 0, r.pos));
                                                    } else {
                                                        Grasscutter.getLogger()
                                                                .error("group.regions null for group {}", group.id);
                                                    }

                                                    if (group.garbages != null && group.garbages.gadgets != null)
                                                        group.garbages.gadgets.forEach(
                                                                g ->
                                                                        addGridPositionToMap(
                                                                                groupPositions.get(g.vision_level),
                                                                                group.id,
                                                                                g.vision_level,
                                                                                g.pos));

                                                    int max_vision_level = -1;
                                                    if (!vision_levels.isEmpty()) {
                                                        for (int vision_level : vision_levels) {
                                                            if (max_vision_level == -1
                                                                    || visionOptions[max_vision_level].visionRange
                                                                            < visionOptions[vision_level].visionRange)
                                                                max_vision_level = vision_level;
                                                        }
                                                    }
                                                    if (max_vision_level == -1) max_vision_level = 0;

                                                    addGridPositionToMap(
                                                            groupPositions.get(max_vision_level),
                                                            group.id,
                                                            max_vision_level,
                                                            group.pos);
                                                });
                            });

            var groupGrids = new ArrayList<Grid>();
            for (int i = 0; i < 6; i++) {
                groupGrids.add(new Grid());
                groupGrids.get(i).grid = groupPositions.get(i);
            }
            groupGridsCache.put(scene.getId(), groupGrids);

            if (!noCacheGroupGridsToDisk) {
                try {
                    Files.createDirectories(path.getParent());
                } catch (IOException ignored) {
                }
                try (var file = new FileWriter(path.toFile())) {
                    file.write(JsonUtils.encode(groupGrids));
                    Grasscutter.getLogger().info("Scene {} saved grid file.", getScene().getId());
                } catch (Exception e) {
                    Grasscutter.getLogger()
                            .error("Scene {} unable to save grid file.", getScene().getId(), e);
                }
            }
            return groupGrids;
        }
    }

    public boolean isInit() {
        return isInit;
    }

    /**
     * Whether init() has finished, regardless of whether it found a scene meta. init() runs on its
     * own thread, so isInit() alone cannot tell "this scene has no scripts" apart from "the scripts
     * have not loaded yet" - and those two want opposite spawn behaviour.
     */
    public boolean isInitAttempted() {
        return initAttempted;
    }

    /** One-shot callback for the actual scene script initialization outcome. */
    public void whenInitialized(java.util.function.Consumer<Boolean> callback) {
        this.initialization.whenComplete(callback);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void loadBlockFromScript(SceneBlock block) {
        block.load(scene.getId(), meta.context);
    }

    public void loadGroupFromScript(SceneGroup group) {
        group.load(getScene().getId());

        this.sceneGroups.put(group.id, group);
        if (this.getCachedGroupInstanceById(group.id) != null) {
            this.sceneGroupsInstances.put(group.id, this.cachedSceneGroupsInstances.get(group.id));
            this.cachedSceneGroupsInstances.get(group.id).setCached(false);
            this.cachedSceneGroupsInstances.get(group.id).setLuaGroup(group);
        } else {
            var instance = new SceneGroupInstance(group, getScene().getWorld().getHost());
            this.sceneGroupsInstances.put(group.id, instance);
            this.cachedSceneGroupsInstances.put(group.id, instance);
            this.cachedSceneGroupsInstances.get(group.id).setCached(false);
            this.cachedSceneGroupsInstances.get(group.id).setLuaGroup(group);
            instance.save(); // Save the instance
        }

        if (group.variables != null) {
            group.variables.forEach(
                    variable -> {
                        val variables = this.getVariables(group.id);
                        if (variables != null && !variables.containsKey(variable.name))
                            variables.put(variable.name, variable.value);
                    });
        }

        // Apply any cross-group Lua writes that landed before this instance existed.
        var orphan = this.orphanGroupVariables.remove(group.id);
        if (orphan != null && !orphan.isEmpty()) {
            this.getVariables(group.id).putAll(orphan);
        }
    }

    public void unregisterGroup(SceneGroup group) {
        if (LiyueCloudDungeonSupport.isTimerGroup(getScene(), group.id))
            liyueDungeonTimers.cancelGroup(group.id);
        this.sceneGroups.remove(group.id);
        this.sceneGroupsInstances.values().removeIf(i -> i.getLuaGroup().equals(group));
        this.cachedSceneGroupsInstances.values().stream()
                .filter(i -> i.getLuaGroup().equals(group))
                .forEach(s -> s.setCached(true));
    }

    public void checkRegions() {
        RegionMembershipTick.check(this, this.regions);
    }

    /** A script-requested permanent kill is independent of ordinary oneoff death rules. */
    static boolean shouldSpawnGadget(
            boolean alreadySpawned, boolean scriptDestroyed,
            boolean oneoff, boolean persistent, boolean ordinaryDead) {
        return !alreadySpawned && !scriptDestroyed
                && (!oneoff || !persistent || !ordinaryDead);
    }

    public List<EntityGadget> getGadgetsInGroupSuite(
            SceneGroupInstance groupInstance, SceneSuite suite) {
        var group = groupInstance.getLuaGroup();
        return suite.sceneGadgets.stream()
                .filter(
                        m -> {
                            var entity = scene.getEntityByConfigId(m.config_id, group.id);
                            return shouldSpawnGadget(
                                    entity != null && entity.getGroupId() == group.id,
                                    groupInstance.isScriptGadgetDestroyed(m.config_id),
                                    m.isOneoff,
                                    // Many oculus gather points are oneoff without persistent.
                                    m.persistent
                                            || emu.grasscutter.game.world.OpenWorldSpawnHelper
                                                    .isPermanentGatherPoint(m.point_type),
                                    groupInstance.getDeadEntities().contains(m.config_id));
                        })
                .map(g -> createGadget(group.id, group.block_id, g, groupInstance.getCachedGadgetState(g)))
                .filter(Objects::nonNull)
                .peek(g -> groupInstance.cacheGadgetState(g.getMetaGadget(), g.getState()))
                .toList();
    }

    public List<EntityMonster> getMonstersInGroupSuite(
            SceneGroupInstance groupInstance, SceneSuite suite) {
        var group = groupInstance.getLuaGroup();
        return suite.sceneMonsters.stream()
                .filter(
                        m -> {
                            var entity = scene.getEntityByConfigId(m.config_id, group.id);
                            return (entity == null
                                    || entity.getGroupId()
                                            != group
                                                    .id); /*&& !groupInstance.getDeadEntities().contains(entity); */ // TODO:
                            // Investigate the usage of deadEntities
                        }) // TODO: Add persistent monster cached data
                .map(mob -> createMonster(group.id, group.block_id, mob))
                .filter(Objects::nonNull)
                .toList();
    }

    public void addGroupSuite(SceneGroupInstance groupInstance, SceneSuite suite) {
        this.addGroupSuite(groupInstance, suite, null);
    }

    public void addGroupSuite(
            SceneGroupInstance groupInstance, SceneSuite suite, List<GameEntity> entities) {
        // we added trigger first
        registerTrigger(suite.sceneTriggers);

        var group = groupInstance.getLuaGroup();
        var toCreate = new ArrayList<GameEntity>();
        toCreate.addAll(getGadgetsInGroupSuite(groupInstance, suite));
        toCreate.addAll(getMonstersInGroupSuite(groupInstance, suite));
        if (entities != null) entities.addAll(toCreate);
        else this.addEntities(toCreate);

        registerRegionInGroupSuite(group, suite);
    }

    public void refreshGroupSuite(SceneGroupInstance groupInstance, SceneSuite suite) {
        // we added trigger first
        registerTrigger(suite.sceneTriggers);

        var group = groupInstance.getLuaGroup();
        var toCreate = new ArrayList<GameEntity>();
        toCreate.addAll(getGadgetsInGroupSuite(groupInstance, suite));
        toCreate.addAll(getMonstersInGroupSuite(groupInstance, suite));
        addEntities(toCreate);

        registerRegionInGroupSuite(group, suite);
    }

    public void removeGroupSuite(SceneGroup group, SceneSuite suite) {
        deregisterTrigger(suite.sceneTriggers);
        removeMonstersInGroup(group, suite);
        removeGadgetsInGroup(group, suite);

        suite.sceneRegions.forEach(this::deregisterRegion);
    }

    public void killGroupSuite(SceneGroup group, SceneSuite suite) {
        deregisterTrigger(suite.sceneTriggers);

        killMonstersInGroup(group, suite);
        killGadgetsInGroup(group, suite);

        suite.sceneRegions.forEach(this::deregisterRegion);
    }

    public void startMonsterTideInGroup(
            String source, SceneGroup group, Integer[] ordersConfigId, int tideCount, int sceneLimit) {
        ScriptMonsterTideService tide;
        synchronized (this) {
            if (destroyed) return;
            tide = ScriptMonsterTideService.prepare(this, source, group, tideCount, sceneLimit, ordersConfigId);
            this.scriptMonsterTideService = tide;
            this.monsterTides.add(tide);
        }
        // Scene departure locks scene -> scripts. Never wait for the scene while holding this
        // manager's monitor; a cancellation between registration and spawning must also be respected.
        tide.spawnFirstTurn();
    }

    /** Every tide started in this scene; Lua may start several per group. */
    public synchronized List<ScriptMonsterTideService> getMonsterTides(int groupId) {
        return this.monsterTides.stream().filter(tide -> tide.getGroupId() == groupId).toList();
    }

    public synchronized void restoreMonsterTide(SceneGroup group, ScriptMonsterTideService.State state) {
        this.scriptMonsterTideService = ScriptMonsterTideService.restore(this, group, state);
        this.monsterTides.add(this.scriptMonsterTideService);
    }

    /** Drops a finished or discarded encounter's tides so stale counts cannot leak into a new one. */
    public synchronized void unloadMonsterTides(int groupId) {
        for (var tide : getMonsterTides(groupId)) {
            tide.unload();
            this.monsterTides.remove(tide);
            if (this.scriptMonsterTideService == tide) this.scriptMonsterTideService = null;
        }
    }

    public synchronized void unloadCurrentMonsterTide() {
        if (this.getScriptMonsterTideService() == null) {
            return;
        }
        this.monsterTides.remove(this.scriptMonsterTideService);
        this.scriptMonsterTideService.unload();
        this.scriptMonsterTideService = null;
    }

    public void spawnMonstersByConfigId(SceneGroup group, int configId, int delayTime) {
        // TODO delay
        var entity = scene.getEntityByConfigId(configId, group.id);
        if (entity != null && entity.getGroupId() == group.id) {
            Grasscutter.getLogger()
                    .debug("entity already exists failed in group {} with config {}", group.id, configId);
            return;
        }
        entity = createMonster(group.id, group.block_id, group.monsters.get(configId));
        if (entity != null) {
            getScene().addEntity(entity);
        } else {
            Grasscutter.getLogger()
                    .warn("failed to create entity with group {} and config {}", group.id, configId);
        }
    }
    // Events
    public Future<?> callEvent(int groupId, int eventType) {
        return callEvent(new ScriptArgs(groupId, eventType));
    }

    public Future<?> callEvent(@Nonnull ScriptArgs params) {
        if (this.destroyed) {
            return CompletableFuture.completedFuture(null);
        }
        /**
         * We use ThreadLocal to trans SceneScriptManager context to ScriptLib, to avoid eval script for
         * every groups' trigger in every scene instances. But when callEvent is called in a ScriptLib
         * func, it may cause NPE because the inner call cleans the ThreadLocal so that outer call could
         * not get it. e.g. CallEvent -> set -> ScriptLib.xxx -> CallEvent -> set -> remove -> NPE ->
         * (remove) So we use thread pool to clean the stack to avoid this new issue.
         */
        if (emu.grasscutter.game.quest.AmberFlightExamSupport.isFlightGroup(getScene(), params.getGroupId())
                || emu.grasscutter.game.quest.MondstadtStoryQuestSupport.isAmberWaterGroup(getScene(), params.getGroupId())) {
            return enqueueAmberQuestEvent(params);
        }
        if (LiyueCloudDungeonSupport.isFireLinkGroup(getScene(), params.getGroupId())) {
            return enqueueLiyueFireEvent(params);
        }
        return eventExecutor.submit(() -> this.realCallEvent(params));
    }

    private synchronized Future<?> enqueueAmberQuestEvent(ScriptArgs params) {
        // Coin batches and the two water switches update shared Lua counters; preserve event order.
        var previous = amberQuestEvents.getOrDefault(params.getGroupId(), CompletableFuture.completedFuture(null));
        var next = previous.handleAsync((ignored, failure) -> {
            realCallEvent(params);
            return (Void) null;
        }, eventExecutor);
        amberQuestEvents.put(params.getGroupId(), next);
        return next;
    }

    private synchronized Future<?> enqueueLiyueFireEvent(ScriptArgs params) {
        // Lua changes enqueue further state/variable events. Keep their guard flags in FIFO order.
        liyueFireEvents = liyueFireEvents.handleAsync((ignored, failure) -> {
            realCallEvent(params);
            return null;
        }, eventExecutor);
        return liyueFireEvents;
    }

    private void realCallEvent(@Nonnull ScriptArgs params) {
        // Before the event is counted: an early return after the increment would leave the count
        // up, and the deferred destroy-time save would never run.
        if (params.type == EVENT_TIMER_EVENT
                && LiyueCloudDungeonSupport.isTimerGroup(getScene(), params.getGroupId())
                && !liyueDungeonTimers.consume(params)) return;
        synchronized (this) {
            if (this.destroyed) return;
            executingEvents++;
        }
        try {
            if (params.type == EventType.EVENT_ANY_MONSTER_DIE) {
                this.sealBattleManager.onMonsterDeath(params.getGroupId(), params.param1);
            }
            ScriptLoader.getScriptLib().setSceneScriptManager(this);

            var eventType = params.type;
            var relevantTriggers =
                    switch (eventType) {
                        case EventType.EVENT_ENTER_REGION, EventType.EVENT_LEAVE_REGION -> this
                                .getTriggersByEvent(eventType)
                                .stream()
                                .filter(
                                        t ->
                                                (t.getName().length() <= 12
                                                                || t.getName().substring(13).equals(String.valueOf(params.param1)))
                                                        && (t.getSource().isEmpty()
                                                                || t.getSource().equals(params.getEventSource())))
                                .collect(Collectors.toSet());
                        default -> this.getTriggersByEvent(eventType).stream()
                                .filter(
                                        t -> params.getGroupId() == 0 || t.getCurrentGroup().id == params.getGroupId())
                                .filter(
                                        t -> (t.getSource().isEmpty() || t.getSource().equals(params.getEventSource())))
                                .collect(Collectors.toSet());
                    };

            if (LiyueCloudDungeonSupport.isFireLinkGroup(getScene(), params.getGroupId())) {
                // Select against the same state before any action can make its opposite condition true.
                var selected = relevantTriggers.stream()
                        .filter(trigger -> selectLiyueFireTrigger(params, trigger))
                        .toList();
                for (SceneTrigger trigger : selected) {
                    runSelectedLiyueFireTrigger(params, trigger);
                }
            } else {
                for (SceneTrigger trigger : relevantTriggers) {
                    handleEventForTrigger(params, trigger);
                }
            }
        } catch (Throwable throwable) {
            Grasscutter.getLogger()
                    .error("Condition Trigger " + params.type + " triggered exception", throwable);
        } finally {
            // make sure it is removed
            ScriptLoader.getScriptLib().removeSceneScriptManager();
            synchronized (this) {
                // A destroy during this event left its final save to us.
                if (--executingEvents == 0 && destroyed) clearDestroyedState();
            }
        }
    }

    private boolean selectLiyueFireTrigger(ScriptArgs params, SceneTrigger trigger) {
        try {
            ScriptLoader.getScriptLib().setCurrentGroup(trigger.currentGroup);
            ScriptLoader.getScriptLib().setCurrentCallParams(params);
            return evaluateTriggerCondition(trigger, params);
        } catch (Throwable ex) {
            Grasscutter.getLogger()
                    .error("Condition Trigger " + trigger.getName() + " triggered exception", ex);
            return false;
        } finally {
            ScriptLoader.getScriptLib().removeCurrentGroup();
        }
    }

    private void runSelectedLiyueFireTrigger(ScriptArgs params, SceneTrigger trigger) {
        try {
            ScriptLoader.getScriptLib().setCurrentGroup(trigger.currentGroup);
            ScriptLoader.getScriptLib().setCurrentCallParams(params);
            callTrigger(trigger, params);
        } catch (Throwable ex) {
            Grasscutter.getLogger()
                    .error("Action Trigger " + trigger.getName() + " triggered exception", ex);
        } finally {
            ScriptLoader.getScriptLib().removeCurrentGroup();
        }
    }

    private boolean handleEventForTrigger(ScriptArgs params, SceneTrigger trigger) {
        Grasscutter.getLogger()
                .trace("checking trigger {} for event {}", trigger.getName(), params.type);
        try {
            // setup execution
            ScriptLoader.getScriptLib().setCurrentGroup(trigger.currentGroup);
            ScriptLoader.getScriptLib().setCurrentCallParams(params);

            if (evaluateTriggerCondition(trigger, params)) {
                callTrigger(trigger, params);
                return true;
            } else {
                Grasscutter.getLogger()
                        .trace("Condition Trigger {} returned false", trigger.getCondition());
            }
            // TODO some ret do not bool
            return false;
        } catch (Throwable ex) {
            Grasscutter.getLogger()
                    .error("Condition Trigger " + trigger.getName() + " triggered exception", ex);
            return false;
        } finally {
            ScriptLoader.getScriptLib().removeCurrentGroup();
        }
    }

    private boolean evaluateTriggerCondition(SceneTrigger trigger, ScriptArgs params) {
        Grasscutter.getLogger()
                .trace(
                        "Call Condition Trigger {}, [{},{},{}], source_eid {}, target_eid {}",
                        trigger.getCondition(),
                        params.param1,
                        params.param2,
                        params.param3,
                        params.source_eid,
                        params.target_eid);
        LuaValue ret = this.callScriptFunc(trigger.getCondition(), trigger.currentGroup, params);
        return ret.isboolean() && ret.checkboolean();
    }

    private void callTrigger(SceneTrigger trigger, ScriptArgs params) {
        // the SetGroupVariableValueByGroup in tower need the param to record the first stage time
        ongoingTriggers.add(trigger);
        try {
            var ret = this.callScriptFunc(trigger.getAction(), trigger.currentGroup, params);
            // A trigger registered before this manager saw it has no counter yet
            var invocationsCounter =
                    triggerInvocations.computeIfAbsent(trigger.getName(), name -> new AtomicInteger());
            var invocations = invocationsCounter.incrementAndGet();
            Grasscutter.getLogger().trace("Call Action Trigger {}", trigger.getAction());

            var activeChallenge = scene.getChallenge();
            if (activeChallenge != null) {
                activeChallenge.onGroupTriggerDeath(trigger);
            }

            var event = trigger.getEvent();
            if (event == EventType.EVENT_ENTER_REGION || event == EventType.EVENT_LEAVE_REGION) {
                // The region an event names is not always still loaded - get(0) on the empty result
                // threw out of here, leaving the trigger ongoing and never deregistering it
                this.regions.values().stream()
                        .filter(p -> p.getConfigId() == params.param1)
                        .findFirst()
                        .ifPresent(
                                region -> {
                                    var metaRegion = region.getMetaRegion();
                                    this.getScene()
                                            .getPlayers()
                                            .forEach(
                                                    p -> {
                                                        if (event == EventType.EVENT_ENTER_REGION) {
                                                            p.onEnterRegion(metaRegion);
                                                        } else {
                                                            p.onLeaveRegion(metaRegion);
                                                        }
                                                    });
                                });
            }

            // always deregister on error, otherwise only if the count is reached
            // or the trigger should be preserved after a RefreshGroup call
            if (trigger.isPreserved()) {
                trigger.setPreserved(false);
            } else if (ret.isboolean() && !ret.checkboolean()
                    || ret.isint() && ret.checkint() != 0
                    || trigger.getTrigger_count() > 0 && invocations >= trigger.getTrigger_count()) {
                deregisterTrigger(trigger);
            }
        } finally {
            ongoingTriggers.remove(trigger);
        }
    }

    private LuaValue callScriptFunc(String funcName, SceneGroup group, ScriptArgs params) {
        LuaValue funcLua = null;
        if (funcName != null && !funcName.isEmpty()) {
            funcLua = (LuaValue) group.getBindings().get(funcName);
        }

        LuaValue ret = LuaValue.TRUE;

        if (funcLua != null) {
            LuaValue args = LuaValue.NIL;

            if (params != null) {
                args = CoerceJavaToLua.coerce(params);
            }

            ret = safetyCall(funcName, funcLua, args, group);
        }
        return ret;
    }

    public LuaValue safetyCall(String name, LuaValue func, LuaValue args, SceneGroup group) {
        try {
            return func.call(ScriptLoader.getScriptLibLua(), args);
        } catch (RuntimeException error) {
            // LuaError, but also the odd crash inside luaj's own traceback builder, which used to
            // escape a method whose whole job is to contain script failures
            ScriptLib.logger.error(
                    "[LUA] call trigger failed in group {} with {},{}", group.id, name, args, error);
            return LuaValue.valueOf(-1);
        }
    }

    public synchronized ScriptMonsterTideService getScriptMonsterTideService() {
        return scriptMonsterTideService;
    }

    public ScriptMonsterSpawnService getScriptMonsterSpawnService() {
        return scriptMonsterSpawnService;
    }

    public EntityGadget createGadget(int groupId, int blockId, SceneGadget g) {
        return createGadget(groupId, blockId, g, g.state);
    }

    public EntityGadget createGadget(int groupId, int blockId, SceneGadget g, int state) {
        if (!emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle.maySpawn(this, groupId)) return null;
        if (scene.getSceneType() == SceneType.SCENE_WORLD
                && !scene.getBlossomManager().isActiveAuthoredGadget(groupId, g.config_id, g.gadget_id))
            return null;
        if (g.isOneoff) {
            var hasEntity =
                    getScene().getEntities().values().stream()
                            .filter(e -> e instanceof EntityGadget)
                            .filter(e -> e.getGroupId() == g.group.id)
                            .filter(e -> e.getConfigId() == g.config_id)
                            .findFirst();
            if (hasEntity.isPresent()) {
                return null;
            }
        }
        EntityGadget entity = new EntityGadget(getScene(), g.gadget_id, g.pos);

        if (entity.getGadgetData() == null) {
            return null;
        }

        entity.setBlockId(blockId);
        entity.setConfigId(g.config_id);
        entity.setGroupId(groupId);
        entity.getRotation().set(g.rot);
        entity.setState(state);

        entity.setPointType(g.point_type);
        entity.setRouteConfig(BaseRoute.fromSceneGadget(g));
        entity.setMetaGadget(g);
        entity.buildContent();

        return entity;
    }

    public EntityNPC createNPC(SceneNPC npc, int blockId, int suiteId) {
        return new EntityNPC(getScene(), npc, blockId, suiteId);
    }

    /** Same as {@link #createMonster}, but the caller chooses where it appears. Backs ScriptLib. */
    public EntityMonster createMonsterByConfigIdByPos(
            SceneGroup group, int configId, Position pos, Position rot) {
        if (!emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle.maySpawnFromGroup(this, group)) return null;
        if (group == null || group.monsters == null) return null;

        var monster = group.monsters.get(configId);
        if (monster == null) {
            Grasscutter.getLogger()
                    .warn("Could not find monster config {} in group {}.", configId, group.id);
            return null;
        }

        var data = GameData.getMonsterDataMap().get(monster.monster_id);
        if (data == null) return null;

        var level = getScene().getLevelForMonster(monster.config_id, monster.level);
        var entity =
                new EntityMonster(
                        getScene(),
                        data,
                        pos != null ? pos : monster.pos,
                        rot != null ? rot : monster.rot,
                        level);
        entity.setGroupId(group.id);
        entity.setBlockId(group.block_id);
        entity.setConfigId(monster.config_id);
        entity.setPoseId(monster.pose_id);
        entity.setMetaMonster(monster);

        this.getScriptMonsterSpawnService()
                .onMonsterCreatedListener
                .forEach(action -> action.onNotify(entity));

        return entity;
    }

    /** Rebuilds a monster that was alive when its group was suspended; it is not a new spawn. */
    public EntityMonster restoreMonster(int groupId, int blockId, SceneMonster monster) {
        boolean previous = this.restoringMonster;
        this.restoringMonster = true;
        try {
            return createMonster(groupId, blockId, monster);
        } finally {
            this.restoringMonster = previous;
        }
    }

    public boolean isRestoringMonster() {
        return this.restoringMonster;
    }

    public EntityMonster createMonster(int groupId, int blockId, SceneMonster monster) {
        if (!emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle.maySpawn(this, groupId)) return null;
        if (monster == null) {
            return null;
        }

        var data = GameData.getMonsterDataMap().get(monster.monster_id);

        if (data == null) {
            return null;
        }

        // Calculate level
        int level = getScene().getLevelForMonster(monster.config_id, monster.level);

        // Spawn mob
        EntityMonster entity = new EntityMonster(getScene(), data, monster.pos, monster.rot, level);
        entity.setGroupId(groupId);
        entity.setBlockId(blockId);
        entity.setConfigId(monster.config_id);
        entity.setPoseId(monster.pose_id);
        entity.setMetaMonster(monster);

        if (!this.isRestoringMonster()) {
            this.getScriptMonsterSpawnService()
                    .onMonsterCreatedListener
                    .forEach(action -> action.onNotify(entity));
        }

        return entity;
    }

    public void addEntity(GameEntity gameEntity) {
        getScene().addEntity(gameEntity);
    }

    public void meetEntities(List<? extends GameEntity> gameEntity) {
        getScene().addEntities(gameEntity, VisionTypeOuterClass.VisionType.VisionType_VISION_MEET);
    }

    public void addEntities(List<? extends GameEntity> gameEntity) {
        getScene().addEntities(gameEntity);
    }

    public void removeEntities(List<? extends GameEntity> gameEntity) {
        getScene()
                .removeEntities(
                        gameEntity.stream().map(e -> (GameEntity) e).collect(Collectors.toList()),
                        VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH);
    }

    public RTree<SceneBlock, Geometry> getBlocksIndex() {
        return meta.sceneBlockIndex;
    }

    public void removeMonstersInGroup(SceneGroup group) {
        // Java-created challenges (including ley-line outcrops) use an ephemeral
        // SceneGroup with no Lua monster definitions. Nothing remains to remove
        // once the last spawned challenge monster dies.
        if (group == null || group.monsters == null || group.monsters.isEmpty()) {
            return;
        }
        var configSet =
                group.monsters.values().stream().map(m -> m.config_id).collect(Collectors.toSet());
        var toRemove =
                getScene().getEntities().values().stream()
                        .filter(e -> e instanceof EntityMonster)
                        .filter(e -> e.getGroupId() == group.id)
                        .filter(e -> configSet.contains(e.getConfigId()))
                        .toList();

        getScene().removeEntities(toRemove, VisionTypeOuterClass.VisionType.VisionType_VISION_MISS);
    }

    public void removeMonstersInGroup(SceneGroup group, SceneSuite suite) {
        var configSet = suite.sceneMonsters.stream().map(m -> m.config_id).collect(Collectors.toSet());
        var toRemove =
                getScene().getEntities().values().stream()
                        .filter(e -> e instanceof EntityMonster)
                        .filter(e -> e.getGroupId() == group.id)
                        .filter(e -> configSet.contains(e.getConfigId()))
                        .toList();

        getScene().removeEntities(toRemove, VisionTypeOuterClass.VisionType.VisionType_VISION_MISS);
    }

    public void removeGadgetsInGroup(SceneGroup group, SceneSuite suite) {
        var configSet = suite.sceneGadgets.stream().map(m -> m.config_id).collect(Collectors.toSet());
        var toRemove =
                getScene().getEntities().values().stream()
                        .filter(e -> e instanceof EntityGadget)
                        .filter(e -> e.getGroupId() == group.id)
                        .filter(e -> configSet.contains(e.getConfigId()))
                        .toList();

        getScene().removeEntities(toRemove, VisionTypeOuterClass.VisionType.VisionType_VISION_MISS);
    }

    public void killMonstersInGroup(SceneGroup group, SceneSuite suite) {
        var configSet = suite.sceneMonsters.stream().map(m -> m.config_id).collect(Collectors.toSet());
        var toRemove =
                getScene().getEntities().values().stream()
                        .filter(e -> e instanceof EntityMonster)
                        .filter(e -> e.getGroupId() == group.id)
                        .filter(e -> configSet.contains(e.getConfigId()))
                        .toList();

        toRemove.forEach(getScene()::killEntity);
    }

    public void killGadgetsInGroup(SceneGroup group, SceneSuite suite) {
        var configSet = suite.sceneGadgets.stream().map(m -> m.config_id).collect(Collectors.toSet());
        var toRemove =
                getScene().getEntities().values().stream()
                        .filter(e -> e instanceof EntityGadget)
                        .filter(e -> e.getGroupId() == group.id)
                        .filter(e -> configSet.contains(e.getConfigId()))
                        .toList();

        toRemove.forEach(getScene()::killEntity);
    }

    public int createGroupTimerEvent(int groupID, String source, double time) {
        if (LiyueCloudDungeonSupport.isTimerGroup(getScene(), groupID))
            return liyueDungeonTimers.create(groupID, source, time);
        var group = getGroupById(groupID);
        if (group == null || group.triggers == null) {
            Grasscutter.getLogger()
                    .debug(
                            "trying to create a timer for unknown group with id {} and source {}",
                            groupID,
                            source);
            return 1;
        }
        boolean hasTrigger =
                group.triggers.values().stream()
                        .anyMatch(t -> t.getEvent() == EVENT_TIMER_EVENT
                                && (t.getSource().isEmpty() || t.getSource().equals(source)));
        if (!hasTrigger) return 0;

        // Lua passes seconds; the scene scheduler counts ticks and stops while the scene is paused.
        int ticks =
                Math.max(1, (int) Math.ceil(time * 1000d / Math.max(1, Configuration.GAME_INFO.tickRateMs)));
        synchronized (this) {
            cancelGroupTimerEvent(groupID, source);
            var timers = activeGroupTimers.computeIfAbsent(groupID, k -> new HashSet<>());
            var timer = new int[1];
            timer[0] =
                    getScene()
                            .getScheduler()
                            .scheduleDelayedTask(
                                    () -> {
                                        // One-shot: a cancelled or replaced timer has left the set.
                                        synchronized (this) {
                                            if (!timers.remove(new Pair<>(source, timer[0]))) return;
                                        }
                                        callEvent(new ScriptArgs(groupID, EVENT_TIMER_EVENT).setEventSource(source));
                                    },
                                    ticks);
            timers.add(new Pair<>(source, timer[0]));
        }
        return 0;
    }

    public int cancelGroupTimerEvent(int groupID, String source) {
        // Outside this manager's lock: the Liyue timers take their own lock and then call back in.
        if (LiyueCloudDungeonSupport.isTimerGroup(getScene(), groupID))
            return liyueDungeonTimers.cancel(groupID, source);
        synchronized (this) {
            var groupTimers = activeGroupTimers.get(groupID);
            if (groupTimers != null) {
                groupTimers.removeIf(
                        timer -> {
                            if (!timer.component1().equals(source)) return false;
                            getScene().getScheduler().cancelTask(timer.component2());
                            return true;
                        });
            }
        }
        return 0;
    }

    /** Dead scene-map entities must not block a cleared-group objective. */
    static boolean isLivingGroupMonster(boolean dead, int entityGroupId, int groupId) {
        return !dead && entityGroupId == groupId;
    }

    public boolean isClearedGroupMonsters(int groupId) {
        val groupInstance = getGroupInstanceById(groupId);
        if (groupInstance == null || groupInstance.getLuaGroup() == null) return false;

        val monsters = groupInstance.getLuaGroup().monsters;

        if (monsters == null || monsters.isEmpty()) return true;

        return monsters.values().stream()
                .noneMatch(
                        m -> {
                            val entity = scene.getEntityByConfigId(m.config_id, groupId);
                            return entity != null && isLivingGroupMonster(entity.isDead(), entity.getGroupId(), groupId);
                        });
    }

    /**
     * Registers a new time axis for this scene. Starts the time axis after.
     *
     * @param timeAxis The time axis.
     */
    public void initTimeAxis(SceneTimeAxis timeAxis) {
        if (this.destroyed) {
            timeAxis.stop();
            return;
        }
        // Each time axis owns a Timer thread; one replaced without being stopped keeps firing
        // (and keeps its thread) until the server stops.
        var previous = this.timeAxis.put(timeAxis.getIdentifier(), timeAxis);
        if (previous != null && previous != timeAxis) {
            previous.stop();
        }
    }

    /**
     * Terminates a time axis.
     *
     * @param identifier The identifier of the time axis.
     */
    public void stopTimeAxis(String identifier) {
        var timeAxis = this.timeAxis.remove(identifier);
        if (timeAxis != null) {
            timeAxis.stop();
        }
    }

    /** Associates a scene cutscene with the groups waiting for its completion. */
    public synchronized void registerCutscene(int cutsceneId, int groupId) {
        if (destroyed || cutsceneId <= 0 || groupId <= 0) return;
        pendingCutsceneGroups.computeIfAbsent(cutsceneId, id -> new HashSet<>()).add(groupId);
    }

    /** Whether a scripted scene is already waiting for a genuine cutscene completion. */
    public synchronized boolean hasPendingCutscene(int cutsceneId) {
        return !destroyed && pendingCutsceneGroups.containsKey(cutsceneId);
    }

    /** Consumes the pending request once, even if multiple clients report completion. */
    public synchronized void finishCutscene(int cutsceneId) {
        var groups = pendingCutsceneGroups.remove(cutsceneId);
        if (destroyed || groups == null) return;
        for (int groupId : groups) {
            callEvent(new ScriptArgs(groupId, EventType.EVENT_CUTSCENE_END, cutsceneId));
        }
    }

    public synchronized void onDestroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        this.liyueDungeonTimers.clear();
        this.initialization.complete(false);
        this.pendingCutsceneGroups.clear();
        this.sealBattleManager.clear();

        activeGroupTimers.forEach(
                (gid, times) ->
                        times.forEach(
                                (e) -> {
                                    try {
                                        getScene().getScheduler().cancelTask(e.getSecond());
                                    } catch (Throwable ignored) {
                                        // Server shutdown may already have disposed the scheduler.
                                    }
                                }));
        activeGroupTimers.clear();

        // SceneTimeAxis owns a java.util.Timer. Stopping it is required to release the manager,
        // scene and player graph after the scene has been deregistered.
        for (SceneTimeAxis axis : new ArrayList<>(timeAxis.values())) {
            try {
                axis.stop();
            } catch (Throwable ignored) {
                // A cancelled timer is already in the desired state.
            }
        }
        timeAxis.clear();

        if (scriptMonsterTideService != null) {
            try {
                scriptMonsterTideService.unload();
            } catch (Throwable ignored) {
                // Listener cleanup should not prevent the remaining scene state from clearing.
            }
            scriptMonsterTideService = null;
        }
        if (scriptMonsterSpawnService != null) {
            scriptMonsterSpawnService.onMonsterCreatedListener.clear();
            scriptMonsterSpawnService.onMonsterDeadListener.clear();
            scriptMonsterSpawnService = null;
        }

        if (executingEvents == 0) clearDestroyedState();
    }

    /**
     * Saves open-world group progress, then drops the script state.
     *
     * <p>Only open-world scenes keep it. Group instances are stored per host and group and every
     * scene loads them back, so saving a dungeon's end state would bring its dead monsters, gadget
     * states and variables back on the next run of a weekly boss, quest dungeon or Abyss chamber.
     */
    private synchronized void clearDestroyedState() {
        if (scene.getSceneType() == emu.grasscutter.game.props.SceneType.SCENE_WORLD) {
            scene.saveGroups();
        }
        monsterTides.clear(); // After saveGroups, which snapshots pending tides.
        currentTriggers.clear();
        ongoingTriggers.clear();
        triggersByGroupScene.clear();
        triggerInvocations.clear();
        regions.clear();
        sceneGroups.clear();
        sceneGroupsInstances.clear();
        cachedSceneGroupsInstances.clear();
        loadedGroupSetPerBlock.clear();
        variables.clear();
    }
}
