package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.MonsterType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import java.util.ArrayList;
import java.util.List;

/**
 * Weekly / domain bosses often leave a stuck corpse when removed with {@code VISION_DIE}
 * (client waits on a death timeline that never finishes). Force {@code VISION_REMOVE} and
 * drop ability-owned client gadgets so models like Dvalin do not linger after settle.
 */
public final class WeeklyBossModelCleanup {
    private WeeklyBossModelCleanup() {}

    public static boolean shouldForceRemoveModel(Scene scene, EntityMonster monster) {
        if (scene == null || monster == null || scene.getSceneType() != SceneType.SCENE_DUNGEON) {
            return false;
        }
        if (monster.getMonsterData() == null) {
            return false;
        }
        MonsterType type = monster.getMonsterData().getType();
        if (type == MonsterType.MONSTER_BOSS) {
            return true;
        }
        // Dvalin / Andrius / Tartaglia / later weekly bosses share 29xxxxxx ids.
        int id = monster.getMonsterData().getId();
        return id >= 29010100 && id <= 29999999;
    }

    /** Remove ability gadgets still owned by this boss, then disappear with VISION_REMOVE. */
    public static void removeBossAndOwnedGadgets(Scene scene, EntityMonster boss) {
        if (scene == null || boss == null) {
            return;
        }
        int ownerId = boss.getId();
        List<GameEntity> owned = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (ge instanceof EntityClientGadget cg && cg.getOwnerEntityId() == ownerId) {
                owned.add(ge);
            }
        }
        if (!owned.isEmpty()) {
            scene.removeEntities(owned, VisionType.VisionType_VISION_REMOVE);
        }
        scene.removeEntity(boss, VisionType.VisionType_VISION_REMOVE);
    }

    /** After dungeon settle: sweep any leftover weekly-boss monsters still on the scene. */
    public static void sweepAfterSettle(Scene scene) {
        if (scene == null || scene.getSceneType() != SceneType.SCENE_DUNGEON) {
            return;
        }
        List<EntityMonster> leftovers = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (ge instanceof EntityMonster em && shouldForceRemoveModel(scene, em)) {
                leftovers.add(em);
            }
        }
        for (EntityMonster em : leftovers) {
            removeBossAndOwnedGadgets(scene, em);
        }
    }
}
