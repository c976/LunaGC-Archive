package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hypostasis (Effigy) shell & elemental-shield HP lock.
 *
 * <p>Official abilities use {@code LockHP} on the cube-shell modifier and {@code ShieldBarMixin} on
 * the elemental gauge (e.g. Pyro burning). The client shows those states, but the server previously
 * applied client-reported HP damage anyway. Mirror Signora's pattern: keep {@code lockHP} in sync
 * and intercept damage while protected.
 */
public final class EffigyCombatHelper {
    /** Entity ids that have had a shell / shield-bar protective modifier at least once. */
    private static final Set<Integer> SEEN_PROTECTIVE_MOD = ConcurrentHashMap.newKeySet();

    private EffigyCombatHelper() {}

    /** Monster IDs 200401xx–200407xx (Electro…Dendro Hypostasis). */
    public static boolean isEffigyMonsterId(int monsterId) {
        int family = monsterId / 100;
        return family >= 200401 && family <= 200407;
    }

    public static boolean isEffigy(EntityMonster monster) {
        if (monster == null || monster.getMonsterData() == null) {
            return false;
        }
        return isEffigyMonsterId(monster.getMonsterData().getId());
    }

    /** Spawn / ability init: shell is the default combat state. */
    public static void onMonsterInit(EntityMonster monster) {
        if (!isEffigy(monster)) {
            return;
        }
        try {
            monster.setLockHP(true);
            purgeStaleRebornGadgets(monster);
            Grasscutter.getLogger()
                    .debug(
                            "[Effigy] spawn lockHP monsterId={} entityId={}",
                            monster.getMonsterData().getId(),
                            monster.getId());
        } catch (Throwable ignored) {
        }
    }

    /**
     * CORESTATE / Hydro {@code _STATE_}: 0 = shelled (immune), ≥1 = core exposed (vulnerable),
     * unless a ShieldBar modifier is still active.
     */
    public static void onGlobalValue(GameEntity entity, String key, float value) {
        if (!(entity instanceof EntityMonster monster) || !isEffigy(monster) || key == null) {
            return;
        }
        if (!isCoreStateKey(key)) {
            return;
        }
        boolean shelled = value < 0.5f;
        if (!shelled && hasShieldBarModifier(entity)) {
            shelled = true;
        }
        applyLock(monster, shelled, "gv:" + key + "=" + value);
    }

    /**
     * After MODIFIER_CHANGE add/remove: recompute from live instanced modifiers + CORESTATE.
     *
     * <p>Until the client has attached a protective modifier at least once, keep the spawn-time
     * lock so a random early remove cannot unlock the boss while still shelled.
     */
    public static void onModifiersChanged(GameEntity entity) {
        if (!(entity instanceof EntityMonster monster) || !isEffigy(monster)) {
            return;
        }
        if (isProtectedByModifiers(entity)) {
            SEEN_PROTECTIVE_MOD.add(monster.getId());
            applyLock(monster, true, "modifiers");
            return;
        }
        Float core = readCoreState(entity);
        if (core != null) {
            applyLock(monster, core < 0.5f, "modifiers+gv");
            return;
        }
        if (SEEN_PROTECTIVE_MOD.contains(monster.getId())) {
            // Had a shell/shield modifier; it is gone and CORESTATE unknown → core phase.
            applyLock(monster, false, "modifiers-cleared");
        }
        // else: still waiting for first shell sync — leave spawn lockHP alone
    }

    /** Periodic / pre-hit sync so we do not need AbilityManager hot-patches. */
    public static void onMonsterTick(EntityMonster monster) {
        if (!isEffigy(monster)) {
            return;
        }
        syncLockFromLiveState(monster);
    }

    /** Block HP damage while shell / elemental shield is up. */
    public static boolean tryInterceptDamage(EntityMonster monster, float amount) {
        if (monster == null || amount <= 0f || !isEffigy(monster)) {
            return false;
        }
        syncLockFromLiveState(monster);
        if (!(monster.isLockHP() || isProtectedByModifiers(monster))) {
            return false;
        }
        // Client still predicts HP locally. If we drop the hit with no CUR_HP echo, its HP drifts
        // down and AttachModifierToHPPercentMixin falsely fires rebirth → floating crystals that
        // never clear. Re-assert server HP so the client stays in sync.
        resyncHp(monster);
        return true;
    }

    /**
     * Effigy skill-obj gadgets the client already places. A server {@code EntityGadget} copy has no
     * SkillObj config and falls back to {@code Default_Item} — a weird floating “device” that never
     * receives KillSelf (especially {@code Effigy_InitialPos} / {@code Effigy_RushPos}).
     */
    public static boolean isEffigyClientOwnedGadget(int gadgetId) {
        return switch (gadgetId) {
            case 42004003, // ElectricBarrageEmitter
                    42004004, // ElectricDefenceCentre
                    42004005, // ElectricDefencePart
                    42004006, // InitialPos (center marker — often the lingering floaty)
                    42004007, // DefencePartCurrent
                    42004008, // ThunderDrop
                    42004010, // RebornPart
                    42004012, // RushPos (ConfigBornByGlobalValue — stacks at origin)
                    42004013, // Elite pile
                    42004014, // elite charge bullet
                    42004015, // elite electric bullet
                    42004016, // elite reborn
                    42004017,
                    42004018, // elite reborn big
                    42004019,
                    42004020 -> true;
            default -> false;
        };
    }

