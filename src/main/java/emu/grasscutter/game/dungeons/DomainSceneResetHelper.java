/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.entity.EntityGadget
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.game.world.SceneGroupInstance
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.VisionTypeOuterClass$VisionType
 *  emu.grasscutter.scripts.SceneScriptManager
 *  emu.grasscutter.scripts.data.SceneBlock
 *  emu.grasscutter.scripts.data.SceneGadget
 *  emu.grasscutter.scripts.data.SceneGroup
 *  emu.grasscutter.scripts.data.SceneSuite
 *  emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DomainChallengeKeyHelper;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.dungeons.DomainMonsterSpawnHelper;
import emu.grasscutter.game.dungeons.DomainRewardStatueHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.VisionTypeOuterClass;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneSuite;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class DomainSceneResetHelper {
    private static final ConcurrentHashMap<Integer, Long> LAST_RESET_MS = new ConcurrentHashMap();
    private static final long RESET_DEBOUNCE_MS = 1500L;
    private static final int REWARD_TREE_GADGET_ID = 70350008;

    private DomainSceneResetHelper() {
    }

    public static void clearGridCache(Scene scene) {
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        try {
            Path path = Path.of("cache", "scene" + scene.getId() + "_grid.json");
            if (Files.deleteIfExists(path)) {
                Grasscutter.getLogger().debug("Cleared domain grid cache {}", (Object)path);
            }
        }
        catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to clear domain grid cache for scene {}", (Object)scene.getId(), (Object)exception);
        }
    }

    public static void refreshToInitSuites(Scene scene) {
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        SceneScriptManager sceneScriptManager = scene.getScriptManager();
        if (sceneScriptManager == null || !sceneScriptManager.isInit()) {
            Grasscutter.getLogger().warn("Domain reset skipped scene={} scriptManager not ready", (Object)scene.getId());
            return;
        }
        long l = System.currentTimeMillis();
        int n = scene.getId();
        Long l2 = LAST_RESET_MS.get(n);
        if (l2 != null && l - l2 < 1500L) {
            int n2 = DomainSceneResetHelper.forceRespawnDomainProps(sceneScriptManager, scene);
            Grasscutter.getLogger().info("Domain reset debounce scene={} still forcedGadgets={}", (Object)n, (Object)n2);
            DomainChallengeKeyHelper.revealChallengeKeys(scene);
            DomainSceneResetHelper.broadcastNonAvatarEntities(scene);
            return;
        }
        LAST_RESET_MS.put(n, l);
        DomainMonsterSpawnHelper.clearDomainSpawnRecords(scene);
        ArrayList<SceneGroup> arrayList = new ArrayList<>();
        for (SceneBlock sceneBlock : sceneScriptManager.getBlocks().values()) {
            scene.loadBlock(sceneBlock);
            if (sceneBlock.groups == null) continue;
            for (SceneGroup sceneGroup : sceneBlock.groups.values()) {
                if (sceneGroup == null || sceneGroup.dynamic_load) continue;
                if (!sceneGroup.isLoaded()) {
                    sceneGroup.load(n);
                }
                arrayList.add(sceneGroup);
            }
        }
        if (!arrayList.isEmpty()) {
            scene.onLoadGroup(arrayList);
            scene.onRegisterGroups();
        }
        int n3 = 0;
        int n4 = 0;
        for (Object object : sceneScriptManager.getBlocks().values()) {
            if (((SceneBlock)object).groups == null) continue;
            for (SceneGroup sceneGroup : ((SceneBlock)object).groups.values()) {
                int n5;
                if (sceneGroup == null || sceneGroup.dynamic_load || sceneGroup.init_config == null || (n5 = sceneGroup.init_config.suite) <= 0 || sceneGroup.suites == null || sceneGroup.suites.isEmpty()) continue;
                SceneGroup sceneGroup2 = sceneScriptManager.getGroupById(sceneGroup.id);
                if (sceneGroup2 != null) {
                    sceneGroup = sceneGroup2;
                }
                if (!sceneGroup.isLoaded()) {
                    sceneGroup.load(n);
                }
                SceneGroupInstance sceneGroupInstance = sceneScriptManager.getGroupInstanceById(sceneGroup.id);
                for (int i = 1; i <= sceneGroup.suites.size(); ++i) {
                    SceneSuite sceneSuite;
                    if (i == n5 || (sceneSuite = sceneGroup.getSuiteByIndex(i)) == null) continue;
                    sceneScriptManager.killGroupSuite(sceneGroup, sceneSuite);
                    sceneScriptManager.removeGroupSuite(sceneGroup, sceneSuite);
                }
                if (sceneGroupInstance != null) {
                    sceneGroupInstance.getCachedGadgetStates().clear();
                    sceneGroupInstance.getCachedVariables().clear();
                    sceneGroupInstance.getDeadEntities().clear();
                    sceneGroupInstance.setActiveSuiteId(0);
                    sceneGroupInstance.setTargetSuiteId(0);
                }
                if (sceneScriptManager.refreshGroupSuite(sceneGroup.id, n5)) {
                    ++n3;
                }
                n4 += DomainSceneResetHelper.forceSpawnInitGadgets(sceneScriptManager, scene, sceneGroup, n5);
            }
        }
        n4 += DomainSceneResetHelper.forceRespawnDomainProps(sceneScriptManager, scene);
        int n6 = 0;
        for (GameEntity gameEntity : scene.getEntities().values()) {
            if (!(gameEntity instanceof EntityGadget)) continue;
            ++n6;
        }
        Grasscutter.getLogger().info("Domain reset scene={} suites={} forcedGadgets={} gadgetEntities={} players={}", new Object[]{n, n3, n4, n6, scene.getPlayers().size()});
        DomainChallengeKeyHelper.revealChallengeKeys(scene);
        DomainSceneResetHelper.broadcastNonAvatarEntities(scene);
    }

    private static void broadcastNonAvatarEntities(Scene scene) {
        for (Player player : scene.getPlayers()) {
            if (player == null) continue;
            ArrayList<GameEntity> arrayList = new ArrayList<GameEntity>();
            for (GameEntity gameEntity : scene.getEntities().values()) {
                if (gameEntity == null || gameEntity instanceof EntityAvatar) continue;
                arrayList.add(gameEntity);
            }
            if (arrayList.isEmpty()) continue;
            player.sendPacket((BasePacket)new PacketSceneEntityAppearNotify(arrayList, VisionTypeOuterClass.VisionType.VisionType_VISION_MEET));
        }
    }

    private static int forceRespawnDomainProps(SceneScriptManager sceneScriptManager, Scene scene) {
        int n = 0;
        for (SceneBlock sceneBlock : sceneScriptManager.getBlocks().values()) {
            if (sceneBlock.groups == null) continue;
            for (SceneGroup sceneGroup : sceneBlock.groups.values()) {
                if (sceneGroup == null || sceneGroup.dynamic_load) continue;
                SceneGroup sceneGroup2 = sceneScriptManager.getGroupById(sceneGroup.id);
                if (sceneGroup2 == null) {
                    sceneGroup2 = sceneGroup;
                }
                if (!sceneGroup2.isLoaded()) {
                    sceneGroup2.load(scene.getId());
                }
                if (sceneGroup2.gadgets == null || sceneGroup2.gadgets.isEmpty()) continue;
                SceneGroupInstance sceneGroupInstance = sceneScriptManager.getGroupInstanceById(sceneGroup2.id);
                for (SceneGadget sceneGadget : sceneGroup2.gadgets.values()) {
                    if (sceneGadget == null || !DomainSceneResetHelper.isDomainPropGadget(sceneGadget.gadget_id)) continue;
                    if (sceneGroupInstance != null) {
                        sceneGroupInstance.getDeadEntities().remove(sceneGadget.config_id);
                        sceneGroupInstance.getCachedGadgetStates().remove(sceneGadget.config_id);
                    }
                    n += DomainSceneResetHelper.recreateGadget(sceneScriptManager, scene, sceneGroup2, sceneGadget, true);
                }
            }
        }
        if (n > 0) {
            Grasscutter.getLogger().info("Domain forceRespawn props scene={} created={}", (Object)scene.getId(), (Object)n);
        }
        return n;
    }

    private static boolean isDomainPropGadget(int n) {
        return DomainChallengeKeyHelper.isChallengeKeyGadgetId(n) || DomainRewardStatueHelper.isExitRewardStatue(n) || n == 70350008;
    }

    private static int forceSpawnInitGadgets(SceneScriptManager sceneScriptManager, Scene scene, SceneGroup sceneGroup, int n) {
        List<SceneGadget> list = DomainSceneResetHelper.resolveInitGadgets(sceneGroup, n);
        if (list.isEmpty()) {
            Grasscutter.getLogger().warn("Domain group {} suite {} has no gadgets (gadgetsMap={})", new Object[]{sceneGroup.id, n, sceneGroup.gadgets != null ? sceneGroup.gadgets.size() : -1});
            return 0;
        }
        int n2 = 0;
        SceneGroupInstance sceneGroupInstance = sceneScriptManager.getGroupInstanceById(sceneGroup.id);
        for (SceneGadget sceneGadget : list) {
            if (sceneGadget == null) continue;
            boolean bl = DomainSceneResetHelper.isDomainPropGadget(sceneGadget.gadget_id);
            if (sceneGroupInstance != null && bl) {
                sceneGroupInstance.getDeadEntities().remove(sceneGadget.config_id);
                sceneGroupInstance.getCachedGadgetStates().remove(sceneGadget.config_id);
            }
            n2 += DomainSceneResetHelper.recreateGadget(sceneScriptManager, scene, sceneGroup, sceneGadget, bl);
        }
        return n2;
    }

    private static List<SceneGadget> resolveInitGadgets(SceneGroup sceneGroup, int n) {
        ArrayList<SceneGadget> arrayList = new ArrayList<SceneGadget>();
        SceneSuite sceneSuite = sceneGroup.getSuiteByIndex(n);
        if (sceneSuite == null) {
            return arrayList;
        }
        if ((sceneSuite.sceneGadgets == null || sceneSuite.sceneGadgets.isEmpty()) && sceneGroup.gadgets != null) {
            sceneSuite.init(sceneGroup);
        }
        if (sceneSuite.sceneGadgets != null && !sceneSuite.sceneGadgets.isEmpty()) {
            arrayList.addAll(sceneSuite.sceneGadgets);
            return arrayList;
        }
        if (sceneSuite.gadgets != null && sceneGroup.gadgets != null) {
            for (Integer n2 : sceneSuite.gadgets) {
                SceneGadget sceneGadget;
                if (n2 == null || (sceneGadget = (SceneGadget)sceneGroup.gadgets.get(n2)) == null) continue;
                arrayList.add(sceneGadget);
            }
        }
        return arrayList;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static int recreateGadget(SceneScriptManager sceneScriptManager, Scene scene, SceneGroup sceneGroup, SceneGadget sceneGadget, boolean bl) {
        EntityGadget entityGadget;
        GameEntity gameEntity = scene.getEntityByConfigId(sceneGadget.config_id, sceneGroup.id);
        if (gameEntity instanceof EntityGadget) {
            boolean bl2;
            EntityGadget entityGadget2 = (EntityGadget)gameEntity;
            boolean bl3 = DomainChallengeKeyHelper.isChallengeKeyGadgetId(entityGadget2.getGadgetId());
            boolean bl4 = bl2 = bl || !entityGadget2.isAlive() || bl3 && entityGadget2.getState() != 0 && entityGadget2.getState() != sceneGadget.state;
            if (!bl2) {
                DomainChallengeKeyHelper.ensureKeyWorktop(entityGadget2);
                return 0;
            }
            DomainSceneResetHelper.removeQuietly(scene, (GameEntity)entityGadget2);
        } else if (gameEntity != null) {
            DomainSceneResetHelper.removeQuietly(scene, gameEntity);
        }
        boolean bl5 = sceneGadget.isOneoff;
        sceneGadget.isOneoff = false;
        try {
            entityGadget = sceneScriptManager.createGadget(sceneGroup.id, sceneGroup.block_id, sceneGadget, sceneGadget.state);
        }
        finally {
            sceneGadget.isOneoff = bl5;
        }
        if (entityGadget == null) {
            Grasscutter.getLogger().warn("Domain createGadget failed scene={} group={} cfg={} gadgetId={}", new Object[]{scene.getId(), sceneGroup.id, sceneGadget.config_id, sceneGadget.gadget_id});
            return 0;
        }
        DomainChallengeKeyHelper.ensureKeyWorktop(entityGadget);
        scene.addEntity((GameEntity)entityGadget);
        return 1;
    }

    private static void removeQuietly(Scene scene, GameEntity gameEntity) {
        try {
            scene.removeEntity(gameEntity, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
        }
        catch (Throwable throwable) {
            try {
                scene.killEntity(gameEntity, 0);
            }
            catch (Throwable throwable2) {
                // empty catch block
            }
        }
    }
}

