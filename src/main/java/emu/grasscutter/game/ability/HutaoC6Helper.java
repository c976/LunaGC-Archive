package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarTalentData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hu Tao C6, Butterfly's Embrace.
 *
 * <p>Official config uses DoActionByEventMixin(HPDown) + thinkInterval, which this server does not
 * run. Hook damage / self-LoseHP instead: below 25% HP or lethal → survive at 1 HP, gain RES/CRIT
 * for 10s, then 60s CD (skill 10464).
 */
public final class HutaoC6Helper {
    public static final int AVATAR_ID = 10000046;
    public static final int TALENT_C6 = 466;
    public static final int SKILL_CD_ID = 10464;
    public static final String TRIGGER_ABILITY = "Avatar_Hutao_Constellation_Limbo_Trigger";

    private static final float DEFAULT_HP_RATIO = 0.25f;
    private static final float DEFAULT_DURATION_SEC = 10f;
    private static final float DEFAULT_SUB_HURT = 2f;
    private static final float DEFAULT_CRIT = 1f;
    private static final long DEFAULT_CD_MS = 60_000L;

    private static final FightProperty[] RES_PROPS = {
        FightProperty.FIGHT_PROP_PHYSICAL_SUB_HURT,
        FightProperty.FIGHT_PROP_FIRE_SUB_HURT,
        FightProperty.FIGHT_PROP_ELEC_SUB_HURT,
        FightProperty.FIGHT_PROP_WATER_SUB_HURT,
        FightProperty.FIGHT_PROP_GRASS_SUB_HURT,
        FightProperty.FIGHT_PROP_WIND_SUB_HURT,
        FightProperty.FIGHT_PROP_ICE_SUB_HURT,
        FightProperty.FIGHT_PROP_ROCK_SUB_HURT
    };

    private static final Int2LongMap CD_UNTIL_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final ConcurrentHashMap<Integer, Integer> BUFF_TASK_IDS = new ConcurrentHashMap<>();

    static {
        CD_UNTIL_MS.defaultReturnValue(0L);
    }

    private HutaoC6Helper() {}

