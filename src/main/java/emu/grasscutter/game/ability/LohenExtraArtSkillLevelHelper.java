package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ProudSkillExtraLevelNotifyOuterClass;
import emu.grasscutter.server.packet.send.PacketAvatarSkillDepotChangeNotify;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Lohen (10000129) utility passive.
 *
 * <p>Official data applies open-config {@code Lohen_PermanentSkill_3_Talent}
 * ({@code AddTalentExtraLevel} talentIndex=2, +1) via modifier mixin {@code FNOJKLNOEAB} on
 * {@code UNIQUE_Lohen_PermanentSkill_3_ExtraArtSkillLevel}. That mixin type is not implemented
 * server-side, so ExtraLevel is mirrored when the modifier is applied/removed (action path or
 * {@code ABILITY_META_MODIFIER_CHANGE}).
 */
public final class LohenExtraArtSkillLevelHelper {
    private static final Logger logger = Grasscutter.getLogger();
    public static final int LOHEN_AVATAR_ID = 10000129;
    public static final String EXTRA_ART_MODIFIER = "UNIQUE_Lohen_PermanentSkill_3_ExtraArtSkillLevel";
    private static final int ELEMENTAL_SKILL_TALENT_INDEX = 2;
    private static final int BONUS_AMOUNT = 1;
    private static final Field PROUD_SKILL_BONUS_MAP;
    /** guid -> elemental-skill proud group currently holding the temporary +1 */
    private static final ConcurrentHashMap<Long, Integer> APPLIED_GROUP_BY_GUID =
            new ConcurrentHashMap<>();
    /** client instanced modifier id -> avatar guid, for MODIFIER_CHANGE remove */
    private static final ConcurrentHashMap<Integer, Long> INSTANCE_TO_GUID = new ConcurrentHashMap<>();

    private LohenExtraArtSkillLevelHelper() {}

    public static void clearPlayerState(Player player) {
        if (player == null || player.getAvatars() == null) {
            return;
        }
        for (Avatar avatar : player.getAvatars()) {
            if (avatar != null) {
                removeBonus(avatar, player, false);
            }
        }
        INSTANCE_TO_GUID.entrySet().removeIf(e -> {
            Avatar avatar = player.getAvatars().getAvatarByGuid(e.getValue());
            return avatar != null && avatar.getAvatarId() == LOHEN_AVATAR_ID;
        });
    }

    public static boolean isExtraArtModifier(String modifierName) {
        return EXTRA_ART_MODIFIER.equals(modifierName);
    }

    public static void onModifierApplied(Ability ability) {
        Avatar avatar = resolveLohenAvatar(ability);
        if (avatar == null) {
            return;
        }
        Player player = ability.getPlayerOwner();
        if (player == null) {
            player = avatar.getPlayer();
        }
        if (applyBonus(avatar, player)) {
            logger.info("Lohen ExtraArt +1 active guid={}", avatar.getGuid());
        }
    }

    public static void onModifierApplied(Ability ability, int instancedModifierId) {
        Avatar avatar = resolveLohenAvatar(ability);
        if (avatar == null) {
            return;
        }
        if (instancedModifierId != 0) {
            INSTANCE_TO_GUID.put(instancedModifierId, avatar.getGuid());
        }
        onModifierApplied(ability);
    }

    public static void onModifierRemoved(Ability ability) {
        Avatar avatar = resolveLohenAvatar(ability);
        if (avatar == null) {
            return;
        }
        Player player = ability.getPlayerOwner();
        if (player == null) {
            player = avatar.getPlayer();
        }
        if (removeBonus(avatar, player, true)) {
            logger.info("Lohen ExtraArt +1 cleared guid={}", avatar.getGuid());
        }
    }

    public static void onModifierRemovedByInstance(Player player, int instancedModifierId) {
        Long guid = INSTANCE_TO_GUID.remove(instancedModifierId);
        if (guid == null || player == null) {
            return;
        }
        Avatar avatar = player.getAvatars().getAvatarByGuid(guid);
        if (avatar == null) {
            return;
        }
        if (removeBonus(avatar, player, true)) {
            logger.info("Lohen ExtraArt +1 cleared (modifier-change) guid={}", guid);
        }
    }

