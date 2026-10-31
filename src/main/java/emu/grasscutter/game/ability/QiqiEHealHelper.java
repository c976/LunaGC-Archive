package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodic healing from Qiqi's elemental skill, i.e. the Herald of Frost's recurring heal.
 *
 * <p>Healing on normal-attack hits and from the burst's talisman sigil already works, since those go
 * through the client's HealHP. The periodic heal is
 * {@code onThinkInterval} plus a server-side HealHP in the official config, which never ticks here, so
 * after the skill is cast a scheduler reproduces it from the talent parameters.
 * In the skill depot skills[0] is the extra-attack entry; the elemental skill is the one with a cooldown
 * and no elemental cost, 10352 by default.
 */
public final class QiqiEHealHelper {
    public static final int QIQI_AVATAR_ID = 10000035;
    public static final int FALLBACK_SKILL_E = 10352;

    private static final float DEFAULT_RATIO = 0.7216f;
    private static final float DEFAULT_FLAT = 71.08f;
    private static final int DEFAULT_DURATION_SEC = 15;
    private static final int TICK_SEC = 1;

    private static final Int2IntMap TASK_IDS = Int2IntMaps.synchronize(new Int2IntOpenHashMap());

    static {
        TASK_IDS.defaultReturnValue(-1);
    }

    private QiqiEHealHelper() {}