    public static void clearEntityState(int entityId) {
        CD_UNTIL_MS.remove(entityId);
        Integer taskId = BUFF_TASK_IDS.remove(entityId);
        if (taskId != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(taskId);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearPlayerState(emu.grasscutter.game.player.Player player) {
        if (player == null || player.getTeamManager() == null) return;
        for (EntityAvatar ea : player.getTeamManager().getActiveTeam()) {
            if (ea != null) clearEntityState(ea.getId());
        }
    }

    /**
     * Adjust incoming damage for C6 (leave 1 HP on lethal / low-HP trigger) and arm the buff.
     *
     * @return possibly reduced damage amount
     */
    public static float filterDamage(EntityAvatar entity, float curHp, float proposedDamage) {
        if (entity == null || proposedDamage <= 0f) {
            return proposedDamage;
        }
        Avatar avatar = entity.getAvatar();
        if (avatar == null || avatar.getAvatarId() != AVATAR_ID) {
            return proposedDamage;
        }
        if (!hasC6(entity)) {
            Grasscutter.getLogger()
                    .info(
                            "[HutaoC6] skip no-C6 entity={} core={} talents={} embryos={}",
                            entity.getId(),
                            avatar.getCoreProudSkillLevel(),
                            avatar.getTalentIdList(),
                            avatar.getExtraAbilityEmbryos());
            return proposedDamage;
        }
        int entityId = entity.getId();
        long now = System.currentTimeMillis();
        if (now < CD_UNTIL_MS.get(entityId)) {
            Grasscutter.getLogger()
                    .info(
                            "[HutaoC6] skip CD entity={} remainMs={}",
                            entityId,
                            CD_UNTIL_MS.get(entityId) - now);
            return proposedDamage;
        }

        float maxHp = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 0f) return proposedDamage;

        float newHp = curHp - proposedDamage;
        boolean lethal = newHp <= 0f;
        boolean belowRatio = newHp / maxHp < resolveHpRatio(avatar);
        boolean nearOne = newHp < 2f;

        if (!lethal && !belowRatio && !nearOne) {
            return proposedDamage;
        }

        float adjusted = proposedDamage;
        if (lethal || newHp < 1f) {
            adjusted = Math.max(0f, curHp - 1f);
        }

        Grasscutter.getLogger()
                .info(
                        "[HutaoC6] intercept entity={} cur={} dmg={} -> leave1 lethal={} below25={}",
                        entityId,
                        curHp,
                        proposedDamage,
                        lethal,
                        belowRatio);
        trigger(entity);
        return adjusted;
    }

    /** Fall / direct HP write path that bypasses {@link GameEntity#damage}. */
    public static float filterDirectHpLoss(EntityAvatar entity, float curHp, float newHp) {
        if (entity == null || newHp >= curHp) {
            return newHp;
        }
        float damage = curHp - newHp;
        float adjustedDamage = filterDamage(entity, curHp, damage);
        return curHp - adjustedDamage;
    }

    private static void trigger(EntityAvatar entity) {
        Avatar avatar = entity.getAvatar();
        float durationSec = DEFAULT_DURATION_SEC;
        float subHurt = DEFAULT_SUB_HURT;
        float crit = DEFAULT_CRIT;
        float[] params = readTalentParams(avatar);
        if (params != null) {
            if (params.length > 1 && params[1] > 0f) durationSec = params[1];
            if (params.length > 2 && params[2] > 0f) subHurt = params[2];
            if (params.length > 3 && params[3] > 0f) crit = params[3];
        }
        // Prefer ability specials when the Limbo_Trigger instance is already loaded.
        for (Ability ability : entity.getInstancedAbilities()) {
            if (ability == null || ability.getData() == null) continue;
            if (!TRIGGER_ABILITY.equals(ability.getData().abilityName)) continue;
            var specials = ability.getAbilitySpecials();
            float d = specials.getOrDefault("Hutao_Constellation_Limbo_Trigger_Duration", 0f);
            float s = specials.getOrDefault("Hutao_Constellation_Limbo_Trigger_SubHurtDelta", 0f);
            float c = specials.getOrDefault("Hutao_Constellation_Limbo_Trigger_CriticalDelta", 0f);
            if (d > 0f) durationSec = d;
            if (s > 0f) subHurt = s;
            if (c > 0f) crit = c;
            break;
        }

        int entityId = entity.getId();
        CD_UNTIL_MS.put(entityId, System.currentTimeMillis() + DEFAULT_CD_MS);

        applyBuff(entity, subHurt, crit);
        scheduleBuffClear(entity, durationSec, subHurt, crit);

        // Run official TriggerAbility path for VFX / modifier sync when possible.
        try {
            var player = entity.getPlayer();
            if (player != null) {
                AbilityManager manager = player.getWorld().getHost().getAbilityManager();
                Ability existing = null;
                for (Ability ab : entity.getInstancedAbilities()) {
                    if (ab != null
                            && ab.getData() != null
                            && TRIGGER_ABILITY.equals(ab.getData().abilityName)) {
                        existing = ab;
                        break;
                    }
                }
                if (existing == null) {
                    manager.addAbilityToEntity(entity, TRIGGER_ABILITY);
                    for (Ability ab : entity.getInstancedAbilities()) {
                        if (ab != null
                                && ab.getData() != null
                                && TRIGGER_ABILITY.equals(ab.getData().abilityName)) {
                            existing = ab;
                            break;
                        }
                    }
                }
                if (existing != null) {
                    manager.fireAbilityOnAbilityStart(existing, entity);
                }
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("[HutaoC6] TriggerAbility path: {}", t.toString());
        }

        Grasscutter.getLogger()
                .info(
                        "[HutaoC6] triggered entity={} duration={}s subHurt={} crit={} cd={}s",
                        entityId,
                        durationSec,
                        subHurt,
                        crit,
                        DEFAULT_CD_MS / 1000);
    }

    private static void applyBuff(EntityAvatar entity, float subHurt, float crit) {
        entity.addFightProperty(FightProperty.FIGHT_PROP_CRITICAL, crit);
        for (FightProperty prop : RES_PROPS) {
            entity.addFightProperty(prop, subHurt);
        }
        syncProps(entity);
    }

    private static void clearBuff(EntityAvatar entity, float subHurt, float crit) {
        if (entity == null || entity.getFightProperties() == null) return;
        entity.addFightProperty(FightProperty.FIGHT_PROP_CRITICAL, -crit);
        for (FightProperty prop : RES_PROPS) {
            entity.addFightProperty(prop, -subHurt);
        }
        syncProps(entity);
    }

    private static void scheduleBuffClear(
            EntityAvatar entity, float durationSec, float subHurt, float crit) {
        int entityId = entity.getId();
        Integer prev = BUFF_TASK_IDS.remove(entityId);
        if (prev != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(prev);
            } catch (Throwable ignored) {
            }
        }
        int ticks = Math.max(1, Math.round(durationSec));
        var scheduler = Grasscutter.getGameServer().getScheduler();
        int taskId =
                scheduler.scheduleDelayedTask(
                        () -> {
                            BUFF_TASK_IDS.remove(entityId);
                            try {
                                if (entity.getScene() == null) return;
                                clearBuff(entity, subHurt, crit);
                            } catch (Throwable ignored) {
                            }
                        },
                        ticks);
        BUFF_TASK_IDS.put(entityId, taskId);
    }

    private static void syncProps(EntityAvatar entity) {
        try {
            entity.getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropUpdateNotify((GameEntity) entity, FightProperty.FIGHT_PROP_CRITICAL));
            for (FightProperty prop : RES_PROPS) {
                entity.getScene()
                        .broadcastPacket(new PacketEntityFightPropUpdateNotify((GameEntity) entity, prop));
            }
            entity.getPlayer().sendPacket(new PacketAvatarFightPropNotify(entity.getAvatar()));
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasC6(EntityAvatar entity) {
        Avatar avatar = entity != null ? entity.getAvatar() : null;
        if (avatar == null || avatar.getAvatarId() != AVATAR_ID) return false;
        try {
            if (avatar.getCoreProudSkillLevel() >= 6) return true;
        } catch (Throwable ignored) {
        }
        var talents = avatar.getTalentIdList();
        if (talents != null && talents.contains(TALENT_C6)) return true;
        var embryos = avatar.getExtraAbilityEmbryos();
        if (embryos != null) {
            for (String name : embryos) {
                if (name != null && name.contains("Hutao_Constellation_Limbo")) return true;
            }
        }
        if (entity.getInstancedAbilities() != null) {
            for (Ability ab : entity.getInstancedAbilities()) {
                if (ab == null || ab.getData() == null || ab.getData().abilityName == null) continue;
                if (ab.getData().abilityName.contains("Hutao_Constellation_Limbo")) return true;
            }
        }
        return false;
    }

    private static float resolveHpRatio(Avatar avatar) {
        float[] params = readTalentParams(avatar);
        if (params != null && params.length > 0 && params[0] > 0f) return params[0];
        return DEFAULT_HP_RATIO;
    }

    private static float[] readTalentParams(Avatar avatar) {
        AvatarTalentData td = GameData.getAvatarTalentDataMap().get(TALENT_C6);
        return td != null ? td.getParamList() : null;
    }
}
