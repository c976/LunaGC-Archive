/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.GameData
 *  emu.grasscutter.data.binout.ScenePointEntry
 *  emu.grasscutter.data.common.PointData
 *  emu.grasscutter.data.excels.dungeon.DungeonData
 *  emu.grasscutter.data.excels.dungeon.DungeonEntryData
 *  emu.grasscutter.game.dungeons.DungeonManager
 *  emu.grasscutter.game.dungeons.enums.DungeonSubType
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.props.ClimateType
 *  emu.grasscutter.game.props.SceneType
 *  emu.grasscutter.game.world.Position
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.game.world.data.TeleportProperties
 *  emu.grasscutter.scripts.SceneScriptManager
 *  emu.grasscutter.scripts.data.SceneBlock
 *  emu.grasscutter.scripts.data.SceneConfig
 *  emu.grasscutter.scripts.data.SceneGroup
 *  emu.grasscutter.server.game.GameServerPacketHandler
 *  emu.grasscutter.server.game.GameSession
 *  it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.avatar.AvatarExtraLevelHelper;
import emu.grasscutter.game.battlepass.BattlePassBuyHelper;
import emu.grasscutter.game.dungeons.DomainChallengeKeyHelper;
import emu.grasscutter.game.dungeons.DomainContinueSpawnHelper;
import emu.grasscutter.game.dungeons.DomainSceneResetHelper;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneConfig;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.server.game.GameSession;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DomainDungeonHelper {
    private static final int DOMAIN_WEATHER_ID = 1;
    private static final float DEFAULT_ENTRY_RADIUS = 45.0f;
    private static final long ENTER_DEBOUNCE_MS = 1500L;
    private static final Int2IntMap CLIENT_SCENE_CACHE = Int2IntMaps.synchronize(new Int2IntOpenHashMap());
    private static final ConcurrentHashMap<Integer, Long> LAST_ENTER_MS = new ConcurrentHashMap();

    private DomainDungeonHelper() {
    }

    public static int resolveClientSceneId(int n) {
        int n2 = CLIENT_SCENE_CACHE.get(n);
        if (n2 != Integer.MIN_VALUE) {
            return n2;
        }
        int n3 = n;
        if (n >= 40816 && n <= 40819) {
            n3 = 40754 + (n - 40816);
        } else if (n >= 40704 && n <= 40707) {
            n3 = 40754 + (n - 40704);
        } else if (n >= 40770 && n <= 40773) {
            n3 = n;
        } else if (n >= 40780 && n <= 40791) {
            n3 = n;
        } else if (n >= 40792 && n <= 40799) {
            n3 = n;
        } else if (n >= 40810 && n <= 40813) {
            n3 = 40506;
        } else if (n >= 40820 && n <= 40823) {
            // Moonchild / NDKL Cycle3: the client has no 40820-40823 art, so reuse the Lost Lunar Court scene
            n3 = 40754 + (n - 40820);
        } else if (n >= 40824 && n <= 40827) {
            // MDDungeon Cycle05: no client art, so reuse the Lost Lunar Court scene
            n3 = 40754 + (n - 40824);
        } else if (n >= 40828 && n <= 40831) {
            // Ice Erosion: the client has no 40828-40831 art, so reuse the Lost Lunar Court scene
            n3 = 40754 + (n - 40828);
        } else if (n >= 40832 && n <= 40835) {
            // Snezhnaya weapon: replicate the Lost Lunar Court (40754-40757) client scene art
            n3 = 40754 + (n - 40832);
        } else if (n >= 40836 && n <= 40839) {
            // Snezhnaya talent: replicate the Fontaine Pale Forgotten Glory / Lightless Depths scenes (40760-40763)
            n3 = 40760 + (n - 40836);
        } else if (n >= 40700 && n <= 40703) {
            n3 = n;
        } else if (n >= 40840 && n <= 40847) {
            // Extra custom domains without client art - reuse the Lost Lunar Court scene
            n3 = 40754 + ((n - 40840) % 4);
        } else if (n >= 40760 && n <= 40767) {
            n3 = n;
        }
        if (n3 != n) {
            // empty if block
        }
        CLIENT_SCENE_CACHE.put(n, n3);
        return n3;
    }

    /** Scene id advertised to the client for art/team bind (may differ from server scene id). */
    public static int notifySceneId(int serverSceneId) {
        return DomainDungeonHelper.resolveClientSceneId(serverSceneId);
    }

    public static int notifySceneId(Player player) {
        if (player == null) {
            return 0;
        }
        return DomainDungeonHelper.notifySceneId(player.getSceneId());
    }

    public static TeleportProperties clientNotifyProperties(TeleportProperties teleportProperties) {
        if (teleportProperties == null) {
            return null;
        }
        int n = DomainDungeonHelper.resolveClientSceneId(teleportProperties.getSceneId());
        if (n == teleportProperties.getSceneId()) {
            return teleportProperties;
        }
        return TeleportProperties.builder().sceneId(n).dungeonId(teleportProperties.getDungeonId()).teleportType(teleportProperties.getTeleportType()).enterReason(teleportProperties.getEnterReason()).teleportTo(teleportProperties.getTeleportTo()).teleportRot(teleportProperties.getTeleportRot()).enterType(teleportProperties.getEnterType()).build();
    }

    public static void prepareOpenWorldExit(Player player, int n) {
        if (player == null || n <= 0) {
            return;
        }
        Scene scene = player.getScene();
        if (scene != null && DomainDungeonHelper.isDomainScene(scene) && player.getSceneId() != n) {
            Grasscutter.getLogger().debug("Domain exit scene sync uid={} from {} to {}", new Object[]{player.getUid(), player.getSceneId(), n});
            player.setSceneId(n);
        }
    }

    public static void onDungeonRestart(Player player, Scene scene) {
        if (player == null || scene == null) {
            return;
        }
        LAST_ENTER_MS.remove(player.getUid());
        scene.setChallenge(null);
        scene.setKilledMonsterCount(0);
        DomainDungeonHelper.applyDomainWeather(player);
        DomainDungeonHelper.syncPlayerClientSceneId(player);
        DomainSceneResetHelper.clearGridCache(scene);
        DomainSceneResetHelper.refreshToInitSuites(scene);
        DomainDungeonHelper.applyDomainSpawn(player);
        DomainChallengeKeyHelper.revealChallengeKeys(scene);
    }

    public static void syncPlayerClientSceneId(Player player) {
        if (player == null) {
            return;
        }
        Scene scene = player.getScene();
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        int n = scene.getId();
        int n2 = DomainDungeonHelper.resolveClientSceneId(n);
        if (n2 != n) {
            Grasscutter.getLogger().info("Domain client art remap uid={} server={} clientArt={} (player.sceneId kept)", new Object[]{player.getUid(), n, n2});
        }
    }

    public static boolean usesRemappedClientArt(Scene scene) {
        if (scene == null) {
            return false;
        }
        int n = scene.getId();
        return DomainDungeonHelper.resolveClientSceneId(n) != n;
    }

    public static boolean isDomainScene(Scene scene) {
        DungeonSubType dungeonSubType;
        if (scene == null) {
            return false;
        }
        DungeonManager dungeonManager = scene.getDungeonManager();
        if (dungeonManager != null && dungeonManager.getDungeonData() != null && ((dungeonSubType = dungeonManager.getDungeonData().getSubType()) == DungeonSubType.DUNGEON_SUB_TALENT || dungeonSubType == DungeonSubType.DUNGEON_SUB_WEAPON || dungeonSubType == DungeonSubType.DUNGEON_SUB_RELIQUARY)) {
            return true;
        }
        int n = scene.getId();
        if (n >= 40100 && n <= 40699) {
            return true;
        }
        if (n >= 40200 && n <= 40299) {
            return true;
        }
        if (n >= 40300 && n <= 40499) {
            return true;
        }
        return n >= 40700 && n <= 40999;
    }

    public static int resolveTalentPack(int n) {
        if (n >= 4430 && n <= 4433 || n >= 4440 && n <= 4443 || n >= 4450 && n <= 4453) {
            return 1;
        }
        if (n >= 4434 && n <= 4437 || n >= 4444 && n <= 4447 || n >= 4454 && n <= 4457) {
            return 2;
        }
        if (n >= 4651 && n <= 4662) {
            return 3;
        }
        // SN talent IV: suite4 on the shared Fontaine scene 40763
        if (n == 4694 || n == 4698 || n == 4702) {
            return 4;
        }
        return 0;
    }

    public static void applyTalentPackVariable(Scene scene) {
        if (scene == null) {
            return;
        }
        int n = scene.getId();
        // Talent shared scenes: NK 40750-40753, Fontaine 40760-40763 (also used by SN talent)
        if (!((n >= 40750 && n <= 40753) || (n >= 40760 && n <= 40763) || (n >= 40836 && n <= 40839))) {
            return;
        }
        DungeonManager dungeonManager = scene.getDungeonManager();
        if (dungeonManager == null || dungeonManager.getDungeonData() == null) {
            return;
        }
        int n2 = dungeonManager.getDungeonData().getId();
        int n3 = DomainDungeonHelper.resolveTalentPack(n2);
        if (n3 <= 0) {
            return;
        }
        int n4 = Integer.parseInt("240" + (n - 40000) + "001");
        SceneScriptManager sceneScriptManager = scene.getScriptManager();
        if (sceneScriptManager == null) {
            return;
        }
        Map map = sceneScriptManager.getVariables(n4);
        if (map == null) {
            return;
        }
        map.put("talent_pack", n3);
        Grasscutter.getLogger().info("talent_pack set uid-scene dungeon={} scene={} pack={}", new Object[]{n2, n, n3});
    }

    public static void onPlayerEnterDomain(Player player) {
        if (player == null) {
            return;
        }
        long l = System.currentTimeMillis();
        Long l2 = LAST_ENTER_MS.get(player.getUid());
        if (l2 != null && l - l2 < 1500L) {
            return;
        }
        LAST_ENTER_MS.put(player.getUid(), l);
        Scene scene = player.getScene();
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        DomainSceneResetHelper.clearGridCache(scene);
        DomainDungeonHelper.applyDomainWeather(player);
        DomainDungeonHelper.syncPlayerClientSceneId(player);
        SceneScriptManager sceneScriptManager = scene.getScriptManager();
        if (sceneScriptManager != null) {
            SceneScriptManager.eventExecutor.execute(() -> DomainDungeonHelper.finishDomainEnterDeferred(player, scene));
        }
    }

    private static void finishDomainEnterDeferred(Player player, Scene scene) {
        if (player == null || scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        try {
            DomainDungeonHelper.ensureGroupsLoaded(scene);
            DomainSceneResetHelper.refreshToInitSuites(scene);
            DomainDungeonHelper.applyTalentPackVariable(scene);
            DomainDungeonHelper.applyDomainSpawn(player);
            DomainChallengeKeyHelper.revealChallengeKeys(scene);
            Grasscutter.getLogger().info("Domain enter done uid={} serverScene={} clientScene={}", new Object[]{player.getUid(), scene.getId(), DomainDungeonHelper.resolveClientSceneId(scene.getId())});
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("Domain deferred enter failed scene={}: {}", (Object)scene.getId(), (Object)throwable.toString());
        }
    }

    public static void ensureGroupsLoaded(Scene scene) {
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        SceneScriptManager sceneScriptManager = scene.getScriptManager();
        if (sceneScriptManager == null) {
            return;
        }
        if (sceneScriptManager.isInit() && !scene.getLoadedGroups().isEmpty()) {
            return;
        }
        if (!sceneScriptManager.isInit()) {
            DomainDungeonHelper.scheduleEnsureGroupsLoaded(scene);
            return;
        }
        if (!scene.getLoadedGroups().isEmpty()) {
            return;
        }
        ArrayList<SceneGroup> arrayList = new ArrayList<SceneGroup>();
        for (SceneBlock sceneBlock : sceneScriptManager.getBlocks().values()) {
            scene.loadBlock(sceneBlock);
            if (sceneBlock.groups == null) continue;
            for (SceneGroup sceneGroup : sceneBlock.groups.values()) {
                if (sceneGroup == null || sceneGroup.dynamic_load) continue;
                arrayList.add(sceneGroup);
            }
        }
        if (!arrayList.isEmpty()) {
            scene.onLoadGroup(arrayList);
            scene.onRegisterGroups();
            Grasscutter.getLogger().debug("Preloaded {} domain groups for scene {}", (Object)arrayList.size(), (Object)scene.getId());
        }
    }

    private static void scheduleEnsureGroupsLoaded(Scene scene) {
        if (scene == null) {
            return;
        }
        SceneScriptManager.eventExecutor.execute(() -> {
            try {
                Thread.sleep(30L);
            }
            catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
            DomainDungeonHelper.ensureGroupsLoaded(scene);
        });
    }

    public static void applyDomainWeather(Player player) {
        if (player == null) {
            return;
        }
        player.setWeather(1, ClimateType.CLIMATE_SUNNY);
    }

    public static void applyDomainSpawn(Player player) {
        if (player == null) {
            return;
        }
        Scene scene = player.getScene();
        if (!DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        if (DomainContinueSpawnHelper.applyNearChallengeKey(player)) {
            return;
        }
        Position position = null;
        Position position2 = null;
        DungeonManager dungeonManager = scene.getDungeonManager();
        if (dungeonManager != null && dungeonManager.getDungeonData() != null) {
            position = dungeonManager.getDungeonData().getStartPosition();
            position2 = dungeonManager.getDungeonData().getStartRotation();
        }
        SceneScriptManager sceneScriptManager = scene.getScriptManager();
        if (position == null && sceneScriptManager != null && sceneScriptManager.getConfig() != null) {
            SceneConfig sceneConfig = sceneScriptManager.getConfig();
            position = sceneConfig.born_pos;
            position2 = sceneConfig.born_rot;
        }
        if (position == null) {
            return;
        }
        player.getPosition().set(position);
        if (position2 != null) {
            player.getRotation().set(position2);
        }
        var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
        if (avatarEntity != null) {
            avatarEntity.move(position, position2 != null ? position2 : player.getRotation());
        }
    }

    public static boolean canEnterDungeonFromOpenWorld(Player player, int n, int n2) {
        if (player == null || n <= 0 || n2 <= 0) {
            return false;
        }
        DungeonData dungeonData = (DungeonData)GameData.getDungeonDataMap().get(n2);
        if (dungeonData == null) {
            Grasscutter.getLogger().warn("Blocked dungeon enter uid={} unknown dungeon {}", (Object)player.getUid(), (Object)n2);
            return false;
        }
        if (dungeonData.getLimitLevel() > player.getLevel()) {
            return false;
        }
        Scene scene = player.getScene();
        if (scene == null) {
            return false;
        }
        if (scene.getSceneType() == SceneType.SCENE_DUNGEON && scene.getId() == dungeonData.getSceneId()) {
            // Re-entry only replays the dungeon this scene was entered for, from the same point.
            var manager = scene.getDungeonManager();
            return manager != null && manager.getDungeonData().getId() == n2 && scene.getPrevScenePoint() == n;
        }
        // Event dungeons have entry points in activity scenes, and some in rooms; the point below
        // still has to offer the dungeon and the player has to stand at it.
        if (scene.getSceneType() != SceneType.SCENE_WORLD
                && scene.getSceneType() != SceneType.SCENE_ACTIVITY
                && scene.getSceneType() != SceneType.SCENE_ROOM) {
            Grasscutter.getLogger().warn("Blocked dungeon enter uid={} dungeon={} from scene {} ({})", new Object[]{player.getUid(), n2, scene.getId(), scene.getSceneType()});
            return false;
        }
        ScenePointEntry scenePointEntry = GameData.getScenePointEntryById((int)scene.getId(), (int)n);
        if (scenePointEntry == null || scenePointEntry.getPointData() == null) {
            Grasscutter.getLogger().warn("Blocked dungeon enter uid={} unknown point {} in scene {}", new Object[]{player.getUid(), n, scene.getId()});
            return false;
        }
        PointData pointData = scenePointEntry.getPointData();
        if (!DomainDungeonHelper.containsDungeonId(pointData, n2)
                && !player.getQuestManager().questsForDungeon(scenePointEntry).contains(n2)) {
            Grasscutter.getLogger().warn("Blocked dungeon enter uid={} point {} does not offer dungeon {}", new Object[]{player.getUid(), n, n2});
            return false;
        }
        if (!DomainDungeonHelper.isNearEntryPoint(player.getPosition(), pointData)) {
            Position position = DomainDungeonHelper.resolveEntryAnchor(pointData);
            Position position2 = player.getPosition();
            Grasscutter.getLogger().warn("Blocked dungeon enter uid={} point={} dungeon={}: player at ({}, {}, {}), entry at ({}, {}, {})", new Object[]{player.getUid(), n, n2, Float.valueOf(position2 != null ? position2.getX() : 0.0f), Float.valueOf(position2 != null ? position2.getY() : 0.0f), Float.valueOf(position2 != null ? position2.getZ() : 0.0f), Float.valueOf(position != null ? position.getX() : 0.0f), Float.valueOf(position != null ? position.getY() : 0.0f), Float.valueOf(position != null ? position.getZ() : 0.0f)});
            return false;
        }
        return !player.isScenePointForceLocked(scene.getId(), n);
    }

    public static void handleQuickOpen(Player player, int n) {
        boolean bl;
        DungeonManager dungeonManager;
        if (player == null || n <= 0) {
            return;
        }
        DungeonEntryData dungeonEntryData = (DungeonEntryData)GameData.getDungeonEntryDataMap().get(n);
        if (dungeonEntryData == null) {
            Grasscutter.getLogger().debug("Unknown dungeon entry config id {}", (Object)n);
            return;
        }
        ScenePointEntry scenePointEntry = GameData.getScenePointEntryById((int)dungeonEntryData.getSceneId(), (int)dungeonEntryData.getDungeonEntryId());
        if (scenePointEntry == null || scenePointEntry.getPointData() == null) {
            Grasscutter.getLogger().debug("Missing scene point {}:{} for entry config {}", new Object[]{dungeonEntryData.getSceneId(), dungeonEntryData.getDungeonEntryId(), n});
            return;
        }
        int[] nArray = scenePointEntry.getPointData().getDungeonIds();
        if (nArray == null || nArray.length == 0) {
            return;
        }
        int n2 = dungeonEntryData.getDungeonEntryId();
        int n3 = nArray[0];
        DungeonData dungeonData = (DungeonData)GameData.getDungeonDataMap().get(n3);
        if (dungeonData == null) {
            return;
        }
        if (!DomainDungeonHelper.canEnterDungeonFromOpenWorld(player, n2, n3)) {
            return;
        }
        Grasscutter.getLogger().info("Dungeon quick-open uid={} entryConfig={} point={} dungeon={}", new Object[]{player.getUid(), n, n2, n3});
        Scene scene = player.getScene();
        DungeonManager dungeonManager2 = dungeonManager = scene != null ? scene.getDungeonManager() : null;
        if (dungeonManager != null && dungeonManager.getDungeonData() != null && dungeonManager.getDungeonData().getId() == n3) {
            player.getServer().getDungeonSystem().restartDungeon(player);
            DomainDungeonHelper.onPlayerEnterDomain(player);
            bl = true;
        } else {
            bl = player.getServer().getDungeonSystem().enterDungeon(player, n2, n3, true);
            if (bl) {
                DomainDungeonHelper.onPlayerEnterDomain(player);
            }
        }
        if (!bl) {
            Grasscutter.getLogger().debug("Quick open failed for entry {} (point {} dungeon {})", new Object[]{n, n2, n3});
        }
    }

    public static boolean tryHandleUnregisteredPacket(GameServerPacketHandler gameServerPacketHandler, GameSession gameSession, int n, byte[] byArray) {
        if (gameSession != null && byArray != null && BattlePassBuyHelper.tryHandle(gameSession.getPlayer(), n, byArray)) {
            return true;
        }
        if (gameSession == null || byArray == null) {
            return false;
        }
        try {
            Field field = GameServerPacketHandler.class.getDeclaredField("handlers");
            field.setAccessible(true);
            Int2ObjectMap int2ObjectMap = (Int2ObjectMap)field.get(gameServerPacketHandler);
            if (int2ObjectMap.containsKey(n)) {
                return false;
            }
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            return false;
        }
        return AvatarExtraLevelHelper.tryHandleUnregisteredPacket(gameSession.getPlayer(), n, byArray);
    }

    public static boolean tryHandleUnknownQuickOpen(Player player, int n, byte[] byArray) {
        return false;
    }

    private static boolean containsDungeonId(PointData pointData, int n) {
        int[] nArray = pointData.getDungeonIds();
        if (nArray != null) {
            for (int n2 : nArray) {
                if (n2 != n) continue;
                return true;
            }
        }
        return false;
    }

    private static Position resolveEntryAnchor(PointData pointData) {
        if (pointData.getPos() != null) {
            return pointData.getPos();
        }
        return pointData.getTranPos();
    }

    private static boolean isNearEntryPoint(Position position, PointData pointData) {
        double d;
        double d2;
        double d3;
        if (position == null || pointData == null) {
            return false;
        }
        Position position2 = DomainDungeonHelper.resolveEntryAnchor(pointData);
        if (position2 == null) {
            return false;
        }
        float f = 45.0f;
        Position position3 = pointData.getSize();
        if (position3 != null) {
            f = Math.max(f, Math.max(position3.getX(), Math.max(position3.getY(), position3.getZ())) * 1.5f);
        }
        return (d3 = (double)(position.getX() - position2.getX())) * d3 + (d2 = (double)(position.getY() - position2.getY())) * d2 + (d = (double)(position.getZ() - position2.getZ())) * d <= (double)(f * f);
    }

    static {
        CLIENT_SCENE_CACHE.defaultReturnValue(Integer.MIN_VALUE);
    }
}

