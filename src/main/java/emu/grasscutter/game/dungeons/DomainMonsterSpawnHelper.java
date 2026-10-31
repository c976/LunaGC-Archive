/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.game.world.SceneGroupInstance
 *  emu.grasscutter.scripts.SceneScriptManager
 *  emu.grasscutter.scripts.data.SceneBlock
 *  emu.grasscutter.scripts.data.SceneGroup
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.world.OpenWorldSpawnHelper;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneGroup;

public final class DomainMonsterSpawnHelper {
    private DomainMonsterSpawnHelper() {
    }

    public static void clearDomainSpawnRecords(Scene scene) {
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        SceneScriptManager sceneScriptManager = scene.getScriptManager();
        if (sceneScriptManager == null || !sceneScriptManager.isInit()) {
            return;
        }
        int n = 0;
        for (SceneBlock sceneBlock : sceneScriptManager.getBlocks().values()) {
            if (sceneBlock == null || sceneBlock.groups == null) continue;
            for (SceneGroup sceneGroup : sceneBlock.groups.values()) {
                if (sceneGroup == null || sceneGroup.id <= 0) continue;
                try {
                    OpenWorldSpawnHelper.clearGroupDeathRecords(scene, sceneGroup.id);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                try {
                    SceneGroupInstance sceneGroupInstance = sceneScriptManager.getGroupInstanceById(sceneGroup.id);
                    if (sceneGroupInstance != null) {
                        sceneGroupInstance.getDeadEntities().clear();
                        sceneGroupInstance.save();
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                ++n;
            }
        }
        Grasscutter.getLogger().info("Cleared domain spawn death records scene={} groups={}", (Object)scene.getId(), (Object)n);
    }
}