    public static void clearPlayerState(Player player) {
        if (player == null) return;
        int taskId = TASK_IDS.remove(player.getUid());
        if (taskId >= 0) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(taskId);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearEntityState(int entityId) {
        // Tasks are keyed by uid; entity cleanup is a no-op besides keeping the API uniform.
    }

    public static void onSkillStart(Player player, EntityAvatar caster, int skillId) {
        if (player == null || caster == null || caster.getAvatar() == null) return;
        if (caster.getAvatar().getAvatarId() != QIQI_AVATAR_ID) return;
        if (!isQiqiElementalSkill(caster.getAvatar(), skillId)) return;

        cancel(player.getUid());

        HealParams params = readParams(caster.getAvatar(), skillId);
        if (params.ratio <= 0f && params.flat <= 0f) return;

        int ticks = Math.max(1, params.durationSec / TICK_SEC);
        int uid = player.getUid();
        int casterId = caster.getId();
        AtomicInteger remaining = new AtomicInteger(ticks);
        int[] taskHolder = new int[1];
        taskHolder[0] =
                Grasscutter.getGameServer()
                        .getScheduler()
                        .scheduleDelayedRepeatingTask(
                                () -> {
                                    if (remaining.decrementAndGet() < 0) {
                                        Grasscutter.getGameServer().getScheduler().cancelTask(taskHolder[0]);
                                        TASK_IDS.remove(uid);
                                        return;
                                    }
                                    Player live = Grasscutter.getGameServer().getPlayerByUid(uid);
                                    if (live == null) {
                                        Grasscutter.getGameServer().getScheduler().cancelTask(taskHolder[0]);
                                        TASK_IDS.remove(uid);
                                        return;
                                    }
                                    EntityAvatar qiqi = findQiqi(live, casterId);
                                    if (qiqi == null) {
                                        Grasscutter.getGameServer().getScheduler().cancelTask(taskHolder[0]);
                                        TASK_IDS.remove(uid);
                                        return;
                                    }
                                    tickHeal(live, qiqi, params);
                                },
                                TICK_SEC,
                                TICK_SEC);
        TASK_IDS.put(uid, taskHolder[0]);
        Grasscutter.getLogger()
                .info(
                        "[QiqiE] HoT armed uid={} ticks={} ratio={} flat={}",
                        uid,
                        ticks,
                        params.ratio,
                        params.flat);
    }

    private static void tickHeal(Player player, EntityAvatar qiqi, HealParams params) {
        EntityAvatar active = player.getTeamManager().getCurrentAvatarEntity();
        if (active == null || !active.isAlive()) return;
        float atk = qiqi.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK);
        float amount = atk * params.ratio + params.flat;
        float healAdd = qiqi.getFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD);
        float healedAdd = active.getFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD);
        float finalAmount = amount * (1.0f + healAdd + healedAdd);
        if (finalAmount <= 0f) return;
        float real = active.heal(finalAmount, false);
        if (real > 0f && player.getWorld() != null) {
            player.getWorld().broadcastPacket(new PacketEvtBeingHealedNotify(qiqi, active, finalAmount, real));
        }
    }

    private static boolean isQiqiElementalSkill(Avatar avatar, int skillId) {
        if (skillId == FALLBACK_SKILL_E) return true;
        AvatarSkillDepotData depot = avatar.getSkillDepot();
        if (depot == null || depot.getSkills() == null) {
            return false;
        }
        // Depot skills: [extraAttack/NA entry, Elemental Skill, ...]
        for (int i = 0; i < depot.getSkills().size(); i++) {
            int sid = depot.getSkills().get(i);
            if (sid <= 0) continue;
            var data = GameData.getAvatarSkillDataMap().get(sid);
            if (data != null && data.getCdTime() > 0 && data.getCostElemVal() <= 0 && skillId == sid) {
                return true;
            }
        }
        if (depot.getSkills().size() > 1 && depot.getSkills().get(1) == skillId) {
            return true;
        }
        return false;
    }

    private static EntityAvatar findQiqi(Player player, int entityId) {
        if (player == null || player.getTeamManager() == null) {
            return null;
        }
        var team = player.getTeamManager().getActiveTeam();
        if (team != null) {
            for (EntityAvatar member : team) {
                if (member != null && member.getId() == entityId) return member;
            }
        }
        EntityAvatar current = player.getTeamManager().getCurrentAvatarEntity();
        if (current != null
                && current.getAvatar() != null
                && current.getAvatar().getAvatarId() == QIQI_AVATAR_ID) {
            return current;
        }
        return null;
    }

    private static HealParams readParams(Avatar avatar, int skillId) {
        HealParams out = new HealParams(DEFAULT_RATIO, DEFAULT_FLAT, DEFAULT_DURATION_SEC);
        int resolveId = skillId > 0 ? skillId : FALLBACK_SKILL_E;
        AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(resolveId);
        if (skillData == null || skillData.getCdTime() <= 0) {
            AvatarSkillDepotData depot = avatar.getSkillDepot();
            if (depot != null && depot.getSkills() != null) {
                for (int sid : depot.getSkills()) {
                    if (sid <= 0) continue;
                    var cand = GameData.getAvatarSkillDataMap().get(sid);
                    if (cand != null && cand.getCdTime() > 0 && cand.getCostElemVal() <= 0) {
                        skillData = cand;
                        resolveId = sid;
                        break;
                    }
                }
            }
        }
        if (skillData == null) return out;

        int groupId = skillData.getProudSkillGroupId();
        int level = 1;
        if (avatar.getSkillLevelMap() != null) {
            level = avatar.getSkillLevelMap().getOrDefault(resolveId, 1);
        }
        int bonus = 0;
        if (avatar.getProudSkillBonusMap() != null) {
            bonus = avatar.getProudSkillBonusMap().getOrDefault(groupId, 0);
        }
        ProudSkillData proud = GameData.getProudSkillDataMap().get(groupId * 100 + level + bonus);
        if (proud == null) {
            proud = GameData.getProudSkillDataMap().get(groupId * 100 + level);
        }
        if (proud == null || proud.getParamList() == null || proud.getParamList().length < 4) {
            return out;
        }
        float[] p = proud.getParamList();
        // Herald of Frost: [hitHealRatio, hitHealFlat, tickHealRatio, tickHealFlat, ..., duration]
        out.ratio = asRatio(p[2]);
        out.flat = p[3];
        for (float v : p) {
            if (v >= 12f && v <= 20f && Math.abs(v - Math.round(v)) < 0.01f) {
                out.durationSec = Math.round(v);
            }
        }
        return out;
    }

    private static float asRatio(float raw) {
        if (raw > 10f) return raw / 100f;
        return raw;
    }

    private static void cancel(int uid) {
        int taskId = TASK_IDS.remove(uid);
        if (taskId >= 0) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(taskId);
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class HealParams {
        float ratio;
        float flat;
        int durationSec;

        HealParams(float ratio, float flat, int durationSec) {
            this.ratio = ratio;
            this.flat = flat;
            this.durationSec = durationSec;
        }
    }
}
