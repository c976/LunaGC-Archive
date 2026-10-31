package emu.grasscutter.game.dailytask;

import com.google.gson.Gson;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.DailyTaskData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.service.ScriptMonsterTideService;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Scene-owned commission proximity only. Never generates tasks, kills entities or grants rewards.
 */
public final class DailyTaskGroupLifecycle {
    private static final Gson JSON = new Gson();
    private static final long TICK_INTERVAL_NANOS = 1_000_000_000L;

    private DailyTaskGroupLifecycle() {}

    /** No static cache or manager-held old-scene chain: ownership has the scene's lifetime. */
    public static final class State {
        final Scene scene;
        final DailyTaskManager manager;
        final Map<Integer, Plan> owned = new LinkedHashMap<>();
        final Set<Integer> restored = new HashSet<>();
        final Set<Integer> managed = new HashSet<>();
        final Set<Integer> recoveryRequested = new HashSet<>();
        final Set<Integer> settling = new HashSet<>();
        final Set<Integer> warned = new HashSet<>();
        Map<String, Position> luaCenters;
        boolean ticked;
        long lastTickNanos;

        State(DailyTaskManager manager, Scene scene) {
            this.manager = manager;
            this.scene = scene;
        }
    }

    record Plan(
            int task, int group, int date, int serial, Position center, float enter, float exit) {}

    // Keep persisted DTOs compatible with Gson versions that do not construct records.
    static final class Thing {
        String kind;
        int config;
        float x, y, z, rx, ry, rz, hp;
        int gadgetState;

        Thing(
                String kind,
                int config,
                float x,
                float y,
                float z,
                float rx,
                float ry,
                float rz,
                float hp,
                int state) {
            this.kind = kind;
            this.config = config;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.hp = hp;
            this.gadgetState = state;
        }
    }

    static final class Snapshot {
        int version, owner, date, task, group, suite;

        /** Absent (0) in snapshots written before issue serials. */
        int serial;

        List<Thing> entities;
        Map<String, Integer> triggers;
        List<Integer> regions;

        /** Absent in snapshots written before tide persistence. */
        List<ScriptMonsterTideService.State> tides;

        Snapshot(
                int version,
                int owner,
                int date,
                int task,
                int group,
                int suite,
                List<Thing> entities,
                Map<String, Integer> triggers,
                List<Integer> regions) {
            this.version = version;
            this.owner = owner;
            this.date = date;
            this.task = task;
            this.group = group;
            this.suite = suite;
            this.entities = entities;
            this.triggers = triggers;
            this.regions = regions;
        }
    }

    private static boolean validDistances(DailyTaskData data) {
        return data != null
                && data.getCenterPosition() != null
                && !data.getCenterPosition().isBlank()
                && Float.isFinite(data.getEnterDistance())
                && Float.isFinite(data.getExitDistance())
                && data.getEnterDistance() > 0
                && data.getExitDistance() >= data.getEnterDistance();
    }

    static Position center(DailyTaskData data) {
        return validDistances(data) ? jsonCenter(data) : null;
    }

    static Position center(DailyTaskData data, State state) {
        var point = center(data);
        if (point != null || !validDistances(data)) return point;
        if (state.luaCenters == null)
            state.luaCenters =
                    DailyTaskCenters.load(
                            emu.grasscutter.utils.FileUtils.getScriptPath(
                                    "Scene/3/scene3_dummy_points.lua"));
        return state.luaCenters.get(data.getCenterPosition());
    }

    private static Position jsonCenter(DailyTaskData data) {
        var globals =
                GameData.getScriptSceneDataMap().get("flat.luas.scenes.full_globals.lua.json");
        if (globals == null || globals.getScriptObjectList() == null) return null;
        var script = globals.getScriptObjectList().get("3/scene3_dummy_points.lua");
        if (script == null || script.getDummyPoints() == null) return null;
        var xyz = script.getDummyPoints().get(data.getCenterPosition() + ".pos");
        if (xyz == null
                || xyz.size() != 3
                || xyz.stream().anyMatch(v -> v == null || !Float.isFinite(v))) return null;
        return new Position(xyz.get(0), xyz.get(1), xyz.get(2));
    }

