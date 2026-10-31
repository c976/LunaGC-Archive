package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.props.FightProperty;

/** Complete only MQ1023's exhausted transform phases through the native group death events. */
public final class LiyueGoldenHouseCombatSupport {
    private LiyueGoldenHouseCombatSupport() {}

    static boolean isTransformPhase(int sceneId, int dungeonId, int groupId, int configId, int monsterId) {
        return sceneId == 20101 && dungeonId == 101 && groupId == 220101001
                && ((configId == 1001 && monsterId == 29030101)
                    || (configId == 1004 && monsterId == 29030102));
    }

    public static void afterDamage(EntityMonster monster, float hpBefore, float damage, int killerId) {
        var scene = monster.getScene();
        var dungeon = scene != null ? scene.getDungeonManager() : null;
        if (dungeon == null || !isTransformPhase(scene.getId(), dungeon.getDungeonData().getId(),
                monster.getGroupId(), monster.getConfigId(), monster.getMonsterData().getId())
                || !Float.isFinite(damage) || damage <= 0 || !Float.isFinite(hpBefore) || hpBefore <= 0
                || !monster.isLimbo() || !monster.isAlive()
                || monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 1.01f
                || scene.getEntityById(monster.getId()) != monster) return;
        synchronized (monster) {
            if (monster.isGoldenHousePhaseTransitionPending()) return;
            monster.setGoldenHousePhaseTransitionPending(true);
        }
        // Give the native animation/KillSelf notification time to finish. If it already removed
        // this entity, the fallback does nothing; repeated hits cannot create duplicate phases.
        scene.getScheduler().scheduleDelayedTask(() -> {
            synchronized (monster) { monster.setGoldenHousePhaseTransitionPending(false); }
            if (scene.getEntityById(monster.getId()) != monster || !monster.isAlive()
                    || !monster.isLimbo()
                    || monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 1.01f) return;
            Grasscutter.getLogger().info("Completing exhausted Golden House transform phase: config={}",
                    monster.getConfigId());
            scene.killEntity(monster, killerId);
        }, LiyueCloudDungeonSupport.ticksForSeconds(2));
    }
}
