package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneGadget;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * After trounce-flower claim: remove flower cleanly (VISION_REMOVE, not DIE), clear corpses,
 * immediately respawn world boss - matching live LunaGC.
 */
public final class WorldBossClaimHelper {
    private static final ConcurrentHashMap<Integer, Long> CLAIM_COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    /** Keep suppress short - it only blocks corpse/script re-drops, not the next real kill's flower. */
    private static final long FLOWER_SUPPRESS_MS = 2500L;

    private WorldBossClaimHelper() {}

    public static boolean isFlowerSuppressed(int groupId) {
        Long until = CLAIM_COOLDOWN_UNTIL.get(groupId);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Temporarily block trounce-flower re-spawn (e.g. after player abandoned flower). */
    public static void suppressFlower(int groupId, long durationMs) {
        if (groupId <= 0 || durationMs <= 0) {
            return;
        }
        CLAIM_COOLDOWN_UNTIL.put(groupId, System.currentTimeMillis() + durationMs);
    }

    /** Allow flower again (e.g. player killed the respawned boss during claim cooldown). */
    public static void clearFlowerSuppress(int groupId) {
        if (groupId > 0) {
            CLAIM_COOLDOWN_UNTIL.remove(groupId);
        }
    }

    public static void onClaimed(EntityGadget flower) {
        if (flower == null || flower.getScene() == null) {
            return;
        }
        SceneGadget meta = flower.getMetaGadget();
        if (meta == null || meta.boss_chest == null) {
            return;
        }
        int groupId = flower.getGroupId();
        if (!WorldBossSpawnHelper.isWorldBossGroup(groupId)) {
            return;
        }

        try {
            Method ensure = WorldBossSpawnHelper.class.getDeclaredMethod("ensureIndexBuilt");
            ensure.setAccessible(true);
            ensure.invoke(null);

            Field bf = WorldBossSpawnHelper.class.getDeclaredField("bossByGroupId");
            bf.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) bf.get(null);
            Object entry = map.get(groupId);
            if (entry == null) {
                return;
            }

            Scene scene = flower.getScene();
            Method spawnKey =
                    WorldBossSpawnHelper.class.getDeclaredMethod("spawnAttemptKey", int.class, int.class);
            spawnKey.setAccessible(true);
            Object key = spawnKey.invoke(null, scene.getId(), groupId);

            Field awaitF = WorldBossSpawnHelper.class.getDeclaredField("awaitingFlowerKeys");
            awaitF.setAccessible(true);
            ((Set<?>) awaitF.get(null)).remove(key);

            Field lastF = WorldBossSpawnHelper.class.getDeclaredField("lastBossSpawnAttemptMs");
            lastF.setAccessible(true);
            ((Map<?, ?>) lastF.get(null)).remove(key);

            OpenWorldSpawnHelper.clearGroupDeathRecords(scene, groupId);
            OpenWorldSpawnHelper.clearGroupEntityDeathRecord(scene, groupId, flower.getConfigId());

            Method removeDead =
                    WorldBossSpawnHelper.class.getDeclaredMethod(
                            "removeDeadBossMonsters", Scene.class, entry.getClass());
            removeDead.setAccessible(true);
            removeDead.invoke(null, scene, entry);

            // Do NOT updateState(102) here - finishOpen already did; re-broadcasting after purge
            // leaves a lingering "opened/smoke" flower on the client.
            int removed = removeAllTrounceFlowers(scene, groupId);
            CLAIM_COOLDOWN_UNTIL.put(groupId, System.currentTimeMillis() + FLOWER_SUPPRESS_MS);

            // Lupus Boreas: after the flower is claimed nothing respawns; the arena goes empty and the
            // start-trial worktop returns.
            if (emu.grasscutter.game.world.AndriusTrialStartHelper.shouldSkipClaimRespawn(groupId)) {
                Grasscutter.getLogger()
                        .info(
                                "WorldBossClaimHelper Andrius claimed - skip immediate respawn group {}, flowersRemoved={}",
                                groupId,
                                removed);
                removeAllTrounceFlowers(scene, groupId);
                scheduleFlowerSweep(scene, groupId, 1);
                scheduleFlowerSweep(scene, groupId, 2);
                scheduleFlowerSweep(scene, groupId, 3);
                try {
                    emu.grasscutter.game.world.AndriusTrialStartHelper.onFlowerClaimed(scene);
                } catch (Throwable t) {
                    Grasscutter.getLogger().warn("Andrius onFlowerClaimed failed", t);
                }
                return;
            }

            SceneScriptManager sm = scene.getScriptManager();
            if (sm != null) {
                Method hasAlive =
                        WorldBossSpawnHelper.class.getDeclaredMethod(
                                "hasAliveBossMonsterInScene",
                                Scene.class,
                                SceneScriptManager.class,
                                entry.getClass());
                hasAlive.setAccessible(true);
                boolean alive = Boolean.TRUE.equals(hasAlive.invoke(null, scene, sm, entry));
                if (!alive) {
                    int wl = scene.getWorld() != null ? scene.getWorld().getWorldLevel() : 0;
                    Method level =
                            WorldBossSpawnHelper.class.getDeclaredMethod("resolveWorldBossLevel", Scene.class);
                    level.setAccessible(true);
                    int bossLv = (Integer) level.invoke(null, scene);
                    Grasscutter.getLogger()
                            .info(
                                    "WorldBossClaimHelper claimed - immediate respawn group {} (WL{} -> L{}), flowersRemoved={}",
                                    groupId,
                                    wl,
                                    bossLv,
                                    removed);
                    Method spawn =
                            WorldBossSpawnHelper.class.getDeclaredMethod(
                                    "ensureBossGroupLoadedAndSpawned",
                                    Scene.class,
                                    SceneScriptManager.class,
                                    entry.getClass());
                    spawn.setAccessible(true);
                    spawn.invoke(null, scene, sm, entry);
                }
                if (!WorldBossSpawnHelper.isJavaExclusiveBossGroup(groupId)) {
                    try {
                        sm.refreshGroupMonster(groupId);
                    } catch (Throwable ignored) {
                    }
                }
            }

            // Sweep again after respawn + delayed (finishOpen's 2s scheduleDespawn race).
            removeAllTrounceFlowers(scene, groupId);
            scheduleFlowerSweep(scene, groupId, 1);
            scheduleFlowerSweep(scene, groupId, 2);
            scheduleFlowerSweep(scene, groupId, 3);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("WorldBossClaimHelper.onClaimed failed", t);
        }
    }

