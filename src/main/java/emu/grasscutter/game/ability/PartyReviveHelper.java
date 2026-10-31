package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.LifeState;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketLifeStateChangeNotify;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Qiqi's C6 and Barbara's C6 revive constellations.
 *
 * <p>Rationale: the official {@code ReviveDeadAvatar} barely lands in this server's ability pipeline, and
 * {@code EntityAvatar.heal}
 * returns immediately at HP &lt;= 0, so a downed character cannot be brought back by ordinary healing.
 *
 * <p>Key rules:
 * <ul>
 *   <li>Qiqi: casting the burst revives a downed teammate to 50% HP, 15 minute cooldown
 *   <li>Barbara: the character must go down first so the death plays, then is immediately revived to full
 *       HP, 15 minute cooldown, and only while she is off-field
 *   <li>Completely independent of Kuki Shinobu's C6 survive-at-1-HP cooldown; they are not shared
 * </ul>
 */
public final class PartyReviveHelper {
    public static final int QIQI_AVATAR_ID = 10000035;
    public static final int BARBARA_AVATAR_ID = 10000014;
    public static final int QIQI_TALENT_C6 = 356;
    public static final int BARBARA_TALENT_C6 = 146;
    public static final int QIQI_BURST_SKILL = 10353;
    public static final float QIQI_REVIVE_RATIO = 0.5f;
    public static final float BARBARA_REVIVE_RATIO = 1.0f;
    public static final long CD_MS = 15L * 60L * 1000L;

    private static final Int2LongMap QIQI_CD_UNTIL = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap BARBARA_CD_UNTIL = Int2LongMaps.synchronize(new Int2LongOpenHashMap());

    static {
        QIQI_CD_UNTIL.defaultReturnValue(0L);
        BARBARA_CD_UNTIL.defaultReturnValue(0L);
    }

    private PartyReviveHelper() {}

    public static void clearPlayerState(Player player) {
        if (player == null) return;
        QIQI_CD_UNTIL.remove(player.getUid());
        BARBARA_CD_UNTIL.remove(player.getUid());
    }

    /** Force-clear CDs (e.g. after a mis-attributed ability revive burned Qiqi's timer). */
    public static void clearAllCds(int uid) {
        QIQI_CD_UNTIL.remove(uid);
        BARBARA_CD_UNTIL.remove(uid);
    }

    public static void onQiqiBurst(Player player, EntityAvatar caster) {
        if (player == null || caster == null || caster.getAvatar() == null) return;
        if (caster.getAvatar().getAvatarId() != QIQI_AVATAR_ID) return;
        if (!hasC6(caster.getAvatar(), QIQI_AVATAR_ID, QIQI_TALENT_C6, "Qiqi_Constellation")) {
            Grasscutter.getLogger().info("[QiqiC6] skip no-C6 uid={}", player.getUid());
            return;
        }
        int n = reviveFallen(player, QIQI_REVIVE_RATIO, QIQI_CD_UNTIL, "QiqiC6");
        if (n == 0) {
            Grasscutter.getLogger()
                    .info("[QiqiC6] burst but nobody to revive uid={}", player.getUid());
        }
    }

    /**
     * Ability-config path. Only consumes Qiqi/Barbara CD when the owning ability actually belongs
     * to that character, never "whoever happens to be in the party".
     */
    public static int reviveFallenFromAbility(Ability ability, float ratio) {
        if (ability == null || ability.getPlayerOwner() == null) return 0;
        Player player = ability.getPlayerOwner();
        String abilityName =
                ability.getData() != null && ability.getData().abilityName != null
                        ? ability.getData().abilityName
                        : "";

        EntityAvatar ownerAvatar = resolveOwnerAvatar(ability);
        int ownerId = ownerAvatar != null && ownerAvatar.getAvatar() != null
                ? ownerAvatar.getAvatar().getAvatarId()
                : 0;

        boolean qiqiAbility =
                ownerId == QIQI_AVATAR_ID || abilityName.contains("Qiqi_Constellation");
        boolean barbaraAbility =
                ownerId == BARBARA_AVATAR_ID || abilityName.contains("Barbara_Constellation");

        if (qiqiAbility) {
            Avatar qiqiAvatar =
                    ownerAvatar != null && ownerAvatar.getAvatar() != null
                            ? ownerAvatar.getAvatar()
                            : OptionalAvatar(findAvatar(player, QIQI_AVATAR_ID));
            if (hasC6(qiqiAvatar, QIQI_AVATAR_ID, QIQI_TALENT_C6, "Qiqi_Constellation")) {
                float use = ratio > 0.01f ? ratio : QIQI_REVIVE_RATIO;
                return reviveFallen(player, use, QIQI_CD_UNTIL, "QiqiC6");
            }
        }
        if (barbaraAbility) {
            Avatar barbaraAvatar = ownerAvatar != null ? ownerAvatar.getAvatar() : null;
            if (hasC6(barbaraAvatar, BARBARA_AVATAR_ID, BARBARA_TALENT_C6, "Barbara_Constellation")) {
                float use = ratio > 0.01f ? ratio : BARBARA_REVIVE_RATIO;
                return reviveFallen(player, use, BARBARA_CD_UNTIL, "BarbaraC6");
            }
        }

        // Generic ReviveAvatar (food / other): revive without burning constellation CDs.
        return reviveFallen(player, ratio > 0.01f ? ratio : QIQI_REVIVE_RATIO, null, "ReviveAvatar");
    }

