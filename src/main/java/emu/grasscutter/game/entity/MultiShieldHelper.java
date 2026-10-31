package emu.grasscutter.game.entity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.dps.DPSAttackContext;
import emu.grasscutter.game.dps.DPSReactionHelper;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import java.util.Map;
import java.util.Set;

/**
 * The revival-shield stack a boss raises over its HP bar.
 *
 * <p>Officially (the Zharptitsa / 不灭衍生造物 fight) the boss spends its own HP to enter the shielded
 * state, and from then on the player is hitting shields rather than the boss: the stack is sized by the
 * ability specials {@code ShieldMaxCount} layers of {@code Init_ShieldValue} of max HP each, and the
 * fight only opens up once every layer is gone.
 *
 * <p>The data drives it through {@code Init_ShieldValue} - its presence on the ability is what marks a
 * stack - so nothing here is boss-specific beyond that marker. The official self-heal while shielded
 * is not simulated: its rate ({@code HealHPRatio}) is written by the scene at runtime and no resource
 * carries it. Instead ordinary hits are damped (see {@link #ORDINARY_EFFICIENCY} and
 * {@link #ORDINARY_NET_SHARE}) while a star reaction, as classified by {@link DPSReactionHelper}, hits
 * the stack at the {@code StarMode_Shield_Break} multiple.
 *
 * <p>Layers are only removed from the server's own modifier records; the client is not sent a
 * modifier change, so its shield display can lag behind until the phase ends.
 */
public final class MultiShieldHelper {

    /** Ability special that marks an ability as owning a shield stack, and sizes one layer. */
    private static final String PER_LAYER_RATIO = "Init_ShieldValue";

    /** Ordinary hits are damped this much before the top-up runs. */
    private static final float ORDINARY_EFFICIENCY = 0.1f;

    /**
     * Share of the damped damage that survives the top-up.
     *
     * <p>Officially the boss tops the stack back up while shielded and only a star reaction cuts that
     * off, so ordinary hits barely move it. The official knob is the {@code HealHPRatio} global value,
     * which no resource file in this build assigns - the scene writes it at runtime - so the top-up is
     * applied per hit instead, at a rate that scales with the player's own damage and therefore needs no
     * number from the data. 20% of the damped damage puts a single 34k open-world layer at roughly half
     * a minute of ordinary DPS while a star reaction still clears it in a couple of hits; at 1% the same
     * layer needed 34 million raw damage and the boss was unkillable without a reaction.
     */
    private static final float ORDINARY_NET_SHARE = 0.2f;

    /** Reactions that break the stack, as classified by {@link DPSReactionHelper}. */
    private static final Set<String> SHIELD_BREAKING_REACTIONS =
            Set.of("Astral Superconduct", "Astral Swirl");

    private MultiShieldHelper() {}

    /** A shield stack: the ability that owns it, and how much HP one of its layers soaks. */
    public record Setup(AbilityData ability, float perLayerHpRatio) {

        float perLayerHp(float maxHp) {
            return maxHp * perLayerHpRatio;
        }
    }

    /**
     * The shield stack this entity is currently holding, or null when its invincibility is not a
     * shield stack (a plain Invincible modifier still blocks damage outright).
     */
    public static Setup findSetup(GameEntity entity) {
        if (entity == null || entity.getInstancedModifiers() == null) return null;

        for (AbilityModifierController ctrl : entity.getInstancedModifiers().values()) {
            if (ctrl == null || ctrl.getModifierData() == null || ctrl.getAbilityData() == null) {
                continue;
            }
            if (ctrl.getModifierData().state != AbilityModifier.State.Invincible) continue;

            AbilityData ability = ctrl.getAbilityData();
            Float ratio = ability.abilitySpecials == null ? null : ability.abilitySpecials.get(PER_LAYER_RATIO);
            if (ratio == null || ratio <= 0f) continue;

            return new Setup(ability, ratio);
        }
        return null;
    }

