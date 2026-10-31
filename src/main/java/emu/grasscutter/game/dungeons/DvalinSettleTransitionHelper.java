package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import java.util.ArrayList;
import java.util.List;

/**
 * Stormterror weekly domain (scene 20025) post-settle cleanup.
 *
 * <p>Never remove Dvalin_S04_Platform gadgets (1002-1010) — they ARE the arena floor.
 * Removing them causes the ground to vanish and the player to fall forever.
 *
 * <p>Do not ENTER_GOTO / removePlayer — that reloads the group and re-spawns Dvalin.
 */
public final class DvalinSettleTransitionHelper {
    private static final int SCENE_ID = 20025;

    private DvalinSettleTransitionHelper() {}

    public static void onDungeonSettleSuccess(Scene scene) {
        if (scene == null || scene.getId() != SCENE_ID) {
            return;
        }
        try {
            scene.getScheduler().scheduleDelayedTask(() -> applyCleanup(scene), 2);
            scene.getScheduler().scheduleDelayedTask(() -> applyCleanup(scene), 5);
        } catch (Throwable t) {
            applyCleanup(scene);
        }
    }

    private static void applyCleanup(Scene scene) {
        try {
            WeeklyBossModelCleanup.sweepAfterSettle(scene);
            removeDvalinMonsters(scene);
            purgeOrphanClientGadgets(scene);
            Grasscutter.getLogger().info("Dvalin settle cleanup applied scene={}", SCENE_ID);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("Dvalin settle cleanup failed", t);
        }
    }

    private static void removeDvalinMonsters(Scene scene) {
        List<GameEntity> toRemove = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (ge instanceof EntityMonster em
                    && em.getMonsterData() != null
                    && em.getMonsterData().getId() >= 29010101
                    && em.getMonsterData().getId() <= 29010104) {
                toRemove.add(em);
            }
        }
        if (!toRemove.isEmpty()) {
            scene.removeEntities(toRemove, VisionType.VisionType_VISION_REMOVE);
        }
    }

    /**
     * Only purge client gadgets whose owner is a dead/missing Dvalin monster — not all client
     * gadgets (avatars may own skill gadgets).
     */
    private static void purgeOrphanClientGadgets(Scene scene) {
        List<GameEntity> toRemove = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityClientGadget cg)) {
                continue;
            }
            int ownerId = cg.getOwnerEntityId();
            GameEntity owner = scene.getEntityById(ownerId);
            if (owner == null) {
                // Owner already gone (killed boss) — safe to drop leftover ability mesh.
                toRemove.add(cg);
                continue;
            }
            if (owner instanceof EntityMonster em
                    && em.getMonsterData() != null
                    && em.getMonsterData().getId() >= 29010101
                    && em.getMonsterData().getId() <= 29010104) {
                toRemove.add(cg);
            }
        }
        if (!toRemove.isEmpty()) {
            scene.removeEntities(toRemove, VisionType.VisionType_VISION_REMOVE);
        }
        for (Player p : scene.getPlayers()) {
            try {
                p.getTeamManager()
                        .getGadgets()
                        .removeIf(
                                g -> {
                                    try {
                                        return !scene.getEntities().containsKey(g.getId());
                                    } catch (Throwable t) {
                                        return false;
                                    }
                                });
            } catch (Throwable ignored) {
            }
        }
    }
}
