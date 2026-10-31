package emu.grasscutter.game.avatar;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.server.packet.send.PacketAvatarSkillDepotChangeNotify;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Tartaglia utility passive Master of Weaponry (PermanentSkill_3 / proud group 3323): while he is in the
 * party with the passive unlocked, all own-party members gain Normal Attack ExtraLevel +1.
 *
 * <p>Mirrors {@link SkirkTeamBonusHelper}: contribution is tracked under a negative proud-group
 * key so {@link Avatar#recalcConstellations()} can preserve it across clears.
 */
public final class TartagliaTeamBonusHelper {
    private static final Logger logger = Grasscutter.getLogger();
    public static final int TARTAGLIA_AVATAR_ID = 10000033;
    private static final int TARTAGLIA_PS3_GROUP = 3323;
    private static final int NORMAL_ATTACK_TALENT_INDEX = 1;
    private static final int BONUS_AMOUNT = 1;
    private static final long REFRESH_DEBOUNCE_MS = 500L;
    private static final long DEFERRED_REFRESH_MS = 750L;
    private static final Field PROUD_SKILL_BONUS_MAP;
    private static final ConcurrentHashMap<Integer, Set<Long>> APPLIED_BY_PLAYER =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> LAST_STATE_BY_PLAYER =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Long> LAST_REFRESH_MS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ScheduledFuture<?>> PENDING_REFRESH =
            new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> REFRESH_IN_PROGRESS =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ScheduledExecutorService REFRESH_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "TartagliaTeamBonusRefresh");
                        t.setDaemon(true);
                        return t;
                    });

    private TartagliaTeamBonusHelper() {}

    public static void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        int uid = player.getUid();
        clearAppliedBonuses(player, uid, false);
        APPLIED_BY_PLAYER.remove(uid);
        LAST_STATE_BY_PLAYER.remove(uid);
        LAST_REFRESH_MS.remove(uid);
        ScheduledFuture<?> pending = PENDING_REFRESH.remove(uid);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    public static void refresh(Player player) {
        if (player == null || Boolean.TRUE.equals(REFRESH_IN_PROGRESS.get())) {
            return;
        }
        long now = System.currentTimeMillis();
        int uid = player.getUid();
        Long last = LAST_REFRESH_MS.get(uid);
        if (last != null && now - last < REFRESH_DEBOUNCE_MS) {
            scheduleRefresh(player);
            return;
        }
        LAST_REFRESH_MS.put(uid, now);
        TeamManager teamManager = player.getTeamManager();
        if (teamManager == null) {
            return;
        }
        List<Avatar> team = resolveTeamAvatars(player, teamManager);
        boolean apply = shouldApplyBonus(team);
        String stateKey = buildStateKey(team, apply);
        String prevState = LAST_STATE_BY_PLAYER.get(uid);
        Set<Long> applied = APPLIED_BY_PLAYER.get(uid);
        boolean missingContribution = false;
        if (apply && applied != null && !applied.isEmpty()) {
            for (Long guid : applied) {
                Avatar avatar = player.getAvatars().getAvatarByGuid(guid);
                if (avatar == null) {
                    missingContribution = true;
                    break;
                }
                int groupId = getNormalAttackProudGroupId(avatar);
                if (groupId > 0 && getContribution(avatar, groupId) <= 0) {
                    missingContribution = true;
                    break;
                }
            }
        }
        if (stateKey.equals(prevState)
                && !missingContribution
                && (!apply || (applied != null && !applied.isEmpty()))) {
            logger.debug(
                    "TartagliaTeamBonus skip uid={} apply={} reason=unchanged team=[{}]",
                    uid,
                    apply,
                    describeTeam(team));
            return;
        }
        if (missingContribution) {
            LAST_STATE_BY_PLAYER.remove(uid);
            APPLIED_BY_PLAYER.remove(uid);
            applied = null;
        }
        LAST_STATE_BY_PLAYER.put(uid, stateKey);
        logger.debug(
                "TartagliaTeamBonus refresh uid={} teamSize={} apply={} reason={} team=[{}]",
                uid,
                team.size(),
                apply,
                explainReason(team, apply),
                describeTeam(team));
        REFRESH_IN_PROGRESS.set(Boolean.TRUE);
        try {
            clearAppliedBonuses(player, uid, !apply);
            if (!apply) {
                APPLIED_BY_PLAYER.remove(uid);
                return;
            }
            HashSet<Long> next = new HashSet<>();
            for (Avatar avatar : team) {
                if (!isOwnAvatar(avatar) || !applyBonus(avatar, player)) {
                    continue;
                }
                next.add(avatar.getGuid());
            }
            if (next.isEmpty()) {
                APPLIED_BY_PLAYER.remove(uid);
                logger.warn("TartagliaTeamBonus apply produced empty set uid={}", uid);
            } else {
                APPLIED_BY_PLAYER.put(uid, next);
                logger.debug("Tartaglia Master of Weaponry active for player {} ({} avatars)", uid, next.size());
            }
        } finally {
            REFRESH_IN_PROGRESS.set(Boolean.FALSE);
        }
    }

    public static void onConstellationRecalc(Player player) {
        if (player == null) {
            return;
        }
        LAST_STATE_BY_PLAYER.remove(player.getUid());
        scheduleRefresh(player);
    }

    public static void onPlayerLogin(Player player) {
        // registerPlayer() runs after teamManager.onPlayerLogin(); wait until the player is mapped.
        scheduleRefresh(player, 2000L);
    }

    public static void onTeamChanged(Player player) {
        scheduleRefresh(player, REFRESH_DEBOUNCE_MS);
    }

    private static void scheduleRefresh(Player player) {
        scheduleRefresh(player, DEFERRED_REFRESH_MS);
    }

    private static void scheduleRefresh(Player player, long delayMs) {
        if (player == null) {
            return;
        }
        int uid = player.getUid();
        ScheduledFuture<?> previous = PENDING_REFRESH.get(uid);
        if (previous != null) {
            previous.cancel(false);
        }
        // Capture the Player instance: onLogin schedules this BEFORE registerPlayer(), so
        // getPlayerByUid(uid) is still null when the first deferred tick fires.
        final Player scheduled = player;
        ScheduledFuture<?> next =
                REFRESH_SCHEDULER.schedule(
                        () -> {
                            PENDING_REFRESH.remove(uid);
                            try {
                                Player target = scheduled;
                                if (Grasscutter.getGameServer() != null) {
                                    Player mapped = Grasscutter.getGameServer().getPlayerByUid(uid);
                                    if (mapped != null) {
                                        target = mapped;
                                    }
                                }
                                TartagliaTeamBonusHelper.refresh(target);
                            } catch (Throwable throwable) {
                                logger.warn(
                                        "TartagliaTeamBonus deferred refresh failed uid={}",
                                        uid,
                                        throwable);
                            }
                        },
                        delayMs,
                        TimeUnit.MILLISECONDS);
        PENDING_REFRESH.put(uid, next);
    }

    public static Set<Long> getAppliedGuids(int uid) {
        Set<Long> set = APPLIED_BY_PLAYER.get(uid);
        return set == null ? Collections.emptySet() : set;
    }

    private static void clearAppliedBonuses(Player player, Integer uid, boolean notify) {
        Set<Long> set = APPLIED_BY_PLAYER.remove(uid);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (Long guid : set) {
            Avatar avatar = player.getAvatars().getAvatarByGuid(guid);
            if (avatar == null) {
                continue;
            }
            removeBonus(avatar, player, notify);
        }
    }

    private static List<Avatar> resolveTeamAvatars(Player player, TeamManager teamManager) {
        LinkedHashMap<Long, Avatar> byGuid = new LinkedHashMap<>();
        TeamInfo teamInfo = teamManager.getCurrentTeamInfo();
        if (teamInfo == null) {
            teamInfo = teamManager.getCurrentSinglePlayerTeamInfo();
        }
        if (teamInfo != null) {
            for (Integer avatarId : teamInfo.getAvatars()) {
                if (avatarId == null || avatarId <= 0) {
                    continue;
                }
                Avatar avatar = player.getAvatars().getAvatarById(avatarId);
                if (avatar == null) {
                    continue;
                }
                byGuid.put(avatar.getGuid(), avatar);
            }
        }
        for (EntityAvatar entityAvatar : teamManager.getActiveTeam()) {
            Avatar avatar = entityAvatar.getAvatar();
            if (avatar == null) {
                continue;
            }
            byGuid.put(avatar.getGuid(), avatar);
        }
        return new ArrayList<>(byGuid.values());
    }

    private static boolean shouldApplyBonus(List<Avatar> team) {
        return team.stream().anyMatch(a -> a.getAvatarId() == TARTAGLIA_AVATAR_ID)
                && team.stream()
                        .filter(a -> a.getAvatarId() == TARTAGLIA_AVATAR_ID)
                        .anyMatch(TartagliaTeamBonusHelper::hasPermanentSkill3Unlocked);
    }

    private static boolean isOwnAvatar(Avatar avatar) {
        return avatar != null && avatar.getPlayer() != null;
    }

    private static boolean hasPermanentSkill3Unlocked(Avatar avatar) {
        if (avatar.getProudSkillList() == null) {
            return false;
        }
        for (Integer proudSkillId : avatar.getProudSkillList()) {
            if (proudSkillId != null && proudSkillId / 100 == TARTAGLIA_PS3_GROUP) {
                return true;
            }
        }
        return false;
    }

    private static String buildStateKey(List<Avatar> team, boolean apply) {
        String guids =
                team.stream()
                        .map(a -> Long.toString(a.getGuid()))
                        .sorted()
                        .collect(Collectors.joining(","));
        return (apply ? "1" : "0") + "|" + guids;
    }

    private static String describeTeam(List<Avatar> team) {
        return team.stream()
                .map(a -> a.getAvatarId() + ":" + a.getGuid())
                .collect(Collectors.joining(","));
    }

    private static String explainReason(List<Avatar> team, boolean apply) {
        Avatar tartaglia =
                team.stream()
                        .filter(a -> a.getAvatarId() == TARTAGLIA_AVATAR_ID)
                        .findFirst()
                        .orElse(null);
        if (tartaglia == null) {
            return "no-tartaglia-in-team";
        }
        return "ok-ps3=" + hasPermanentSkill3Unlocked(tartaglia);
    }

    private static int getNormalAttackProudGroupId(Avatar avatar) {
        AvatarSkillDepotData depot = avatar.getSkillDepot();
        if (depot == null) {
            return 0;
        }
        List<Integer> skills = depot.getSkills();
        if (skills == null || skills.isEmpty()) {
            return 0;
        }
        AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(skills.get(0));
        if (skillData == null) {
            return 0;
        }
        return skillData.getProudSkillGroupId();
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> bonusMap(Avatar avatar) {
        try {
            return (Map<Integer, Integer>) PROUD_SKILL_BONUS_MAP.get(avatar);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access proudSkillBonusMap", e);
        }
    }

    private static boolean applyBonus(Avatar avatar, Player player) {
        int groupId = getNormalAttackProudGroupId(avatar);
        if (groupId <= 0) {
            logger.warn(
                    "TartagliaTeamBonus skip avatarId={} guid={}: missing NA proud group",
                    avatar.getAvatarId(),
                    avatar.getGuid());
            return false;
        }
        if (getContribution(avatar, groupId) >= BONUS_AMOUNT) {
            return true;
        }
        setContribution(avatar, groupId, BONUS_AMOUNT);
        avatar.recalcStats(true);
        int extra = getTotalExtraLevel(avatar, groupId);
        logger.debug(
                "TartagliaTeamBonus applied avatarId={} guid={} naGroup={} extraLevel={}",
                avatar.getAvatarId(),
                avatar.getGuid(),
                groupId,
                extra);
        TartagliaTeamBonusNotify.send(player, avatar, NORMAL_ATTACK_TALENT_INDEX, extra);
        player.sendPacket(new PacketAvatarSkillDepotChangeNotify(avatar));
        return true;
    }

    private static void removeBonus(Avatar avatar, Player player, boolean notify) {
        int groupId = getNormalAttackProudGroupId(avatar);
        if (groupId <= 0 || getContribution(avatar, groupId) <= 0) {
            return;
        }
        setContribution(avatar, groupId, 0);
        avatar.recalcStats(true);
        if (notify) {
            TartagliaTeamBonusNotify.send(
                    player, avatar, NORMAL_ATTACK_TALENT_INDEX, getTotalExtraLevel(avatar, groupId));
            player.sendPacket(new PacketAvatarSkillDepotChangeNotify(avatar));
        }
    }

    private static int getTotalExtraLevel(Avatar avatar, int groupId) {
        return avatar.getProudSkillBonusMap().getOrDefault(groupId, 0);
    }

    private static int getContribution(Avatar avatar, int groupId) {
        Integer contrib = bonusMap(avatar).get(contributionKey(groupId));
        return contrib == null ? 0 : contrib;
    }

    private static void setContribution(Avatar avatar, int groupId, int amount) {
        Map<Integer, Integer> map = bonusMap(avatar);
        int key = contributionKey(groupId);
        if (amount <= 0) {
            Integer removed = map.remove(key);
            if (removed != null && removed > 0) {
                adjustTotalBonus(map, groupId, -removed);
            }
            return;
        }
        int prev = map.getOrDefault(key, 0);
        if (prev == amount) {
            return;
        }
        map.put(key, amount);
        adjustTotalBonus(map, groupId, amount - prev);
    }

    private static int contributionKey(int groupId) {
        return -groupId;
    }

    private static void adjustTotalBonus(Map<Integer, Integer> map, int groupId, int delta) {
        if (delta == 0) {
            return;
        }
        map.compute(
                groupId,
                (k, v) -> {
                    int updated = (v == null ? 0 : v) + delta;
                    return updated > 0 ? Integer.valueOf(updated) : null;
                });
    }

    static {
        try {
            PROUD_SKILL_BONUS_MAP = Avatar.class.getDeclaredField("proudSkillBonusMap");
            PROUD_SKILL_BONUS_MAP.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