    /**
     * Layers of this stack still standing.
     *
     * <p>Counted per owning ability, not per modifier: a boss can carry an unrelated Invincible
     * modifier - this one has a second one on its return-to-born ability - and counting those too
     * would inflate the stack and stretch the phase out.
     */
    private static int countLayers(GameEntity entity, Setup setup) {
        if (entity == null || entity.getInstancedModifiers() == null) return 0;
        int layers = 0;
        for (AbilityModifierController ctrl : entity.getInstancedModifiers().values()) {
            if (ctrl != null
                    && ctrl.getModifierData() != null
                    && ctrl.getModifierData().state == AbilityModifier.State.Invincible
                    && ctrl.getAbilityData() == setup.ability()) {
                layers++;
            }
        }
        return layers;
    }

    /**
     * Takes one layer off the stack, so the client's view follows.
     *
     * @return the layer count left standing
     */
    private static int breakOneLayer(GameEntity entity, Setup setup) {
        for (var entry : entity.getInstancedModifiers().entrySet()) {
            AbilityModifierController ctrl = entry.getValue();
            if (ctrl == null || ctrl.getModifierData() == null) continue;
            if (ctrl.getModifierData().state != AbilityModifier.State.Invincible) continue;
            if (ctrl.getAbilityData() != setup.ability()) continue;

            entity.getInstancedModifiers().remove(entry.getKey());
            break;
        }
        return countLayers(entity, setup);
    }

    /**
     * Drains {@code amount} out of the shield stack.
     *
     * <p>{@code effectiveness} is the star-reaction multiple for a reaction hit and
     * {@link #ORDINARY_EFFICIENCY} for anything else; the ordinary kind is then topped back up, so what
     * actually lands on the stack is {@link #ORDINARY_NET_SHARE} of it.
     *
     * @return true while a layer is still standing, meaning the hit never reached the HP bar.
     */
    public static boolean absorb(GameEntity entity, Setup setup, float amount, float effectiveness) {
        float maxHp = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float perLayer = setup.perLayerHp(maxHp);
        if (perLayer <= 0f) return false;

        // Hits from several players land on separate threads; the pool is a read-modify-write.
        // GameEntity.heal() already synchronizes on the entity, so this shares its lock.
        boolean broken;
        synchronized (entity) {
            int layers = countLayers(entity, setup);
            if (layers <= 0) return false;
            broken = drain(entity, setup, perLayer, maxHp, layers, amount, effectiveness);
        }
        if (!broken) return true;

        // Stack is empty: the fight opens up. Clear the phase marker the stack modifier's onAdded set.
        if (clearPhaseMarkers(entity, setup)) entity.onAbilityValueUpdate();
        Grasscutter.getLogger()
                .debug("[MultiShield] entity={} stack broken, boss is now damageable", entity.getId());
        return false;
    }

    /** @return true when the last layer fell. */
    private static boolean drain(
            GameEntity entity,
            Setup setup,
            float perLayer,
            float maxHp,
            int layers,
            float amount,
            float effectiveness) {
        float pool = entity.getShieldPool();
        if (pool < 0f) {
            pool = layers * perLayer;
            Grasscutter.getLogger()
                    .debug(
                            "[MultiShield] entity={} shield stack up: layers={} perLayer={} pool={} (maxHp={})",
                            entity.getId(),
                            layers,
                            (int) perLayer,
                            (int) pool,
                            (int) maxHp);
        }

        float drained = amount * effectiveness;
        if (effectiveness < 1f) {
            // The boss tops the stack back up. A star reaction (effectiveness >= 1) has cut the top-up
            // off, so its damage sticks in full.
            drained *= ORDINARY_NET_SHARE;
        }
        pool -= drained;

        // Layers fall as the pool drains past each one's worth.
        while (layers > 0 && pool <= (layers - 1) * perLayer) {
            layers = breakOneLayer(entity, setup);
            entity.refreshModifierInvincible();
            Grasscutter.getLogger()
                    .debug("[MultiShield] entity={} layer broken, {} left", entity.getId(), layers);
        }

        // Drop the pool once the stack is empty, so the next stack starts sized from its own layers.
        entity.setShieldPool(layers <= 0 ? -1f : pool);
        return layers <= 0;
    }