    /**
     * Documented local policy: XYZ distance, enter<=resource.enter, retain<=resource.exit; any
     * scene visitor.
     */
    static boolean near(Scene scene, Position center, float radius) {
        for (var p : scene.getPlayers()) {
            if (p == null
                    || p.getScene() != scene
                    || p.getWorld() != scene.getWorld()
                    || p.getSceneLoadState() != Player.SceneLoadState.LOADED
                    || p.getPosition() == null) continue;
            var pos = p.getPosition();
            double x = (double) pos.getX() - center.getX(),
                    y = (double) pos.getY() - center.getY(),
                    z = (double) pos.getZ() - center.getZ();
            if (Double.isFinite(x)
                    && Double.isFinite(y)
                    && Double.isFinite(z)
                    && x * x + y * y + z * z <= (double) radius * radius) return true;
        }
        return false;
    }

    static boolean owner(DailyTaskManager m, Scene s) {
        return m != null
                && s != null
                && s.getId() == 3
                && s.getWorld() != null
                && m.getPlayer() != null
                && s.getWorld().getHost() == m.getPlayer()
                && m.getOwnerUid() == m.getPlayer().getUid()
                && m.getPlayer().getDailyTaskManager() == m
                && m.getPlayer().getWorld() == s.getWorld();
    }

    static boolean loaded(Scene s, int group) {
        return s.getLoadedGroups().stream().anyMatch(g -> g.id == group);
    }

    static boolean questOwned(Scene scene, int group) {
        for (var p : scene.getPlayers()) {
            if (p != null
                    && p.getQuestManager() != null
                    && p.getQuestManager().getSceneGroupSuite(3).stream()
                            .anyMatch(q -> q.getGroup() == group)) return true;
        }
        return scene.getChallenge() != null && scene.getChallenge().getGroup().id == group;
    }

    private static State state(DailyTaskManager manager, Scene scene) {
        var state = scene.getDailyTaskGroups();
        if (state == null || state.manager != manager) {
            state = new State(manager, scene);
            scene.setDailyTaskGroups(state);
        }
        return state;
    }

    public static void tick(Scene scene) {
        tick(scene, System.nanoTime());
    }

    /** Periodic polling is bounded; explicit load/progress/save calls remain immediate. */
    static void tick(Scene scene, long nowNanos) {
        if (scene == null || scene.getWorld() == null || scene.getWorld().getHost() == null) return;
        var manager = scene.getWorld().getHost().getDailyTaskManager();
        if (!owner(manager, scene) || !manager.isUnlocked()) return;
        synchronized (manager) {
            var state = state(manager, scene);
            if (state.ticked && nowNanos - state.lastTickNanos < TICK_INTERVAL_NANOS) return;
            state.ticked = true;
            state.lastTickNanos = nowNanos;
            try {
                manager.loadActiveGroups(scene);
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error("[DailyTask] Lifecycle update failed; board retained.", failure);
            }
        }
    }