    /** After constellation recalc wipe, restore Enjoyment bonus if still active. */
    public static void onConstellationRecalc(Avatar avatar) {
        if (avatar == null) {
            return;
        }
        Integer groupId = APPLIED_GROUP_BY_GUID.get(avatar.getGuid());
        if (groupId == null || groupId <= 0) {
            return;
        }
        Map<Integer, Integer> map = bonusMap(avatar);
        // After clear+constellation restore, Enjoyment +1 is gone; put it back.
        map.merge(groupId, BONUS_AMOUNT, Integer::sum);
    }

    private static Avatar resolveLohenAvatar(Ability ability) {
        if (ability == null || ability.getOwner() == null) {
            return null;
        }
        if (!(ability.getOwner() instanceof EntityAvatar entityAvatar)) {
            return null;
        }
        Avatar avatar = entityAvatar.getAvatar();
        if (avatar == null || avatar.getAvatarId() != LOHEN_AVATAR_ID) {
            return null;
        }
        return avatar;
    }

    private static boolean applyBonus(Avatar avatar, Player player) {
        int groupId = getElementalSkillProudGroupId(avatar);
        if (groupId <= 0) {
            logger.warn("Lohen ExtraArt skip guid={}: missing E proud group", avatar.getGuid());
            return false;
        }
        if (APPLIED_GROUP_BY_GUID.containsKey(avatar.getGuid())) {
            return true;
        }
        bonusMap(avatar).merge(groupId, BONUS_AMOUNT, Integer::sum);
        APPLIED_GROUP_BY_GUID.put(avatar.getGuid(), groupId);
        if (player != null) {
            notify(player, avatar, getTotalExtraLevel(avatar, groupId));
        }
        return true;
    }

    private static boolean removeBonus(Avatar avatar, Player player, boolean notifyClient) {
        Integer groupId = APPLIED_GROUP_BY_GUID.remove(avatar.getGuid());
        if (groupId == null || groupId <= 0) {
            return false;
        }
        Map<Integer, Integer> map = bonusMap(avatar);
        map.compute(
                groupId,
                (k, v) -> {
                    int updated = (v == null ? 0 : v) - BONUS_AMOUNT;
                    return updated > 0 ? Integer.valueOf(updated) : null;
                });
        if (notifyClient && player != null) {
            notify(player, avatar, getTotalExtraLevel(avatar, groupId));
        }
        return true;
    }

    private static void notify(Player player, Avatar avatar, int extraLevel) {
        ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify proto =
                ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .setTalentType(3)
                        .setTalentIndex(ELEMENTAL_SKILL_TALENT_INDEX)
                        .setExtraLevel(extraLevel)
                        .build();
        BasePacket packet = new BasePacket(PacketOpcodes.ProudSkillExtraLevelNotify);
        packet.setData(proto.toByteArray());
        player.sendPacket(packet);
        player.sendPacket(new PacketAvatarSkillDepotChangeNotify(avatar));
    }

    private static int getElementalSkillProudGroupId(Avatar avatar) {
        AvatarSkillDepotData depot = avatar.getSkillDepot();
        if (depot == null) {
            return 0;
        }
        List<Integer> skills = depot.getSkills();
        if (skills == null || skills.size() < 2) {
            return 0;
        }
        AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(skills.get(1));
        return skillData == null ? 0 : skillData.getProudSkillGroupId();
    }

    private static int getTotalExtraLevel(Avatar avatar, int groupId) {
        return avatar.getProudSkillBonusMap().getOrDefault(groupId, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> bonusMap(Avatar avatar) {
        try {
            return (Map<Integer, Integer>) PROUD_SKILL_BONUS_MAP.get(avatar);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access proudSkillBonusMap", e);
        }
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
