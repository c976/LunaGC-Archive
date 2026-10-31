package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Lauma's C1, Thread of Life.
 *
 * <p>After a Lunar Bloom the client usually pushes only {@code MODIFIER_CHANGE} (HealHP) and never goes
 * through the server's {@code ApplyModifier},
 * so besides ApplyModifier the heal is also settled directly on: E/Q opening the 20s window, the modifier
 * attaching, and the Lunar Bloom GV/mixin signals.
 */
public final class LaumaC1HealHelper {
    public static final int LAUMA_AVATAR_ID = 10000119;
    public static final int SKILL_E = 11192;
    public static final int SKILL_Q = 11195;
    public static final int TALENT_C1 = 1191;
    public static final String ABILITY_NAME = "Avatar_Lauma_Constellation_1";
    public static final String HEAL_MODIFIER = "UNIQUE_Avatar_Lauma_Constellation_1_HealHP";
    public static final String TEAM_HANDLER = "UNIQUE_Avatar_Lauma_Constellation_1_TeamHandler";

    private static final String RATIO_KEY = "Constellation_1_Heal_Ratio";
    private static final String CD_KEY = "Constellation_1_Heal_CD";
    private static final float DEFAULT_RATIO = 5.0f;
    private static final float DEFAULT_CD_SEC = 1.9f;
    private static final long DEFAULT_WINDOW_MS = 20_000L;