    public static int update(DailyTaskManager manager, Scene scene) {
        if (!owner(manager, scene)
                || manager.getDailyTasks() == null
                || scene.getScriptManager() == null
                || !scene.getScriptManager().isInit()) return 0;
        synchronized (manager) {
            var state = state(manager, scene);
            var eligible = manager.getActiveGroupIds();
            var plans = new LinkedHashMap<Integer, Plan>();
            var ambiguous = new HashSet<Integer>();
            for (var task : manager.getDailyTasks()) {
                if (task == null || task.isFinished()) continue;
                var row = GameData.getDailyTaskDataMap().get(task.getTaskId());
                var point = center(row, state);
                if (row == null || point == null) {
                    if (state.warned.add(task.getTaskId()))
                        Grasscutter.getLogger()
                                .warn(
                                        "[DailyTask] Cannot activate task {}: invalid/missing"
                                                + " resource center or distances; board retained.",
                                        task.getTaskId());
                    continue;
                }
                for (int group : row.getNewGroupVec())
                    if (eligible.contains(group)) {
                        if (ambiguous.contains(group)) continue;
                        var prior =
                                plans.putIfAbsent(
                                        group,
                                        new Plan(
                                                task.getTaskId(),
                                                group,
                                                manager.getLastGenerationDate(),
                                                manager.getGenerationSerial(),
                                                point,
                                                row.getEnterDistance(),
                                                row.getExitDistance()));
                        if (prior != null && prior.task != task.getTaskId()) {
                            plans.remove(group);
                            ambiguous.add(group);
                        }
                    }
            }
            for (var entry : new ArrayList<>(state.owned.entrySet())) {
                var plan = plans.get(entry.getKey());
                if (questOwned(scene, entry.getKey())) {
                    state.owned.remove(entry.getKey());
                    state.managed.remove(entry.getKey());
                    state.settling.remove(entry.getKey());
                    var instance = scene.getScriptManager().getGroupInstanceById(entry.getKey());
                    if (instance != null) instance.setDailyTaskSnapshot(null);
                    continue;
                }
                if (plan == null
                        || plan.task != entry.getValue().task
                        || plan.date != entry.getValue().date
                        || plan.serial != entry.getValue().serial
                        || !near(scene, plan.center, plan.exit))
                    suspend(manager, state, entry.getValue());
                else {
                    boolean monsters = hasMonsters(scene, plan.group);
                    if (!monsters && state.recoveryRequested.add(plan.group))
                        manager.scheduleActiveWaveRecovery(scene, plan.task);
                    if (monsters) state.recoveryRequested.remove(plan.group);
                }
            }
            int ready = 0;
            for (var plan : plans.values()) {
                if (loaded(scene, plan.group)) {
                    ready++;
                    continue;
                } // Never take ownership of an unrelated preloaded group.
                if (!near(scene, plan.center, plan.enter) || questOwned(scene, plan.group))
                    continue;
                state.owned.put(plan.group, plan);
                state.managed.add(plan.group);
                try {
                    if (scene.loadDynamicGroup(plan.group) <= 0) {
                        state.owned.remove(plan.group);
                        continue;
                    }
                } catch (RuntimeException failure) {
                    state.owned.remove(plan.group);
                    if (state.warned.add(-plan.group))
                        Grasscutter.getLogger()
                                .error(
                                        "[DailyTask] Cannot restore group {}; issued board"
                                                + " retained.",
                                        plan.group,
                                        failure);
                    continue;
                }
                snapshot(manager, state, plan);
                if (!hasMonsters(scene, plan.group) && state.recoveryRequested.add(plan.group))
                    manager.scheduleActiveWaveRecovery(scene, plan.task);
                ready++;
            }
            return ready;
        }
    }

    /**
     * Update the encounter cache at the real post-Lua progress boundary, before normal board save.
     */
    public static void progressChanged(DailyTaskManager manager, Scene scene, int group) {
        if (!owner(manager, scene)) return;
        var state = scene.getDailyTaskGroups();
        var plan = state == null || state.manager != manager ? null : state.owned.get(group);
        if (plan != null && loaded(scene, group)) {
            try {
                snapshot(manager, state, plan);
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error(
                                "[DailyTask] Encounter snapshot failed; normal progress retained.",
                                failure);
            }
        }
    }

    /**
     * Existing World/Scene group saves remain the persistence boundary; no quota/account save
     * added.
     */
    public static void captureAll(Scene scene) {
        if (scene == null || scene.getWorld() == null || scene.getWorld().getHost() == null) return;
        var manager = scene.getWorld().getHost().getDailyTaskManager();
        var state = scene.getDailyTaskGroups();
        if (state == null
                || state.manager != manager
                || manager == null
                || manager.getOwnerUid() != scene.getWorld().getHost().getUid()) return;
        for (var plan : new ArrayList<>(state.owned.values())) {
            try {
                if (loaded(scene, plan.group)) snapshot(manager, state, plan);
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error(
                                "[DailyTask] Encounter save snapshot failed; existing group saves"
                                        + " continue.",
                                failure);
            }
        }
    }

    private static void suspend(DailyTaskManager m, State state, Plan plan) {
        var scripts = state.scene.getScriptManager();
        synchronized (scripts) {
            if (scripts.isExecutingScripts())
                return; // Existing event executor increments under this same monitor.
            state.recoveryRequested.remove(plan.group);
            if (loaded(state.scene, plan.group)) {
                // A zero-population unfinished wave may still be settling in Lua. Keep its
                // definition,
                // not a refreshed empty/initial suite; the next tick removes a newly produced wave.
                var task = m.getDailyTask(plan.task);
                if (task != null && !task.isFinished() && !hasMonsters(state.scene, plan.group))
                    return;
                if (!snapshot(m, state, plan)) return;
                // Removal only; no killEntity/onDeath. A refused removal leaves the encounter live
                // and owned.
                if (!state.scene.unregisterDynamicGroup(plan.group)) return;
                // Dormant tides must not react to unrelated deaths; resume rebuilds them from the
                // snapshot.
                scripts.unloadMonsterTides(plan.group);
            }
            state.owned.remove(plan.group);
            state.settling.remove(plan.group);
        }
    }