    private static Avatar OptionalAvatar(EntityAvatar ea) {
        return ea != null ? ea.getAvatar() : null;
    }

    /**
     * Official C6 flow: the character goes down first, then is immediately revived to 100% HP.
     * Call this after LIFE_DEAD has been sent, or is about to be. Do not turn it into a
     * never-die-just-heal-to-full effect.
     */
    public static boolean tryBarbaraC6AfterDeath(EntityAvatar dead) {
        if (dead == null || dead.getAvatar() == null || dead.getPlayer() == null) {
            return false;
        }
        Player player = dead.getPlayer();
        // Barbara must be off-field (not the fallen character).
        EntityAvatar barbara = findAvatar(player, BARBARA_AVATAR_ID);
        if (barbara == null || barbara.getId() == dead.getId()) {
            return false;
        }
        if (!barbara.isAlive()
                || barbara.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0f) {
            return false;
        }
        if (!hasC6(barbara.getAvatar(), BARBARA_AVATAR_ID, BARBARA_TALENT_C6, "Barbara_Constellation")) {
            return false;
        }
        int uid = player.getUid();
        long now = System.currentTimeMillis();
        if (now < BARBARA_CD_UNTIL.get(uid)) {
            Grasscutter.getLogger()
                    .info(
                            "[BarbaraC6] skip CD uid={} remainMs={}",
                            uid,
                            BARBARA_CD_UNTIL.get(uid) - now);
            return false;
        }
        float hp = applyRevive(dead, BARBARA_REVIVE_RATIO);
        if (hp <= 0f) return false;
        BARBARA_CD_UNTIL.put(uid, now + CD_MS);
        Grasscutter.getLogger()
                .info("[BarbaraC6] die-revive entity={} uid={} hp={}", dead.getId(), uid, hp);
        return true;
    }

    private static int reviveFallen(
            Player player, float ratio, Int2LongMap cdMap, String tag) {
        int uid = player.getUid();
        long now = System.currentTimeMillis();
        if (cdMap != null && now < cdMap.get(uid)) {
            Grasscutter.getLogger()
                    .info("[{}] skip CD uid={} remainMs={}", tag, uid, cdMap.get(uid) - now);
            return 0;
        }
        int revived = 0;
        for (EntityAvatar member : player.getTeamManager().getActiveTeam()) {
            if (member == null || member.getAvatar() == null) continue;
            float cur = member.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            if (member.isAlive() && cur > 0f) {
                continue;
            }
            float hp = applyRevive(member, ratio);
            if (hp > 0f) {
                revived++;
            }
        }
        if (revived > 0 && cdMap != null) {
            cdMap.put(uid, now + CD_MS);
            Grasscutter.getLogger().info("[{}] revived {} uid={} ratio={}", tag, revived, uid, ratio);
        } else if (revived > 0) {
            Grasscutter.getLogger().info("[{}] revived {} uid={} ratio={}", tag, revived, uid, ratio);
        }
        return revived;
    }

    /** Restore HP + life-state packets the client needs to show a fallen avatar as alive again. */
    private static float applyRevive(EntityAvatar entity, float ratio) {
        // reviveToRatio is on EntityAvatar so it can clear the protected dead flag.
        float hp = entity.reviveToRatio(ratio);
        if (hp > 0f) {
            try {
                Player player = entity.getPlayer();
                if (player != null) {
                    player.getSatiationManager().removeSatiationDirectly(entity.getAvatar(), 15000);
                }
            } catch (Throwable ignored) {
            }
            Grasscutter.getLogger()
                    .info(
                            "[ReviveSync] avatarId={} entity={} hp={}/{}",
                            entity.getAvatar().getAvatarId(),
                            entity.getId(),
                            hp,
                            entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        }
        return hp;
    }

    private static EntityAvatar resolveOwnerAvatar(Ability ability) {
        var owner = ability.getOwner();
        if (owner instanceof emu.grasscutter.game.entity.EntityClientGadget g) {
            var next = g.getScene() != null ? g.getScene().getEntityById(g.getOwnerEntityId()) : null;
            if (next instanceof EntityAvatar ea) return ea;
            owner = next;
        }
        return owner instanceof EntityAvatar ea ? ea : null;
    }

    private static EntityAvatar findAvatar(Player player, int avatarId) {
        if (player == null || player.getTeamManager() == null) return null;
        for (EntityAvatar member : player.getTeamManager().getActiveTeam()) {
            if (member != null
                    && member.getAvatar() != null
                    && member.getAvatar().getAvatarId() == avatarId) {
                return member;
            }
        }
        return null;
    }

    public static boolean hasC6(Avatar avatar, int avatarId, int talentId, String embryoNeedle) {
        if (avatar == null || avatar.getAvatarId() != avatarId) return false;
        try {
            if (avatar.getCoreProudSkillLevel() >= 6) return true;
        } catch (Throwable ignored) {
        }
        var talents = avatar.getTalentIdList();
        if (talents != null && talents.contains(talentId)) return true;
        var embryos = avatar.getExtraAbilityEmbryos();
        if (embryos != null && embryoNeedle != null) {
            for (String name : embryos) {
                if (name != null && name.contains(embryoNeedle)) return true;
            }
        }
        return false;
    }
}