    /** Remove every trounce flower for the group with VISION_REMOVE (no death smoke). */
    static int removeAllTrounceFlowers(Scene scene, int groupId) {
        if (scene == null || groupId <= 0) {
            return 0;
        }
        ArrayList<EntityGadget> flowers = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityGadget eg)) {
                continue;
            }
            if (eg.getGroupId() != groupId) {
                continue;
            }
            int gid = 0;
            try {
                gid = eg.getGadgetId();
            } catch (Throwable ignored) {
            }
            boolean idMatch = gid >= 70210106 && gid <= 70210112;
            SceneGadget meta = eg.getMetaGadget();
            boolean metaMatch = meta != null && meta.boss_chest != null;
            if (idMatch || metaMatch) {
                flowers.add(eg);
            }
        }
        for (EntityGadget eg : flowers) {
            try {
                OpenWorldSpawnHelper.clearGroupEntityDeathRecord(scene, groupId, eg.getConfigId());
            } catch (Throwable ignored) {
            }
            scene.removeEntity(eg, VisionType.VisionType_VISION_REMOVE);
        }
        return flowers.size();
    }

    private static void scheduleFlowerSweep(Scene scene, int groupId, int delaySec) {
        try {
            scene.getScheduler()
                    .scheduleDelayedTask(
                            () -> {
                                try {
                                    int n = removeAllTrounceFlowers(scene, groupId);
                                    if (n > 0) {
                                        Grasscutter.getLogger()
                                                .info(
                                                        "WorldBossClaimHelper delayed flower sweep removed {} group={}",
                                                        n,
                                                        groupId);
                                    }
                                } catch (Throwable t) {
                                    Grasscutter.getLogger()
                                            .debug("WorldBossClaimHelper flower sweep: {}", t.toString());
                                }
                            },
                            delaySec);
        } catch (Throwable ignored) {
        }
    }
}
