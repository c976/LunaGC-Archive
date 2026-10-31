/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.avatar;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.SkirkTeamBonusNotify;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.ElementType;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class SkirkTeamBonusHelper {
    private static final Logger logger = Grasscutter.getLogger();
    private static final int SKIRK_AVATAR_ID = 10000114;
    private static final int SKIRK_PS3_GROUP = 11423;
    private static final int ELEMENTAL_SKILL_TALENT_INDEX = 2;
    private static final int BONUS_AMOUNT = 1;
    private static final long REFRESH_DEBOUNCE_MS = 250L;
    private static final Field PROUD_SKILL_BONUS_MAP;
    private static final ConcurrentHashMap<Integer, Set<Long>> APPLIED_BY_PLAYER;
    private static final ConcurrentHashMap<Integer, String> LAST_STATE_BY_PLAYER;
    private static final ConcurrentHashMap<Integer, Long> LAST_REFRESH_MS;
    private static final ThreadLocal<Boolean> REFRESH_IN_PROGRESS;

    private SkirkTeamBonusHelper() {
    }

    public static void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        int uid = player.getUid();
        // The bonus is stored on Avatar and is persisted with it. Remove the transient contribution
        // before dropping the bookkeeping set, otherwise a team change while offline could leave a
        // stale permanent bonus that the next login no longer knows how to undo.
        clearAppliedBonuses(player, uid, false);
        APPLIED_BY_PLAYER.remove(uid);
        LAST_STATE_BY_PLAYER.remove(uid);
        LAST_REFRESH_MS.remove(uid);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void refresh(Player player) {
        if (player == null || Boolean.TRUE.equals(REFRESH_IN_PROGRESS.get())) {
            return;
        }
        long l = System.currentTimeMillis();
        Integer n = player.getUid();
        Long l2 = LAST_REFRESH_MS.get(n);
        if (l2 != null && l - l2 < 250L) {
            return;
        }
        LAST_REFRESH_MS.put(n, l);
        TeamManager teamManager = player.getTeamManager();
        if (teamManager == null) {
            return;
        }
        List<Avatar> list = SkirkTeamBonusHelper.resolveTeamAvatars(player, teamManager);
        boolean bl = SkirkTeamBonusHelper.shouldApplyBonus(list);
        String string = SkirkTeamBonusHelper.buildStateKey(list, bl);
        String string2 = LAST_STATE_BY_PLAYER.get(n);
        Set<Long> set = APPLIED_BY_PLAYER.get(n);
        if (string.equals(string2) && (!bl || set != null && !set.isEmpty())) {
            return;
        }
        LAST_STATE_BY_PLAYER.put(n, string);
        logger.debug("SkirkTeamBonus refresh uid={} teamSize={} elements=[{}] apply={} reason={}", n, list.size(), SkirkTeamBonusHelper.describeElements(list), bl, SkirkTeamBonusHelper.explainReason(list, bl));
        REFRESH_IN_PROGRESS.set(Boolean.TRUE);
        try {
            SkirkTeamBonusHelper.clearAppliedBonuses(player, n, !bl);
            if (!bl) {
                APPLIED_BY_PLAYER.remove(n);
                return;
            }
            HashSet<Long> hashSet = new HashSet<Long>();
            for (Avatar avatar : list) {
                if (!SkirkTeamBonusHelper.isOwnAvatar(avatar) || !SkirkTeamBonusHelper.applyBonus(avatar, player)) continue;
                hashSet.add(avatar.getGuid());
            }
            if (hashSet.isEmpty()) {
                APPLIED_BY_PLAYER.remove(n);
            } else {
                APPLIED_BY_PLAYER.put(n, hashSet);
                logger.debug("Skirk shared-arts bonus active for player {} ({} avatars)", (Object)n, (Object)hashSet.size());
            }
        }
        finally {
            REFRESH_IN_PROGRESS.set(Boolean.FALSE);
        }
    }

    public static void onConstellationRecalc(Player player) {
        if (player == null) {
            return;
        }
        Integer n = player.getUid();
        LAST_STATE_BY_PLAYER.remove(n);
        LAST_REFRESH_MS.remove(n);
        SkirkTeamBonusHelper.refresh(player);
    }

    public static void onPlayerLogin(Player player) {
        if (player == null) {
            return;
        }
        Integer n = player.getUid();
        LAST_STATE_BY_PLAYER.remove(n);
        LAST_REFRESH_MS.remove(n);
        SkirkTeamBonusHelper.refresh(player);
        try {
            TartagliaTeamBonusHelper.onPlayerLogin(player);
        } catch (Throwable throwable) {
            logger.warn("Tartaglia team bonus login hook failed", throwable);
        }
    }

    public static void onTeamChanged(Player player) {
        if (player == null) {
            return;
        }
        Integer n = player.getUid();
        LAST_STATE_BY_PLAYER.remove(n);
        LAST_REFRESH_MS.remove(n);
        SkirkTeamBonusHelper.refresh(player);
        try {
            TartagliaTeamBonusHelper.onTeamChanged(player);
        } catch (Throwable throwable) {
            logger.warn("Tartaglia team bonus team-change hook failed", throwable);
        }
    }

    public static Set<Long> getAppliedGuids(int n) {
        Set<Long> set = APPLIED_BY_PLAYER.get(n);
        return set == null ? Collections.emptySet() : set;
    }

    private static void clearAppliedBonuses(Player player, Integer n, boolean bl) {
        Set<Long> set = APPLIED_BY_PLAYER.remove(n);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (Long l : set) {
            Avatar avatar = player.getAvatars().getAvatarByGuid(l);
            if (avatar == null) continue;
            SkirkTeamBonusHelper.removeBonus(avatar, player, bl);
        }
    }

    private static List<Avatar> resolveTeamAvatars(Player player, TeamManager teamManager) {
        Avatar avatar;
        LinkedHashMap<Long, Avatar> linkedHashMap = new LinkedHashMap<Long, Avatar>();
        TeamInfo teamInfo = teamManager.getCurrentTeamInfo();
        if (teamInfo == null) {
            teamInfo = teamManager.getCurrentSinglePlayerTeamInfo();
        }
        if (teamInfo != null) {
            for (Integer object : teamInfo.getAvatars()) {
                if (object == null || object <= 0 || (avatar = player.getAvatars().getAvatarById(object)) == null) continue;
                linkedHashMap.put(avatar.getGuid(), avatar);
            }
        }
        for (EntityAvatar entityAvatar : teamManager.getActiveTeam()) {
            avatar = entityAvatar.getAvatar();
            if (avatar == null) continue;
            linkedHashMap.put(avatar.getGuid(), avatar);
        }
        return new ArrayList<Avatar>(linkedHashMap.values());
    }

    private static boolean shouldApplyBonus(List<Avatar> list) {
        if (list.isEmpty()) {
            return false;
        }
        return SkirkTeamBonusHelper.hasSkirkWithPassiveUnlocked(list) && SkirkTeamBonusHelper.matchesIceWaterTeamRule(list);
    }

    private static boolean matchesIceWaterTeamRule(List<Avatar> list) {
        boolean bl = false;
        boolean bl2 = false;
        for (Avatar avatar : list) {
            ElementType elementType = SkirkTeamBonusHelper.getAvatarElement(avatar);
            if (elementType == ElementType.Ice) {
                bl = true;
                continue;
            }
            if (elementType == ElementType.Water) {
                bl2 = true;
                continue;
            }
            return false;
        }
        return bl && bl2;
    }

    private static boolean hasSkirkWithPassiveUnlocked(List<Avatar> list) {
        for (Avatar avatar : list) {
            if (avatar.getAvatarId() != 10000114 || !SkirkTeamBonusHelper.hasPermanentSkill3Unlocked(avatar)) continue;
            return true;
        }
        return false;
    }

    private static boolean hasPermanentSkill3Unlocked(Avatar avatar) {
        for (Integer n : avatar.getProudSkillList()) {
            if (n / 100 != 11423) continue;
            return true;
        }
        return false;
    }

    private static boolean isOwnAvatar(Avatar avatar) {
        return avatar.getTrialAvatarId() == 0;
    }

    private static String buildStateKey(List<Avatar> list, boolean bl) {
        String string = list.stream().map(avatar -> avatar.getGuid() + ":" + SkirkTeamBonusHelper.getAvatarElement(avatar).name()).sorted().collect(Collectors.joining("|"));
        return bl + ":" + string;
    }

    private static String explainReason(List<Avatar> list, boolean bl) {
        if (bl) {
            return "ok";
        }
        if (list.isEmpty()) {
            return "empty-team";
        }
        if (!SkirkTeamBonusHelper.hasSkirkWithPassiveUnlocked(list)) {
            return "no-skirk-or-ps3";
        }
        boolean bl2 = false;
        boolean bl3 = false;
        for (Avatar avatar : list) {
            ElementType elementType = SkirkTeamBonusHelper.getAvatarElement(avatar);
            if (elementType == ElementType.Ice) {
                bl2 = true;
                continue;
            }
            if (elementType == ElementType.Water) {
                bl3 = true;
                continue;
            }
            return avatar.getAvatarId() + ":" + elementType.name();
        }
        if (!bl2) {
            return "missing-ice";
        }
        if (!bl3) {
            return "missing-water";
        }
        return "unknown";
    }

    private static String describeElements(List<Avatar> list) {
        return list.stream().map(avatar -> avatar.getAvatarId() + ":" + SkirkTeamBonusHelper.getAvatarElement(avatar).name()).collect(Collectors.joining(","));
    }

    private static ElementType getAvatarElement(Avatar avatar) {
        AvatarSkillDepotData avatarSkillDepotData = avatar.getSkillDepot();
        if (avatarSkillDepotData == null) {
            return ElementType.None;
        }
        ElementType elementType = avatarSkillDepotData.getElementType();
        return elementType != null ? elementType : ElementType.None;
    }

    private static int getElementalSkillProudGroupId(Avatar avatar) {
        AvatarSkillDepotData avatarSkillDepotData = avatar.getSkillDepot();
        if (avatarSkillDepotData == null) {
            return 0;
        }
        List<Integer> list = avatarSkillDepotData.getSkills();
        if (list == null || list.size() < 2) {
            return 0;
        }
        AvatarSkillData avatarSkillData = GameData.getAvatarSkillDataMap().get(list.get(1));
        if (avatarSkillData == null) {
            return 0;
        }
        return avatarSkillData.getProudSkillGroupId();
    }

    private static Map<Integer, Integer> bonusMap(Avatar avatar) {
        try {
            return (Map)PROUD_SKILL_BONUS_MAP.get(avatar);
        }
        catch (IllegalAccessException illegalAccessException) {
            throw new IllegalStateException("Unable to access proudSkillBonusMap", illegalAccessException);
        }
    }

    private static boolean applyBonus(Avatar avatar, Player player) {
        int n = SkirkTeamBonusHelper.getElementalSkillProudGroupId(avatar);
        if (n <= 0) {
            logger.warn("SkirkTeamBonus skip avatarId={} guid={}: missing elemental skill proud group", (Object)avatar.getAvatarId(), (Object)avatar.getGuid());
            return false;
        }
        if (SkirkTeamBonusHelper.getSkirkContribution(avatar, n) >= 1) {
            return true;
        }
        SkirkTeamBonusHelper.setSkirkContribution(avatar, n, 1);
        avatar.recalcStats(true);
        SkirkTeamBonusNotify.send(player, avatar, 2, SkirkTeamBonusHelper.getTotalElementalSkillExtraLevel(avatar, n));
        player.sendPacket(new PacketAvatarSkillDepotChangeNotify(avatar));
        return true;
    }

    private static void removeBonus(Avatar avatar, Player player, boolean bl) {
        int n = SkirkTeamBonusHelper.getElementalSkillProudGroupId(avatar);
        if (n <= 0 || SkirkTeamBonusHelper.getSkirkContribution(avatar, n) <= 0) {
            return;
        }
        SkirkTeamBonusHelper.setSkirkContribution(avatar, n, 0);
        avatar.recalcStats(true);
        if (bl) {
            SkirkTeamBonusNotify.send(player, avatar, 2, SkirkTeamBonusHelper.getTotalElementalSkillExtraLevel(avatar, n));
            player.sendPacket(new PacketAvatarSkillDepotChangeNotify(avatar));
        }
    }

    private static int getTotalElementalSkillExtraLevel(Avatar avatar, int n) {
        return avatar.getProudSkillBonusMap().getOrDefault(n, 0);
    }

    private static int getSkirkContribution(Avatar avatar, int n) {
        Map<Integer, Integer> map = SkirkTeamBonusHelper.bonusMap(avatar);
        Integer n2 = map.get(SkirkTeamBonusHelper.skirkBonusKey(n));
        return n2 == null ? 0 : n2;
    }

    private static void setSkirkContribution(Avatar avatar, int n, int n2) {
        Map<Integer, Integer> map = SkirkTeamBonusHelper.bonusMap(avatar);
        int n3 = SkirkTeamBonusHelper.skirkBonusKey(n);
        if (n2 <= 0) {
            Integer n4 = map.remove(n3);
            if (n4 != null && n4 > 0) {
                SkirkTeamBonusHelper.adjustTotalBonus(map, n, -n4.intValue());
            }
            return;
        }
        int n5 = map.getOrDefault(n3, 0);
        if (n5 == n2) {
            return;
        }
        map.put(n3, n2);
        SkirkTeamBonusHelper.adjustTotalBonus(map, n, n2 - n5);
    }

    private static int skirkBonusKey(int n) {
        return -n;
    }

    private static void adjustTotalBonus(Map<Integer, Integer> map, int n, int n4) {
        if (n4 == 0) {
            return;
        }
        map.compute(n, (n2, n3) -> {
            int updated = (n3 == null ? 0 : n3) + n4;
            return updated > 0 ? Integer.valueOf(updated) : null;
        });
    }

    static {
        APPLIED_BY_PLAYER = new ConcurrentHashMap();
        LAST_STATE_BY_PLAYER = new ConcurrentHashMap();
        LAST_REFRESH_MS = new ConcurrentHashMap();
        REFRESH_IN_PROGRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
        try {
            PROUD_SKILL_BONUS_MAP = Avatar.class.getDeclaredField("proudSkillBonusMap");
            PROUD_SKILL_BONUS_MAP.setAccessible(true);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            throw new ExceptionInInitializerError(reflectiveOperationException);
        }
    }
}