    private static boolean snapshot(DailyTaskManager manager, State state, Plan plan) {
        var scripts = state.scene.getScriptManager();
        synchronized (scripts) {
            if (scripts.isExecutingScripts()) return false;
            var instance = scripts.getGroupInstanceById(plan.group);
            if (instance == null || instance.getLuaGroup() == null) return false;
            var group = instance.getLuaGroup();
            var things = new ArrayList<Thing>();
            for (var e : state.scene.getEntities().values()) {
                if (e.getGroupId() != plan.group
                        || !e.isAlive()
                        || e instanceof EntityMonster
                                && e.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0)
                    continue;
                String kind =
                        e instanceof EntityMonster
                                ? "M"
                                : e instanceof EntityGadget
                                        ? "G"
                                        : e instanceof EntityNPC ? "N" : null;
                if (kind == null || e.getConfigId() <= 0 || things.size() >= 1024) return false;
                var p = e.getPosition();
                var r = e.getRotation();
                if (p == null || r == null) return false;
                var thing =
                        new Thing(
                                kind,
                                e.getConfigId(),
                                p.getX(),
                                p.getY(),
                                p.getZ(),
                                r.getX(),
                                r.getY(),
                                r.getZ(),
                                e instanceof EntityMonster
                                        ? e.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                                        : 0,
                                e instanceof EntityGadget g ? g.getState() : 0);
                if (!finite(thing)) return false;
                things.add(thing);
            }
            var triggers = new LinkedHashMap<String, Integer>();
            for (var t : group.triggers.values()) {
                if (scripts.getTriggersByEvent(t.getEvent()).contains(t))
                    triggers.put(t.getName(), scripts.getTriggerInvocationCount(t.getName()));
            }
            var regions = new ArrayList<>(scripts.getRegisteredRegionConfigs(plan.group));
            var value =
                    new Snapshot(
                            1,
                            manager.getOwnerUid(),
                            plan.date,
                            plan.task,
                            plan.group,
                            instance.getActiveSuiteId(),
                            things,
                            triggers,
                            regions);
            var tides = new ArrayList<ScriptMonsterTideService.State>();
            for (var tide : scripts.getMonsterTides(plan.group))
                if (tide.isPending()) tides.add(tide.snapshot());
            if (!tides.isEmpty()) value.tides = tides;
            value.serial = plan.serial;
            String text = JSON.toJson(value);
            if (text.length() > 262144) return false;
            instance.setDailyTaskSnapshot(text);
            return true;
        }
    }

    static boolean hasMonsters(Scene scene, int group) {
        return scene.getEntities().values().stream()
                .anyMatch(
                        e ->
                                e instanceof EntityMonster
                                        && e.getGroupId() == group
                                        && e.isAlive()
                                        && e.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 0);
    }

    static boolean finite(Thing t) {
        return t != null
                && t.kind != null
                && t.config > 0
                && Float.isFinite(t.x)
                && Float.isFinite(t.y)
                && Float.isFinite(t.z)
                && Float.isFinite(t.rx)
                && Float.isFinite(t.ry)
                && Float.isFinite(t.rz)
                && Float.isFinite(t.hp)
                && t.hp >= 0
                && (!t.kind.equals("M") || t.hp > 0);
    }