    private static final Int2LongMap LAST_HEAL_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap WINDOW_UNTIL_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());

    private LaumaC1HealHelper() {}

    public static boolean isHealModifier(String modifierName) {
        return HEAL_MODIFIER.equals(modifierName);
    }

    public static boolean isLaumaC1Ability(String abilityName) {
        return ABILITY_NAME.equals(abilityName);
    }

    public static boolean isLaumaC1(Ability ability) {
        return ability != null
                && ability.getData() != null
                && ABILITY_NAME.equals(ability.getData().abilityName);
    }

    /** E and Q open the 20s Thread of Life window. */
    public static void onSkillStart(Player player, EntityAvatar caster, int skillId) {
        if (player == null || caster == null || caster.getAvatar() == null) {
            return;
        }
        if (caster.getAvatar().getAvatarId() != LAUMA_AVATAR_ID) {
            return;
        }
        if (skillId != SKILL_E && skillId != SKILL_Q) {
            return;
        }
        if (!hasConstellation1(caster.getAvatar())) {
            return;
        }
        armWindow(player.getUid(), DEFAULT_WINDOW_MS);
        Grasscutter.getLogger().info("[LaumaC1] arm 20s window skillId={} uid={}", skillId, player.getUid());
    }

    /** TriggerAbility and TeamHandler attachment open the window too. */
    public static void onThreadOfLife(Player player) {
        if (player == null) {
            return;
        }
        EntityAvatar lauma = findLaumaInTeam(player);
        if (lauma == null || !hasConstellation1(lauma.getAvatar())) {
            return;
        }
        armWindow(player.getUid(), DEFAULT_WINDOW_MS);
        Grasscutter.getLogger().info("[LaumaC1] arm 20s window via ThreadOfLife uid={}", player.getUid());
    }

    public static void onModifierAdded(Player player, String parentAbility, AbilityModifier modifier) {
        if (player == null || !isLaumaC1Ability(parentAbility)) {
            return;
        }
        if (modifierHasHealHp(modifier)) {
            Grasscutter.getLogger().info("[LaumaC1] HealHP modifier added -> tryHeal");
            tryHeal(player, null);
            return;
        }
        if (isTeamHandlerModifier(modifier)) {
            onThreadOfLife(player);
        }
    }

    public static void onModifierAddedByName(Player player, String parentAbility, String modifierName) {
        if (player == null || !isLaumaC1Ability(parentAbility)) {
            return;
        }
        if (isHealModifier(modifierName)) {
            Grasscutter.getLogger().info("[LaumaC1] HealHP name hit -> tryHeal");
            tryHeal(player, null);
        } else if (TEAM_HANDLER.equals(modifierName)) {
            onThreadOfLife(player);
        }
    }

    /** Lunar Bloom signals (GV, mixin, dew). Heals only inside the Thread of Life window. */
    public static void onMoonBloom(Player player) {
        if (player == null || !isWindowActive(player.getUid())) {
            return;
        }
        Grasscutter.getLogger().info("[LaumaC1] moon bloom while armed -> tryHeal uid={}", player.getUid());
        tryHeal(player, null);
    }

    public static boolean tryHeal(Ability ability) {
        if (ability == null) {
            return false;
        }
        return tryHeal(ability.getPlayerOwner(), ability);
    }

    public static boolean tryHeal(Player player, Ability ability) {
        if (player == null || player.getTeamManager() == null || player.getWorld() == null) {
            return false;
        }

        EntityAvatar lauma = ability != null ? findLauma(player, ability) : findLaumaInTeam(player);
        if (lauma == null || lauma.getAvatar() == null) {
            Grasscutter.getLogger().info("[LaumaC1] tryHeal: no Lauma in team");
            return false;
        }
        if (!hasConstellation1(lauma.getAvatar())) {
            Grasscutter.getLogger().info("[LaumaC1] tryHeal: C1 not unlocked");
            return false;
        }

        float cdSec = DEFAULT_CD_SEC;
        float ratio = DEFAULT_RATIO;
        if (ability != null) {
            cdSec = ability.getAbilitySpecials().getOrDefault(CD_KEY, DEFAULT_CD_SEC);
            ratio = ability.getAbilitySpecials().getOrDefault(RATIO_KEY, DEFAULT_RATIO);
        } else {
            float[] params = readC1Params(lauma.getAvatar());
            if (params[1] > 0f) {
                ratio = params[1];
            }
            if (params[2] > 0f) {
                cdSec = params[2];
            }
        }
        if (cdSec <= 0f) {
            cdSec = DEFAULT_CD_SEC;
        }
        if (ratio <= 0f) {
            ratio = DEFAULT_RATIO;
        }

        long now = System.currentTimeMillis();
        int key = lauma.getId();
        synchronized (LAST_HEAL_MS) {
            if (LAST_HEAL_MS.containsKey(key) && now - LAST_HEAL_MS.get(key) < (long) (cdSec * 1000f)) {
                Grasscutter.getLogger().debug("[LaumaC1] ICD blocked");
                return false;
            }
        }

        EntityAvatar active = player.getTeamManager().getCurrentAvatarEntity();
        if (active == null || !active.isAlive()) {
            return false;
        }

        float mastery = lauma.getFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY);
        float amount = mastery * ratio;
        if (amount <= 0f) {
            Grasscutter.getLogger()
                    .warn("[LaumaC1] heal amount<=0 mastery={} ratio={}", mastery, ratio);
            return false;
        }

        float healAdd = lauma.getFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD);
        float healedAdd = active.getFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD);
        float finalAmount = amount * (1.0f + healAdd + healedAdd);
        float real = active.heal(finalAmount, false);
        if (real > 0f) {
            player.getWorld()
                    .broadcastPacket(new PacketEvtBeingHealedNotify(lauma, active, finalAmount, real));
        }

        synchronized (LAST_HEAL_MS) {
            LAST_HEAL_MS.put(key, now);
        }

        Grasscutter.getLogger()
                .info(
                        "[LaumaC1] heal active={} amount={} real={} (EM={} * {})",
                        active.getAvatar() != null ? active.getAvatar().getAvatarId() : active.getId(),
                        finalAmount,
                        real,
                        mastery,
                        ratio);
        return true;
    }

    public static void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        int uid = player.getUid();
        synchronized (WINDOW_UNTIL_MS) {
            WINDOW_UNTIL_MS.remove(uid);
        }
        if (player.getTeamManager() == null) {
            return;
        }
        synchronized (LAST_HEAL_MS) {
            for (EntityAvatar member : player.getTeamManager().getActiveTeam()) {
                if (member != null) {
                    LAST_HEAL_MS.remove(member.getId());
                }
            }
        }
    }

    public static boolean modifierHasHealHp(AbilityModifier modifier) {
        if (modifier == null || modifier.onAdded == null) {
            return false;
        }
        for (AbilityModifierAction action : modifier.onAdded) {
            if (action != null && action.type == AbilityModifierAction.Type.HealHP) {
                return true;
            }
        }
        return false;
    }

    /** TeamHandler.onAdded attaches AvatarHandler to all party members. */
    private static boolean isTeamHandlerModifier(AbilityModifier modifier) {
        if (modifier == null || modifier.onAdded == null) {
            return false;
        }
        for (AbilityModifierAction action : modifier.onAdded) {
            if (action == null || action.type != AbilityModifierAction.Type.AttachModifier) {
                continue;
            }
            if (action.modifierName != null && action.modifierName.contains("AvatarHandler")) {
                return true;
            }
        }
        return false;
    }

    private static void armWindow(int uid, long durationMs) {
        long until = System.currentTimeMillis() + durationMs;
        synchronized (WINDOW_UNTIL_MS) {
            long prev = WINDOW_UNTIL_MS.getOrDefault(uid, 0L);
            if (until > prev) {
                WINDOW_UNTIL_MS.put(uid, until);
            }
        }
    }

    private static boolean isWindowActive(int uid) {
        synchronized (WINDOW_UNTIL_MS) {
            return System.currentTimeMillis() <= WINDOW_UNTIL_MS.getOrDefault(uid, 0L);
        }
    }

    private static boolean hasConstellation1(Avatar avatar) {
        if (avatar == null || avatar.getTalentIdList() == null) {
            return false;
        }
        return avatar.getTalentIdList().contains(TALENT_C1);
    }

    /** paramList: [dura=20, healRatio=5, healCd=1.9, stamina=0.4, addDura=5] */
    private static float[] readC1Params(Avatar avatar) {
        float[] out = new float[] {20f, DEFAULT_RATIO, DEFAULT_CD_SEC, 0.4f, 5f};
        var talent = GameData.getAvatarTalentDataMap().get(TALENT_C1);
        if (talent != null && talent.getParamList() != null && talent.getParamList().length >= 3) {
            out[0] = talent.getParamList()[0];
            out[1] = talent.getParamList()[1];
            out[2] = talent.getParamList()[2];
        }
        return out;
    }

    private static EntityAvatar findLaumaInTeam(Player player) {
        for (EntityAvatar member : player.getTeamManager().getActiveTeam()) {
            if (member != null
                    && member.getAvatar() != null
                    && member.getAvatar().getAvatarId() == LAUMA_AVATAR_ID) {
                return member;
            }
        }
        return null;
    }

    private static EntityAvatar findLauma(Player player, Ability ability) {
        EntityAvatar fromAbility = ability.getCasterEntity();
        if (fromAbility != null
                && fromAbility.getAvatar() != null
                && fromAbility.getAvatar().getAvatarId() == LAUMA_AVATAR_ID) {
            return fromAbility;
        }
        GameEntity owner = ability.getOwner();
        if (owner instanceof EntityAvatar avatar
                && avatar.getAvatar() != null
                && avatar.getAvatar().getAvatarId() == LAUMA_AVATAR_ID) {
            return avatar;
        }
        return findLaumaInTeam(player);
    }
}
