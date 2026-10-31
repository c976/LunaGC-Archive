package emu.grasscutter.game.world;

import emu.grasscutter.game.entity.EntityMonster;

/**
 * La Signora combat-start hook.
 *
 * <p>Placeholder. The three entry points below are already wired into {@link EntityMonster} - tick,
 * damage and removal - but the fight data they need (group / config ids, the intro phase window, the
 * invulnerability the boss holds during its transformation) was never committed with those call
 * sites, so there is nothing here to drive them. See {@link AndriusTrialStartHelper} and
 * {@link EffigyCombatHelper} for the shape a real implementation takes.
 *
 * <p>Every method is neutral on purpose: the callers wrap each one in {@code try/catch} and read a
 * {@code false} interception as "let the normal path run", so this behaves exactly as if the hooks
 * were not there. Filling it in is a behaviour change, not a compile fix, and needs the fight's
 * scene data first.
 */
public final class SignoraCombatStartHelper {

    private SignoraCombatStartHelper() {}

    /** Per-tick phase driver. Neutral until the fight data exists. */
    public static void onMonsterTick(EntityMonster monster) {
        // no-op
    }

    /**
     * True when the boss must swallow this damage (intro / transformation invulnerability).
     *
     * @return always {@code false} - damage takes the normal path.
     */
    public static boolean tryInterceptDamage(EntityMonster monster, float amount) {
        return false;
    }

    /** Called when the boss entity leaves the scene. Neutral until the fight data exists. */
    public static void onBossRemoved(EntityMonster monster) {
        // no-op
    }
}
