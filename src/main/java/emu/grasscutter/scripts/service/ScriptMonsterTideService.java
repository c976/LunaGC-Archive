package emu.grasscutter.scripts.service;

import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.listener.ScriptMonsterListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScriptMonsterTideService {
    private final SceneScriptManager sceneScriptManager;
    private final SceneGroup currentGroup;
    private final AtomicInteger monsterAlive;
    private final AtomicInteger monsterTideCount;
    private final AtomicInteger monsterKillCount;
    private final int monsterSceneLimit;
    private final ConcurrentLinkedQueue<Integer> monsterConfigOrders;
    private final List<Integer> monsterConfigIds;
    private final OnMonsterCreated onMonsterCreated = new OnMonsterCreated();
    private final OnMonsterDead onMonsterDead = new OnMonsterDead();
    private final String source;
    private volatile boolean unloaded;
    private volatile boolean firstTurnStarted;

    /** Persistable progress of one tide, so a restored encounter resumes its pending waves. */
    public static final class State {
        public String source;
        public int tideCount, killCount, alive, sceneLimit;
        public List<Integer> orders, configIds;

        /** Null in older snapshots, whose first turn was already published. */
        public Boolean started;
    }

    public ScriptMonsterTideService(
            SceneScriptManager sceneScriptManager,
            String source,
            SceneGroup group,
            int tideCount,
            int monsterSceneLimit,
            Integer[] ordersConfigId) {
        this(sceneScriptManager, source, group, tideCount, monsterSceneLimit, ordersConfigId, true);
    }

    /**
     * Registers listeners without acquiring the scene monitor or spawning an initial population.
     */
    public static ScriptMonsterTideService prepare(
            SceneScriptManager scripts,
            String source,
            SceneGroup group,
            int tideCount,
            int sceneLimit,
            Integer[] orders) {
        return new ScriptMonsterTideService(
                scripts, source, group, tideCount, sceneLimit, orders, false);
    }

    /** Re-registers a tide at its saved progress without spawning a new first turn. */
    public static ScriptMonsterTideService restore(
            SceneScriptManager sceneScriptManager, SceneGroup group, State state) {
        var service =
                new ScriptMonsterTideService(
                        sceneScriptManager,
                        state.source,
                        group,
                        state.tideCount,
                        state.sceneLimit,
                        state.configIds.toArray(new Integer[0]),
                        false);
        service.monsterConfigOrders.clear();
        service.monsterConfigOrders.addAll(state.orders);
        service.monsterKillCount.set(state.killCount);
        service.monsterAlive.set(state.alive);
        service.firstTurnStarted = state.started == null || state.started;
        return service;
    }

    private ScriptMonsterTideService(
            SceneScriptManager sceneScriptManager,
            String source,
            SceneGroup group,
            int tideCount,
            int monsterSceneLimit,
            Integer[] ordersConfigId,
            boolean spawnFirstTurn) {
        this.sceneScriptManager = sceneScriptManager;
        this.currentGroup = group;
        this.monsterSceneLimit = monsterSceneLimit;
        this.monsterTideCount = new AtomicInteger(tideCount);
        this.monsterKillCount = new AtomicInteger(0);
        this.monsterAlive = new AtomicInteger(0);
        this.monsterConfigOrders = new ConcurrentLinkedQueue<>(List.of(ordersConfigId));
        this.monsterConfigIds = List.of(ordersConfigId);
        this.source = source;

        synchronized (sceneScriptManager) {
            this.sceneScriptManager
                    .getScriptMonsterSpawnService()
                    .addMonsterCreatedListener(onMonsterCreated);
            this.sceneScriptManager
                    .getScriptMonsterSpawnService()
                    .addMonsterDeadListener(onMonsterDead);
        }
        if (spawnFirstTurn) spawnFirstTurn();
    }

    /** First-turn publication uses the same scene -> scripts lock order as scene departure. */
    public void spawnFirstTurn() {
        if (unloaded || firstTurnStarted) return;
        var scene = sceneScriptManager.getScene();
        synchronized (scene) {
            synchronized (sceneScriptManager) {
                if (unloaded || firstTurnStarted || sceneScriptManager.isDestroyed()) return;
                firstTurnStarted = true;
                for (int i = 0; !unloaded && i < monsterSceneLimit; i++) {
                    var monster =
                            sceneScriptManager.createMonster(
                                    currentGroup.id, currentGroup.block_id, getNextMonster());
                    if (monster != null) sceneScriptManager.addEntity(monster);
                }
            }
        }
    }

    public class OnMonsterCreated implements ScriptMonsterListener {
        @Override
        public void onNotify(EntityMonster sceneMonster) {
            if (!unloaded
                    && monsterConfigIds.contains(sceneMonster.getConfigId())
                    && monsterSceneLimit > 0) {
                monsterAlive.incrementAndGet();
                monsterTideCount.decrementAndGet();
            }
        }
    }

    public SceneMonster getNextMonster() {
        var nextId = this.monsterConfigOrders.poll();
        if (nextId == null) {
            // AutoMonsterTide has been called with fewer monster config IDs than the total tide
            // count.
            // Get last config ID from the list, then.
            return currentGroup.monsters.get(monsterConfigIds.get(monsterConfigIds.size() - 1));
        } else if (currentGroup.monsters.containsKey(nextId)) {
            return currentGroup.monsters.get(nextId);
        }
        // TODO some monster config_id do not exist in groups, so temporarily set it to the first
        return currentGroup.monsters.values().stream().findFirst().orElse(null);
    }

    public class OnMonsterDead implements ScriptMonsterListener {
        @Override
        public void onNotify(EntityMonster sceneMonster) {
            if (unloaded || monsterSceneLimit <= 0) {
                return;
            }
            if (monsterAlive.decrementAndGet() >= monsterSceneLimit) {
                // maybe not happen
                return;
            }
            monsterKillCount.incrementAndGet();
            if (monsterTideCount.get() > 0) {
                // add more
                var next =
                        sceneScriptManager.createMonster(
                                currentGroup.id, currentGroup.block_id, getNextMonster());
                if (next != null) sceneScriptManager.addEntity(next);
            }
            // call registered events that may spawn in more monsters
            var scriptArgs =
                    new ScriptArgs(
                            currentGroup.id,
                            EventType.EVENT_MONSTER_TIDE_DIE,
                            monsterKillCount.get());
            scriptArgs.setEventSource(source);
            sceneScriptManager.callEvent(scriptArgs);
        }
    }

    public int getGroupId() {
        return currentGroup.id;
    }

    public String getSource() {
        return source;
    }

    /** A finished tide holds nothing worth saving. */
    public boolean isPending() {
        return monsterSceneLimit > 0 && (monsterTideCount.get() > 0 || monsterAlive.get() > 0);
    }

    public State snapshot() {
        var state = new State();
        state.source = source;
        state.tideCount = monsterTideCount.get();
        state.killCount = monsterKillCount.get();
        state.alive = monsterAlive.get();
        state.sceneLimit = monsterSceneLimit;
        state.orders = new ArrayList<>(monsterConfigOrders);
        state.configIds = new ArrayList<>(monsterConfigIds);
        state.started = firstTurnStarted;
        return state;
    }

    public void unload() {
        synchronized (sceneScriptManager) {
            unloaded = true;
            this.sceneScriptManager
                    .getScriptMonsterSpawnService()
                    .removeMonsterCreatedListener(onMonsterCreated);
            this.sceneScriptManager
                    .getScriptMonsterSpawnService()
                    .removeMonsterDeadListener(onMonsterDead);
        }
    }
}
