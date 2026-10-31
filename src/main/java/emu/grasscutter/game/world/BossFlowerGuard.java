/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.world;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGadget;
import java.util.ArrayList;
import java.util.Map;

public final class BossFlowerGuard {
    private static final int[] FLOWER_GADGET_IDS = new int[]{70210106, 70210107, 70210108, 70210109, 70210110, 70210111, 70210112};

    private BossFlowerGuard() {
    }

    private static boolean isFlowerGadgetId(int n) {
        for (int n2 : FLOWER_GADGET_IDS) {
            if (n2 != n) continue;
            return true;
        }
        return false;
    }

    public static boolean hasUnclaimedFlower(Scene scene, int n, float f, float f2, float f3) {
        if (scene == null || n <= 0) {
            return false;
        }
        try {
            Map<Integer, GameEntity> map = scene.getEntities();
            if (map == null) {
                return false;
            }
            for (GameEntity gameEntity : map.values()) {
                boolean bl;
                if (!(gameEntity instanceof EntityGadget)) continue;
                EntityGadget entityGadget = (EntityGadget)gameEntity;
                int n2 = entityGadget.getGroupId();
                int n3 = 0;
                try {
                    n3 = entityGadget.getGadgetId();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                if (n3 <= 0) {
                    try {
                        n3 = entityGadget.getEntityTypeId();
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
                boolean bl2 = n2 == n;
                boolean bl3 = BossFlowerGuard.isFlowerGadgetId(n3);
                SceneGadget sceneGadget = null;
                try {
                    sceneGadget = entityGadget.getMetaGadget();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                if (bl2 && bl3 && (sceneGadget == null || sceneGadget.boss_chest == null)) {
                    sceneGadget = WorldBossSpawnHelper.ensureBossChestGadgetMeta(entityGadget);
                }
                boolean bl4 = sceneGadget != null && sceneGadget.boss_chest != null;
                int n4 = 0;
                try {
                    n4 = entityGadget.getState();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                boolean bl5 = bl = n4 == 102;
                if (bl3) {
                    if (bl || (bl2 && !bl4)) continue;
                    if (bl2) {
                        return true;
                    }
                    try {
                        double d;
                        double d2;
                        double d3;
                        Position position = entityGadget.getPosition();
                        if (position != null && (d3 = (double)(position.getX() - f)) * d3 + (d2 = (double)(position.getY() - f2)) * d2 + (d = (double)(position.getZ() - f3)) * d <= 1600.0) {
                            return true;
                        }
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
                if (!bl2 || bl || !bl4) continue;
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    public static void despawnAliveBossMonsters(Scene scene, int n, int n2) {
        if (scene == null || n <= 0) {
            return;
        }
        try {
            Map<Integer, GameEntity> map = scene.getEntities();
            if (map == null) {
                return;
            }
            ArrayList<EntityMonster> arrayList = new ArrayList<EntityMonster>();
            for (GameEntity gameEntity : map.values()) {
                EntityMonster entityMonster;
                if (!(gameEntity instanceof EntityMonster) || (entityMonster = (EntityMonster)gameEntity).getGroupId() != n || n2 > 0 && entityMonster.getEntityTypeId() != n2) continue;
                try {
                    if (!entityMonster.isAlive()) {
                        continue;
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                arrayList.add(entityMonster);
            }
            for (GameEntity gameEntity : arrayList) {
                try {
                    scene.removeEntity(gameEntity);
                }
                catch (Throwable throwable) {}
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}
