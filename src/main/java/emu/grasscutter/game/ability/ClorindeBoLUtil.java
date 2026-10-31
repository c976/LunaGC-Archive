package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangeHpDebtsReason._ChangeHpDebtsReason;
import emu.grasscutter.net.proto.ForwardTypeOuterClass.ForwardType;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketAbilityInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

/**
 * Clorinde Bond of Life. Official behaviour: Swift Hunt grants BoL when the pistolet is FIRED,
 *
 * <ul>
 *   <li>Casting E grants no BoL
 *   <li>During Night Vigil each Swift Hunt shot (GunShot / ShotGun_Attack) grants +35% MaxHP while BoL &lt; 100% MaxHP
 *   <li>The burst grants BoL per her passive
 *   <li>During Night Vigil: healing other than Impale the Night converts to BoL; Impale the Night heals and pays BoL down
 * </ul>
 */
public final class ClorindeBoLUtil {
    public static final int CLORINDE_AVATAR_ID = 10000098;
    public static final int SKILL_NA = 10981;
    public static final int SKILL_E = 10982;
    public static final int SKILL_DODGE = 10983;
    public static final int SKILL_Q = 10985;

    private static final String ACTIVE_FLAG = "_ABILITY_Clorinde_ElementalArt_Active_Flag";
    private static final String DODGE_HEAL_FLAG = "_ABILITY_Clorinde_Dodge_HealFlag";
    private static final String HP_DEBTS_ENHANCED = "_ABILITY_Clorinde_ElementalArt_HpDebts_Enhanced";
    private static final String DODGE_ENHANCED = "_ABILITY_Clorinde_DodgeEnhanced";

    private static final float SHOT_RATIO = 0.35f;
    private static final float HEAL_TO_BOL_RATIO = 0.8f;
    /** Passive defaults for Impale the Night healing: BoL &lt; 100% / BoL &ge; 100% (104% / 110% in the detail panel). */
    private static final float DODGE_HEAL_RATIO_HAS_DEBT = 1.04f;
    private static final float DODGE_HEAL_RATIO_FULL_DEBT = 1.10f;
    private static final String FORBID_FOOD_HEAL = "_ABILITY_Avatar_ForbidFoodHeal";
    /** Dedupes Impale the Night healing; the skill-start settle and HealHP can both fire. */
    private static final long DODGE_HEAL_ICD_MS = 500L;
    /** Dedupes multiple actions from one shot; must be shorter than the Swift Hunt burst interval. */
    private static final long SHOT_ICD_MS = 220L;
    private static final long VIGIL_MS = 9000L;
    /** Short Impale the Night window, used only to help identify healing - no longer gates Swift Hunt BoL. */
    private static final long DODGE_WINDOW_MS = 800L;

    private static final Int2LongMap LAST_SHOT_GRANT_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap LAST_DODGE_HEAL_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap VIGIL_UNTIL_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap DODGE_UNTIL_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final ThreadLocal<Float> HEAL_SAVED_BOL = new ThreadLocal<>();

    private ClorindeBoLUtil() {}

    public static boolean isClorinde(EntityAvatar avatar) {
        return avatar != null
                && avatar.getAvatar() != null
                && avatar.getAvatar().getAvatarId() == CLORINDE_AVATAR_ID;
    }

    public static boolean isNightVigil(EntityAvatar avatar) {
        if (avatar == null) {
            return false;
        }
        if (globalFlag(avatar, ACTIVE_FLAG) > 0.01f) {
            return true;
        }
        return VIGIL_UNTIL_MS.getOrDefault(avatar.getId(), 0L) > System.currentTimeMillis();
    }

    public static boolean isDodgeHeal(EntityAvatar avatar) {
        if (avatar == null) {
            return false;
        }
        // Use the short window only. Do not trust the sticky global Dodge_HealFlag: it stays 1 after
        // Impale the Night, which would block every later BoL grant.
        long until = DODGE_UNTIL_MS.getOrDefault(avatar.getId(), 0L);
        long now = System.currentTimeMillis();
        if (until > now) {
            return true;
        }
        // Window has passed: clear the sticky flag we wrote so later checks are not misled.
        if (avatar.getGlobalAbilityValues() != null
                && globalFlag(avatar, DODGE_HEAL_FLAG) > 0.01f) {
            avatar.getGlobalAbilityValues().put(DODGE_HEAL_FLAG, Float.valueOf(0f));
        }
        return false;
    }

