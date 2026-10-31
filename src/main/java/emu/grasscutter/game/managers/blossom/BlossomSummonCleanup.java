package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.server.packet.send.PacketSceneEntityDisappearNotify;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import java.util.*;
import java.util.function.ToIntFunction;

/** Removes only entities summoned by monsters belonging to a completed ley-line fight. */
final class BlossomSummonCleanup {
    private BlossomSummonCleanup() {}

    /**
     * Find the transitive children of encounter roots, even when a child appears before its parent
     * in the scene collection. Never return the roots themselves or unrelated scene entities.
     */
    static <T> List<T> descendants(
            Collection<T> entities,
            Collection<Integer> rootIds,
            ToIntFunction<T> entityId,
            ToIntFunction<T> parentId) {
        Set<Integer> ownedIds = new HashSet<>(rootIds);
        List<T> pending = new ArrayList<>(entities);
        List<T> owned = new ArrayList<>();
        boolean changed;
        do {
            changed = false;
            var iterator = pending.iterator();
            while (iterator.hasNext()) {
                T entity = iterator.next();
                int id = entityId.applyAsInt(entity);
                int parent = parentId.applyAsInt(entity);
                if (parent == 0 || ownedIds.contains(id) || !ownedIds.contains(parent)) continue;
                ownedIds.add(id);
                owned.add(entity);
                iterator.remove();
                changed = true;
            }
        } while (changed);
        return owned;
    }

    static boolean isEncounterChild(
            int parentId, Collection<Integer> rootIds, Collection<Integer> recordedIds) {
        return parentId != 0 && (rootIds.contains(parentId) || recordedIds.contains(parentId));
    }

    static int parentId(GameEntity entity) {
        if (entity instanceof EntityMonster monster) return monster.getOwnerEntityId();
        if (entity instanceof EntityClientGadget gadget) return gadget.getOwnerEntityId();
        if (entity instanceof EntityGadget gadget && gadget.getOwner() != null) {
            return gadget.getOwner().getId();
        }
        return 0;
    }

    static List<GameEntity> find(Scene scene, Collection<Integer> ownerIds) {
        return descendants(new ArrayList<>(scene.getEntities().values()), ownerIds,
                GameEntity::getId, BlossomSummonCleanup::parentId);
    }

    record Removal(int live, int stale) {}

    static Removal remove(Scene scene, Collection<GameEntity> remembered) {
        var live = new ArrayList<GameEntity>();
        var stale = new ArrayList<GameEntity>();
        for (var summon : remembered) {
            if (scene.getEntityById(summon.getId()) == summon) {
                live.add(summon);
            } else {
                // The entity previously disappeared with VISION_DIE. Some 7.1 SkillObjs
                // continue rendering until an explicit VISION_REMOVE arrives.
                stale.add(summon);
            }
            if (summon instanceof EntityClientGadget client) {
                client.getOwner().getTeamManager().getGadgets().remove(client);
            }
        }
        if (!live.isEmpty()) {
            // No death reward, quest progress or callbacks for temporary ability summons.
            scene.removeEntities(live, VisionType.VisionType_VISION_REMOVE);
        }
        if (!stale.isEmpty()) {
            scene.broadcastPacket(new PacketSceneEntityDisappearNotify(
                    stale, VisionType.VisionType_VISION_REMOVE));
        }
        return new Removal(live.size(), stale.size());
    }
}
