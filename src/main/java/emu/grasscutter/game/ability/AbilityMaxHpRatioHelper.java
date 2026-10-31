package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies {@code Actor_MaxHPRatio} modifier properties (Yelan C4, Furina overflow, Columbina C2,
 * ...).
 *
 * <p>These never arrive through {@code ActionAttachModifier}: the client attaches them itself and
 * only reports them as a {@code MODIFIER_CHANGE}, so {@link GameEntity#onAddAbilityModifier} has no
 * chance to fold them into the fight props. Every ratio that is currently live on an entity is
 * tracked here instead, and folded back in whenever stats are recomputed - the same shape as
 * {@link WaterResonanceHelper}, which multiplies MAX_HP after {@code recalcStats} rather than
 * trying to inject an extra HP% term into the stat pass itself.
 *
 * <p>Ratios stack additively: two live +0.2 modifiers give 1.4x, not 1.44x. Current HP is carried
 * across at a constant fraction of max, so gaining or losing a ratio never heals or kills.
 */
public final class AbilityMaxHpRatioHelper {
    /** entityId -> (modifier name -> live entry). */
    private static final Map<Integer, Map<String, Entry>> ACTIVE = new ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> IN_APPLY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AbilityMaxHpRatioHelper() {}

    private record Entry(AbilityModifier data, Ability ability) {
        float ratio() {
            if (data == null || data.properties == null) {
                return 0f;
            }
            DynamicFloat f = data.properties.Actor_MaxHPRatio;
            if (f == null) {
                return 0f;
            }
            return ability != null ? f.get(ability, 0f) : f.get(0f);
        }
    }

    /** True when this modifier carries a non-zero {@code Actor_MaxHPRatio}. */
    public static boolean hasMaxHpRatio(AbilityModifier data) {
        if (data == null || data.properties == null) {
            return false;
        }
        DynamicFloat f = data.properties.Actor_MaxHPRatio;
        if (f == null) {
            return false;
        }
        // A dynamic value names an ability special, so its worth is only known once resolved
        // against the owning ability - treat it as interesting and let ratio() decide.
        return f.isDynamic() || f.getConstant() != 0f;
    }

    public static void onModifierAdded(
            Ability ability, String modifierName, AbilityModifier data, GameEntity entity) {
        if (entity == null || modifierName == null || !hasMaxHpRatio(data)) {
            return;
        }
        Entry entry = new Entry(data, ability);
        if (entry.ratio() == 0f) {
            return;
        }
        ACTIVE.computeIfAbsent(entity.getId(), k -> new ConcurrentHashMap<>()).put(modifierName, entry);
        refresh(entity);
    }

    public static void onModifierRemoved(Ability ability, String modifierName, GameEntity entity) {
        if (entity == null || modifierName == null) {
            return;
        }
        Map<String, Entry> live = ACTIVE.get(entity.getId());
        if (live == null || live.remove(modifierName) == null) {
            return;
        }
        if (live.isEmpty()) {
            ACTIVE.remove(entity.getId());
        }
        refresh(entity);
    }

    /**
     * An ability instance is rebuilt whenever its specials change (talent level, constellation).
     * Re-point tracked entries at the new instance so dynamic ratios resolve against fresh specials.
     */
    public static void onAbilityCreated(Ability ability) {
        if (ability == null || ability.getOwner() == null || ability.getData() == null) {
            return;
        }
        Map<String, Entry> live = ACTIVE.get(ability.getOwner().getId());
        if (live == null || live.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (var e : live.entrySet()) {
            AbilityModifier tracked = ability.getData().modifiers.get(e.getKey());
            if (tracked != null && tracked == e.getValue().data()) {
                live.put(e.getKey(), new Entry(tracked, ability));
                changed = true;
            }
        }
        if (changed) {
            refresh(ability.getOwner());
        }
    }

    /** Summed ratio currently live on an entity; 0 when it has none. */
    public static float totalRatio(GameEntity entity) {
        if (entity == null) {
            return 0f;
        }
        Map<String, Entry> live = ACTIVE.get(entity.getId());
        if (live == null || live.isEmpty()) {
            return 0f;
        }
        float sum = 0f;
        for (Entry entry : live.values()) {
            sum += entry.ratio();
        }
        return sum;
    }

    /** Drop every ratio tracked for an entity (scene unload, monster death). */
    public static void clear(GameEntity entity) {
        if (entity != null) {
            ACTIVE.remove(entity.getId());
        }
    }

    private static void refresh(GameEntity entity) {
        if (Boolean.TRUE.equals(IN_APPLY.get())) {
            return;
        }
        try {
            if (entity instanceof EntityAvatar avatarEntity && avatarEntity.getAvatar() != null) {
                // recalcStats runs the whole stat pass and calls back into afterRecalc.
                avatarEntity.getAvatar().recalcStats(true);
            } else {
                applyToEntity(entity, totalRatio(entity));
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("[MaxHPRatio] refresh failed: {}", t.toString());
        }
    }

    /** Fold the live ratios back into an avatar's freshly recomputed stats. */
    public static void afterRecalc(Avatar avatar) {
        if (avatar == null || Boolean.TRUE.equals(IN_APPLY.get())) {
            return;
        }
        EntityAvatar entity = avatar.getAsEntity();
        if (entity == null) {
            return;
        }
        float ratio = totalRatio(entity);
        if (ratio == 0f) {
            return;
        }
        IN_APPLY.set(Boolean.TRUE);
        try {
            float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            if (maxHp <= 1f || Float.isNaN(maxHp)) {
                return;
            }
            float curHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            float newMax = maxHp * (1f + ratio);
            if (newMax <= 1f || Float.isNaN(newMax)) {
                return;
            }
            float fraction = curHp > 0f ? Math.min(1f, curHp / maxHp) : 0f;
            float newCur = newMax * fraction;

            avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, newMax);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, newCur);
            entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, newMax);
            entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, newCur);

            Player player = avatar.getPlayer();
            if (player != null && player.hasSentLoginPackets()) {
                // Coalesced with the send at the end of recalcStats: two full-props packets per
                // recalc is what turned one modifier change into a four-packet burst.
                avatar.queueFightPropNotify();
                broadcastHp(entity);
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("[MaxHPRatio] afterRecalc failed: {}", t.toString());
        } finally {
            IN_APPLY.set(Boolean.FALSE);
        }
    }

    /**
     * Non-avatar entities have no stat pass to hook, so the ratio is applied to the base max HP
     * kept alongside the entity and pushed straight out.
     */
    private static void applyToEntity(GameEntity entity, float ratio) {
        if (entity == null) {
            return;
        }
        float baseMax = entity.getFightProperty(FightProperty.FIGHT_PROP_BASE_HP);
        if (baseMax <= 0f || Float.isNaN(baseMax)) {
            return;
        }
        float maxHp = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float curHp = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float newMax = baseMax * (1f + ratio);
        if (newMax <= 0f || Float.isNaN(newMax)) {
            return;
        }
        float fraction = (maxHp > 0f && curHp > 0f) ? Math.min(1f, curHp / maxHp) : 1f;
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, newMax);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, newMax * fraction);
        broadcastHp(entity);
    }

    private static void broadcastHp(GameEntity entity) {
        if (entity == null || entity.getScene() == null) {
            return;
        }
        List<FightProperty> props =
                Arrays.asList(FightProperty.FIGHT_PROP_MAX_HP, FightProperty.FIGHT_PROP_CUR_HP);
        entity.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, props));
    }
}