    /**
     * Only callbacks into groups this lifecycle owns are blocked while suspended; unrelated Lua is
     * untouched.
     */
    public static boolean maySpawn(SceneScriptManager scripts, int group) {
        var scene = scripts.getScene();
        if (scene == null || scene.getWorld() == null || scene.getWorld().getHost() == null)
            return true;
        var manager = scene.getWorld().getHost().getDailyTaskManager();
        var state = scene.getDailyTaskGroups();
        if (state == null || state.manager != manager || !state.managed.contains(group))
            return true;
        if (!owner(manager, scene)) return false;
        var plan = state.owned.get(group);
        var task = plan == null ? null : manager.getDailyTask(plan.task);
        if (plan == null
                || plan.date != manager.getLastGenerationDate()
                || task == null
                || task.isFinished()) return false;
        boolean visitor =
                scene.getPlayers().stream()
                        .anyMatch(
                                p ->
                                        p != null
                                                && p.getWorld() == scene.getWorld()
                                                && p.getScene() == scene
                                                && p.getSceneLoadState()
                                                        == Player.SceneLoadState.LOADED);
        if (!visitor) return false;
        if (near(scene, plan.center, plan.exit)) return true;
        if (state.settling.contains(group)) return true;
        if (loaded(scene, group) && !hasMonsters(scene, group)) {
            state.settling.add(group);
            return true;
        }
        return false;
    }

    public static boolean maySpawnFromGroup(SceneScriptManager scripts, SceneGroup group) {
        return group != null && maySpawn(scripts, group.id);
    }

    public static boolean unregisterOwned(Scene scene, int group, DailyTaskManager manager) {
        if (!owner(manager, scene) || questOwned(scene, group)) return false;
        var state = scene.getDailyTaskGroups();
        if (state == null || state.manager != manager || !state.managed.contains(group))
            return false;
        var scripts = scene.getScriptManager();
        var active = scripts.getGroupInstanceById(group);
        var cached = scripts.getCachedGroupInstances().get(group);
        var activeText = active == null ? null : active.getDailyTaskSnapshot();
        var cachedText = cached == null ? null : cached.getDailyTaskSnapshot();
        // Clear BEFORE unregister queues its normal cached-group save, so a cold restart cannot
        // revive
        // this quota.
        // Proximity suspension does not use this explicit completion/reset path.
        if (active != null) active.setDailyTaskSnapshot(null);
        if (cached != null) cached.setDailyTaskSnapshot(null);
        boolean result;
        try {
            result = scene.unregisterDynamicGroup(group);
        } catch (RuntimeException | Error failure) {
            if (active != null) active.setDailyTaskSnapshot(activeText);
            if (cached != null) cached.setDailyTaskSnapshot(cachedText);
            throw failure;
        }
        if (!result) {
            if (active != null) active.setDailyTaskSnapshot(activeText);
            if (cached != null) cached.setDailyTaskSnapshot(cachedText);
        }
        if (result) {
            scripts.unloadMonsterTides(group);
            state.owned.remove(group);
            state.restored.remove(group);
            state.recoveryRequested.remove(group);
            state.settling.remove(group);
        }
        return result;
    }

    /**
     * A same-day reroll may select the same task and group again; nothing saved for the previous
     * issue of an unloaded (dormant) encounter may survive into it.
     */
    public static void discardDormant(Scene scene, int group, DailyTaskManager manager) {
        if (!owner(manager, scene) || questOwned(scene, group) || loaded(scene, group)) return;
        var scripts = scene.getScriptManager();
        var active = scripts.getGroupInstanceById(group);
        var cached = scripts.getCachedGroupInstances().get(group);
        if (active != null) active.setDailyTaskSnapshot(null);
        if (cached != null) cached.setDailyTaskSnapshot(null);
        scripts.unloadMonsterTides(group);
        var state = scene.getDailyTaskGroups();
        if (state == null || state.manager != manager) return;
        state.owned.remove(group);
        state.restored.remove(group);
        state.recoveryRequested.remove(group);
        state.settling.remove(group);
    }

    public static boolean isActive(DailyTaskManager manager, Scene scene, int task) {
        if (!owner(manager, scene)) return false;
        var state = scene.getDailyTaskGroups();
        if (state == null || state.manager != manager) return false;
        var current = manager.getDailyTask(task);
        return current != null
                && !current.isFinished()
                && state.owned.values().stream()
                        .anyMatch(
                                p ->
                                        p.task == task
                                                && p.date == manager.getLastGenerationDate()
                                                && loaded(scene, p.group)
                                                && near(scene, p.center, p.exit));
    }

    /**
     * Called instead of only the commission-owned loader refresh. Other groups run the original
     * body.
     */
    public static int refresh(
            SceneScriptManager scripts,
            SceneGroupInstance instance,
            int suite,
            boolean excludePrevious) {
        return refresh(scripts, instance, suite, excludePrevious, false);
    }

