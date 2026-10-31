package emu.grasscutter.game.world;

import emu.grasscutter.game.entity.EntityMonster;

/**
 * Azhdaha combat-start hook.
 *
 * <p>Placeholder, same situation as {@link SignoraCombatStartHelper}: {@link EntityMonster} already
 * calls into it while building the monster's config abilities and on every tick, but the scene id
 * and the ability names that have to be withheld until the fight's element-swap phase begins were
 * never committed alongside those call sites.
 *
 * <p>Both predicates answer {@code false}, which is the pre-hook behaviour - no scene is treated as
 * the Azhdaha arena, so no config ability is skipped and every ability is attached as usual.
 */
public final class AzhdahaCombatStartHelper {

    private AzhdahaCombatStartHelper() {}

    /**
     * True for the Azhdaha trounce-domain scene.
     *
     * @return always {@code false} - no scene is special-cased yet.
     */
    public static boolean isAzhdahaScene(int sceneId) {
        return false;
    }

    /**
     * True for a config ability that must not be attached at spawn (element-swap phase abilities
     * the boss only gains partway through the fight).
     *
     * @return always {@code false} - every config ability is attached.
     */
    public static boolean shouldSkipAbility(String abilityName) {
        return false;
    }

    /** Per-tick phase driver. Neutral until the fight data exists. */
    public static void onMonsterTick(EntityMonster monster) {
        // no-op
    }
}