    /**
     * Rebirth prisms, which the Effigy spawns several of at once.
     *
     * <p>{@code CreateRebornPart1/2/3} all name the same gadget id, so the one-summon-per-owner
     * cleanup in {@code ActionCreateGadget} would delete prism #1 while spawning #2 and leave the
     * arena with a single prism the fight cannot be finished from.
     */
    public static boolean isEffigyRebornPrismGadget(int gadgetId) {
        return switch (gadgetId) {
            case 42004010, // RebornPart
                    42004016, // elite reborn
                    42004018 -> // elite reborn big
                    true;
            default -> false;
        };
    }

    /** Drop leftover server Default_Item shells when the boss (re)spawns. */
    public static void purgeStaleRebornGadgets(EntityMonster monster) {
        if (!isEffigy(monster) || monster.getScene() == null) {
            return;
        }
        try {
            var scene = monster.getScene();
            var stale =
                    scene.getEntities().values().stream()
                            .filter(
                                    e ->
                                            e instanceof emu.grasscutter.game.entity.EntityGadget g
                                                    && isEffigyClientOwnedGadget(g.getGadgetId())
                                                    && (g.getOwner() == monster
                                                            || g.getOwner() == null
                                                            || distanceSq(monster, g) < 80f * 80f))
                            .toList();
            for (var e : stale) {
                scene.removeEntity(e);
                Grasscutter.getLogger()
                        .info(
                                "[Effigy] purged stale helper gadget={} entityId={}",
                                e instanceof emu.grasscutter.game.entity.EntityGadget g
                                        ? g.getGadgetId()
                                        : -1,
                                e.getId());
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("[Effigy] purge helper gadgets: {}", t.toString());
        }
    }

    private static float distanceSq(EntityMonster monster, emu.grasscutter.game.entity.EntityGadget g) {
        try {
            var a = monster.getPosition();
            var b = g.getPosition();
            float dx = a.getX() - b.getX();
            float dy = a.getY() - b.getY();
            float dz = a.getZ() - b.getZ();
            return dx * dx + dy * dy + dz * dz;
        } catch (Throwable ignored) {
            return Float.MAX_VALUE;
        }
    }

    private static void resyncHp(EntityMonster monster) {
        try {
            monster
                    .getScene()
                    .broadcastPacket(
                            new emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify(
                                    monster, emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Derive lockHP from already-tracked instanced modifiers + CORESTATE GVs (no AbilityManager
     * patch required for hot-inject).
     */
    private static void syncLockFromLiveState(EntityMonster monster) {
        if (isProtectedByModifiers(monster)) {
            SEEN_PROTECTIVE_MOD.add(monster.getId());
            applyLock(monster, true, "live-mod");
            return;
        }
        Float core = readCoreState(monster);
        if (core != null) {
            boolean shelled = core < 0.5f;
            if (!shelled && hasShieldBarModifier(monster)) {
                shelled = true;
            }
            applyLock(monster, shelled, "live-gv");
            return;
        }
        if (SEEN_PROTECTIVE_MOD.contains(monster.getId())) {
            applyLock(monster, false, "live-cleared");
        }
    }

    public static boolean isProtectedByModifiers(GameEntity entity) {
        if (entity == null || entity.getInstancedModifiers() == null) {
            return false;
        }
        for (AbilityModifierController ctrl : entity.getInstancedModifiers().values()) {
            if (ctrl == null || ctrl.getModifierData() == null) {
                continue;
            }
            if (isProtectiveModifier(ctrl.getModifierData())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isProtectiveModifier(AbilityModifier data) {
        if (data == null) {
            return false;
        }
        if (data.state == AbilityModifier.State.LockHP
                || data.state == AbilityModifier.State.Invincible) {
            return true;
        }
        return hasShieldBarMixin(data);
    }

    private static Float readCoreState(GameEntity entity) {
        if (entity.getGlobalAbilityValues() == null) {
            return null;
        }
        for (var e : entity.getGlobalAbilityValues().entrySet()) {
            if (e.getKey() != null && isCoreStateKey(e.getKey()) && e.getValue() != null) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean hasShieldBarModifier(GameEntity entity) {
        if (entity == null || entity.getInstancedModifiers() == null) {
            return false;
        }
        for (AbilityModifierController ctrl : entity.getInstancedModifiers().values()) {
            if (ctrl != null && hasShieldBarMixin(ctrl.getModifierData())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasShieldBarMixin(AbilityModifier data) {
        if (data == null || data.modifierMixins == null) {
            return false;
        }
        for (AbilityMixinData mixin : data.modifierMixins) {
            if (mixin != null && mixin.type == AbilityMixinData.Type.ShieldBarMixin) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCoreStateKey(String key) {
        // Fire/Electric/Rock/Wind/Ice/Grass use *_CORESTATE_; Hydro uses _STATE_.
        if ("_STATE_".equals(key)) {
            return true;
        }
        String upper = key.toUpperCase();
        return upper.contains("CORESTATE");
    }

    private static void applyLock(EntityMonster monster, boolean lock, String reason) {
        try {
            boolean prev = monster.isLockHP();
            monster.setLockHP(lock);
            if (prev != lock) {
                Grasscutter.getLogger()
                        .info(
                                "[Effigy] lockHP={} entityId={} monsterId={} via {}",
                                lock,
                                monster.getId(),
                                monster.getMonsterData().getId(),
                                reason);
            }
        } catch (Throwable ignored) {
        }
    }
}