    private static float globalFlag(EntityAvatar avatar, String key) {
        if (avatar == null || avatar.getGlobalAbilityValues() == null) {
            return 0f;
        }
        Object v = avatar.getGlobalAbilityValues().get(key);
        if (v instanceof Float f) {
            return f;
        }
        if (v instanceof Number n) {
            return n.floatValue();
        }
        return 0f;
    }

    public static void applyFromSkillHook(EntityAvatar avatar, float ratio) {
        Grasscutter.getLogger()
                .info("[BoL][Clorinde] stock applyClorindeBoL ignored ratio=" + ratio);
    }

    public static void applyNoArgHook(EntityAvatar avatar) {
        // Spurious grant seen when Dodge_HealFlag is cleared - ignore.
    }

    /**
     * After damage, re-push HP_DEBTS and the enhancement tier via UpdateNotify.
     * A full AvatarFightPropNotify must not carry BoL (HP would stop dropping), but skipping the
     * re-push loses the BoL bar and the Impale the Night tier.
     */
    public static void onDamaged(EntityAvatar avatar) {
        if (!isClorinde(avatar)) {
            return;
        }
        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (bol <= 0.5f || maxHp <= 1f) {
            return;
        }
        Player player = avatar.getPlayer();
        if (player != null && avatar.getAvatar() != null) {
            try {
                player.sendPacket(
                        new PacketAvatarFightPropUpdateNotify(
                                avatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            } catch (Exception ignored) {
            }
        }
        if (avatar.getScene() != null) {
            try {
                avatar.getScene()
                        .broadcastPacket(
                                new PacketEntityFightPropUpdateNotify(
                                        avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            } catch (Exception ignored) {
            }
        }
        syncClorindeDebtTiers(avatar, bol, maxHp, true);
    }

    public static void onSkillStart(EntityAvatar avatar, int skillId) {
        if (!isClorinde(avatar)) {
            return;
        }
        if (skillId == SKILL_E) {
            if (avatar.getGlobalAbilityValues() != null) {
                avatar.getGlobalAbilityValues().put(ACTIVE_FLAG, Float.valueOf(1f));
            }
            pushGlobalFloat(avatar, ACTIVE_FLAG, 1f);
            pushGlobalFloat(avatar, DODGE_HEAL_FLAG, 0f);
            VIGIL_UNTIL_MS.put(avatar.getId(), System.currentTimeMillis() + VIGIL_MS);
            // Refresh the tier from the real BoL, clearing any fake full-BoL left by the previous keepTier.
            float bolNow = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            syncClorindeDebtTiers(avatar, bolNow, maxHp, true);
            Grasscutter.getLogger()
                    .info(
                            "[BoL][Clorinde] E start - no BoL grant, vigil on, bol="
                                    + bolNow
                                    + " tier="
                                    + debtTier(bolNow, maxHp));
            return;
        }
        if (skillId == SKILL_DODGE) {
            DODGE_UNTIL_MS.put(avatar.getId(), System.currentTimeMillis() + DODGE_WINDOW_MS);
            // Lock the tier to the current BoL before Impale the Night starts; the tier must not drop
            // to 0 the moment healing pays the BoL down.
            float bolNow = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            syncClorindeDebtTiers(avatar, bolNow, maxHp, true);
            pushGlobalFloat(avatar, DODGE_HEAL_FLAG, 0f);
            Grasscutter.getLogger()
                    .info(
                            "[BoL][Clorinde] Dodge/Impale window open tier="
                                    + debtTier(bolNow, maxHp)
                                    + " bol="
                                    + bolNow);
            // Do not wait for the client's Predicated flag: if there is BoL, settle heal as BoL x ratio.
            tryDodgeHealFromSkill(avatar);
            return;
        }
        if (skillId == SKILL_Q) {
            onBurst(avatar);
            return;
        }
        // NA skill reporting is unreliable and double-counts with GunShot; the main grant path is gunshot-action.
    }

    public static void onBurst(EntityAvatar avatar) {
        if (!isClorinde(avatar)) {
            return;
        }
        grant(avatar, burstBondRatio(avatar.getAvatar()), "burst");
    }

    /**
     * Any GunShot action executing means the pistolet fired (official onAbilityStart.AddHPDebts); no hit required.
     * Do NOT gate the grant on the isDodgeHeal window: repeated Impale casts keep refreshing it, which
     * would stop every later Swift Hunt from ever granting BoL.
     */
    public static void onAbilityAction(Ability ability, GameEntity target) {
        EntityAvatar avatar = resolveAvatar(ability, target);
        if (!isClorinde(avatar) || !isNightVigil(avatar)) {
            return;
        }
        String name = abilityName(ability);
        if (name.contains("ElementalArt_Dodge") && !name.contains("GunShot")) {
            return;
        }
        if (name.contains("ElementalArt_GunShot") || name.contains("GunShot")) {
            onSwiftHuntCast(avatar, "gunshot-action");
        }
    }

    /**
     * The Swift Hunt animation modifier (ShotGun_Attack*) attaching means the shot fired; no hit required.
     */
    public static void onModifierChange(Player player, AbilityInvokeEntry entry) {
        if (player == null || entry == null || entry.getAbilityData() == null) {
            return;
        }
        EntityAvatar avatar = player.getTeamManager().getCurrentAvatarEntity();
        if (!isClorinde(avatar) || !isNightVigil(avatar)) {
            return;
        }
        try {
            AbilityMetaModifierChange meta =
                    AbilityMetaModifierChange.parseFrom(entry.getAbilityData());
            if (meta == null || meta.getAction() != ModifierAction.ModifierAction_ADDED) {
                return;
            }
            String abilityName = resolveAbilityString(meta.getParentAbilityName());
            if (abilityName == null) {
                abilityName = resolveAbilityString(meta.getParentAbilityOverride());
            }
            if (abilityName == null) {
                return;
            }
            if (abilityName.contains("ElementalArt_Dodge") && !abilityName.contains("GunShot")) {
                return;
            }
            if (abilityName.contains("ElementalArt_GunShot") || abilityName.contains("GunShot")) {
                onSwiftHuntCast(avatar, "mod-gunshot");
                return;
            }
            if (!abilityName.contains("ElementalArt_Activate")
                    && !abilityName.contains("ElementalArt")) {
                return;
            }
            String modName = resolveModifierName(abilityName, meta.getModifierLocalId());
            if (modName == null) {
                return;
            }
            String lower = modName.toLowerCase();
            if (lower.contains("shotgun_attack")
                    || lower.contains("skillobj_ani_shotgun_attack")
                    || lower.contains("elementalart_attack")) {
                onSwiftHuntCast(avatar, "mod-" + modName);
            }
        } catch (Exception e) {
            Grasscutter.getLogger().debug("[BoL][Clorinde] onModifierChange fail", e);
        }
    }

    /** Hit fallback for builds where the GunShot action is not reported. Shares the fire ICD to avoid double grants. */
    public static boolean isSwiftHuntHit(AttackResult attackResult) {
        if (attackResult == null) {
            return true;
        }
        String anim = attackResult.getAnimEventId();
        if (anim == null || anim.isEmpty()) {
            return true;
        }
        String lower = anim.toLowerCase();
        if (lower.contains("dodge")
                || lower.contains("burst")
                || lower.contains("extraattack")
                || lower.contains("plunge")
                || lower.contains("elementalburst")) {
            return false;
        }
        return true;
    }

    public static void onCombatHit(EntityAvatar avatar, AttackResult attackResult) {
        if (!isClorinde(avatar) || !isNightVigil(avatar)) {
            return;
        }
        if (!isSwiftHuntHit(attackResult)) {
            return;
        }
        onSwiftHuntCast(avatar, "hit-fallback");
    }

    public static void onNightVigilShot(EntityAvatar avatar) {
        onSwiftHuntCast(avatar, "gunshot");
    }

    public static void onSwiftHuntCast(EntityAvatar avatar, String reason) {
        if (!isClorinde(avatar) || !isNightVigil(avatar)) {
            return;
        }
        // The Impale window no longer gates grants; repeated casts refresh it and would starve Swift Hunt.
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 1f) {
            return;
        }
        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        // BoL already full: no further grant, but the tier must be kept or the client stops recognising
        // the enhanced normal attack.
        if (bol + 0.5f >= maxHp) {
            syncClorindeDebtTiers(avatar, bol, maxHp, true);
            return;
        }
        int eid = avatar.getId();
        long now = System.currentTimeMillis();
        // executeAction runs concurrently on the pool, so the ICD must be synchronised or one shot double-grants.
        synchronized (LAST_SHOT_GRANT_MS) {
            if (LAST_SHOT_GRANT_MS.containsKey(eid)
                    && now - LAST_SHOT_GRANT_MS.get(eid) < SHOT_ICD_MS) {
                return;
            }
            LAST_SHOT_GRANT_MS.put(eid, now);
        }
        grant(avatar, SHOT_RATIO, reason);
    }

    public static boolean handleAddHpDebts(
            Ability ability, AbilityModifierAction action, GameEntity target) {
        EntityAvatar avatar = resolveAvatar(ability, target);
        if (!isClorinde(avatar)) {
            return false;
        }
        String name = abilityName(ability);
        if (name.contains("ElementalArt_GunShot") || name.contains("GunShot")) {
            onSwiftHuntCast(avatar, "addhpdebts");
            return true;
        }
        if (name.contains("ElementalBurst")) {
            return true;
        }
        return false;
    }

    public static boolean handleReduceHpDebts(Ability ability, GameEntity target) {
        EntityAvatar avatar = resolveAvatar(ability, target);
        if (!isClorinde(avatar)) {
            return false;
        }
        if (!isNightVigil(avatar)) {
            return false;
        }
        String name = abilityName(ability);
        boolean dodge =
                name.contains("Dodge")
                        || name.contains("ElementalArt_Heal")
                        || isDodgeHeal(avatar);
        if (!dodge) {
            Grasscutter.getLogger().info("[BoL][Clorinde] block ReduceHPDebts in vigil name=" + name);
            return true;
        }
        return false;
    }

    public static boolean handleHealHp(
            Ability ability, AbilityModifierAction action, GameEntity target) {
        // Vigil conversion and Impale healing key off whether the HEAL TARGET is Clorinde, not the ability owner.
        EntityAvatar avatar = resolveClorindeHealTarget(ability, target);
        if (!isClorinde(avatar)) {
            return false;
        }
        String name = abilityName(ability);
        String healTag = action != null ? action.healTag : null;
        boolean dodgeHeal =
                "Clorinde_ElementalArt_Heal".equals(healTag)
                        || name.contains("ElementalArt_Dodge")
                        || name.contains("ElementalArt_Heal")
                        || isDodgeHeal(avatar);

        if (dodgeHeal) {
            return executeDodgeHeal(ability, action, avatar, "healhp");
        }

        if (isNightVigil(avatar)) {
            float amount = resolveHealAmount(ability, action, avatar);
            float ratio = healToBolRatio(avatar);
            float add = Math.max(0f, amount) * ratio;
            if (add > 0.5f) {
                float before = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
                float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
                float next = Math.min(before + add, maxHp * 2f);
                pushBoL(
                        avatar,
                        next,
                        _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
                Grasscutter.getLogger()
                        .info(
                                "[BoL][Clorinde] heal->BoL amount="
                                        + amount
                                        + " ratio="
                                        + ratio
                                        + " "
                                        + before
                                        + " -> "
                                        + next);
            }
            return true;
        }

        return false;
    }

    /** Settles directly when skill 10983 is reported. Shares the HealHP ICD to avoid settling twice. */
    private static void tryDodgeHealFromSkill(EntityAvatar avatar) {
        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (bol <= 0.5f) {
            Grasscutter.getLogger().info("[BoL][Clorinde] Impale skill - no BoL to heal");
            return;
        }
        executeDodgeHeal(null, null, avatar, "skill-10983");
    }

    /**
     * Impale the Night: heal amount = current BoL x the HasDebt/FullDebt ratio (104%/110% from the passive).
     * Officially the BoL is not cleared separately; the heal simply exceeds it, and {@link GameEntity#heal}
     * pays the BoL down first and then restores HP.
     * This must avoid ActionHealHP's x0.8 path under ForbidFoodHeal/convertToHpDebt, which pays down BoL
     * without restoring any HP.
     */
    private static boolean executeDodgeHeal(
            Ability ability, AbilityModifierAction action, EntityAvatar avatar, String reason) {
        int eid = avatar.getId();
        long now = System.currentTimeMillis();
        synchronized (LAST_DODGE_HEAL_MS) {
            if (LAST_DODGE_HEAL_MS.containsKey(eid)
                    && now - LAST_DODGE_HEAL_MS.get(eid) < DODGE_HEAL_ICD_MS) {
                return true;
            }
            LAST_DODGE_HEAL_MS.put(eid, now);
        }

        DODGE_UNTIL_MS.put(eid, now + DODGE_WINDOW_MS);
        avatar.setConvertToHpDebt(false);
        clearForbidFoodHeal(avatar);

        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float ratio = resolveDodgeHealRatio(ability, bol, maxHp);
        float expected = Math.max(0f, bol) * ratio;

        // The config stack often computes 0 or too little because FullDebtHealHpRatio is not injected;
        // Impale the Night uses BoL x ratio as the source of truth.
        float healAmount = resolveHealAmount(ability, action, avatar);
        if (healAmount < expected * 0.5f) {
            healAmount = expected;
        }

        // The opening tier must use the pre-heal BoL. Setting DodgeEnhanced=0 right after the BoL is paid
        // down makes the client fall back to the weak Impale.
        float castTier = debtTier(bol, maxHp);
        syncClorindeDebtTiers(avatar, bol, maxHp, true);

        boolean mute = action != null && action.muteHealEffect;
        float realHp = healAmount > 0.5f ? avatar.heal(healAmount, mute) : 0f;
        float bolAfter = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (bolAfter > 0.5f && healAmount + 0.5f >= bol) {
            bolAfter = 0f;
        }
        pushBoL(
                avatar,
                bolAfter,
                bolAfter <= 0.5f
                        ? _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH
                        : _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY,
                castTier);

        Grasscutter.getLogger()
                .info(
                        "[BoL][Clorinde] Impale heal ("
                                + reason
                                + ") amount="
                                + healAmount
                                + " (bol="
                                + bol
                                + "x"
                                + ratio
                                + ") realHp="
                                + realHp
                                + " bol "
                                + bol
                                + " -> "
                                + bolAfter
                                + " keepTier="
                                + castTier);
        return true;
    }

    /** 0 = no BoL, 1 = partial BoL, 2 = BoL &ge; 100% MaxHP (maps to DodgeFlag_Rank / enhanced Impale). */
    private static float debtTier(float bol, float maxHp) {
        if (bol <= 0.5f || maxHp <= 1f) {
            return 0f;
        }
        return bol + 0.5f >= maxHp ? 2f : 1f;
    }

    /**
     * Pushes Clorinde's enhancement tier. force=true recomputes from bol even inside the Impale window.
     * When bol is about 0 after Impale pays it down, the opening tier is briefly kept for the animation;
     * as soon as BoL starts stacking again the tier follows the real value, so the UI cannot show a full-BoL
     * enhancement while the server only has 35%/70%.
     */
    private static void syncClorindeDebtTiers(
            EntityAvatar avatar, float bol, float maxHp, boolean force) {
        if (avatar == null || avatar.getGlobalAbilityValues() == null) {
            return;
        }
        float tier = debtTier(bol, maxHp);
        boolean inDodge =
                DODGE_UNTIL_MS.getOrDefault(avatar.getId(), 0L) > System.currentTimeMillis();
        if (!force && inDodge) {
            float cur = globalFlag(avatar, DODGE_ENHANCED);
            // Empty right after payback: keep the animation tier. Already restacking: follow the real bol,
            // never a fake full value.
            if (bol <= 0.5f && tier + 0.1f < cur) {
                return;
            }
        }
        pushGlobalFloat(avatar, HP_DEBTS_ENHANCED, tier);
        pushGlobalFloat(avatar, DODGE_ENHANCED, tier);
        // The full-BoL High tier also needs HealFlag==0; stuck at 1 it fails High and never reaches Medium.
        if (tier >= 1f) {
            pushGlobalFloat(avatar, DODGE_HEAL_FLAG, 0f);
        }
    }

    private static void syncClorindeDebtTiersKeep(EntityAvatar avatar, float keepTier) {
        if (avatar == null || avatar.getGlobalAbilityValues() == null) {
            return;
        }
        float tier = Math.max(0f, keepTier);
        pushGlobalFloat(avatar, HP_DEBTS_ENHANCED, tier);
        pushGlobalFloat(avatar, DODGE_ENHANCED, tier);
        // Keep HealFlag=0 after the opening until the client's own High branch sets it to 1.
        if (tier >= 1f) {
            pushGlobalFloat(avatar, DODGE_HEAL_FLAG, 0f);
        }
    }

    /** SGV plus Ability META_GLOBAL_FLOAT so the client's ByTargetGlobalValue can read it. */
    private static void pushGlobalFloat(EntityAvatar avatar, String key, float value) {
        if (avatar == null || key == null || key.isEmpty()) {
            return;
        }
        if (avatar.getGlobalAbilityValues() != null) {
            avatar.getGlobalAbilityValues().put(key, Float.valueOf(value));
        }
        try {
            avatar.onAbilityValueUpdate();
        } catch (Exception ignored) {
        }
        Player player = avatar.getPlayer();
        if (player == null) {
            return;
        }
        try {
            player.sendPacket(new PacketServerGlobalValueChangeNotify(avatar, key, value));
            if (avatar.getScene() != null) {
                avatar.getScene()
                        .broadcastPacket(new PacketServerGlobalValueChangeNotify(avatar, key, value));
            }
            AbilityScalarValueEntry entry =
                    AbilityScalarValueEntry.newBuilder()
                            .setKey(
                                    AbilityString.newBuilder()
                                            .setStr(key)
                                            .setHash(Utils.abilityHash(key))
                                            .build())
                            .setFloatValue(value)
                            .build();
            AbilityInvokeEntry invoke =
                    AbilityInvokeEntry.newBuilder()
                            .setEntityId(avatar.getId())
                            .setArgumentType(
                                    AbilityInvokeArgument
                                            .AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                            .setForwardType(ForwardType.ForwardType_FORWARD_TO_ALL)
                            .setAbilityData(entry.toByteString())
                            .build();
            player.sendPacket(new PacketAbilityInvocationsNotify(invoke));
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .debug("[BoL][Clorinde] pushGlobalFloat {}={} fail: {}", key, value, e.toString());
        }
    }

    private static float resolveDodgeHealRatio(Ability ability, float bol, float maxHp) {
        float hasDebt = DODGE_HEAL_RATIO_HAS_DEBT;
        float fullDebt = DODGE_HEAL_RATIO_FULL_DEBT;
        if (ability != null && ability.getAbilitySpecials() != null) {
            float h = ability.getAbilitySpecials().getOrDefault("HasDebtHealHpRatio", 0f);
            float f = ability.getAbilitySpecials().getOrDefault("FullDebtHealHpRatio", 0f);
            if (h > 0.01f) {
                hasDebt = h;
            }
            if (f > 0.01f) {
                fullDebt = f;
            }
        }
        return bol + 0.5f >= maxHp ? fullDebt : hasDebt;
    }

    private static void clearForbidFoodHeal(EntityAvatar avatar) {
        if (avatar.getGlobalAbilityValues() == null) {
            return;
        }
        Float forbid = avatar.getGlobalAbilityValues().get(FORBID_FOOD_HEAL);
        if (forbid != null && forbid > 0f) {
            avatar.getGlobalAbilityValues().put(FORBID_FOOD_HEAL, Float.valueOf(0f));
        }
    }

    /** Prefer the target when the heal target is Clorinde; otherwise fall back to resolving the caster. */
    private static EntityAvatar resolveClorindeHealTarget(Ability ability, GameEntity target) {
        if (target instanceof EntityAvatar ea && isClorinde(ea)) {
            return ea;
        }
        return resolveAvatar(ability, target);
    }

    private static float resolveHealAmount(
            Ability ability, AbilityModifierAction action, EntityAvatar avatar) {
        try {
            Object2FloatOpenHashMap<String> props = new Object2FloatOpenHashMap<>();
            for (FightProperty fp : FightProperty.values()) {
                props.put(fp.name(), avatar.getFightProperty(fp));
            }
            if (ability != null) {
                props.putAll(ability.getAbilitySpecials());
            }
            if (action != null && action.amount != null) {
                return action.amount.get(props, 0f);
            }
            if (action != null && action.ratio != null) {
                return action.ratio.get(props, 0f);
            }
        } catch (Exception ignored) {
        }
        return 0f;
    }

    private static float healToBolRatio(EntityAvatar avatar) {
        if (avatar.getAvatar() != null) {
            try {
                if (avatar.getAvatar().getPromoteLevel() >= 4) {
                    return 1.0f;
                }
            } catch (Exception ignored) {
            }
        }
        return HEAL_TO_BOL_RATIO;
    }

    public static boolean shouldSkipHealBoLPay(EntityAvatar avatar) {
        return isClorinde(avatar) && isNightVigil(avatar) && !isDodgeHeal(avatar);
    }

    public static void beforeHeal(GameEntity entity) {
        HEAL_SAVED_BOL.remove();
        if (!(entity instanceof EntityAvatar avatar) || !shouldSkipHealBoLPay(avatar)) {
            return;
        }
        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        HEAL_SAVED_BOL.set(bol);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0f);
    }

    public static void afterHeal(GameEntity entity) {
        Float saved = HEAL_SAVED_BOL.get();
        HEAL_SAVED_BOL.remove();
        if (saved == null || !(entity instanceof EntityAvatar avatar)) {
            return;
        }
        pushBoL(
                avatar,
                saved,
                _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
    }

    public static float burstBondRatio(Avatar avatar) {
        int level = 1;
        if (avatar != null && avatar.getSkillLevelMap() != null) {
            level = avatar.getSkillLevelMap().getOrDefault(SKILL_Q, 1);
        }
        if (level < 1) {
            level = 1;
        }
        if (level > 15) {
            level = 15;
        }
        return 0.60f + 0.06f * level;
    }

    public static void grant(EntityAvatar avatar, float ratioOfMaxHp, String reason) {
        if (avatar == null || ratioOfMaxHp <= 0f) {
            return;
        }
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float before = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float ask = maxHp * ratioOfMaxHp;
        float next = Math.min(before + ask, maxHp * 2f);
        float gained = next - before;
        if (gained <= 0.01f) {
            return;
        }
        pushBoL(avatar, next, _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
        Grasscutter.getLogger()
                .info(
                        "[BoL][Clorinde] grant "
                                + reason
                                + " ratio="
                                + ratioOfMaxHp
                                + " "
                                + before
                                + " -> "
                                + next
                                + " (+"
                                + gained
                                + ")");
    }

    /**
     * Clorinde BoL sync: push only FIGHT_PROP_CUR_HP_DEBTS (the BoL bar) plus her own tier GV.
     * Does not use Arlecchino's Cur_HPDebts/_HPDebts, and does not send AvatarFightPropNotify - that strips
     * HP_DEBTS and the bar only reappears after switching characters.
     */
    public static void pushBoL(EntityAvatar avatar, float debts, _ChangeHpDebtsReason reason) {
        pushBoL(avatar, debts, reason, Float.NaN);
    }

    /**
     * @param keepTier NaN recomputes the tier from debts; a value keeps the opening tier after Impale pays
     *     the BoL down, while animations and VFX are still reading DodgeEnhanced
     */
    public static void pushBoL(
            EntityAvatar avatar, float debts, _ChangeHpDebtsReason reason, float keepTier) {
        if (avatar == null) {
            return;
        }
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float capped = Math.min(Math.max(debts, 0f), maxHp * 2f);
        float prev = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, capped);
        float change = capped - prev;

        PacketEntityFightPropUpdateNotify update =
                new PacketEntityFightPropUpdateNotify(
                        avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        PacketAvatarFightPropUpdateNotify avatarPkt =
                avatar.getAvatar() != null
                        ? new PacketAvatarFightPropUpdateNotify(
                                avatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP_DEBTS)
                        : null;
        Player player = avatar.getPlayer();
        if (player != null) {
            player.sendPacket(update);
            if (avatarPkt != null) {
                player.sendPacket(avatarPkt);
            }
        }
        if (avatar.getScene() != null) {
            avatar.getScene().broadcastPacket(update);
            if (Math.abs(change) >= 0.5f) {
                avatar.getScene()
                        .broadcastPacket(
                                new PacketEntityFightPropChangeReasonNotify(
                                        avatar,
                                        FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                                        Float.valueOf(change),
                                        PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                                        reason));
            }
        } else if (avatar.getWorld() != null) {
            avatar.getWorld().broadcastPacket(update);
        }

        if (!Float.isNaN(keepTier)) {
            syncClorindeDebtTiersKeep(avatar, keepTier);
        } else {
            syncClorindeDebtTiers(avatar, capped, maxHp, false);
        }
    }

    private static String abilityName(Ability ability) {
        if (ability == null || ability.getData() == null || ability.getData().abilityName == null) {
            return "";
        }
        return ability.getData().abilityName;
    }

    private static String resolveAbilityString(AbilityString abs) {
        if (abs == null) {
            return null;
        }
        if (abs.hasStr()) {
            return abs.getStr();
        }
        if (abs.getHash() != 0) {
            String s = GameData.getAbilityHashes().get(abs.getHash());
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private static String resolveModifierName(String abilityName, int modifierLocalId) {
        try {
            AbilityData data = GameData.getAbilityData(abilityName);
            if (data == null || data.modifiers == null || data.modifiers.isEmpty()) {
                return null;
            }
            // modifiers is usually a LinkedHashMap, so take the Nth entry in insertion order.
            int i = 0;
            for (var e : data.modifiers.entrySet()) {
                if (i == modifierLocalId) {
                    return e.getKey();
                }
                i++;
            }
            // In some versions localId corresponds to a field inside AbilityModifier; try scanning by value.
            for (var e : data.modifiers.entrySet()) {
                AbilityModifier mod = e.getValue();
                if (mod != null) {
                    try {
                        var f = mod.getClass().getField("localId");
                        Object v = f.get(mod);
                        if (v instanceof Integer id && id.intValue() == modifierLocalId) {
                            return e.getKey();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static EntityAvatar resolveAvatar(Ability ability, GameEntity target) {
        GameEntity entity = ability != null ? ability.getOwner() : null;
        if (entity instanceof EntityWeapon && ability != null && ability.getPlayerOwner() != null) {
            entity = ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        }
        if (entity == null) {
            entity = target;
        }
        if (entity instanceof EntityWeapon && ability != null && ability.getPlayerOwner() != null) {
            entity = ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        }
        return entity instanceof EntityAvatar ea ? ea : null;
    }

    /** Clears ICD / vigil / dodge windows that otherwise outlive a session. */
    public static void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        clearEntityState(player.getUid());
        if (player.getTeamManager() != null) {
            for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
                if (avatar != null) {
                    clearEntityState(avatar.getId());
                }
            }
        }
    }

    public static void clearEntityState(int entityId) {
        LAST_SHOT_GRANT_MS.remove(entityId);
        LAST_DODGE_HEAL_MS.remove(entityId);
        VIGIL_UNTIL_MS.remove(entityId);
        DODGE_UNTIL_MS.remove(entityId);
    }
}
