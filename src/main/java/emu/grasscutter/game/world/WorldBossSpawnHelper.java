/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.GameData
 *  emu.grasscutter.data.excels.InvestigationMonsterData
 *  emu.grasscutter.data.excels.world.WorldLevelData
 *  emu.grasscutter.game.entity.EntityGadget
 *  emu.grasscutter.game.entity.EntityMonster
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.entity.gadget.platform.BaseRoute
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.world.Position
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.game.world.SceneGroupInstance
 *  emu.grasscutter.scripts.SceneIndexManager
 *  emu.grasscutter.scripts.SceneScriptManager
 *  emu.grasscutter.scripts.data.SceneBlock
 *  emu.grasscutter.scripts.data.SceneBossChest
 *  emu.grasscutter.scripts.data.SceneGadget
 *  emu.grasscutter.scripts.data.SceneGroup
 *  emu.grasscutter.scripts.data.SceneMeta
 *  emu.grasscutter.scripts.data.SceneMonster
 *  emu.grasscutter.scripts.data.SceneSuite
 *  emu.grasscutter.utils.FileUtils
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
 */
package emu.grasscutter.game.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.InvestigationMonsterData;
import emu.grasscutter.data.excels.world.WorldLevelData;
import emu.grasscutter.game.drop.BossChestDropTagResolver;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.gadget.platform.BaseRoute;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.BossFlowerGuard;
import emu.grasscutter.game.world.OpenWorldSpawnHelper;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.SceneIndexManager;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneBossChest;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMeta;
import emu.grasscutter.scripts.data.SceneMonster;
import emu.grasscutter.scripts.data.SceneSuite;
import emu.grasscutter.utils.FileUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorldBossSpawnHelper {
    private static final int OPEN_WORLD_SCENE_ID = 3;
    private static final int MIN_BOSS_INVESTIGATION_ID = 6;
    private static final int MIN_PATCH_BLOCK_ID = 3402;
    private static final int BLOCK_SIZE = 1024;
    private static final int BOSS_REFRESH_ID = 1003;
    private static final int MANUAL_BOSS_REFRESH_ID = 999999;
    private static final int BOSS_FLOWER_CLEAR_DISTANCE = 100;
    private static final int BOSS_SPAWN_DISTANCE = 220;
    private static final int BOSS_CHEST_OPENED_STATE = 102;
    private static final int TROUNCE_FLOWER_GADGET_ID = 70210106;
    private static final int[] WORLD_BOSS_LEVEL_BY_WORLD_LEVEL = new int[]{36, 37, 41, 50, 62, 72, 83, 91, 93, 100};
    private static final long SPAWN_RETRY_MS = 5000L;
    private static final long NEARBY_CHECK_INTERVAL_MS = 1000L;
    private static final long TELEPORT_GRACE_MS = 10000L;
    private static final long KICKSTART_DEFER_MS = 6000L;
    private static volatile Int2ObjectMap<BossSpawnEntry> bossByGroupId;
    private static volatile Set<Integer> bossMonsterConfigIds;
    private static volatile Map<Integer, BlockPatchInfo> blockPatchById;
    private static volatile Set<Integer> excludedBossGroupIds;
    private static final ConcurrentHashMap<Long, Long> lastBossSpawnAttemptMs;
    private static final ConcurrentHashMap<Integer, Long> lastNearbyCheckMs;
    private static final Set<Long> awaitingFlowerKeys;
    /** Cleared flower while the player was far - spawn only when someone is near again, which avoids
     * invisible far-spawns. */
    private static final Set<Long> pendingRespawnKeys;
    private static final long LOGIN_GRACE_MS = 12000L;
    private static final ConcurrentHashMap<Integer, Long> loginGraceUntilMs;
    private static final ConcurrentHashMap<Integer, Long> teleportGraceUntilMs;
    private static final ConcurrentHashMap<Integer, Long> kickstartAfterMs;
    private static final int WATCHER_PRIMO_BLOCK_ID = 4104;
    private static final int WATCHER_PRIMO_GROUP_ID = 134104001;
    private static final Set<Long> javaExclusiveBossGroupsReady;

    private WorldBossSpawnHelper() {
    }

    public static void markPlayerLoginGrace(Player var0) {
        if (var0 != null) {
            loginGraceUntilMs.put(var0.getUid(), System.currentTimeMillis() + 12000L);
        }
    }

    public static void markPlayerTeleportGrace(Player var0) {
        if (var0 != null) {
            long var1 = System.currentTimeMillis() + 10000L;
            teleportGraceUntilMs.put(var0.getUid(), var1);
            loginGraceUntilMs.put(var0.getUid(), var1);
        }
    }

    public static void clearLoginGrace(Player var0) {
        if (var0 != null) {
            loginGraceUntilMs.remove(var0.getUid());
        }
    }

    public static boolean isInLoginGrace(int uid) {
        Long until = loginGraceUntilMs.get(uid);
        return until != null && System.currentTimeMillis() < until;
    }

    public static void kickstartNearbyBosses(Scene scene, Player player) {
        if (player != null) {
            WorldBossSpawnHelper.clearLoginGrace(player);
            try {
                kickstartAfterMs.remove(player.getUid());
                teleportGraceUntilMs.remove(player.getUid());
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if (scene == null || scene.getId() != 3) {
            return;
        }
        lastNearbyCheckMs.remove(scene.getId());
        try {
            Iterator iterator = ((ConcurrentHashMap.KeySetView)lastBossSpawnAttemptMs.keySet()).iterator();
            int n = scene.getId();
            while (iterator.hasNext()) {
                long l = (Long)iterator.next();
                if ((int)(l >> 32) != n) continue;
                iterator.remove();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        WorldBossSpawnHelper.ensureNearbyBosses(scene);
    }

    private static boolean isLoginGraceActive(Scene var0) {
        if (var0 != null && !var0.getPlayers().isEmpty()) {
            long var1 = System.currentTimeMillis();
            for (Player var4 : var0.getPlayers()) {
                Long var5 = loginGraceUntilMs.get(var4.getUid());
                if (var5 == null) continue;
                if (var1 < var5) {
                    return true;
                }
                loginGraceUntilMs.remove(var4.getUid(), var5);
            }
            return false;
        }
        return false;
    }

    private static boolean isTeleportGraceActive(Scene var0) {
        if (var0 != null && !var0.getPlayers().isEmpty()) {
            long var1 = System.currentTimeMillis();
            for (Player var4 : var0.getPlayers()) {
                Long var5 = teleportGraceUntilMs.get(var4.getUid());
                if (var5 == null) continue;
                if (var1 < var5) {
                    return true;
                }
                teleportGraceUntilMs.remove(var4.getUid(), var5);
            }
            return false;
        }
        return false;
    }

    private static boolean isKickstartDeferred(Scene var0) {
        if (var0 != null && !var0.getPlayers().isEmpty()) {
            long var1 = System.currentTimeMillis();
            for (Player var4 : var0.getPlayers()) {
                Long var5 = kickstartAfterMs.get(var4.getUid());
                if (var5 == null) continue;
                if (var1 < var5) {
                    return true;
                }
                kickstartAfterMs.remove(var4.getUid(), var5);
                WorldBossSpawnHelper.clearLoginGrace(var4);
                teleportGraceUntilMs.remove(var4.getUid());
            }
            return false;
        }
        return false;
    }

    private static boolean isSpawnGraceActive(Scene var0) {
        return WorldBossSpawnHelper.isLoginGraceActive(var0) || WorldBossSpawnHelper.isTeleportGraceActive(var0) || WorldBossSpawnHelper.isKickstartDeferred(var0);
    }

    private static boolean isScene3OpenWorldGroup(int var0) {
        return var0 >= 133000000 && var0 < 134000000 || var0 >= 134000000 && var0 < 135000000;
    }

    private static boolean shouldRegisterSyntheticBlock(int var0, int var1) {
        return var1 != 4104 && !WorldBossSpawnHelper.isJavaExclusiveBossGroup(var0) ? WorldBossSpawnHelper.isScene3OpenWorldGroup(var0) && var1 >= 3402 : false;
    }

    public static void ensureMetaBlocks(SceneMeta var0, int var1) {
        if (var0 != null && var1 == 3) {
            WorldBossSpawnHelper.ensureIndexBuilt();
            if (!blockPatchById.isEmpty()) {
                boolean var2 = false;
                if (var0.blocks.remove(4104) != null) {
                    var2 = true;
                    Grasscutter.getLogger().info("WorldBossSpawnHelper removed unstable block {} from scene meta (Watcher Primo is Java-isolated)", (Object)4104);
                }
                for (BlockPatchInfo var4 : blockPatchById.values()) {
                    if (var0.blocks.containsKey(var4.blockId)) continue;
                    SceneBlock var5 = new SceneBlock();
                    var5.id = var4.blockId;
                    var5.min = new Position((float)var4.minX, 0.0f, (float)var4.minZ);
                    var5.max = new Position((float)var4.maxX, 0.0f, (float)var4.maxZ);
                    var5.groups = new HashMap();
                    var0.blocks.put(var4.blockId, var5);
                    var2 = true;
                    Grasscutter.getLogger().info("WorldBossSpawnHelper added missing scene block {} [{},{}]-[{},{}]", new Object[]{var4.blockId, var4.minX, var4.minZ, var4.maxX, var4.maxZ});
                }
                if (var2) {
                    var0.sceneBlockIndex = SceneIndexManager.buildIndex((int)2, var0.blocks.values(), SceneBlock::toRectangle);
                }
            }
        }
    }

    public static boolean isJavaExclusiveBossGroup(int var0) {
        return var0 == 134104001;
    }

    public static boolean tryLoadSyntheticBlock(SceneBlock var0, int var1) {
        return false;
    }

    public static void injectBossGroups(SceneBlock var0, int var1) {
        if (var0 != null && var1 == 3) {
            if (var0.groups == null) {
                var0.groups = new HashMap();
            }
            WorldBossSpawnHelper.ensureIndexBuilt();
            for (BossSpawnEntry var3 : bossByGroupId.values()) {
                if (var3.blockId != var0.id || WorldBossSpawnHelper.isJavaExclusiveBossGroup(var3.groupId) || var0.groups.containsKey(var3.groupId)) continue;
                SceneGroup var4 = SceneGroup.of((int)var3.groupId);
                var4.block_id = var0.id;
                var4.pos = var3.position.clone();
                var4.refresh_id = var3.investigationId > 0 ? 1003 : 999999;
                // Keep world bosses pinned: otherwise checkGroups unloads and reloads every tick, causing flicker.
                var4.dontUnload = true;
                var4.dynamic_load = true;
                var0.groups.put(var3.groupId, var4);
                Grasscutter.getLogger().debug("WorldBossSpawnHelper injected group {} into block {}", (Object)var3.groupId, (Object)var0.id);
            }
            if (var0.sceneGroupIndex != null && !var0.groups.isEmpty()) {
                var0.sceneGroupIndex = SceneIndexManager.buildIndex((int)3, var0.groups.values(), var0x -> var0x.pos.toPoint());
            }
        }
    }

    private static boolean isExcludedComplexBossGroup(int var0) {
        WorldBossSpawnHelper.ensureExcludedBossGroupsLoaded();
        return excludedBossGroupIds.contains(var0);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private static void ensureExcludedBossGroupsLoaded() {
        if (excludedBossGroupIds != null) return;
        Class<WorldBossSpawnHelper> clazz = WorldBossSpawnHelper.class;
        synchronized (WorldBossSpawnHelper.class) {
            if (excludedBossGroupIds != null) return;
            HashSet<Integer> var1 = new HashSet<Integer>();
            Path var2 = Path.of("resources", "patch", "world-boss-exclude.json");
            if (Files.isRegularFile(var2, new LinkOption[0])) {
                try (BufferedReader var3 = Files.newBufferedReader(var2, StandardCharsets.UTF_8);){
                    for (JsonElement var6 : JsonParser.parseReader((Reader)var3).getAsJsonArray()) {
                        if (!var6.isJsonPrimitive() || !var6.getAsJsonPrimitive().isNumber()) continue;
                        var1.add(var6.getAsInt());
                    }
                }
                catch (IOException var10) {
                    Grasscutter.getLogger().warn("WorldBossSpawnHelper failed to load world-boss-exclude.json", (Throwable)var10);
                }
            }
            excludedBossGroupIds = Set.copyOf(var1);
            if (var1.isEmpty()) return;
            Grasscutter.getLogger().info("WorldBossSpawnHelper excluded {} complex boss group(s): {}", (Object)var1.size(), var1);
            // ** MonitorExit[var0] (shouldn't be in output)
            return;
        }
    }

    public static boolean isWorldBossGroup(int var0) {
        if (WorldBossSpawnHelper.isExcludedComplexBossGroup(var0)) {
            return false;
        }
        WorldBossSpawnHelper.ensureIndexBuilt();
        return bossByGroupId.containsKey(var0);
    }

    public static boolean isBossRespawnBlocked(Scene var0, int var1) {
        if (var0 != null && var1 > 0) {
            WorldBossSpawnHelper.ensureIndexBuilt();
            BossSpawnEntry var2 = (BossSpawnEntry)bossByGroupId.get(var1);
            if (var2 == null) {
                return false;
            }
            long var3 = WorldBossSpawnHelper.spawnAttemptKey(var0.getId(), var1);
            // Only block while a real unclaimed flower is waiting to be claimed.
            // Dead corpses alone must NOT block forever (empty arena after leaving flower).
            if (WorldBossSpawnHelper.hasUnclaimedBossChest(var0, var2)) {
                awaitingFlowerKeys.add(var3);
                return true;
            }
            if (awaitingFlowerKeys.contains(var3)) {
                // Stale await with no flower entity - clear it and allow a respawn.
                awaitingFlowerKeys.remove(var3);
                return false;
            }
            return false;
        }
        return false;
    }

    public static int resolveLevelForBossConfigId(Scene var0, int var1) {
        WorldBossSpawnHelper.ensureIndexBuilt();
        return var1 > 0 && bossMonsterConfigIds.contains(var1) ? WorldBossSpawnHelper.resolveWorldBossLevel(var0) : 0;
    }

    public static int resolveLevelForBossMonster(Scene var0, int var1) {
        int var2 = WorldBossSpawnHelper.resolveLevelForBossConfigId(var0, var1);
        if (var2 > 0) {
            return var2;
        }
        WorldBossSpawnHelper.ensureIndexBuilt();
        for (BossSpawnEntry var4 : bossByGroupId.values()) {
            if (var4.monsterId != var1) continue;
            return WorldBossSpawnHelper.resolveWorldBossLevel(var0);
        }
        return 0;
    }

    public static int resolveWorldBossLevel(Scene scene) {
        int n;
        WorldLevelData worldLevelData;
        int n2 = 0;
        if (scene != null && scene.getWorld() != null) {
            n2 = scene.getWorld().getWorldLevel();
        }
        if (n2 < 0) {
            n2 = 0;
        }
        if ((worldLevelData = (WorldLevelData)GameData.getWorldLevelDataMap().get(n2)) != null && (n = worldLevelData.getMonsterLevel()) > 0) {
            return n;
        }
        int[] nArray = new int[]{36, 37, 41, 50, 62, 72, 83, 91, 93, 103};
        return n2 >= nArray.length ? nArray[nArray.length - 1] : nArray[n2];
    }

    public static SceneGadget ensureBossChestGadgetMeta(EntityGadget var0) {
        if (var0 == null) {
            return null;
        }
        SceneGadget var1 = var0.getMetaGadget();
        if (var1 != null && var1.boss_chest != null && var1.drop_tag != null) {
            return var1;
        }
        if (var0.getScene() != null && WorldBossSpawnHelper.isWorldBossGroup(var0.getGroupId())) {
            SceneGadget var5;
            WorldBossSpawnHelper.ensureIndexBuilt();
            BossSpawnEntry var2 = (BossSpawnEntry)bossByGroupId.get(var0.getGroupId());
            if (var2 == null) {
                return var1;
            }
            SceneScriptManager var3 = var0.getScene().getScriptManager();
            if (var3 == null) {
                return var1;
            }
            SceneGroup var4 = WorldBossSpawnHelper.ensureGroupDefinitionsLoaded(var3, var2);
            SceneGadget sceneGadget = var5 = var4 != null ? (SceneGadget)var4.gadgets.get(var0.getConfigId()) : null;
            if (var5 == null) {
                int var6 = WorldBossSpawnHelper.resolveMonsterConfigId(var3, var2);
                var5 = WorldBossSpawnHelper.resolveBossChestGadgetWithFallback(var4, var2, var6);
            }
            if (var5 == null) {
                return var1;
            }
            var5.group = var4;
            var0.setMetaGadget(var5);
            return var5;
        }
        return var1;
    }

    public static void onBossChestClaimed(EntityGadget var0) {
        // Delegate to claim helper: remove flower, suppress re-flower, delayed boss respawn.
        WorldBossClaimHelper.onClaimed(var0);
    }

    public static void onWorldBossKilled(EntityMonster var0) {
        int var1;
        if (var0 == null || var0.getScene() == null) return;
        var1 = var0.getGroupId();
        if (!WorldBossSpawnHelper.isWorldBossGroup(var1)) {
            if (var0.getMonsterData() != null
                    && var0.getMonsterData().getType() == emu.grasscutter.game.props.MonsterType.MONSTER_BOSS) {
                Grasscutter.getLogger().info(
                        "World boss death not indexed scene={} group={} cfg={} monster={} - no reward flower rule",
                        var0.getScene().getId(), var1, var0.getConfigId(), var0.getEntityTypeId());
            }
            return;
        }
        WorldBossSpawnHelper.ensureIndexBuilt();
        BossSpawnEntry var2 = (BossSpawnEntry)bossByGroupId.get(var1);
        // Accept form variants (e.g. 26230301/02/03) - an exact id mismatch previously skipped flower tracking.
        if (var2 == null || !WorldBossSpawnHelper.isMatchingBossMonsterId(var2.monsterId, var0.getEntityTypeId())) {
            Grasscutter.getLogger().warn(
                    "World boss death ID mismatch scene={} group={} cfg={} actualMonster={} expectedMonster={}",
                    var0.getScene().getId(), var1, var0.getConfigId(), var0.getEntityTypeId(),
                    var2 != null ? var2.monsterId : 0);
            return;
        }
        // Real combat kill must always drop a flower. Claim/forceRespawn suppress only blocks
        // corpse/script re-drops - clearing here fixes "killed too fast, no flower".
        WorldBossClaimHelper.clearFlowerSuppress(var1);
        Scene var3 = var0.getScene();
        long key = WorldBossSpawnHelper.spawnAttemptKey(var3.getId(), var1);
        awaitingFlowerKeys.add(key);
        pendingRespawnKeys.remove(key);
        lastBossSpawnAttemptMs.remove(key);
        WorldBossSpawnHelper.removeDeadBossMonsters(var3, var2);
        SceneScriptManager var4 = var3.getScriptManager();
        if (var4 != null) {
            WorldBossSpawnHelper.resolveBossGroup(var3, var4, var2);
        }
        boolean existingFlower = WorldBossSpawnHelper.hasUnclaimedBossChest(var3, var2);
        WorldBossSpawnHelper.spawnTrounceFlowerIfMissing(var3, var2, var0.getConfigId());
        if (WorldBossSpawnHelper.hasUnclaimedBossChest(var3, var2)) {
            // A pre-existing script flower may have been sent before its boss_chest
            // metadata was attached. Refresh its proto so the client sees the claim action.
            if (existingFlower) {
                for (GameEntity entity : var3.getEntities().values()) {
                    if (!(entity instanceof EntityGadget flower)
                            || flower.getGroupId() != var1 || flower.getState() == 102
                            || flower.getMetaGadget() == null
                            || flower.getMetaGadget().boss_chest == null) continue;
                    flower.buildContent();
                    var3.broadcastPacket(new emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify(
                            flower,
                            emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH,
                            0));
                }
            }
            Grasscutter.getLogger().info("WorldBossSpawnHelper boss killed group {} cfg {} monster {} - claimable flower ready", var1, var0.getConfigId(), var0.getEntityTypeId());
        } else {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper boss killed group {} cfg {} monster {} - no claimable flower spawned", var1, var0.getConfigId(), var0.getEntityTypeId());
        }
    }

    private static boolean isMatchingBossMonsterId(int expected, int actual) {
        if (expected <= 0 || actual <= 0) {
            return false;
        }
        if (expected == actual) {
            return true;
        }
        // Same 6-digit family with small suffix drift (01/02/03...).
        return expected / 100 == actual / 100 && Math.abs(expected - actual) <= 10;
    }

    public static void ensureNearbyBosses(Scene var0) {
        if (var0 == null || var0.getPlayers().isEmpty()) {
            return;
        }
        long var1 = System.currentTimeMillis();
        Long var3 = lastNearbyCheckMs.get(var0.getId());
        if (var3 != null && var1 - var3 < 1000L) {
            return;
        }
        lastNearbyCheckMs.put(var0.getId(), var1);
        WorldBossSpawnHelper.ensureIndexBuilt();
        SceneScriptManager var4 = var0.getScriptManager();
        if (bossByGroupId.isEmpty() || var4 == null) {
            return;
        }
        if (!WorldBossSpawnHelper.isScriptReady(var4)) {
            Grasscutter.getLogger().debug("WorldBossSpawnHelper scene {} script manager not ready yet", (Object)var0.getId());
            return;
        }
        boolean grace = WorldBossSpawnHelper.isSpawnGraceActive(var0);
        for (BossSpawnEntry var6 : bossByGroupId.values()) {
            try {
                // Teleport/login grace must NOT skip flower-abandon, or unclaimed flowers
                // unload with the block and the boss never comes back.
                if (grace) {
                    WorldBossSpawnHelper.processFlowerAbandonOnly(var0, var4, var6);
                } else {
                    WorldBossSpawnHelper.processNearbyBossEntry(var0, var4, var6, var1);
                }
            } catch (Throwable var8) {
                Grasscutter.getLogger().warn("WorldBossSpawnHelper skipped boss group {}: {}", (Object)var6.groupId, (Object)var8.toString());
            }
        }
    }

    private static void processFlowerAbandonOnly(Scene scene, SceneScriptManager sm, BossSpawnEntry entry) {
        if (scene == null || entry == null) {
            return;
        }
        long key = WorldBossSpawnHelper.spawnAttemptKey(scene.getId(), entry.groupId);
        boolean flowerPending =
                WorldBossSpawnHelper.hasUnclaimedBossChest(scene, entry)
                        || awaitingFlowerKeys.contains(key);
        double dist = WorldBossSpawnHelper.nearestPlayerDistance(scene, entry);
        if (flowerPending && dist > (double) BOSS_FLOWER_CLEAR_DISTANCE) {
            WorldBossSpawnHelper.abandonTrounceFlowerAndRespawn(scene, sm, entry, key);
        }
    }

    private static void processNearbyBossEntry(Scene var0, SceneScriptManager var1, BossSpawnEntry var2, long var3) {
        if (var0 == null || var2 == null) {
            return;
        }
        // Lupus Boreas: never auto-spawn on approach; the intro has to be started via the trial option.
        try {
            if (emu.grasscutter.game.world.AndriusTrialStartHelper.shouldBlockAutoSpawn(var2.groupId)) {
                return;
            }
        } catch (Throwable ignored) {
        }
        int n = var2.groupId;
        long var5 = WorldBossSpawnHelper.spawnAttemptKey(var0.getId(), var2.groupId);
        double var7 = WorldBossSpawnHelper.nearestPlayerDistance(var0, var2);

        // Trounce blossom: if the player is too far, only clear the flower and mark it pending. Spawning at
        // range would create a boss the client cannot see.
        boolean flowerPending =
                WorldBossSpawnHelper.hasUnclaimedBossChest(var0, var2)
                        || awaitingFlowerKeys.contains(var5);
        if (flowerPending && var7 > (double) BOSS_FLOWER_CLEAR_DISTANCE) {
            WorldBossSpawnHelper.abandonTrounceFlowerAndRespawn(var0, var1, var2, var5);
            return;
        }

        if (n > 0 && WorldBossSpawnHelper.isBossRespawnBlocked(var0, n)) {
            try {
                if (WorldBossSpawnHelper.hasAliveBossMonsterInScene(var0, var1, var2)
                        && WorldBossSpawnHelper.isAliveBossNearAnyPlayer(var0, var2)) {
                    awaitingFlowerKeys.remove(var5);
                    lastBossSpawnAttemptMs.remove(var5);
                    WorldBossClaimHelper.removeAllTrounceFlowers(var0, n);
                    WorldBossSpawnHelper.removeDeadBossMonsters(var0, var2);
                    return;
                }
            } catch (Throwable throwable) {
                // empty catch block
            }
            try {
                WorldBossSpawnHelper.removeDeadBossMonsters(var0, var2);
            } catch (Throwable throwable) {
                // empty catch block
            }
            return;
        }

        if (var7 > 400.0) {
            return;
        }
        if (WorldBossSpawnHelper.hasUnclaimedBossChest(var0, var2)) {
            awaitingFlowerKeys.add(var5);
            lastBossSpawnAttemptMs.remove(var5);
            return;
        }
        if (WorldBossSpawnHelper.hasDeadBossMonsterInScene(var0, var2)) {
            WorldBossSpawnHelper.removeDeadBossMonsters(var0, var2);
            try {
                OpenWorldSpawnHelper.clearGroupDeathRecords(var0, var2.groupId);
            } catch (Throwable ignored) {
            }
        }
        if (awaitingFlowerKeys.contains(var5)) {
            awaitingFlowerKeys.remove(var5);
        }

        if (var7 > (double) BOSS_SPAWN_DISTANCE) {
            return;
        }

        boolean pending = pendingRespawnKeys.contains(var5);
        boolean alive = WorldBossSpawnHelper.hasAliveBossMonsterInScene(var0, var1, var2);
        boolean nearPlayer = alive && WorldBossSpawnHelper.isAliveBossNearAnyPlayer(var0, var2);

        if (alive && nearPlayer && !pending) {
            int var14 = WorldBossSpawnHelper.resolveWorldBossLevel(var0);
            WorldBossSpawnHelper.removeNonJavaBossMonsters(var0, var2, var14);
            WorldBossSpawnHelper.dedupeJavaPreferredBoss(var0, var2, var14);
            WorldBossClaimHelper.removeAllTrounceFlowers(var0, var2.groupId);
            awaitingFlowerKeys.remove(var5);
            lastBossSpawnAttemptMs.remove(var5);
            return;
        }

        // Empty arena or pending after abandon. Do NOT thrash-respawn an alive boss that is
        // merely outside a tight ring (same single-authority path as Fontaine/Natlan).
        if (alive && !nearPlayer) {
            // True orphan: a live boss far from every player - despawn only and wait for an approach.
            try {
                BossFlowerGuard.despawnAliveBossMonsters(var0, var2.groupId, var2.monsterId);
            } catch (Throwable ignored) {
            }
            WorldBossSpawnHelper.removeDeadBossMonsters(var0, var2);
            Grasscutter.getLogger()
                    .info(
                            "WorldBossSpawnHelper despawned orphan boss group {} monster {} ({}m); no forceRespawn",
                            var2.groupId,
                            var2.monsterId,
                            (int) var7);
            return;
        }

        Player var10 = WorldBossSpawnHelper.findNearestPlayerToBoss(var0, var2);
        if (var10 == null) {
            return;
        }
        Long var13 = lastBossSpawnAttemptMs.get(var5);
        if (var13 != null && var3 - var13 < 2500L) {
            return;
        }
        lastBossSpawnAttemptMs.put(var5, var3);
        Grasscutter.getLogger()
                .info(
                        "WorldBossSpawnHelper nearby respawn uid={} group {} monster {} ({}m) pending={} ghost={}",
                        var10.getUid(),
                        var2.groupId,
                        var2.monsterId,
                        (int) var7,
                        pending,
                        false);
        WorldBossSpawnHelper.forceRespawnBossNow(var0, var1, var2, pending ? "pending-near" : "empty-arena");
        pendingRespawnKeys.remove(var5);
    }

    /**
     * True if an alive boss entity for this group is within spawn range of any player.
     * Must match {@link #BOSS_SPAWN_DISTANCE}: a tighter radius (e.g. 80) with a 220m spawn
     * ring marks the boss as "ghost" while the player is still approaching, causing forceRespawn
     * thrash, leaving the client with a stale entity id that is untargetable and takes no damage.
     */
    private static boolean isAliveBossNearAnyPlayer(Scene scene, BossSpawnEntry entry) {
        if (scene == null || entry == null || scene.getPlayers().isEmpty()) {
            return false;
        }
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster)) continue;
            EntityMonster em = (EntityMonster) ge;
            if (em.getGroupId() != entry.groupId
                    || !WorldBossSpawnHelper.isMatchingBossMonsterId(entry.monsterId, em.getEntityTypeId())
                    || !em.isAlive()
                    || em.getPosition() == null) {
                continue;
            }
            for (Player p : scene.getPlayers()) {
                if (p.getPosition() != null
                        && p.getPosition().computeDistance(em.getPosition())
                                <= (double) BOSS_SPAWN_DISTANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Clear flower/death state and force the world boss back into the scene (player should be near).
     */
    private static void forceRespawnBossNow(
            Scene scene, SceneScriptManager sm, BossSpawnEntry entry, String reason) {
        if (scene == null || entry == null) {
            return;
        }
        long key = WorldBossSpawnHelper.spawnAttemptKey(scene.getId(), entry.groupId);
        try {
            awaitingFlowerKeys.remove(key);
            WorldBossClaimHelper.removeAllTrounceFlowers(scene, entry.groupId);
            try {
                BossFlowerGuard.despawnAliveBossMonsters(scene, entry.groupId, entry.monsterId);
            } catch (Throwable ignored) {
            }
            WorldBossSpawnHelper.removeDeadBossMonsters(scene, entry);
            try {
                if (sm != null) {
                    SceneGroupInstance deadInst = sm.getGroupInstanceById(entry.groupId);
                    if (deadInst != null) {
                        deadInst.getDeadEntities().clear();
                        deadInst.save();
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                OpenWorldSpawnHelper.clearGroupDeathRecords(scene, entry.groupId);
            } catch (Throwable ignored) {
            }
            WorldBossClaimHelper.suppressFlower(entry.groupId, 1500L);

            boolean alive = false;
            if (sm != null) {
                SceneGroup group = WorldBossSpawnHelper.resolveBossGroup(scene, sm, entry);
                SceneGroupInstance inst = sm.getGroupInstanceById(entry.groupId);
                if (group != null) {
                    WorldBossSpawnHelper.forceSpawnBossMonsters(sm, inst, group, entry, null, true);
                }
                alive = WorldBossSpawnHelper.hasAliveBossMonsterInScene(scene, sm, entry);
                if (!alive) {
                    WorldBossSpawnHelper.ensureBossGroupLoadedAndSpawned(scene, sm, entry);
                    alive = WorldBossSpawnHelper.hasAliveBossMonsterInScene(scene, sm, entry);
                }
                if (!alive && group != null) {
                    WorldBossSpawnHelper.forceSpawnBossMonsters(sm, inst, group, entry, null, true);
                    alive = WorldBossSpawnHelper.hasAliveBossMonsterInScene(scene, sm, entry);
                }
            }
            if (!alive) {
                try {
                    WorldBossDirectSpawner.spawnBossAt(
                            scene,
                            entry.groupId,
                            entry.monsterId,
                            entry.position.getX(),
                            entry.position.getY(),
                            entry.position.getZ());
                    alive = WorldBossSpawnHelper.hasAliveBossMonsterInScene(scene, sm, entry);
                } catch (Throwable t) {
                    Grasscutter.getLogger()
                            .warn(
                                    "WorldBossSpawnHelper DirectSpawner fallback failed group {}: {}",
                                    entry.groupId,
                                    t.toString());
                }
            }
            if (alive) {
                pendingRespawnKeys.remove(key);
            }
            Grasscutter.getLogger()
                    .info(
                            "WorldBossSpawnHelper forceRespawn reason={} group={} monster={} alive={}",
                            reason,
                            entry.groupId,
                            entry.monsterId,
                            alive);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn(
                            "WorldBossSpawnHelper forceRespawn failed group {}: {}",
                            entry.groupId,
                            t.toString());
        }
    }

    /**
     * Player left the trounce flower: remove flower only; respawn when they come back near.
     */
    private static void abandonTrounceFlowerAndRespawn(
            Scene scene, SceneScriptManager sm, BossSpawnEntry entry, long key) {
        if (scene == null || entry == null) {
            return;
        }
        try {
            int removed = WorldBossClaimHelper.removeAllTrounceFlowers(scene, entry.groupId);
            awaitingFlowerKeys.remove(key);
            lastBossSpawnAttemptMs.remove(key);
            WorldBossSpawnHelper.removeDeadBossMonsters(scene, entry);
            try {
                BossFlowerGuard.despawnAliveBossMonsters(scene, entry.groupId, entry.monsterId);
            } catch (Throwable ignored) {
            }
            try {
                if (sm != null) {
                    SceneGroupInstance deadInst = sm.getGroupInstanceById(entry.groupId);
                    if (deadInst != null) {
                        deadInst.getDeadEntities().clear();
                        deadInst.save();
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                OpenWorldSpawnHelper.clearGroupDeathRecords(scene, entry.groupId);
            } catch (Throwable ignored) {
            }
            // Do NOT spawn while far - that creates client-invisible bosses which block a later respawn.
            pendingRespawnKeys.add(key);
            WorldBossClaimHelper.suppressFlower(entry.groupId, 3000L);
            Grasscutter.getLogger()
                    .info(
                            "WorldBossSpawnHelper abandoned trounce flower group {} (>{}m), flowersRemoved={}, pendingRespawn=true",
                            entry.groupId,
                            BOSS_FLOWER_CLEAR_DISTANCE,
                            removed);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn(
                            "WorldBossSpawnHelper abandon flower failed group {}: {}",
                            entry.groupId,
                            t.toString());
        }
    }

    private static boolean isScriptReady(SceneScriptManager var0) {
        return var0 != null && var0.isInit() && var0.getBlocks() != null && !var0.getBlocks().isEmpty();
    }

    private static boolean waitForScriptInit(SceneScriptManager var0) {
        for (int var1 = 0; var1 < 40; ++var1) {
            if (var0.isInit()) {
                return var0.getBlocks() != null && !var0.getBlocks().isEmpty();
            }
            try {
                Thread.sleep(100L);
                continue;
            }
            catch (InterruptedException var3) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return var0.isInit() && var0.getBlocks() != null && !var0.getBlocks().isEmpty();
    }

    public static void ensureBossMonstersAfterRefresh(SceneScriptManager sceneScriptManager, SceneGroupInstance sceneGroupInstance, SceneSuite sceneSuite, List<GameEntity> list) {
    }

    private static void ensureBossSpawnedOnce(Scene var0, SceneScriptManager var1, BossSpawnEntry var2, SceneGroup var3, SceneGroupInstance var4, List<GameEntity> var5) {
        int n;
        Field field;
        block32: {
            block31: {
                try {
                    if (var2 == null) {
                        return;
                    }
                    field = var2.getClass().getDeclaredField("groupId");
                    field.setAccessible(true);
                    n = field.getInt(var2);
                    int n2 = 0;
                    try {
                        Field field2 = var2.getClass().getDeclaredField("monsterId");
                        field2.setAccessible(true);
                        n2 = field2.getInt(var2);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    if (WorldBossSpawnHelper.isJavaExclusiveBossGroup(n)) break block31;
                    if (var0 == null || var1 == null) {
                        return;
                    }
                    if (WorldBossSpawnHelper.isBossRespawnBlocked(var0, n)) {
                        return;
                    }
                    boolean bl = false;
                    if (n2 > 0) {
                        for (Object v : var0.getEntities().values()) {
                            EntityMonster entityMonster;
                            if (!(v instanceof EntityMonster) || !(entityMonster = (EntityMonster)v).isAlive() || entityMonster.getMonsterData() == null || entityMonster.getMonsterData().getId() != n2) continue;
                            bl = true;
                            break;
                        }
                    }
                    if (bl) {
                        return;
                    }
                    try {
                        WorldBossSpawnHelper.ensureGroupDefinitionsLoaded(var1, var2);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    var1.refreshGroupMonster(n);
                    return;
                }
                catch (Throwable throwable) {
                    return;
                }
            }
            try {
                if (var2 == null) {
                    return;
                }
                field = var2.getClass().getDeclaredField("groupId");
                field.setAccessible(true);
                n = field.getInt(var2);
                if (!WorldBossSpawnHelper.isJavaExclusiveBossGroup(n)) {
                    return;
                }
            }
            catch (Throwable throwable) {
                return;
            }
            try {
                if (var2 == null) {
                    return;
                }
                field = var2.getClass().getDeclaredField("groupId");
                field.setAccessible(true);
                n = field.getInt(var2);
                if (WorldBossSpawnHelper.isJavaExclusiveBossGroup(n)) break block32;
                if (var0 != null && var1 != null && !WorldBossSpawnHelper.isBossRespawnBlocked(var0, n)) {
                    try {
                        WorldBossSpawnHelper.ensureGroupDefinitionsLoaded(var1, var2);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    var1.refreshGroupMonster(n);
                }
                return;
            }
            catch (Throwable throwable) {
                return;
            }
        }
        try {
            if (var2 == null) {
                return;
            }
            field = var2.getClass().getDeclaredField("groupId");
            field.setAccessible(true);
            n = field.getInt(var2);
            if (!WorldBossSpawnHelper.isJavaExclusiveBossGroup(n)) {
            }
        }
        catch (Throwable throwable) {
            return;
        }
        if (var0 != null && var1 != null && var2 != null && !WorldBossSpawnHelper.isBossRespawnBlocked(var0, var2.groupId)) {
            int var6 = WorldBossSpawnHelper.resolveWorldBossLevel(var0);
            WorldBossSpawnHelper.removeNonJavaBossMonsters(var0, var2, var6);
            if (WorldBossSpawnHelper.hasAliveJavaBossMonsterInScene(var0, var2, var6)) {
                WorldBossSpawnHelper.dedupeJavaPreferredBoss(var0, var2, var6);
            } else {
                var3 = WorldBossSpawnHelper.ensureGroupDefinitionsLoaded(var1, var2);
                if (var3 == null && var4 != null) {
                    var3 = var4.getLuaGroup();
                }
                if (var3 != null && WorldBossSpawnHelper.isAnyPlayerNearBoss(var0, var2, 220)) {
                    WorldBossSpawnHelper.forceSpawnBossMonsters(var1, var4, var3, var2, var5);
                    WorldBossSpawnHelper.removeNonJavaBossMonsters(var0, var2, var6);
                    WorldBossSpawnHelper.dedupeJavaPreferredBoss(var0, var2, var6);
                }
            }
        }
    }

    private static long spawnAttemptKey(int var0, int var1) {
        return (long)var0 << 32 | (long)var1 & 0xFFFFFFFFL;
    }

    private static double nearestPlayerDistance(Scene var0, BossSpawnEntry var1) {
        if (var0 != null && var1 != null && !var0.getPlayers().isEmpty() && var1.position != null) {
            double var2 = Double.MAX_VALUE;
            float bx = var1.position.getX();
            float bz = var1.position.getZ();
            for (Player var5 : var0.getPlayers()) {
                if (var5.getPosition() == null) continue;
                // Horizontal distance only - underwater bosses such as the Emperor have a large Y delta
                // that falsely triggered "left flower" abandon within seconds of the kill.
                float dx = var5.getPosition().getX() - bx;
                float dz = var5.getPosition().getZ() - bz;
                var2 = Math.min(var2, Math.sqrt(dx * dx + dz * dz));
            }
            return var2;
        }
        return Double.MAX_VALUE;
    }

    private static boolean isAnyPlayerNearBoss(Scene var0, BossSpawnEntry var1, int var2) {
        return WorldBossSpawnHelper.nearestPlayerDistance(var0, var1) <= (double)var2;
    }

    private static boolean isAnyPlayerNearBoss(Scene var0, BossSpawnEntry var1) {
        return WorldBossSpawnHelper.isAnyPlayerNearBoss(var0, var1, 400);
    }

    private static Player findNearestPlayerToBoss(Scene var0, BossSpawnEntry var1) {
        if (var0 != null && var1 != null && !var0.getPlayers().isEmpty()) {
            Player var2 = null;
            double var3 = Double.MAX_VALUE;
            for (Player var6 : var0.getPlayers()) {
                double var7 = var6.getPosition().computeDistance(var1.position);
                if (!(var7 < var3)) continue;
                var3 = var7;
                var2 = var6;
            }
            return var2;
        }
        return null;
    }

    private static boolean hasUnclaimedBossChest(Scene scene, BossSpawnEntry bossSpawnEntry) {
        boolean bl;
        block8: {
            if (scene == null || bossSpawnEntry == null) {
                return false;
            }
            int n = 0;
            float f = 0.0f;
            float f2 = 0.0f;
            float f3 = 0.0f;
            try {
                Field field = bossSpawnEntry.getClass().getDeclaredField("groupId");
                field.setAccessible(true);
                n = field.getInt(bossSpawnEntry);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                Field field = bossSpawnEntry.getClass().getDeclaredField("position");
                field.setAccessible(true);
                Object object = field.get(bossSpawnEntry);
                if (object instanceof Position) {
                    Position position = (Position)object;
                    f = position.getX();
                    f2 = position.getY();
                    f3 = position.getZ();
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            bl = BossFlowerGuard.hasUnclaimedFlower(scene, n, f, f2, f3);
            if (!bl || n <= 0) break block8;
            try {
                long l = WorldBossSpawnHelper.spawnAttemptKey(scene.getId(), n);
                awaitingFlowerKeys.add(l);
            }
            catch (Throwable throwable) {}
        }
        return bl;
    }

    private static int resolveBossChestConfigId(SceneScriptManager var0, BossSpawnEntry var1) {
        if (var0 != null && var1 != null) {
            int var2 = WorldBossSpawnHelper.resolveMonsterConfigId(var0, var1);
            SceneGroup var3 = WorldBossSpawnHelper.safeGetRegisteredGroup(var0, var1.groupId);
            SceneGadget var4 = WorldBossSpawnHelper.resolveBossChestGadget(var3, var1, var2);
            return var4 != null ? var4.config_id : 0;
        }
        return 0;
    }

    private static boolean isBossRespawnBlocked(Scene var0, BossSpawnEntry var1) {
        return var1 != null && WorldBossSpawnHelper.isBossRespawnBlocked(var0, var1.groupId);
    }

    private static void clearUnclaimedBossFlowers(Scene var0, BossSpawnEntry var1) {
        if (var0 != null && var1 != null) {
            ArrayList<EntityGadget> flowers = new ArrayList<EntityGadget>();
            for (GameEntity var4 : var0.getEntities().values()) {
                SceneGadget var6;
                EntityGadget var5;
                if (!(var4 instanceof EntityGadget) || (var5 = (EntityGadget)var4).getGroupId() != var1.groupId || (var6 = var5.getMetaGadget()) == null || var6.boss_chest == null || var5.getState() == 102) continue;
                flowers.add(var5);
            }
            for (EntityGadget var8 : flowers) {
                var0.removeEntity((GameEntity)var8);
            }
            if (!flowers.isEmpty()) {
                Grasscutter.getLogger().debug("WorldBossSpawnHelper cleared {} unclaimed trounce flower(s) for group {}", (Object)flowers.size(), (Object)var1.groupId);
            }
        }
    }

    private static void removeDeadBossMonsters(Scene var0, BossSpawnEntry var1) {
        if (var0 != null && var1 != null) {
            ArrayList<EntityMonster> deadBosses = new ArrayList<EntityMonster>();
            for (GameEntity var4 : var0.getEntities().values()) {
                EntityMonster var5;
                if (!(var4 instanceof EntityMonster) || (var5 = (EntityMonster)var4).getGroupId() != var1.groupId || var5.getEntityTypeId() != var1.monsterId || var5.isAlive()) continue;
                deadBosses.add(var5);
            }
            for (EntityMonster var7 : deadBosses) {
                var0.removeEntity((GameEntity)var7);
            }
        }
    }

    private static void spawnTrounceFlowerIfMissing(Scene var0, BossSpawnEntry var1, int var2) {
        if (var0 != null && var1 != null && !WorldBossClaimHelper.isFlowerSuppressed(var1.groupId) && !WorldBossSpawnHelper.hasUnclaimedBossChest(var0, var1)) {
            SceneScriptManager var3;
            WorldBossSpawnHelper.purgeOpenedBossChestGadgets(var0, var1, var2);
            if (!WorldBossSpawnHelper.hasUnclaimedBossChest(var0, var1) && (var3 = var0.getScriptManager()) != null) {
                SceneGadget var5;
                SceneGroup var4 = WorldBossSpawnHelper.ensureGroupDefinitionsLoaded(var3, var1);
                if (var4 == null) {
                    var4 = WorldBossSpawnHelper.resolveBossGroup(var0, var3, var1);
                }
                if ((var5 = WorldBossSpawnHelper.resolveBossChestGadgetWithFallback(var4, var1, var2)) != null) {
                    GameEntity var6;
                    if (var4 == null) {
                        var4 = SceneGroup.of((int)var1.groupId);
                        var4.block_id = var1.blockId;
                        var4.pos = var1.position.clone();
                        var5.group = var4;
                    }
                    if (var4.block_id <= 0) {
                        var4.block_id = var1.blockId;
                    }
                    var6 = var0.getEntityByConfigId(var5.config_id, var1.groupId);
                    if (var6 instanceof EntityGadget existing) {
                        if (existing.getMetaGadget() == null
                                || existing.getMetaGadget().boss_chest == null) {
                            WorldBossSpawnHelper.ensureBossChestGadgetMeta(existing);
                        }
                        if (existing.getMetaGadget() != null
                                && existing.getMetaGadget().boss_chest != null) {
                            existing.buildContent();
                        }
                    }
                    if (!(var6 instanceof EntityGadget existing
                            && existing.getContent() instanceof emu.grasscutter.game.entity.gadget.GadgetChest
                            && existing.getMetaGadget() != null
                            && existing.getMetaGadget().boss_chest != null)) {
                        if (var6 != null) {
                            var0.removeEntity(var6);
                        }
                        OpenWorldSpawnHelper.clearGroupEntityDeathRecord(var0, var1.groupId, var5.config_id);
                        EntityGadget var7 = WorldBossSpawnHelper.createTrounceFlowerEntity(var0, var4, var1, var5);
                        if (var7 == null) {
                            Grasscutter.getLogger().warn("WorldBossSpawnHelper failed to spawn trounce flower {} in group {}", (Object)var5.config_id, (Object)var1.groupId);
                        } else {
                            var0.addEntity((GameEntity)var7);
                            Grasscutter.getLogger().info("WorldBossSpawnHelper spawned trounce flower {} for group {}", (Object)var5.config_id, (Object)var1.groupId);
                        }
                    }
                }
            }
        }
    }

    private static EntityGadget createTrounceFlowerEntity(Scene var0, SceneGroup var1, BossSpawnEntry var2, SceneGadget var3) {
        if (var0 != null && var1 != null && var3 != null && var2 != null) {
            var3.group = var1;
            EntityGadget var4 = new EntityGadget(var0, var3.gadget_id, var3.pos);
            if (var4.getGadgetData() == null) {
                Grasscutter.getLogger().warn("WorldBossSpawnHelper unknown gadget_id {} for group {}", (Object)var3.gadget_id, (Object)var2.groupId);
                return null;
            }
            int var5 = var1.block_id > 0 ? var1.block_id : var2.blockId;
            var4.setBlockId(var5);
            var4.setConfigId(var3.config_id);
            var4.setGroupId(var2.groupId);
            if (var3.rot != null) {
                var4.getRotation().set(var3.rot);
            }
            var4.setState(0);
            var4.setPointType(var3.point_type);
            var4.setRouteConfig(BaseRoute.fromSceneGadget((SceneGadget)var3));
            var4.setMetaGadget(var3);
            var4.buildContent();
            return var4;
        }
        return null;
    }

    private static SceneGadget resolveBossChestGadgetWithFallback(SceneGroup var0, BossSpawnEntry var1, int var2) {
        SceneGadget var3 = WorldBossSpawnHelper.resolveBossChestGadget(var0, var1, var2);
        if (var3 != null) {
            return var3;
        }
        if (var1 == null) {
            return null;
        }
        SceneGadget var4 = WorldBossSpawnHelper.loadBossChestGadgetFromGroupScript(var1.groupId, var2);
        if (var4 == null) {
            var4 = WorldBossSpawnHelper.buildSyntheticBossChestGadget(var1, var2);
        }
        if (var4 == null) {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper no gadget defs for trounce flower in group {}", (Object)var1.groupId);
            return null;
        }
        if (var0 != null) {
            if (var0.gadgets == null) {
                var0.gadgets = new HashMap();
            }
            var0.gadgets.putIfAbsent(var4.config_id, var4);
            var4.group = var0;
        }
        return var4;
    }

    private static boolean hasBossMonsterInEntityList(List<GameEntity> var0, BossSpawnEntry var1) {
        if (var0 != null && var1 != null) {
            for (GameEntity var3 : var0) {
                EntityMonster var4;
                if (!(var3 instanceof EntityMonster) || (var4 = (EntityMonster)var3).getGroupId() != var1.groupId || var4.getEntityTypeId() != var1.monsterId || !var4.isAlive()) continue;
                return true;
            }
            return false;
        }
        return false;
    }

    private static SceneGadget resolveBossChestGadget(SceneGroup var0, BossSpawnEntry var1, int var2) {
        if (var0 != null && var0.gadgets != null && var1 != null) {
            SceneGadget var3 = null;
            for (SceneGadget var5 : var0.gadgets.values()) {
                SceneMonster var6;
                if (var5.boss_chest == null) continue;
                if (var2 > 0 && var5.boss_chest.monster_config_id == var2) {
                    return var5;
                }
                if (var3 != null || (var6 = var0.monsters != null ? (SceneMonster)var0.monsters.get(var5.boss_chest.monster_config_id) : null) == null || var6.monster_id != var1.monsterId) continue;
                var3 = var5;
            }
            return var3;
        }
        return null;
    }

    private static SceneGadget resolveBossChestGadgetFromScene(Scene var0, BossSpawnEntry var1, int var2) {
        SceneScriptManager var3;
        SceneScriptManager sceneScriptManager = var3 = var0 != null ? var0.getScriptManager() : null;
        if (var3 == null) {
            return null;
        }
        SceneGroup var4 = WorldBossSpawnHelper.safeGetRegisteredGroup(var3, var1.groupId);
        if (var4 == null) {
            var4 = WorldBossSpawnHelper.resolveBossGroup(var0, var3, var1);
        }
        return WorldBossSpawnHelper.resolveBossChestGadget(var4, var1, var2);
    }

    private static void purgeOpenedBossChestGadgets(Scene var0, BossSpawnEntry var1, int var2) {
        if (var0 != null && var1 != null) {
            ArrayList<EntityGadget> openedChests = new ArrayList<EntityGadget>();
            for (GameEntity var5 : var0.getEntities().values()) {
                EntityGadget var6;
                if (!(var5 instanceof EntityGadget) || (var6 = (EntityGadget)var5).getGroupId() != var1.groupId || var2 > 0 && var6.getConfigId() != var2 || var6.getState() != 102) continue;
                openedChests.add(var6);
            }
            for (EntityGadget var8 : openedChests) {
                OpenWorldSpawnHelper.clearGroupEntityDeathRecord(var0, var1.groupId, var8.getConfigId());
                var0.removeEntity((GameEntity)var8);
            }
        }
    }

    private static void purgeBossChestGadgets(Scene var0, BossSpawnEntry var1, int var2) {
        if (var0 != null && var1 != null) {
            ArrayList<EntityGadget> chestGadgets = new ArrayList<EntityGadget>();
            for (GameEntity var5 : var0.getEntities().values()) {
                EntityGadget var6;
                if (!(var5 instanceof EntityGadget) || (var6 = (EntityGadget)var5).getGroupId() != var1.groupId || var2 > 0 && var6.getConfigId() != var2) continue;
                SceneGadget var7 = var6.getMetaGadget();
                if (var2 <= 0 && (var7 == null || var7.boss_chest == null)) continue;
                chestGadgets.add(var6);
            }
            for (EntityGadget var9 : chestGadgets) {
                OpenWorldSpawnHelper.clearGroupEntityDeathRecord(var0, var1.groupId, var9.getConfigId());
                var0.removeEntity((GameEntity)var9);
            }
        }
    }

    private static SceneGroup safeGetRegisteredGroup(SceneScriptManager var0, int var1) {
        if (var0 != null && var1 > 0) {
            SceneGroup var2 = WorldBossSpawnHelper.getRegisteredWatcherGroup(var0, var1);
            if (var2 != null) {
                return var2;
            }
            try {
                SceneGroup var4;
                SceneGroupInstance var3 = var0.getGroupInstanceById(var1);
                if (var3 != null && (var4 = var3.getLuaGroup()) != null) {
                    return var4;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return null;
        }
        return null;
    }

    private static int resolveMonsterConfigId(SceneScriptManager var0, BossSpawnEntry var1) {
        if (var0 != null && var1 != null) {
            SceneGroup var2 = WorldBossSpawnHelper.safeGetRegisteredGroup(var0, var1.groupId);
            if (var2 == null) {
                return WorldBossSpawnHelper.resolveConfigId(var1.groupId);
            }
            if (var2.monsters != null) {
                for (SceneMonster var4 : var2.monsters.values()) {
                    if (var4.monster_id != var1.monsterId) continue;
                    return var4.config_id;
                }
            }
            if (var2.gadgets != null) {
                for (SceneGadget var6 : var2.gadgets.values()) {
                    if (var6.boss_chest == null) continue;
                    return var6.boss_chest.monster_config_id;
                }
            }
            return WorldBossSpawnHelper.resolveConfigId(var1.groupId);
        }
        return 0;
    }

    private static boolean hasGroupMonsterDefinitions(SceneGroup var0) {
        return var0 != null && var0.monsters != null && !var0.monsters.isEmpty();
    }

    private static boolean hasAliveJavaBossMonsterInScene(Scene var0, BossSpawnEntry var1, int var2) {
        if (var0 != null && var1 != null) {
            for (GameEntity var4 : var0.getEntities().values()) {
                EntityMonster var5;
                if (!(var4 instanceof EntityMonster) || (var5 = (EntityMonster)var4).getGroupId() != var1.groupId || var5.getEntityTypeId() != var1.monsterId || !var5.isAlive() || var5.getLevel() != var2) continue;
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Keep a single preferred boss in the group (Fontaine/Natlan style). Strip wildlife /
     * script extras that share the boss group so Java + scene scripts do not double-own.
     */
    private static void removeNonJavaBossMonsters(Scene scene, BossSpawnEntry bossSpawnEntry, int n) {
        if (scene == null || bossSpawnEntry == null) {
            return;
        }
        ArrayList<EntityMonster> toRemove = new ArrayList<EntityMonster>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster)) continue;
            EntityMonster em = (EntityMonster) ge;
            if (em.getGroupId() != bossSpawnEntry.groupId) continue;
            if (WorldBossSpawnHelper.isMatchingBossMonsterId(
                    bossSpawnEntry.monsterId, em.getEntityTypeId())) {
                continue;
            }
            toRemove.add(em);
        }
        for (EntityMonster em : toRemove) {
            try {
                scene.removeEntity(em);
                Grasscutter.getLogger()
                        .info(
                                "WorldBossSpawnHelper removed non-boss {} entityId={} in group {}",
                                em.getEntityTypeId(),
                                em.getId(),
                                bossSpawnEntry.groupId);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void dedupeJavaPreferredBoss(Scene var0, BossSpawnEntry var1, int var2) {
        if (var0 != null && var1 != null) {
            ArrayList<EntityMonster> aliveBosses = new ArrayList<EntityMonster>();
            for (GameEntity var5 : var0.getEntities().values()) {
                EntityMonster var6;
                if (!(var5 instanceof EntityMonster) || (var6 = (EntityMonster)var5).getGroupId() != var1.groupId || var6.getEntityTypeId() != var1.monsterId || !var6.isAlive() || var6.getLevel() != var2) continue;
                aliveBosses.add(var6);
            }
            while (aliveBosses.size() > 1) {
                EntityMonster var7 = (EntityMonster)aliveBosses.remove(aliveBosses.size() - 1);
                var0.removeEntity((GameEntity)var7);
                Grasscutter.getLogger().debug("WorldBossSpawnHelper removed extra Java boss cfg {} in group {}", (Object)var7.getConfigId(), (Object)var1.groupId);
            }
        }
    }

    private static boolean hasAliveBossMonsterInScene(Scene var0, SceneScriptManager var1, BossSpawnEntry var2) {
        if (var0 != null && var2 != null) {
            for (GameEntity var4 : var0.getEntities().values()) {
                EntityMonster var5;
                if (!(var4 instanceof EntityMonster) || (var5 = (EntityMonster)var4).getGroupId() != var2.groupId || !WorldBossSpawnHelper.isMatchingBossMonsterId(var2.monsterId, var5.getEntityTypeId()) || !var5.isAlive()) continue;
                return true;
            }
            return false;
        }
        return false;
    }

    private static boolean hasDeadBossMonsterInScene(Scene var0, BossSpawnEntry var1) {
        if (var0 != null && var1 != null) {
            for (GameEntity var3 : var0.getEntities().values()) {
                EntityMonster var4;
                if (!(var3 instanceof EntityMonster) || (var4 = (EntityMonster)var3).getGroupId() != var1.groupId || var4.getEntityTypeId() != var1.monsterId || var4.isAlive()) continue;
                return true;
            }
            return false;
        }
        return false;
    }

    private static SceneMonster findBossMonsterDef(SceneGroup var0, BossSpawnEntry var1) {
        if (var0 != null && var0.monsters != null && var1 != null) {
            for (SceneMonster var3 : var0.monsters.values()) {
                if (var3.monster_id != var1.monsterId) continue;
                return var3;
            }
            return null;
        }
        return null;
    }

    private static void ensureBossGroupLoadedAndSpawned(Scene var0, SceneScriptManager var1, BossSpawnEntry var2) {
        if (var0 == null || var1 == null || var2 == null) {
            return;
        }
        if (WorldBossSpawnHelper.isBossRespawnBlocked(var0, var2)) {
            return;
        }
        // Non-java-exclusive bosses previously only loaded definitions and returned -
        // injected stub groups (e.g. Icewind Suite 133402002) never got refreshGroupMonster.
        SceneGroup var3 = WorldBossSpawnHelper.resolveBossGroup(var0, var1, var2);
        if (var3 == null) {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper could not load group {} in block {}", (Object)var2.groupId, (Object)var2.blockId);
            return;
        }
        long var4 = WorldBossSpawnHelper.spawnAttemptKey(var0.getId(), var2.groupId);
        if (awaitingFlowerKeys.contains(var4) || WorldBossSpawnHelper.hasDeadBossMonsterInScene(var0, var2)) {
            // Dead corpse with no flower: clean and continue to spawn (do not re-drop flower).
            if (!WorldBossSpawnHelper.hasUnclaimedBossChest(var0, var2)) {
                awaitingFlowerKeys.remove(var4);
                WorldBossSpawnHelper.removeDeadBossMonsters(var0, var2);
                try {
                    OpenWorldSpawnHelper.clearGroupDeathRecords(var0, var2.groupId);
                } catch (Throwable ignored) {
                }
                // fall through to spawn below
            } else {
                int var9 = WorldBossSpawnHelper.resolveMonsterConfigId(var1, var2);
                WorldBossSpawnHelper.spawnTrounceFlowerIfMissing(var0, var2, var9);
                if (WorldBossSpawnHelper.hasUnclaimedBossChest(var0, var2)) {
                    WorldBossSpawnHelper.removeDeadBossMonsters(var0, var2);
                }
                return;
            }
        }
        if (WorldBossSpawnHelper.hasAliveBossMonsterInScene(var0, var1, var2)) {
            int var8 = WorldBossSpawnHelper.resolveWorldBossLevel(var0);
            WorldBossSpawnHelper.removeNonJavaBossMonsters(var0, var2, var8);
            WorldBossSpawnHelper.dedupeJavaPreferredBoss(var0, var2, var8);
            return;
        }
        SceneGroupInstance var6 = var1.getGroupInstanceById(var2.groupId);
        var3 = WorldBossSpawnHelper.ensureGroupDefinitionsLoaded(var1, var2);
        WorldBossSpawnHelper.ensureBossSpawnedOnce(var0, var1, var2, var3, var6, null);
        if (!WorldBossSpawnHelper.hasAliveBossMonsterInScene(var0, var1, var2)) {
            // refreshGroupMonster can no-op if suite never met; force spawn as fallback
            WorldBossSpawnHelper.forceSpawnBossMonsters(var1, var6, var3 != null ? var3 : WorldBossSpawnHelper.resolveBossGroup(var0, var1, var2), var2, null, true);
            Grasscutter.getLogger().info("WorldBossSpawnHelper force-spawn fallback group {} monster {}", (Object)var2.groupId, (Object)var2.monsterId);
        }
    }

    private static SceneGroup resolveBossGroup(Scene var0, SceneScriptManager var1, BossSpawnEntry var2) {
        SceneGroup var6;
        if (WorldBossSpawnHelper.isJavaExclusiveBossGroup(var2.groupId)) {
            return WorldBossSpawnHelper.resolveWatcherPrimoGroup(var0, var1, var2);
        }
        SceneGroup var3 = var1.getGroupById(var2.groupId);
        if (var3 != null) {
            try {
                var3.dontUnload = true;
                var3.dynamic_load = true;
            } catch (Throwable ignored) {
            }
            return var3;
        }
        SceneBlock var4 = (SceneBlock)var1.getBlocks().get(var2.blockId);
        if (var4 == null) {
            WorldBossSpawnHelper.ensureRuntimeBossBlocks(var0, var1);
            var4 = (SceneBlock)var1.getBlocks().get(var2.blockId);
        }
        if (var4 == null) {
            return null;
        }
        if (WorldBossSpawnHelper.isSpawnGraceActive(var0)) {
            return var1.getGroupById(var2.groupId);
        }
        if (!var0.getLoadedBlocks().contains(var4) && !var4.isLoaded()) {
            if (var4.groups == null) {
                var4.groups = new HashMap();
            }
            var0.loadBlock(var4);
        }
        WorldBossSpawnHelper.injectBossGroups(var4, var0.getId());
        if (var4.groups != null) {
            var3 = (SceneGroup)var4.groups.get(var2.groupId);
        }
        if (var3 == null) {
            WorldBossSpawnHelper.injectBossGroups(var4, var0.getId());
            if (var4.groups != null) {
                var3 = (SceneGroup)var4.groups.get(var2.groupId);
            }
        }
        if (var3 == null) {
            return null;
        }
        try {
            var3.dontUnload = true;
            var3.dynamic_load = true;
        } catch (Throwable ignored) {
        }
        boolean var5 = var0.getLoadedGroups().stream().anyMatch(var1x -> var1x.id == var2.groupId);
        if (!var5) {
            var0.onLoadGroup(List.of(var3));
            try {
                var0.onRegisterGroups();
            }
            catch (Throwable var7) {
                Grasscutter.getLogger().debug("WorldBossSpawnHelper onRegisterGroups failed for group {}", (Object)var2.groupId, (Object)var7);
            }
        }
        if ((var6 = WorldBossSpawnHelper.ensureGroupDefinitionsLoaded(var1, var2)) != null) {
            return var6;
        }
        return var4.groups != null ? (SceneGroup)var4.groups.get(var2.groupId) : var3;
    }

    private static SceneGroup resolveWatcherPrimoGroup(Scene var0, SceneScriptManager var1, BossSpawnEntry var2) {
        long var3 = WorldBossSpawnHelper.spawnAttemptKey(var0.getId(), var2.groupId);
        SceneGroup var5 = WorldBossSpawnHelper.getRegisteredWatcherGroup(var1, var2.groupId);
        if (var5 != null && WorldBossSpawnHelper.hasWatcherGroupDefinitions(var5)) {
            if (var5.block_id <= 0) {
                var5.block_id = var2.blockId;
            }
            return var5;
        }
        if (!javaExclusiveBossGroupsReady.contains(var3)) {
            var5 = WorldBossSpawnHelper.buildWatcherGroupStub(var2);
            if (!WorldBossSpawnHelper.loadWatcherGroupLua(var5, var0.getId())) {
                Grasscutter.getLogger().warn("WorldBossSpawnHelper Watcher Primo group.load failed for group {}", (Object)var2.groupId);
                return null;
            }
            WorldBossSpawnHelper.registerWatcherGroupInManager(var1, var5);
            WorldBossSpawnHelper.ensureWatcherGroupInstance(var1, var5, var0);
            javaExclusiveBossGroupsReady.add(var3);
            Grasscutter.getLogger().info("WorldBossSpawnHelper Watcher Primo group {} ready (Java-isolated, no block {})", (Object)var2.groupId, (Object)var2.blockId);
        } else {
            var5 = WorldBossSpawnHelper.getRegisteredWatcherGroup(var1, var2.groupId);
        }
        if (var5 != null && WorldBossSpawnHelper.hasWatcherGroupDefinitions(var5)) {
            if (var5.block_id <= 0) {
                var5.block_id = var2.blockId;
            }
            return var5;
        }
        return var5;
    }

    private static SceneGroup buildWatcherGroupStub(BossSpawnEntry var0) {
        SceneGroup var1 = SceneGroup.of((int)var0.groupId);
        var1.block_id = var0.blockId;
        var1.pos = var0.position.clone();
        var1.refresh_id = 1003;
        return var1;
    }

    private static boolean hasWatcherGroupDefinitions(SceneGroup var0) {
        return var0 != null && var0.monsters != null && !var0.monsters.isEmpty() && var0.gadgets != null && !var0.gadgets.isEmpty();
    }

    private static boolean loadWatcherGroupLua(SceneGroup var0, int var1) {
        if (var0 == null) {
            return false;
        }
        try {
            var0.load(var1);
            return WorldBossSpawnHelper.hasWatcherGroupDefinitions(var0);
        }
        catch (Throwable var3) {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper Watcher Primo group.load failed for group {}", (Object)var0.id, (Object)var3);
            return false;
        }
    }

    private static SceneGroup getRegisteredWatcherGroup(SceneScriptManager var0, int var1) {
        if (var0 == null) {
            return null;
        }
        try {
            Field var2 = SceneScriptManager.class.getDeclaredField("sceneGroups");
            var2.setAccessible(true);
            Map var3 = (Map)var2.get(var0);
            SceneGroup var4 = (SceneGroup)var3.get(var1);
            if (var4 != null) {
                return var4;
            }
        }
        catch (Throwable var5) {
            Grasscutter.getLogger().debug("WorldBossSpawnHelper sceneGroups lookup failed for group {}: {}", (Object)var1, (Object)var5.toString());
        }
        return var0.getGroupById(var1);
    }

    private static void registerWatcherGroupInManager(SceneScriptManager var0, SceneGroup var1) {
        if (var0 != null && var1 != null) {
            try {
                Field var2 = SceneScriptManager.class.getDeclaredField("sceneGroups");
                var2.setAccessible(true);
                Map var3 = (Map)var2.get(var0);
                var3.put(var1.id, var1);
            }
            catch (Throwable var4) {
                Grasscutter.getLogger().warn("WorldBossSpawnHelper failed to register Watcher group {}", (Object)var1.id, (Object)var4);
            }
        }
    }

    private static void ensureWatcherGroupInstance(SceneScriptManager var0, SceneGroup var1, Scene var2) {
        if (var0 != null && var1 != null && var2 != null) {
            SceneGroupInstance var3 = var0.getGroupInstanceById(var1.id);
            if (var3 == null) {
                var3 = (SceneGroupInstance)var0.getCachedGroupInstances().get(var1.id);
            }
            Player var4 = var2.getWorld().getHost();
            if (var3 == null && var4 != null) {
                var3 = new SceneGroupInstance(var1, var4);
                try {
                    var3.save();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
            if (var3 != null) {
                var3.setLuaGroup(var1);
                var3.setCached(false);
                try {
                    Field var5 = SceneScriptManager.class.getDeclaredField("sceneGroupsInstances");
                    var5.setAccessible(true);
                    Map var6 = (Map)var5.get(var0);
                    var6.put(var1.id, var3);
                    var0.getCachedGroupInstances().put(var1.id, var3);
                }
                catch (Throwable var7) {
                    Grasscutter.getLogger().warn("WorldBossSpawnHelper failed to bind Watcher instance for group {}", (Object)var1.id, (Object)var7);
                }
            }
        }
    }

    private static SceneGroup ensureGroupDefinitionsLoaded(SceneScriptManager var0, BossSpawnEntry var1) {
        boolean var5;
        boolean var3;
        if (var0 == null || var1 == null) {
            return null;
        }
        if (WorldBossSpawnHelper.isJavaExclusiveBossGroup(var1.groupId)) {
            Scene var9 = var0.getScene();
            return var9 == null ? null : WorldBossSpawnHelper.resolveWatcherPrimoGroup(var9, var0, var1);
        }
        SceneGroup var2 = WorldBossSpawnHelper.safeGetRegisteredGroup(var0, var1.groupId);
        if (var2 == null) {
            return null;
        }
        boolean bl = var3 = var2.monsters == null || var2.monsters.isEmpty() || var2.gadgets == null || var2.gadgets.isEmpty();
        if (!var3) {
            return var2;
        }
        try {
            var0.loadGroupFromScript(var2);
        }
        catch (Throwable var8) {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper loadGroupFromScript failed for group {}", (Object)var1.groupId, (Object)var8);
        }
        SceneGroup var4 = WorldBossSpawnHelper.safeGetRegisteredGroup(var0, var1.groupId);
        if (var4 != null) {
            var2 = var4;
        }
        boolean bl2 = var5 = var2.monsters == null || var2.monsters.isEmpty() || var2.gadgets == null || var2.gadgets.isEmpty();
        if (var5 && var0.getScene() != null) {
            try {
                var2.load(var0.getScene().getId());
                SceneGroupInstance var6 = var0.getGroupInstanceById(var1.groupId);
                if (var6 != null) {
                    var6.setLuaGroup(var2);
                }
            }
            catch (Throwable var7) {
                Grasscutter.getLogger().warn("WorldBossSpawnHelper group.load failed for group {}", (Object)var1.groupId, (Object)var7);
            }
        }
        return (var4 = WorldBossSpawnHelper.safeGetRegisteredGroup(var0, var1.groupId)) != null ? var4 : var2;
    }

    private static void forceSpawnBossMonsters(SceneScriptManager var0, SceneGroupInstance var1, SceneGroup var2, BossSpawnEntry var3, List<GameEntity> var4) {
        WorldBossSpawnHelper.forceSpawnBossMonsters(var0, var1, var2, var3, var4, false);
    }

    private static void forceSpawnBossMonsters(SceneScriptManager var0, SceneGroupInstance var1, SceneGroup var2, BossSpawnEntry var3, List<GameEntity> var4, boolean force) {
        if (var2 == null || var3 == null || var0 == null) {
            return;
        }
        if (var2.block_id <= 0) {
            var2.block_id = var3.blockId;
        }
        if (force || !WorldBossSpawnHelper.isBossRespawnBlocked(var0.getScene(), var3.groupId)) {
            WorldBossSpawnHelper.removeDeadBossMonsters(var0.getScene(), var3);
            if (!WorldBossSpawnHelper.hasAliveBossMonsterInScene(var0.getScene(), var0, var3) && (var2 = WorldBossSpawnHelper.ensureGroupDefinitionsLoaded(var0, var3)) != null) {
                SceneMonster var5;
                if (var2.monsters != null && !var2.monsters.isEmpty() && (var5 = WorldBossSpawnHelper.findBossMonsterDef(var2, var3)) != null) {
                    WorldBossSpawnHelper.spawnBossMonsterDef(var0, var2, var3, var5, var4);
                    return;
                }
                EntityMonster var7 = WorldBossSpawnHelper.spawnFallbackBoss(var0, var2, var3);
                if (var7 != null) {
                    if (var4 != null) {
                        var4.add((GameEntity)var7);
                    } else {
                        WorldBossSpawnHelper.addBossEntityToScene(var0, var7);
                    }
                    Grasscutter.getLogger().info("WorldBossSpawnHelper spawned fallback boss {} for group {} at {}, {}, {}", new Object[]{var3.monsterId, var3.groupId, Float.valueOf(var7.getPosition().getX()), Float.valueOf(var7.getPosition().getY()), Float.valueOf(var7.getPosition().getZ())});
                }
            }
        }
    }

    private static void spawnBossMonsterDef(SceneScriptManager var0, SceneGroup var1, BossSpawnEntry var2, SceneMonster var3, List<GameEntity> var4) {
        GameEntity gameEntity = var0.getScene().getEntityByConfigId(var3.config_id, var1.id);
        if (gameEntity instanceof EntityMonster) {
            EntityMonster var6 = (EntityMonster)gameEntity;
            if (var6.isAlive()) {
                return;
            }
            var0.getScene().removeEntity((GameEntity)var6);
        }
        Position var8 = var3.pos != null ? var3.pos.clone() : WorldBossSpawnHelper.resolveSpawnPosition(var2, WorldBossSpawnHelper.findNearestPlayer(var0.getScene()));
        var3.pos = var8.clone();
        var3.level = WorldBossSpawnHelper.resolveWorldBossLevel(var0.getScene());
        EntityMonster var7 = var0.createMonster(var1.id, var1.block_id, var3);
        if (var7 == null) {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper failed to create boss {} in group {}", (Object)var3.monster_id, (Object)var1.id);
        } else {
            if (var4 != null) {
                var4.add((GameEntity)var7);
            } else {
                WorldBossSpawnHelper.addBossEntityToScene(var0, var7);
            }
            Grasscutter.getLogger().debug("WorldBossSpawnHelper spawned boss {} in group {} at {}, {}, {}", new Object[]{var3.monster_id, var1.id, Float.valueOf(var7.getPosition().getX()), Float.valueOf(var7.getPosition().getY()), Float.valueOf(var7.getPosition().getZ())});
        }
    }

    private static EntityMonster spawnFallbackBoss(SceneScriptManager var0, SceneGroup var1, BossSpawnEntry var2) {
        SceneMonster var3 = new SceneMonster();
        var3.config_id = WorldBossSpawnHelper.resolveConfigId(var2.groupId);
        var3.monster_id = var2.monsterId;
        var3.level = WorldBossSpawnHelper.resolveWorldBossLevel(var0.getScene());
        var3.drop_id = 1000100;
        var3.pose_id = 101;
        var3.area_id = 32;
        Position var4 = WorldBossSpawnHelper.resolveSpawnPosition(var2, WorldBossSpawnHelper.findNearestPlayer(var0.getScene()));
        var3.pos = var4.clone();
        var3.rot = new Position(0.0f, 0.0f, 0.0f);
        if (var1.monsters == null) {
            var1.monsters = new HashMap();
        }
        var1.monsters.putIfAbsent(var3.config_id, var3);
        return var0.createMonster(var1.id, var1.block_id, var3);
    }

    private static void addBossEntityToScene(SceneScriptManager var0, EntityMonster var1) {
        Scene var2 = var0.getScene();
        if (var2 != null && var1 != null) {
            var2.addEntity((GameEntity)var1);
        }
    }

    private static Player findNearestPlayer(Scene var0) {
        return var0 != null && !var0.getPlayers().isEmpty() ? (Player)var0.getPlayers().iterator().next() : null;
    }

    private static Position resolveSpawnPosition(BossSpawnEntry var0, Player var1) {
        return var0.position.clone();
    }

    private static int resolveConfigId(int var0) {
        int var1 = var0 % 1000;
        int var2 = var1 * 1000 + 1;
        return var2 > 0 ? var2 : 1001;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private static void ensureIndexBuilt() {
        if (bossByGroupId != null) return;
        Class<WorldBossSpawnHelper> clazz = WorldBossSpawnHelper.class;
        synchronized (WorldBossSpawnHelper.class) {
            if (bossByGroupId != null) return;
            bossByGroupId = new Int2ObjectOpenHashMap();
            bossMonsterConfigIds = ConcurrentHashMap.newKeySet();
            blockPatchById = new HashMap<Integer, BlockPatchInfo>();
            Map<Integer, float[]> var1 = WorldBossSpawnHelper.loadInvestigationPositions();
            for (InvestigationMonsterData var3 : GameData.getInvestigationMonsterDataMap().values()) {
                int var6;
                if (var3.getId() < 6 || !"Boss".equalsIgnoreCase(var3.getMonsterCategory())) continue;
                List var4 = var3.getGroupIdList();
                List var5 = var3.getMonsterIdList();
                if (var4 == null || var4.isEmpty() || var5 == null || var5.isEmpty() || !WorldBossSpawnHelper.isScene3OpenWorldGroup(var6 = ((Integer)var4.get(0)).intValue()) || WorldBossSpawnHelper.isExcludedComplexBossGroup(var6)) continue;
                int var7 = WorldBossSpawnHelper.resolveBlockId(var6);
                float[] var8 = var1.get(var3.getId());
                float[] var9 = WorldBossSpawnHelper.resolvePositionFromGroupScript(var6);
                if (var9 != null) {
                    if (var8 == null) {
                        var8 = var9;
                    } else {
                        double var10 = Math.sqrt(Math.pow(var8[0] - var9[0], 2.0) + Math.pow(var8[1] - var9[1], 2.0) + Math.pow(var8[2] - var9[2], 2.0));
                        if (var10 > 30.0) {
                            var8 = var9;
                        }
                    }
                }
                if (var8 == null) {
                    Grasscutter.getLogger().warn("WorldBossSpawnHelper missing position for boss id={} group={}", (Object)var3.getId(), (Object)var6);
                    continue;
                }
                BossSpawnEntry var14 = new BossSpawnEntry(var3.getId(), var6, var7, (Integer)var5.get(0), var8[0], var8[1], var8[2]);
                WorldBossSpawnHelper.registerBossEntry(var14);
                if (!WorldBossSpawnHelper.shouldRegisterSyntheticBlock(var6, var7)) continue;
                BlockPatchInfo var11 = blockPatchById.computeIfAbsent(var7, BlockPatchInfo::new);
                var11.groupIds.add(var6);
                var11.expand(var8[0], var8[2]);
            }
            WorldBossSpawnHelper.loadManualBosses();
            Grasscutter.getLogger().info("WorldBossSpawnHelper trounce-flower patch v8 active \u2014 indexed {} world bosses across {} synthetic blocks", (Object)bossByGroupId.size(), (Object)blockPatchById.size());
            // ** MonitorExit[var0] (shouldn't be in output)
            return;
        }
    }

    private static int resolveBlockId(int var0) {
        return var0 / 1000 % 10000;
    }

    private static void ensureRuntimeBossBlocks(Scene var0, SceneScriptManager var1) {
        Map var2;
        if (var0 != null && var1 != null && var0.getId() == 3 && (var2 = var1.getBlocks()) != null) {
            WorldBossSpawnHelper.ensureIndexBuilt();
            if (!blockPatchById.isEmpty()) {
                boolean var3 = false;
                if (var2.remove(4104) != null) {
                    var3 = true;
                    Grasscutter.getLogger().info("WorldBossSpawnHelper removed unstable block {} from script manager (Watcher Primo is Java-isolated)", (Object)4104);
                }
                for (BlockPatchInfo var5 : blockPatchById.values()) {
                    if (var5.blockId == 4104 || var2.containsKey(var5.blockId)) continue;
                    SceneBlock var6 = new SceneBlock();
                    var6.id = var5.blockId;
                    var6.min = new Position((float)var5.minX, 0.0f, (float)var5.minZ);
                    var6.max = new Position((float)var5.maxX, 0.0f, (float)var5.maxZ);
                    var6.groups = new HashMap();
                    var2.put(var5.blockId, var6);
                    var3 = true;
                    Grasscutter.getLogger().info("WorldBossSpawnHelper runtime-added scene block {} [{},{}]-[{},{}]", new Object[]{var5.blockId, var5.minX, var5.minZ, var5.maxX, var5.maxZ});
                }
                if (var3) {
                    WorldBossSpawnHelper.injectBossGroupsForLoadedBlocks(var2, var0.getId());
                }
            }
        }
    }

    private static void injectBossGroupsForLoadedBlocks(Map<Integer, SceneBlock> var0, int var1) {
        if (var0 != null && var1 == 3) {
            for (SceneBlock var3 : var0.values()) {
                if (!blockPatchById.containsKey(var3.id)) continue;
                WorldBossSpawnHelper.injectBossGroups(var3, var1);
            }
        }
    }

    private static void loadManualBosses() {
        Path var0 = Path.of("resources", "patch", "nod-krai-world-bosses.json");
        if (Files.isRegularFile(var0, new LinkOption[0])) {
            try (BufferedReader var1 = Files.newBufferedReader(var0, StandardCharsets.UTF_8);){
                JsonArray var2 = JsonParser.parseReader((Reader)var1).getAsJsonArray();
                int var3 = 0;
                for (JsonElement var5 : var2) {
                    int var7;
                    JsonObject var6;
                    if (!var5.isJsonObject() || !(var6 = var5.getAsJsonObject()).has("groupId") || !var6.has("monsterId") || WorldBossSpawnHelper.isExcludedComplexBossGroup(var7 = var6.get("groupId").getAsInt())) continue;
                    boolean var8 = bossByGroupId.containsKey(var7);
                    int var9 = var6.get("monsterId").getAsInt();
                    int var10 = var6.has("investigationId") ? var6.get("investigationId").getAsInt() : 0;
                    float var11 = var6.get("x").getAsFloat();
                    float var12 = var6.get("y").getAsFloat();
                    float var13 = var6.get("z").getAsFloat();
                    int var14 = WorldBossSpawnHelper.resolveBlockId(var7);
                    BossSpawnEntry var15 = new BossSpawnEntry(var10, var7, var14, var9, var11, var12, var13);
                    WorldBossSpawnHelper.registerBossEntry(var15);
                    if (WorldBossSpawnHelper.shouldRegisterSyntheticBlock(var7, var14)) {
                        BlockPatchInfo var16 = blockPatchById.computeIfAbsent(var14, BlockPatchInfo::new);
                        if (!var16.groupIds.contains(var7)) {
                            var16.groupIds.add(var7);
                        }
                        var16.expand(var11, var13);
                    }
                    if (var8) {
                        Grasscutter.getLogger().info("WorldBossSpawnHelper manual override group {} -> monster {}", (Object)var7, (Object)var9);
                    }
                    ++var3;
                }
                if (var3 > 0) {
                    Grasscutter.getLogger().info("WorldBossSpawnHelper loaded {} manual boss entries", (Object)var3);
                }
            }
            catch (IOException var19) {
                Grasscutter.getLogger().error("WorldBossSpawnHelper failed to load manual bosses", (Throwable)var19);
            }
        }
    }

    private static Map<Integer, float[]> loadInvestigationPositions() {
        HashMap<Integer, float[]> var0 = new HashMap<Integer, float[]>();
        WorldBossSpawnHelper.mergeCoordFile(var0, Path.of("resources", "patch", "world-boss-coords.json"));
        WorldBossSpawnHelper.mergeCoordFile(var0, Path.of("resources", "patch", "nod-krai-world-bosses.json"));
        Path var1 = Path.of("resources", "ExcelBinOutput", "InvestigationMonsterConfigData.json");
        if (!Files.isRegularFile(var1, new LinkOption[0])) {
            return var0;
        }
        try (BufferedReader var2 = Files.newBufferedReader(var1, StandardCharsets.UTF_8);){
            for (JsonElement var5 : JsonParser.parseReader((Reader)var2).getAsJsonArray()) {
                JsonArray var7;
                JsonObject var6 = var5.getAsJsonObject();
                if (!var6.has("id") || !var6.has("DJLCKJCAKDA") || var0.containsKey(var6.get("id").getAsInt()) || (var7 = var6.getAsJsonArray("DJLCKJCAKDA")).size() < 3) continue;
                var0.put(var6.get("id").getAsInt(), new float[]{var7.get(0).getAsFloat(), var7.get(1).getAsFloat(), var7.get(2).getAsFloat()});
            }
        }
        catch (IOException var10) {
            Grasscutter.getLogger().error("WorldBossSpawnHelper failed to load investigation positions", (Throwable)var10);
        }
        return var0;
    }

    private static void mergeCoordFile(Map<Integer, float[]> var0, Path var1) {
        if (Files.isRegularFile(var1, new LinkOption[0])) {
            try (BufferedReader var2 = Files.newBufferedReader(var1, StandardCharsets.UTF_8);){
                for (JsonElement var5 : JsonParser.parseReader((Reader)var2).getAsJsonArray()) {
                    JsonObject var6 = var5.getAsJsonObject();
                    if (!var6.has("x") || !var6.has("y") || !var6.has("z")) continue;
                    float[] var7 = new float[]{var6.get("x").getAsFloat(), var6.get("y").getAsFloat(), var6.get("z").getAsFloat()};
                    if (!var6.has("investigationId") || var6.get("investigationId").getAsInt() <= 0) continue;
                    var0.put(var6.get("investigationId").getAsInt(), var7);
                }
            }
            catch (IOException var10) {
                Grasscutter.getLogger().error("WorldBossSpawnHelper failed to load {}", (Object)var1, (Object)var10);
            }
        }
    }

    public static PatchedBossSpawn getPatchedBossSpawn(int groupId) {
        WorldBossSpawnHelper.ensureIndexBuilt();
        BossSpawnEntry entry = (BossSpawnEntry)bossByGroupId.get(groupId);
        if (entry == null) {
            return null;
        }
        return new PatchedBossSpawn(entry.investigationId, entry.groupId, entry.monsterId, entry.position);
    }

    private static void registerBossEntry(BossSpawnEntry var0) {
        bossByGroupId.put(var0.groupId, var0);
        Set<Integer> var1 = WorldBossSpawnHelper.loadBossMonsterConfigIdsFromGroupScript(var0.groupId);
        if (var1.isEmpty()) {
            var1 = Set.of(Integer.valueOf(WorldBossSpawnHelper.resolveConfigId(var0.groupId)));
        }
        bossMonsterConfigIds.addAll(var1);
    }

    private static Path resolveGroupScriptPath(int var0) {
        String var1 = "Scene/3/scene3_group" + var0 + ".lua";
        try {
            Path var2 = FileUtils.getScriptPath((String)var1);
            if (var2 != null && Files.isRegularFile(var2, new LinkOption[0])) {
                return var2;
            }
        }
        catch (Throwable var2) {
            // empty catch block
        }
        Path var4 = Path.of("resources", "Scripts", "Scene", "3", "scene3_group" + var0 + ".lua");
        return Files.isRegularFile(var4, new LinkOption[0]) ? var4 : null;
    }

    private static SceneGadget buildSyntheticBossChestGadget(BossSpawnEntry var0, int var1) {
        if (var0 == null) {
            return null;
        }
        int var2 = var1 > 0 ? var1 : WorldBossSpawnHelper.resolveConfigId(var0.groupId);
        int var3 = var2 + 1;
        SceneGadget var4 = new SceneGadget();
        var4.config_id = var3;
        var4.gadget_id = 70210106;
        Position var5 = var0.position.clone();
        var5.setZ(var5.getZ() + 5.0f);
        var4.pos = var5;
        var4.rot = new Position(0.0f, 0.0f, 0.0f);
        var4.isOneoff = true;
        var4.persistent = true;
        SceneBossChest var6 = new SceneBossChest();
        var6.monster_config_id = var2;
        var6.resin = 40;
        var6.life_time = 600;
        var6.take_num = 100;
        var4.boss_chest = var6;
        int monsterId = var0.monsterId > 0 ? var0.monsterId : 0;
        String dropTag = BossChestDropTagResolver.resolve(var0.groupId, monsterId);
        if (dropTag != null) {
            var4.drop_tag = dropTag;
        }
        Grasscutter.getLogger().info("WorldBossSpawnHelper synthetic trounce flower {} for group {} (script unreadable)", (Object)var3, (Object)var0.groupId);
        return var4;
    }

    private static SceneGadget loadBossChestGadgetFromGroupScript(int var0, int var1) {
        Path var2 = WorldBossSpawnHelper.resolveGroupScriptPath(var0);
        if (var2 == null) {
            return null;
        }
        try {
            String var3 = Files.readString(var2, StandardCharsets.UTF_8);
            Pattern var4 = Pattern.compile("\\{\\s*config_id\\s*=\\s*(\\d+)\\s*,\\s*gadget_id\\s*=\\s*(\\d+)[\\s\\S]*?boss_chest\\s*=\\s*\\{\\s*monster_config_id\\s*=\\s*(\\d+)\\s*,\\s*resin\\s*=\\s*(\\d+)(?:\\s*,\\s*life_time\\s*=\\s*(\\d+))?(?:\\s*,\\s*take_num\\s*=\\s*(\\d+))?[\\s\\S]*?\\}[\\s\\S]*?\\}");
            Matcher var5 = var4.matcher(var3);
            SceneGadget var6 = null;
            while (var5.find()) {
                int var7 = Integer.parseInt(var5.group(1));
                int var8 = Integer.parseInt(var5.group(2));
                int var9 = Integer.parseInt(var5.group(3));
                int var10 = Integer.parseInt(var5.group(4));
                int var11 = var5.group(5) != null ? Integer.parseInt(var5.group(5)) : 600;
                int var12 = var5.group(6) != null ? Integer.parseInt(var5.group(6)) : 100;
                String var13 = var5.group(0);
                Matcher var14 = Pattern.compile("pos\\s*=\\s*\\{\\s*x\\s*=\\s*([-\\d.]+)\\s*,\\s*y\\s*=\\s*([-\\d.]+)\\s*,\\s*z\\s*=\\s*([-\\d.]+)").matcher(var13);
                if (!var14.find()) continue;
                Position var15 = new Position(Float.parseFloat(var14.group(1)), Float.parseFloat(var14.group(2)), Float.parseFloat(var14.group(3)));
                Position var16 = new Position(0.0f, 0.0f, 0.0f);
                Matcher var17 = Pattern.compile("rot\\s*=\\s*\\{\\s*x\\s*=\\s*([-\\d.]+)\\s*,\\s*y\\s*=\\s*([-\\d.]+)\\s*,\\s*z\\s*=\\s*([-\\d.]+)").matcher(var13);
                if (var17.find()) {
                    var16 = new Position(Float.parseFloat(var17.group(1)), Float.parseFloat(var17.group(2)), Float.parseFloat(var17.group(3)));
                }
                SceneGadget var18 = new SceneGadget();
                var18.config_id = var7;
                var18.gadget_id = var8;
                var18.pos = var15;
                var18.rot = var16;
                var18.isOneoff = true;
                var18.persistent = true;
                SceneBossChest var19 = new SceneBossChest();
                var19.monster_config_id = var9;
                var19.resin = var10;
                var19.life_time = var11;
                var19.take_num = var12;
                var18.boss_chest = var19;
                Matcher dropTagMatcher = Pattern.compile("drop_tag\\s*=\\s*\"([^\"]+)\"|drop_tag\\s*=\\s*'([^']+)'").matcher(var13);
                if (dropTagMatcher.find()) {
                    String string = var18.drop_tag = dropTagMatcher.group(1) != null ? dropTagMatcher.group(1) : dropTagMatcher.group(2);
                }
                if (var1 > 0 && var9 == var1) {
                    return var18;
                }
                if (var6 != null) continue;
                var6 = var18;
            }
            return var6;
        }
        catch (IOException var20) {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper failed to read boss chest gadget for group {}", (Object)var0, (Object)var20);
            return null;
        }
    }

    private static Set<Integer> loadBossMonsterConfigIdsFromGroupScript(int var0) {
        Path var1 = WorldBossSpawnHelper.resolveGroupScriptPath(var0);
        if (var1 == null) {
            return Set.of();
        }
        try {
            String var2 = Files.readString(var1, StandardCharsets.UTF_8);
            Matcher var3 = Pattern.compile("config_id\\s*=\\s*(\\d+)[^\\n]*monster_id\\s*=").matcher(var2);
            HashSet<Integer> var4 = new HashSet<Integer>();
            while (var3.find()) {
                var4.add(Integer.parseInt(var3.group(1)));
            }
            return var4;
        }
        catch (IOException var5) {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper failed to read boss config ids for group {}", (Object)var0, (Object)var5);
            return Set.of();
        }
    }

    private static float[] resolvePositionFromGroupScript(int var0) {
        Path var1 = WorldBossSpawnHelper.resolveGroupScriptPath(var0);
        if (var1 == null) {
            return null;
        }
        try {
            String var2 = Files.readString(var1, StandardCharsets.UTF_8);
            Matcher var3 = Pattern.compile("monster_id\\s*=\\s*\\d+[\\s\\S]*?pos\\s*=\\s*\\{\\s*x\\s*=\\s*([-\\d.]+)\\s*,\\s*y\\s*=\\s*([-\\d.]+)\\s*,\\s*z\\s*=\\s*([-\\d.]+)").matcher(var2);
            if (var3.find()) {
                return new float[]{Float.parseFloat(var3.group(1)), Float.parseFloat(var3.group(2)), Float.parseFloat(var3.group(3))};
            }
            Matcher var4 = Pattern.compile("gadget_id\\s*=\\s*\\d+[\\s\\S]*?pos\\s*=\\s*\\{\\s*x\\s*=\\s*([-\\d.]+)\\s*,\\s*y\\s*=\\s*([-\\d.]+)\\s*,\\s*z\\s*=\\s*([-\\d.]+)").matcher(var2);
            if (var4.find()) {
                return new float[]{Float.parseFloat(var4.group(1)), Float.parseFloat(var4.group(2)), Float.parseFloat(var4.group(3))};
            }
        }
        catch (IOException var5) {
            Grasscutter.getLogger().warn("WorldBossSpawnHelper failed to read group script {}", (Object)var0, (Object)var5);
        }
        return null;
    }

    static {
        lastBossSpawnAttemptMs = new ConcurrentHashMap();
        lastNearbyCheckMs = new ConcurrentHashMap();
        awaitingFlowerKeys = ConcurrentHashMap.newKeySet();
        pendingRespawnKeys = ConcurrentHashMap.newKeySet();
        loginGraceUntilMs = new ConcurrentHashMap();
        teleportGraceUntilMs = new ConcurrentHashMap();
        kickstartAfterMs = new ConcurrentHashMap();
        javaExclusiveBossGroupsReady = ConcurrentHashMap.newKeySet();
    }

    private static final class BlockPatchInfo {
        private final int blockId;
        private double minX = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;
        private final Set<Integer> groupIds = new HashSet<Integer>();

        private BlockPatchInfo(int var1) {
            this.blockId = var1;
        }

        private void expand(float var1, float var2) {
            double var3 = Math.floor(var1 / 1024.0f) * 1024.0;
            double var5 = Math.floor(var2 / 1024.0f) * 1024.0;
            double var7 = var3 + 1024.0;
            double var9 = var5 + 1024.0;
            this.minX = Math.min(this.minX, var3);
            this.minZ = Math.min(this.minZ, var5);
            this.maxX = Math.max(this.maxX, var7);
            this.maxZ = Math.max(this.maxZ, var9);
        }
    }

    private static final class BossSpawnEntry {
        private final int investigationId;
        final int groupId;
        final int blockId;
        final int monsterId;
        private final Position position;

        private BossSpawnEntry(int var1, int var2, int var3, int var4, float var5, float var6, float var7) {
            this.investigationId = var1;
            this.groupId = var2;
            this.blockId = var3;
            this.monsterId = var4;
            this.position = new Position(var5, var6, var7);
        }
    }

    public static final class PatchedBossSpawn {
        public final int investigationId;
        public final int groupId;
        public final int monsterId;
        public final Position position;

        private PatchedBossSpawn(int investigationId, int groupId, int monsterId, Position position) {
            this.investigationId = investigationId;
            this.groupId = groupId;
            this.monsterId = monsterId;
            this.position = position;
        }
    }
}