    /**
     * Resets the global values a stack modifier raises for its phase.
     *
     * <p>The stack modifier (named {@code ...MultiShield...}) sets its phase marker in {@code onAdded}
     * and resets it in {@code onRemoved} - for the Zharptitsa {@code _MONSTER_Zharptitsa_HasMultiShield}.
     * Breaking the stack here is what ends the phase, so every key such a modifier both sets and
     * resets is cleared, instead of naming one boss's key.
     *
     * @return true if any value changed
     */
    static boolean clearPhaseMarkers(GameEntity entity, Setup setup) {
        Map<String, Float> values = entity.getGlobalAbilityValues();
        var modifiers = setup.ability().modifiers;
        if (values == null || modifiers == null) return false;

        boolean changed = false;
        for (var entry : modifiers.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().contains("MultiShield")) continue;
            AbilityModifier modifier = entry.getValue();
            if (modifier == null) continue;
            Set<String> raised = globalValueKeys(modifier.onAdded);
            for (String key : globalValueKeys(modifier.onRemoved)) {
                if (!raised.contains(key)) continue;
                Float old = values.put(key, 0f);
                changed |= old == null || old != 0f;
            }
        }
        return changed;
    }

    private static Set<String> globalValueKeys(AbilityModifier.AbilityModifierAction[] actions) {
        if (actions == null) return Set.of();
        var keys = new java.util.HashSet<String>();
        for (var action : actions) {
            if (action == null || action.key == null) continue;
            if (action.type == AbilityModifier.AbilityModifierAction.Type.SetGlobalValue
                    || action.type == AbilityModifier.AbilityModifierAction.Type.SetGlobalValueV2) {
                keys.add(action.key);
            }
        }
        return keys;
    }

    /**
     * How much of a hit lands on the stack: a star reaction hits at the {@code StarMode_Shield_Break}
     * multiple from the ability specials, anything else is damped hard.
     */
    public static float effectivenessFor(GameEntity entity, float amount, int killerId, ElementType element) {
        String reaction = classifyReaction(entity, killerId, element);
        if (reaction != null && SHIELD_BREAKING_REACTIONS.contains(reaction)) {
            float multiplier = starShieldBreakMultiplier(entity);
            Grasscutter.getLogger()
                    .debug(
                            "[MultiShield] entity={} star reaction {} breaks shields at {}x (amount={} element={})",
                            entity.getId(),
                            reaction,
                            multiplier,
                            amount,
                            element);
            return multiplier;
        }
        return ORDINARY_EFFICIENCY;
    }

    /**
     * Classifies the hit through the DPS helper, which owns the reaction tables.
     *
     * <p>This deliberately uses the helper's full matcher, including its {@code attackTag} path. An
     * earlier revision narrowed it to the reaction's own ability name and measured the result: a
     * Cryo+Electro team produced zero hits, so genuine star-reaction damage arrives on the character's
     * own abilities and only the attackTag path sees it.
     */
    private static String classifyReaction(GameEntity entity, int killerId, ElementType element) {
        try {
            var attacker = entity.getScene() == null ? null : entity.getScene().getEntityById(killerId);
            return DPSReactionHelper.detect(DPSAttackContext.get(), attacker, element);
        } catch (Throwable t) {
            return null;
        }
    }

    /** {@code StarMode_Shield_Break} off the StarMode ability, defaulting to the official 2. */
    private static float starShieldBreakMultiplier(GameEntity entity) {
        if (entity.getInstancedModifiers() == null) return 2f;
        for (AbilityModifierController ctrl : entity.getInstancedModifiers().values()) {
            if (ctrl == null || ctrl.getAbilityData() == null) continue;
            var specials = ctrl.getAbilityData().abilitySpecials;
            if (specials != null && specials.containsKey("StarMode_Shield_Break")) {
                return specials.get("StarMode_Shield_Break");
            }
        }
        return 2f;
    }
}