    /** Scene reloads retain ordinary puzzle progress; commission snapshots keep their own policy. */
    public static int load(SceneScriptManager scripts, SceneGroupInstance instance, int suite) {
        return refresh(scripts, instance, suite, false, true);
    }

    private static int refresh(SceneScriptManager scripts, SceneGroupInstance instance,
            int suite, boolean excludePrevious, boolean loading) {
        java.util.function.IntSupplier ordinary = () -> loading
                ? scripts.restoreGroup(instance, suite)
                : scripts.refreshGroup(instance, suite, excludePrevious);
        var scene = scripts.getScene();
        if (scene == null || scene.getWorld() == null || scene.getWorld().getHost() == null)
            return ordinary.getAsInt();
        var manager = scene.getWorld().getHost().getDailyTaskManager();
        if (!owner(manager, scene)) return ordinary.getAsInt();
        var state = scene.getDailyTaskGroups();
        var plan =
                state == null || state.manager != manager
                        ? null
                        : state.owned.get(instance.getGroupId());
        if (plan == null) {
            if (state != null
                    && state.manager == manager
                    && state.managed.remove(instance.getGroupId()))
                instance.setDailyTaskSnapshot(
                        null); // Explicit external loader owns this activation.
            return ordinary.getAsInt();
        }
        String text = instance.getDailyTaskSnapshot();
        if (text == null) return scripts.refreshGroup(instance, suite, excludePrevious);
        Snapshot saved;
        var group = instance.getLuaGroup();
        // Only validation is recoverable here. Construction/script failures must not start a second
        // refresh over partially installed encounter state.
        try {
            if (text.length() > 262144)
                throw new IllegalStateException("Oversized commission snapshot");
            try {
                saved = JSON.fromJson(text, Snapshot.class);
            } catch (RuntimeException bad) {
                throw new IllegalStateException("Malformed commission snapshot", bad);
            }
            if (saved == null) throw new IllegalStateException("Missing commission snapshot");
            if (saved.owner != manager.getOwnerUid()
                    || saved.date != plan.date
                    || saved.serial != plan.serial
                    || saved.task != plan.task
                    || saved.group != plan.group)
                throw new IllegalStateException("Snapshot belongs to a different commission board");
            if (saved.version != 1)
                throw new IllegalStateException("Unknown commission snapshot version");
            if (saved.entities == null
                    || saved.entities.size() > 1024
                    || saved.triggers == null
                    || saved.regions == null
                    || group.suites == null
                    || saved.suite < 1
                    || saved.suite > group.suites.size()
                    || saved.triggers.size() > group.triggers.size()
                    || saved.regions.size() > group.regions.size())
                throw new IllegalStateException("Invalid commission snapshot");
            var identities = new HashSet<String>();
            for (var t : saved.entities) {
                if (!finite(t)
                        || !identities.add(t.kind + ":" + t.config)
                        || !(t.kind.equals("M") && group.monsters.containsKey(t.config)
                                || t.kind.equals("G") && group.gadgets.containsKey(t.config)
                                || t.kind.equals("N") && group.npcs.containsKey(t.config)))
                    throw new IllegalStateException("Invalid commission entity snapshot");
            }
            for (var entry : saved.triggers.entrySet())
                if (entry.getKey() == null
                        || entry.getValue() == null
                        || entry.getValue() < 0
                        || !group.triggers.containsKey(entry.getKey()))
                    throw new IllegalStateException("Invalid commission trigger snapshot");
            for (var id : saved.regions)
                if (id == null || !group.regions.containsKey(id))
                    throw new IllegalStateException("Invalid commission region snapshot");
            if (saved.tides != null) {
                if (saved.tides.size() > 16)
                    throw new IllegalStateException("Invalid commission tide snapshot");
                for (var tide : saved.tides)
                    if (tide == null
                            || tide.source == null
                            || tide.tideCount < 0
                            || tide.killCount < 0
                            || tide.alive < 0
                            || Boolean.FALSE.equals(tide.started) && tide.alive != 0
                            || tide.sceneLimit <= 0
                            || tide.sceneLimit > 64
                            || tide.orders == null
                            || tide.configIds == null
                            || tide.configIds.isEmpty()
                            || tide.configIds.size() > 64
                            || tide.orders.size() > 64
                            || !tide.configIds.stream()
                                    .allMatch(id -> id != null && group.monsters.containsKey(id))
                            || !tide.orders.stream()
                                    .allMatch(id -> id != null && group.monsters.containsKey(id)))
                        throw new IllegalStateException("Invalid commission tide snapshot");
            }
        } catch (IllegalStateException invalid) {
            Grasscutter.getLogger()
                    .warn(
                            "[DailyTask] Discarding invalid snapshot for group {}; refreshing its"
                                    + " normal suite: {}",
                            instance.getGroupId(),
                            invalid.getMessage());
            return refreshWithoutSnapshot(scripts, instance, state, suite, excludePrevious);
        }
        var entities = new ArrayList<GameEntity>();
        for (var t : saved.entities) {
            GameEntity entity =
                    switch (t.kind) {
                        case "M" ->
                                scripts.restoreMonster(
                                        group.id, group.block_id, group.monsters.get(t.config));
                        case "G" ->
                                scripts.createGadget(
                                        group.id,
                                        group.block_id,
                                        group.gadgets.get(t.config),
                                        t.gadgetState);
                        case "N" ->
                                scripts.createNPC(
                                        group.npcs.get(t.config), group.block_id, saved.suite);
                        default -> null;
                    };
            if (entity == null)
                throw new IllegalStateException("Missing commission entity config " + t.config);
            entity.getPosition().set(new Position(t.x, t.y, t.z));
            entity.getRotation().set(new Position(t.rx, t.ry, t.rz));
            if (entity instanceof EntityMonster)
                entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, t.hp);
            entities.add(entity);
        }
        for (var entry : saved.triggers.entrySet()) {
            var trigger = group.triggers.get(entry.getKey());
            scripts.restoreTriggerInvocation(trigger, entry.getValue());
        }
        for (int id : saved.regions) {
            var region = group.regions.get(id);
            if (region == null) throw new IllegalStateException("Missing commission region");
            scripts.registerRegion(new EntityRegion(scene, region));
        }
        // Suspension detaches tides, so resume rebuilds them from the snapshot (a still-live one is
        // kept).
        if (saved.tides != null) {
            var live = new HashSet<String>();
            for (var tide : scripts.getMonsterTides(group.id)) live.add(tide.getSource());
            for (var tide : saved.tides)
                if (!live.contains(tide.source)) scripts.restoreMonsterTide(group, tide);
        }
        // Hydrate existing encounter state without replaying refresh/group-load scripts, onCreate
        // (including EVENT_GADGET_CREATE), or monster-live actions. These are survivors, not new
        // spawns.
        for (var entity : entities) scene.getEntities().put(entity.getId(), entity);
        if (!entities.isEmpty())
            scene.broadcastPacket(
                    new PacketSceneEntityAppearNotify(entities, VisionType.VisionType_VISION_MEET));
        instance.setActiveSuiteId(saved.suite);
        state.restored.add(group.id);
        // Departure can save a registered tide before it publishes its first turn. Resume only that
        // explicit phase; legacy snapshots and already-started tides must not spawn another first
        // turn.
        for (var tide : scripts.getMonsterTides(group.id)) tide.spawnFirstTurn();
        return saved.suite;
    }

    private static int refreshWithoutSnapshot(
            SceneScriptManager scripts,
            SceneGroupInstance instance,
            State state,
            int suite,
            boolean excludePrevious) {
        int group = instance.getGroupId();
        instance.setDailyTaskSnapshot(null);
        var active = scripts.getGroupInstanceById(group);
        var cached = scripts.getCachedGroupInstances().get(group);
        if (active != null) active.setDailyTaskSnapshot(null);
        if (cached != null) cached.setDailyTaskSnapshot(null);
        state.restored.remove(group);
        scripts.unloadMonsterTides(group);
        return scripts.refreshGroup(instance, suite, excludePrevious);
    }

    public static java.util.concurrent.Future<?> groupLoad(
            SceneScriptManager scripts, ScriptArgs args) {
        var scene = scripts.getScene();
        if (scene == null || scene.getWorld() == null || scene.getWorld().getHost() == null)
            return scripts.callEvent(args);
        var manager = scene.getWorld().getHost().getDailyTaskManager();
        var state = scene.getDailyTaskGroups();
        if (state != null && state.manager == manager && state.restored.remove(args.getGroupId()))
            return CompletableFuture.completedFuture(null);
        return scripts.callEvent(args);
    }
}
