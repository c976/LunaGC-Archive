/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.ClorindeBoLUtil;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass;
import emu.grasscutter.net.proto.AbilityStringOuterClass;
import emu.grasscutter.net.proto.AttackResultOuterClass;
import emu.grasscutter.net.proto.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.ChangHpReasonOuterClass;
import emu.grasscutter.net.proto.ModifierActionOuterClass;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMaps;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ArlecchinoBoLUtil {
    public static final int ARLECCHINO_AVATAR_ID = 10000096;
    public static final int CLORINDE_AVATAR_ID = 10000098;
    public static final int ARLECCHINO_CHARGED_SKILL_ID = 10961;
    public static final int ARLECCHINO_NORMAL_SKILL_ID = 10966;
    public static final int ARLECCHINO_ESKILL_ID = 10962;
    public static final int ARLECCHINO_BURST_SKILL_ID = 10965;
    public static final int ARLECCHINO_C4_TALENT_ID = 964;
    public static final float BOL_HARD_CAP_RATIO = 2.0f;
    public static final float RED_DEATH_RATIO = 0.3f;
    public static final float NA_REDUCE_RATIO = 0.075f;
    public static final float MARK_L1_RATIO = 0.65f;
    public static final float MARK_L2_RATIO = 1.3f;
    public static final float MARK_GRANT_WINDOW_CAP_RATIO = 1.45f;
    public static final long MARK_GRANT_WINDOW_MS = 35000L;
    public static final long MARK_RECYCLE_WINDOW_MS = 3000L;
    public static final long NA_REDUCE_ICD_MS = 30L;
    public static final long SKILL_HIT_BLOCK_MS = 2500L;
    private static final ConcurrentHashMap<Integer, Long> SKILL_HIT_BLOCK_UNTIL = new ConcurrentHashMap();
    public static final long BURST_PENDING_TIMEOUT_MS = 4000L;
    public static final long BURST_MIN_CAST_MS = 1200L;
    private static final ConcurrentHashMap<Integer, Long> BURST_PENDING_SINCE = new ConcurrentHashMap();
    public static final float C4_ENERGY = 15.0f;
    public static final long C4_ICD_MS = 10000L;
    public static final String BURST_ATTACK_ABILITY = "Avatar_Arlecchino_ElementalBurst_Attack";
    public static final float BURST_HEAL_DEBT_RATIO = 1.5f;
    public static final float BURST_HEAL_ATK_RATIO = 1.5f;
    public static final long GATE_MIRROR_MS = 3400L;
    public static final long CHARGED_HIT_WINDOW_MS = 3000L;
    public static final long E_HIT_CONFIRM_MS = 2000L;
    public static final long DIRECTIVE_DURATION_MS = 30000L;
    public static final int E_ROUND_MAX_TARGETS = 10;
    public static final long BURST_RECYCLE_GRACE_MS = 750L;
    public static final int BLOOD_MOON_WEAPON_ID = 13512;
    public static final float WEAPON_BOL_RATIO = 0.25f;
    public static final long WEAPON_BOL_CD_MS = 14000L;
    public static final int ARLECCHINO_A1_TALENT_ID = 961;
    public static final int ARLECCHINO_C2_TALENT_ID = 962;
    private static final Int2LongMap LAST_NA_REDUCE_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap LAST_C4_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final ConcurrentHashMap<Integer, Long> MARK_RECYCLE_UNTIL = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Long> E_GRANT_UNTIL = new ConcurrentHashMap();
    private static final Int2FloatMap E_GRANT_ACCUM = Int2FloatMaps.synchronize(new Int2FloatOpenHashMap());
    private static final ConcurrentHashMap<Integer, Long> LAST_E_SKILL_MS = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Long> LAST_CA_SKILL_MS = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Long> LAST_SERVER_RECYCLE_MS = new ConcurrentHashMap();
    private static final long E_PLACEMENT_GUARD_MS = 1500L;
    private static final long SERVER_RECYCLE_ICD_MS = 1200L;
    private static final long MARK_UPGRADE_MS = 5000L;
    private static final ConcurrentHashMap<Integer, Long> GATE_MIRROR_UNTIL = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Boolean> GATE_TAKEN_OVER = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Long> WEAPON_BOL_CD_UNTIL = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Long> RECENT_MARK_CLEAR_MS = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, ESkillRound> E_SKILL_ROUNDS = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Integer> PENDING_MARK_CHARGES = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Boolean> BURST_PENDING = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Long> BURST_POST_CLEAR_UNTIL = new ConcurrentHashMap();
    private static final long BURST_POST_CLEAR_MS = 2500L;
    private static final ConcurrentHashMap<Integer, Boolean> BURST_DONE = new ConcurrentHashMap();
    private static final Int2FloatMap LAST_BURST_SNAP = Int2FloatMaps.synchronize(new Int2FloatOpenHashMap());
    private static final Int2FloatMap BURST_PENDING_SNAP = Int2FloatMaps.synchronize(new Int2FloatOpenHashMap());
    private static final ConcurrentHashMap<Integer, EntityAvatar> BURST_PENDING_AVATAR = new ConcurrentHashMap();
    private static final Int2FloatMap AUTHORITATIVE_BOL = Int2FloatMaps.synchronize(new Int2FloatOpenHashMap());
    private static final ThreadLocal<Boolean> ALLOW_BOL_MUTATE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> TRUST_BOL_INCREASE = ThreadLocal.withInitial(() -> false);
    private static final Int2LongMap LAST_DRAIN_RESYNC_LOG_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final long NA_SETTLE_MS = 800L;
    private static final ConcurrentHashMap<Integer, Long> NA_SETTLE_UNTIL = new ConcurrentHashMap();
    private static final long CA_NO_CONSUME_MS = 600L;
    private static final ConcurrentHashMap<Integer, Long> CA_NO_CONSUME_UNTIL = new ConcurrentHashMap();
    private static final long FIRE_ATTACK_REDUCE_WINDOW_MS = 250L;
    private static final ConcurrentHashMap<Integer, Long> FIRE_ATTACK_REDUCE_UNTIL = new ConcurrentHashMap();
    public static final String BURST_HEAL_TAG = "Arlecchino_ElementalBurst_Heal";
    public static final String FIRE_ATTACK_REDUCE_MODIFIER = "Avatar_Arlecchino_FireAttack_ReduceHPDebts";
    public static final String CLEAR_HP_DEBTS_MARK_HANDLER = "Arlecchino_Clear_HPDebtsMark_Handler";
    public static final String EXTRA_ATTACK_ADD_HP_DEBTS_1 = "UNIQUE_Avatar_Arlecchino_ExtraAttack_AddHpDebts_1";
    public static final String EXTRA_ATTACK_ADD_HP_DEBTS_2 = "UNIQUE_Avatar_Arlecchino_ExtraAttack_AddHpDebts_2";
    private static final ConcurrentHashMap<Integer, Integer> BOL_KEEPALIVE_TASK = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, EntityAvatar> BOL_KEEPALIVE_AVATAR = new ConcurrentHashMap();
    private static final Int2LongMap LAST_FORCE_PUSH_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final Int2LongMap LAST_CLORINDE_HIT_LOG_MS = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private static final String[] OFFICIAL_NA_ANIM_EVENTS = new String[]{"ATK01_Plus", "ATK02_Plus", "ATK03_Plus", "ATK04_Plus", "ATK04_1_Plus", "ATK04_2_Plus", "ATK05_Plus", "ATK06_Plus", "Arlecchino_Attack01_Plus", "Arlecchino_Attack02_Plus", "Arlecchino_Attack03_Plus", "Arlecchino_Attack04_Plus", "Arlecchino_Attack05_Plus", "Arlecchino_Attack06_Plus"};
    private static final String[] OFFICIAL_CA_ANIM_EVENTS = new String[]{"ExtraAttack", "ExtraAttack_Plus", "Arlecchino_ExtraAttack", "Arlecchino_ExtraAttack_Plus"};

    private ArlecchinoBoLUtil() {
    }

    /** Releases combat state that is indexed by a player or one of their scene entities. */
    public static void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }

        List<Integer> keys = new ArrayList<>();
        keys.add(player.getUid());
        if (player.getTeamManager() != null) {
            for (EntityAvatar entityAvatar : new ArrayList<>(player.getTeamManager().getActiveTeam())) {
                if (entityAvatar != null) {
                    keys.add(entityAvatar.getId());
                }
            }
        }
        keys.forEach(ArlecchinoBoLUtil::clearStateKey);
    }

    public static void clearEntityState(int entityId) {
        clearStateKey(entityId);
    }

    private static void clearStateKey(int key) {
        SKILL_HIT_BLOCK_UNTIL.remove(key);
        BURST_PENDING_SINCE.remove(key);
        MARK_RECYCLE_UNTIL.remove(key);
        E_GRANT_UNTIL.remove(key);
        LAST_E_SKILL_MS.remove(key);
        LAST_CA_SKILL_MS.remove(key);
        LAST_SERVER_RECYCLE_MS.remove(key);
        GATE_MIRROR_UNTIL.remove(key);
        GATE_TAKEN_OVER.remove(key);
        WEAPON_BOL_CD_UNTIL.remove(key);
        RECENT_MARK_CLEAR_MS.remove(key);
        E_SKILL_ROUNDS.remove(key);
        PENDING_MARK_CHARGES.remove(key);
        BURST_PENDING.remove(key);
        BURST_POST_CLEAR_UNTIL.remove(key);
        BURST_DONE.remove(key);
        BURST_PENDING_AVATAR.remove(key);
        NA_SETTLE_UNTIL.remove(key);
        CA_NO_CONSUME_UNTIL.remove(key);
        FIRE_ATTACK_REDUCE_UNTIL.remove(key);
        synchronized (LAST_NA_REDUCE_MS) {
            LAST_NA_REDUCE_MS.remove(key);
        }
        synchronized (LAST_C4_MS) {
            LAST_C4_MS.remove(key);
        }
        synchronized (E_GRANT_ACCUM) {
            E_GRANT_ACCUM.remove(key);
        }
        synchronized (LAST_BURST_SNAP) {
            LAST_BURST_SNAP.remove(key);
        }
        synchronized (BURST_PENDING_SNAP) {
            BURST_PENDING_SNAP.remove(key);
        }
        synchronized (AUTHORITATIVE_BOL) {
            AUTHORITATIVE_BOL.remove(key);
        }
        synchronized (LAST_DRAIN_RESYNC_LOG_MS) {
            LAST_DRAIN_RESYNC_LOG_MS.remove(key);
        }
        synchronized (LAST_FORCE_PUSH_MS) {
            LAST_FORCE_PUSH_MS.remove(key);
        }
        synchronized (LAST_CLORINDE_HIT_LOG_MS) {
            LAST_CLORINDE_HIT_LOG_MS.remove(key);
        }
        cancelBoLKeepalive(key);
    }

    private static void markGateMutation(int n, float f) {
        GATE_TAKEN_OVER.put(n, true);
        GATE_MIRROR_UNTIL.put(n, System.currentTimeMillis() + 3400L);
        ArlecchinoBoLUtil.recordAuthoritativeBoL(n, f);
    }

    private static boolean isGateMirroring(int n) {
        Long l = GATE_MIRROR_UNTIL.get(n);
        return l != null && System.currentTimeMillis() < l;
    }

    private static ESkillRound getOrCreateRound(int n2) {
        return E_SKILL_ROUNDS.computeIfAbsent(n2, n -> new ESkillRound());
    }

    private static boolean hasA1(Avatar avatar) {
        return avatar != null && avatar.getTalentIdList().contains(961);
    }

    private static boolean hasC2(Avatar avatar) {
        return avatar != null && avatar.getTalentIdList().contains(962);
    }

    private static int resolveDirectiveStartLevel(EntityAvatar entityAvatar, int n) {
        Avatar avatar;
        Avatar avatar2 = avatar = entityAvatar != null ? entityAvatar.getAvatar() : null;
        if (ArlecchinoBoLUtil.hasC2(avatar)) {
            return 2;
        }
        return 1;
    }

    private static int resolveDirectiveRecycleLevel(EntityAvatar entityAvatar, DirectiveEntry directiveEntry) {
        long l;
        Avatar avatar;
        Avatar avatar2 = avatar = entityAvatar != null ? entityAvatar.getAvatar() : null;
        if (ArlecchinoBoLUtil.hasC2(avatar)) {
            return 2;
        }
        if (directiveEntry == null) {
            return 1;
        }
        if (ArlecchinoBoLUtil.hasA1(avatar) && (l = System.currentTimeMillis() - directiveEntry.placedAtMs) >= 5000L) {
            return 2;
        }
        return 1;
    }

    private static int resolveAndConsumeRecycleLevel(Player player, EntityAvatar entityAvatar, int n) {
        Avatar avatar = entityAvatar != null ? entityAvatar.getAvatar() : null;
        DirectiveEntry directiveEntry = ArlecchinoBoLUtil.popOneDirective(player);
        if (directiveEntry == null) {
            Grasscutter.getLogger().info("[BoL] mark-clear skip (no ledger left, already reclaimed?) clientClaimed=" + n);
            return 0;
        }
        int n2 = ArlecchinoBoLUtil.hasC2(avatar) ? 2 : ArlecchinoBoLUtil.resolveDirectiveRecycleLevel(entityAvatar, directiveEntry);
        Grasscutter.getLogger().info("[BoL] recycle from ledger ageMs=" + (System.currentTimeMillis() - directiveEntry.placedAtMs) + " -> lv=" + n2 + " (clientClaimed=" + n + ")");
        return n2;
    }

    private static DirectiveEntry popOneDirective(Player player) {
        if (player == null) {
            return null;
        }
        ESkillRound eSkillRound = E_SKILL_ROUNDS.get(player.getUid());
        if (eSkillRound == null || eSkillRound.targets.isEmpty()) {
            return null;
        }
        Integer n = (Integer)((ConcurrentHashMap.KeySetView)eSkillRound.targets.keySet()).iterator().next();
        return eSkillRound.targets.remove(n);
    }

    public static void onDirectivePlaced(Player player, GameEntity gameEntity, int n) {
        if (player == null || gameEntity == null) {
            return;
        }
        EntityAvatar entityAvatar = player.getTeamManager().getCurrentAvatarEntity();
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return;
        }
        int n2 = player.getUid();
        long l = System.currentTimeMillis();
        Long l2 = LAST_SERVER_RECYCLE_MS.get(n2);
        Long l3 = LAST_E_SKILL_MS.get(n2);
        if (l2 != null && (l3 == null || l2 >= l3) && l - l2 < 1500L) {
            return;
        }
        if (!(ArlecchinoBoLUtil.isInEGrantWindow(player) || l3 != null && l - l3 <= 2000L)) {
            return;
        }
        ESkillRound eSkillRound = ArlecchinoBoLUtil.getOrCreateRound(n2);
        if (eSkillRound.targets.size() >= 10 && !eSkillRound.targets.containsKey(gameEntity.getId())) {
            return;
        }
        DirectiveEntry directiveEntry = eSkillRound.targets.get(gameEntity.getId());
        long l4 = directiveEntry != null ? directiveEntry.placedAtMs : l;
        Avatar avatar = entityAvatar.getAvatar();
        int n3 = ArlecchinoBoLUtil.hasC2(avatar) ? 2 : (ArlecchinoBoLUtil.hasA1(avatar) && l - l4 >= 5000L ? 2 : 1);
        eSkillRound.targets.put(gameEntity.getId(), new DirectiveEntry(n3, l4));
        Grasscutter.getLogger().info("[BoL] directive placed target=" + gameEntity.getId() + " lv=" + n3 + " ageMs=" + (l - l4) + " clientClaimed=" + n + " roundTargets=" + eSkillRound.targets.size());
    }

    private static void startESkillRound(Player player) {
        if (player == null) {
            return;
        }
        int n = player.getUid();
        long l = System.currentTimeMillis();
        ESkillRound eSkillRound = ArlecchinoBoLUtil.getOrCreateRound(n);
        eSkillRound.reset(l);
        LAST_E_SKILL_MS.put(n, l);
        MARK_RECYCLE_UNTIL.remove(n);
        LAST_CA_SKILL_MS.remove(n);
        ArlecchinoBoLUtil.openEGrantWindow(player);
        Grasscutter.getLogger().info("[BoL] E round started (ledger reset, no BoL yet)");
    }

    private static void recycleAllDirectivesNow(Player player, EntityAvatar entityAvatar, String string) {
        Serializable serializable;
        if (player == null || entityAvatar == null) {
            return;
        }
        int n = player.getUid();
        long l = System.currentTimeMillis();
        ESkillRound eSkillRound = E_SKILL_ROUNDS.get(n);
        if (eSkillRound == null || eSkillRound.targets.isEmpty()) {
            return;
        }
        if (string != null && !"Q".equals(string) && (serializable = LAST_SERVER_RECYCLE_MS.get(n)) != null && l - (Long)serializable < 1200L) {
            eSkillRound.targets.clear();
            Grasscutter.getLogger().info("[BoL] reclaim ICD skip (" + string + ") cleared leftover stamps");
            return;
        }
        serializable = new ArrayList<DirectiveEntry>(eSkillRound.targets.values());
        eSkillRound.targets.clear();
        LAST_SERVER_RECYCLE_MS.put(n, l);
        ArlecchinoBoLUtil.openMarkRecycleWindow(player);
        Grasscutter.getLogger().info("[BoL] blood-debt reclaim (" + string + ") count=" + ((ArrayList)serializable).size());
        Iterator iterator = ((ArrayList)serializable).iterator();
        while (iterator.hasNext()) {
            DirectiveEntry directiveEntry = (DirectiveEntry)iterator.next();
            int n2 = ArlecchinoBoLUtil.resolveDirectiveRecycleLevel(entityAvatar, directiveEntry);
            ArlecchinoBoLUtil.grantMarkBoLResolved(player, entityAvatar, n2, false);
        }
        float f = ArlecchinoBoLUtil.getAuthBoL(entityAvatar);
        ArlecchinoBoLUtil.forcePushBoL(entityAvatar, f, ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY, true);
        ArlecchinoBoLUtil.ensureBoLKeepalive(entityAvatar);
        ArlecchinoBoLUtil.pinBoLToClientUi(entityAvatar, f);
    }

    private static void pinBoLToClientUi(EntityAvatar entityAvatar, float f) {
        if (entityAvatar == null) {
            return;
        }
        Player player = entityAvatar.getPlayer();
        if (player == null) {
            return;
        }
        try {
            Map<String, Float> map = entityAvatar.getGlobalAbilityValues();
            if (map != null) {
                map.put("_HPDebts", Float.valueOf(f));
                map.put("Cur_HPDebts", Float.valueOf(f));
            }
            player.sendPacket(new PacketAbilityChangeNotify(entityAvatar));
            player.sendPacket(new PacketAvatarFightPropNotify(entityAvatar.getAvatar()));
            player.sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, "_HPDebts", f));
            player.sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, "Cur_HPDebts", f));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public static void onClearHpDebtsMarkHandler(Ability ability) {
        if (ability == null) {
            return;
        }
        Player player = ability.getPlayerOwner();
        EntityAvatar entityAvatar = null;
        if (player != null) {
            entityAvatar = player.getTeamManager().getCurrentAvatarEntity();
        }
        if (entityAvatar == null && ability.getOwner() instanceof EntityAvatar) {
            entityAvatar = (EntityAvatar)ability.getOwner();
        }
        if (entityAvatar == null && ability.getPlayerOwner() != null) {
            player = ability.getPlayerOwner();
            entityAvatar = player.getTeamManager().getCurrentAvatarEntity();
        }
        if (player == null || entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return;
        }
        ESkillRound eSkillRound = E_SKILL_ROUNDS.get(player.getUid());
        if (eSkillRound == null || eSkillRound.targets.isEmpty()) {
            ArlecchinoBoLUtil.pinBoLToClientUi(entityAvatar, ArlecchinoBoLUtil.getAuthBoL(entityAvatar));
            return;
        }
        ArlecchinoBoLUtil.openMarkRecycleWindow(player);
        ArlecchinoBoLUtil.recycleAllDirectivesNow(player, entityAvatar, "AddHpDebts-Apply");
    }

    public static void onArlecchinoModifierChange(Player player, AbilityInvokeEntryOuterClass.AbilityInvokeEntry abilityInvokeEntry) {
        block11: {
            if (player == null || abilityInvokeEntry == null || abilityInvokeEntry.getAbilityData() == null) {
                return;
            }
            EntityAvatar entityAvatar = player.getTeamManager().getCurrentAvatarEntity();
            if (entityAvatar == null || entityAvatar.getAvatar() == null || entityAvatar.getAvatar().getAvatarId() != 10000096) {
                return;
            }
            ESkillRound eSkillRound = E_SKILL_ROUNDS.get(player.getUid());
            boolean bl = eSkillRound != null && !eSkillRound.targets.isEmpty();
            Long l = LAST_E_SKILL_MS.get(player.getUid());
            long l2 = System.currentTimeMillis();
            boolean bl2 = l != null && l2 - l <= 35000L;
            boolean bl3 = bl || bl2;
            try {
                AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange abilityMetaModifierChange = AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange.parseFrom(abilityInvokeEntry.getAbilityData());
                if (abilityMetaModifierChange == null) {
                    if (bl3) {
                        Grasscutter.getLogger().info("[BoL] ModChange-diag parse=null");
                    }
                    return;
                }
                String string = ArlecchinoBoLUtil.resolveAbilityString(abilityMetaModifierChange.getParentAbilityName());
                String string2 = ArlecchinoBoLUtil.resolveAbilityString(abilityMetaModifierChange.getParentAbilityOverride());
                if (string == null) {
                    string = string2;
                }
                int n = abilityMetaModifierChange.getModifierLocalId();
                int n2 = abilityMetaModifierChange.getApplyEntityId();
                String string3 = ArlecchinoBoLUtil.describeAbilityString(abilityMetaModifierChange.getParentAbilityName()) + "|" + ArlecchinoBoLUtil.describeAbilityString(abilityMetaModifierChange.getParentAbilityOverride());
                if (bl3) {
                    int n3 = eSkillRound != null ? eSkillRound.targets.size() : 0;
                    Grasscutter.getLogger().info("[BoL] ModChange-diag action=" + String.valueOf(abilityMetaModifierChange.getAction()) + " ability=" + string + " modLocalId=" + n + " applyEntity=" + n2 + " abilityRaw=" + string3 + " ledger=" + n3);
                }
                if (!bl) {
                    return;
                }
                if (abilityMetaModifierChange.getAction() != ModifierActionOuterClass.ModifierAction.ModifierAction_ADDED) {
                    return;
                }
                String string4 = string;
                if (string4 == null || !string4.contains("Arlecchino_ExtraAttack") && !string4.contains("ExtraAttack")) {
                    return;
                }
                ArlecchinoBoLUtil.openMarkRecycleWindow(player);
                ArlecchinoBoLUtil.recycleAllDirectivesNow(player, entityAvatar, "ModChange-" + string4);
            }
            catch (Exception exception) {
                if (!bl3) break block11;
                Grasscutter.getLogger().info("[BoL] ModChange-diag exception=" + exception.getClass().getSimpleName());
            }
        }
    }

    private static String describeAbilityString(AbilityStringOuterClass.AbilityString abilityString) {
        if (abilityString == null) {
            return "-";
        }
        if (abilityString.hasStr()) {
            return "str:" + abilityString.getStr();
        }
        if (abilityString.getHash() != 0) {
            Object v = GameData.getAbilityHashes().get(abilityString.getHash());
            if (v instanceof String) {
                String string = (String)v;
                return "hash:" + abilityString.getHash() + "=" + string;
            }
            return "hash:" + abilityString.getHash() + "=?";
        }
        return "empty";
    }

    private static String resolveAbilityString(AbilityStringOuterClass.AbilityString abilityString) {
        Object v;
        if (abilityString == null) {
            return null;
        }
        if (abilityString.hasStr()) {
            return abilityString.getStr();
        }
        if (abilityString.getHash() != 0 && (v = GameData.getAbilityHashes().get(abilityString.getHash())) instanceof String) {
            String string = (String)v;
            return string;
        }
        return null;
    }

    public static boolean isExtraAttackAddHpDebtsModifier(String string) {
        if (string == null) {
            return false;
        }
        return EXTRA_ATTACK_ADD_HP_DEBTS_1.equals(string) || EXTRA_ATTACK_ADD_HP_DEBTS_2.equals(string) || string.contains("ExtraAttack_AddHpDebts");
    }

    private static void recycleAllDirectivesForBurst(Player player, EntityAvatar entityAvatar) {
        ArlecchinoBoLUtil.recycleAllDirectivesNow(player, entityAvatar, "Q");
    }

    public static boolean hasBloodMoonWeapon(EntityAvatar entityAvatar) {
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return false;
        }
        GameItem gameItem = entityAvatar.getAvatar().getWeapon();
        return gameItem != null && gameItem.getItemId() == 13512;
    }

    public static void tryBloodMoonWeaponBoL(EntityAvatar entityAvatar, AttackResultOuterClass.AttackResult attackResult) {
        if (entityAvatar == null || !ArlecchinoBoLUtil.looksLikeChargedAttack(attackResult)) {
            return;
        }
        if (!ArlecchinoBoLUtil.hasBloodMoonWeapon(entityAvatar)) {
            return;
        }
        int n = entityAvatar.getId();
        long l = System.currentTimeMillis();
        Long l2 = WEAPON_BOL_CD_UNTIL.get(n);
        if (l2 != null && l < l2) {
            return;
        }
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float f2 = f * 0.25f;
        if (f2 <= 0.0f) {
            return;
        }
        float f3 = ArlecchinoBoLUtil.getAuthBoL(entityAvatar);
        float f4 = Math.min(f3 + f2, ArlecchinoBoLUtil.getBolCap(entityAvatar));
        if (f4 <= f3 + 0.5f) {
            return;
        }
        WEAPON_BOL_CD_UNTIL.put(n, l + 14000L);
        ArlecchinoBoLUtil.applyBoLChange(entityAvatar, f4, ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
        Grasscutter.getLogger().info("[BoL] BloodMoon weapon +25% MaxHP=" + (f4 - f3) + " (outside 145% cap)");
    }

    private static float getAuthBoL(EntityAvatar entityAvatar) {
        if (entityAvatar == null) {
            return 0.0f;
        }
        int n = entityAvatar.getId();
        if (AUTHORITATIVE_BOL.containsKey(n)) {
            return AUTHORITATIVE_BOL.get(n);
        }
        return entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
    }

    public static void onSkillNotify(Player player, EntityAvatar entityAvatar, int n) {
        if (player == null) {
            return;
        }
        if (n == 10961) {
            ESkillRound eSkillRound;
            long l = System.currentTimeMillis();
            LAST_CA_SKILL_MS.put(player.getUid(), l);
            ArlecchinoBoLUtil.openMarkRecycleWindow(player);
            if (entityAvatar != null && (eSkillRound = E_SKILL_ROUNDS.get(player.getUid())) != null && !eSkillRound.targets.isEmpty()) {
                ArlecchinoBoLUtil.recycleAllDirectivesNow(player, entityAvatar, "CA-10961");
            }
            return;
        }
        if ((n == 10962 || n == 10965) && entityAvatar != null) {
            SKILL_HIT_BLOCK_UNTIL.put(entityAvatar.getId(), System.currentTimeMillis() + 2500L);
        }
        if (n == 10962) {
            ArlecchinoBoLUtil.startESkillRound(player);
            return;
        }
        // Q BoL clear is owned exclusively by ArlecchinoBurstBoL (delay until slash / heal).
        // Do NOT beginBurstCast here — dual settle wiped BoL before client scaled the burst.
        if (n == 10965) {
            return;
        }
    }

    public static void onConfirmedChargedAttack(Player player, EntityAvatar entityAvatar) {
        if (player == null) {
            return;
        }
        long l = System.currentTimeMillis();
        LAST_CA_SKILL_MS.put(player.getUid(), l);
        if (entityAvatar != null) {
            int n = entityAvatar.getId();
            CA_NO_CONSUME_UNTIL.put(n, l + 600L);
            SKILL_HIT_BLOCK_UNTIL.put(n, l + 2500L);
            ESkillRound eSkillRound = E_SKILL_ROUNDS.get(player.getUid());
            if (eSkillRound != null && !eSkillRound.targets.isEmpty()) {
                ArlecchinoBoLUtil.recycleAllDirectivesNow(player, entityAvatar, "CA-hit-fallback");
                return;
            }
        }
        ArlecchinoBoLUtil.openMarkRecycleWindow(player);
    }

    /** Q clear is owned by {@link ArlecchinoBurstBoL}; only recycle blood-debt marks here. */
    private static void beginBurstCast(Player player, EntityAvatar entityAvatar, boolean bl) {
        if (player != null && entityAvatar != null) {
            ArlecchinoBoLUtil.openMarkRecycleWindow(player);
            ArlecchinoBoLUtil.recycleAllDirectivesNow(player, entityAvatar, "Q");
        }
    }

    @Deprecated
    public static void tryServerSideRecycle(Player player, EntityAvatar entityAvatar, String string) {
    }

    public static void armNaConsume() {
    }

    public static void clearNaConsumeArm() {
    }

    public static boolean isNaConsumeArmed() {
        return false;
    }

    public static void markFireAttackReducePending(EntityAvatar entityAvatar) {
        if (entityAvatar != null) {
            FIRE_ATTACK_REDUCE_UNTIL.put(entityAvatar.getId(), System.currentTimeMillis() + 250L);
        }
    }

    public static void armFireAttackReduceWindow(Ability ability) {
        EntityAvatar entityAvatar = ArlecchinoBoLUtil.resolveAvatar(ability);
        if (entityAvatar == null) {
            return;
        }
        ArlecchinoBoLUtil.markFireAttackReducePending(entityAvatar);
        ArlecchinoBoLUtil.tryNormalAttackConsumeBoL(entityAvatar, ability);
    }

    private static EntityAvatar resolveAvatar(Ability ability) {
        if (ability == null) {
            return null;
        }
        GameEntity gameEntity = ability.getOwner();
        if (gameEntity instanceof EntityAvatar) {
            EntityAvatar entityAvatar = (EntityAvatar)gameEntity;
            return entityAvatar;
        }
        if (gameEntity instanceof EntityWeapon && ability.getPlayerOwner() != null) {
            return ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        }
        Player player = ability.getPlayerOwner();
        if (player != null) {
            return player.getTeamManager().getCurrentAvatarEntity();
        }
        return null;
    }

    public static boolean isOfficialFireAttackReduce(Ability ability, EntityAvatar entityAvatar) {
        Object object;
        if (ability != null && ability.getModifiers() != null && ability.getModifiers().containsKey(FIRE_ATTACK_REDUCE_MODIFIER)) {
            return true;
        }
        return entityAvatar != null && (object = FIRE_ATTACK_REDUCE_UNTIL.get(entityAvatar.getId())) != null && System.currentTimeMillis() < (Long)object;
    }

    public static boolean isOfficialFireAttackReduce(Ability ability) {
        return ArlecchinoBoLUtil.isOfficialFireAttackReduce(ability, null);
    }

    public static boolean isOfficialNaReduceAllowed(Ability ability, EntityAvatar entityAvatar) {
        return ArlecchinoBoLUtil.isOfficialFireAttackReduce(ability, entityAvatar);
    }

    private static void recordAuthoritativeBoL(int n, float f) {
        float f2 = Math.max(0.0f, f);
        AUTHORITATIVE_BOL.put(n, f2);
        if (f2 <= 0.5f) {
            ArlecchinoBoLUtil.cancelBoLKeepalive(n);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void onPlayerTick(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        EntityAvatar entityAvatar = player.getTeamManager().getCurrentAvatarEntity();
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return;
        }
        int n = entityAvatar.getId();
        if (!Boolean.TRUE.equals(GATE_TAKEN_OVER.get(n))) {
            return;
        }
        float f = AUTHORITATIVE_BOL.getOrDefault(n, 0.0f);
        float f2 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (Math.abs(f2 - f) <= 0.5f) {
            return;
        }
        ALLOW_BOL_MUTATE.set(true);
        try {
            ArlecchinoBoLUtil.resyncBoLTo(entityAvatar, f);
        }
        finally {
            ALLOW_BOL_MUTATE.set(false);
        }
    }

    public static boolean shouldBlockStockHeal(EntityAvatar entityAvatar) {
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return false;
        }
        return entityAvatar.getAvatar().getAvatarId() == 10000096;
    }

    public static void ensureBoLKeepalive(EntityAvatar entityAvatar) {
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return;
        }
        int n = entityAvatar.getId();
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (f <= 0.5f) {
            ArlecchinoBoLUtil.cancelBoLKeepalive(n);
            return;
        }
        try {
            entityAvatar.setRestrictedFromHealing(true);
        }
        catch (Exception exception) {
            // empty catch block
        }
        BOL_KEEPALIVE_AVATAR.put(n, entityAvatar);
        if (BOL_KEEPALIVE_TASK.containsKey(n)) {
            return;
        }
        try {
            int n2 = Grasscutter.getGameServer().getScheduler().scheduleDelayedRepeatingTask(() -> ArlecchinoBoLUtil.tickBoLKeepalive(n), 1, 2);
            BOL_KEEPALIVE_TASK.put(n, n2);
            Grasscutter.getLogger().info("[BoL] keepalive armed entity=" + n + " auth=" + f);
        }
        catch (Exception exception) {
            Grasscutter.getLogger().warn("[BoL] keepalive schedule failed: " + exception.getMessage());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void tickBoLKeepalive(int n) {
        EntityAvatar entityAvatar = BOL_KEEPALIVE_AVATAR.get(n);
        if (entityAvatar == null
                || entityAvatar.getAvatar() == null
                || entityAvatar.getPlayer() == null
                || !entityAvatar.getPlayer().isOnline()) {
            ArlecchinoBoLUtil.cancelBoLKeepalive(n);
            return;
        }
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (f <= 0.5f) {
            ArlecchinoBoLUtil.cancelBoLKeepalive(n);
            return;
        }
        // Re-push the authoritative Bond every tick so a client-side predicted wipe (CA dash /
        // E cast / taking a hit) self-corrects within ~1s instead of showing a phantom vanish.
        ALLOW_BOL_MUTATE.set(true);
        try {
            ArlecchinoBoLUtil.forcePushBoL(entityAvatar, f, ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY, true);
        }
        finally {
            ALLOW_BOL_MUTATE.set(false);
        }
    }

    private static void cancelBoLKeepalive(int n) {
        Integer n2 = BOL_KEEPALIVE_TASK.remove(n);
        EntityAvatar entityAvatar = BOL_KEEPALIVE_AVATAR.remove(n);
        if (n2 != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(n2);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (entityAvatar != null) {
            try {
                entityAvatar.setRestrictedFromHealing(true);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    public static boolean handleAddHpDebtsIfArlecchino(Ability ability, AbilityModifier.AbilityModifierAction abilityModifierAction, GameEntity gameEntity) {
        GameEntity gameEntity2;
        GameEntity gameEntity3 = gameEntity2 = ability != null ? ability.getOwner() : null;
        if (gameEntity2 instanceof EntityWeapon && ability.getPlayerOwner() != null) {
            gameEntity2 = ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        }
        if (gameEntity2 == null) {
            gameEntity2 = gameEntity;
        }
        if (!(gameEntity2 instanceof EntityAvatar)) {
            return false;
        }
        EntityAvatar entityAvatar = (EntityAvatar)gameEntity2;
        if (entityAvatar.getAvatar() == null || entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return false;
        }
        int n = entityAvatar.getId();
        if (ArlecchinoBoLUtil.isBurstPostClear(n)) {
            float f = 0.0f;
            try {
                Object2FloatOpenHashMap<String> object2FloatOpenHashMap = new Object2FloatOpenHashMap<String>();
                for (FightProperty fightProperty : FightProperty.values()) {
                    object2FloatOpenHashMap.put(fightProperty.name(), gameEntity2.getFightProperty(fightProperty));
                }
                if (ability != null) {
                    object2FloatOpenHashMap.putAll((Map<String, Float>)ability.getAbilitySpecials());
                }
                if (abilityModifierAction != null && abilityModifierAction.ratio != null) {
                    f = abilityModifierAction.ratio.get(object2FloatOpenHashMap, 0.0f);
                }
            }
            catch (Exception exception) {
                f = 0.0f;
            }
            float f2 = ArlecchinoBoLUtil.resolveBurstHealParam(entityAvatar.getAvatar(), "HealHp_Debt_Ratio");
            float f3 = ArlecchinoBoLUtil.applyBurstHeal(entityAvatar, Math.max(0.0f, f) * f2);
            Grasscutter.getLogger().info("[BoL] AddHPDebts blocked post-clear; converted ask=" + f + " -> heal=" + f3);
            return true;
        }
        String string = abilityModifierAction != null ? abilityModifierAction.modifierName : null;
        boolean bl = ArlecchinoBoLUtil.isExtraAttackAddHpDebtsModifier(string) || abilityModifierAction != null && abilityModifierAction.abilityName != null && abilityModifierAction.abilityName.contains("ExtraAttack_AddHpDebts");
        Player player = entityAvatar.getPlayer();
        if (player == null && ability != null) {
            player = ability.getPlayerOwner();
        }
        if (player == null) {
            return true;
        }
        ESkillRound eSkillRound = E_SKILL_ROUNDS.get(player.getUid());
        boolean bl2 = eSkillRound != null && !eSkillRound.targets.isEmpty();
        boolean bl3 = ArlecchinoBoLUtil.isMarkRecycleAllowed(player);
        if (bl || (bl3 || bl2) && !ArlecchinoBoLUtil.isBurstPending(entityAvatar.getId())) {
            if (bl2) {
                ArlecchinoBoLUtil.openMarkRecycleWindow(player);
                ArlecchinoBoLUtil.recycleAllDirectivesNow(player, entityAvatar, "AddHpDebts-UI");
            } else {
                ArlecchinoBoLUtil.forcePushBoL(entityAvatar, ArlecchinoBoLUtil.getAuthBoL(entityAvatar), ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY, true);
                try {
                    player.sendPacket(new PacketAbilityChangeNotify(entityAvatar));
                    player.sendPacket(new PacketAvatarFightPropNotify(entityAvatar.getAvatar()));
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            return true;
        }
        return true;
    }

    public static void adoptServerBoL(EntityAvatar entityAvatar) {
        float f;
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return;
        }
        if (!TRUST_BOL_INCREASE.get().booleanValue()) {
            return;
        }
        int n = entityAvatar.getId();
        if (ArlecchinoBoLUtil.isBurstPostClear(n)) {
            return;
        }
        float f2 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        ArlecchinoBoLUtil.recordAuthoritativeBoL(n, f2);
        if (ArlecchinoBoLUtil.isBurstPending(n) && f2 > (f = BURST_PENDING_SNAP.getOrDefault(n, 0.0f))) {
            BURST_PENDING_SNAP.put(n, f2);
        }
    }

    public static void enforceBoLAuthority(EntityAvatar entityAvatar) {
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return;
        }
        // Highest priority: while BurstBoL holds Q lock, never force client/server BoL to 0.
        if (ArlecchinoBurstBoL.isConsumeBlocked(entityAvatar)) {
            ArlecchinoBurstBoL.repinClientBoL(entityAvatar);
            return;
        }
        int n = entityAvatar.getId();
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float f2 = ArlecchinoBoLUtil.getBolCap(entityAvatar);
        if (ArlecchinoBoLUtil.isBurstPending(n)) {
            float f3 = BURST_PENDING_SNAP.getOrDefault(n, AUTHORITATIVE_BOL.getOrDefault(n, f));
            if (Math.abs(f - (f3 = Math.min(Math.max(f3, 0.0f), f2))) > 0.5f) {
                ArlecchinoBoLUtil.resyncBoLTo(entityAvatar, f3);
            } else {
                ArlecchinoBoLUtil.recordAuthoritativeBoL(n, f3);
            }
            return;
        }
        if (ArlecchinoBoLUtil.isBurstPostClear(n)) {
            // Do not wipe if BurstBoL is still settling the same Q.
            if (ArlecchinoBurstBoL.isPending(n)) {
                return;
            }
            if (f > 0.5f) {
                ArlecchinoBoLUtil.resyncBoLTo(entityAvatar, 0.0f);
            } else {
                ArlecchinoBoLUtil.recordAuthoritativeBoL(n, 0.0f);
            }
            return;
        }
        if (!Boolean.TRUE.equals(GATE_TAKEN_OVER.get(n))) {
            ArlecchinoBoLUtil.recordAuthoritativeBoL(n, f);
            return;
        }
        float f4 = AUTHORITATIVE_BOL.getOrDefault(n, f);
        if (Math.abs(f - (f4 = Math.min(Math.max(f4, 0.0f), f2))) > 0.5f) {
            if (f < f4 - 0.5f && ArlecchinoBoLUtil.tryAdoptClientNaPay(entityAvatar, n, f4, f)) {
                return;
            }
            if (f < f4 - 0.5f && (!LAST_DRAIN_RESYNC_LOG_MS.containsKey(n) || System.currentTimeMillis() - LAST_DRAIN_RESYNC_LOG_MS.get(n) > 800L)) {
                LAST_DRAIN_RESYNC_LOG_MS.put(n, System.currentTimeMillis());
                Grasscutter.getLogger().info("[BoL] enforce reject client wipe " + f + " -> keep " + f4);
            }
            ArlecchinoBoLUtil.resyncBoLTo(entityAvatar, f4);
            return;
        }
        ArlecchinoBoLUtil.recordAuthoritativeBoL(n, f4);
    }

    private static boolean tryAdoptClientNaPay(EntityAvatar entityAvatar, int n, float f, float f2) {
        if (entityAvatar == null || f <= 0.5f || f2 < 0.0f) {
            return false;
        }
        if (!ArlecchinoBoLUtil.isRedDeathBanquet(entityAvatar)) {
            return false;
        }
        long l = System.currentTimeMillis();
        Long l2 = SKILL_HIT_BLOCK_UNTIL.get(n);
        if (l2 != null && l < l2) {
            return false;
        }
        Long l3 = CA_NO_CONSUME_UNTIL.get(n);
        if (l3 != null && l < l3) {
            return false;
        }
        if (LAST_NA_REDUCE_MS.containsKey(n) && l - LAST_NA_REDUCE_MS.get(n) < 30L) {
            return false;
        }
        float f3 = Math.max(3.0f, f * 0.025f);
        float f4 = f;
        for (int i = 1; i <= 3; ++i) {
            if (!(Math.abs((f4 *= 0.925f) - f2) <= f3)) continue;
            LAST_NA_REDUCE_MS.put(n, l);
            ChangeHpDebtsReason._ChangeHpDebtsReason changeHpDebtsReason = f4 <= 0.5f ? ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH : ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY;
            ArlecchinoBoLUtil.applyBoLChange(entityAvatar, Math.max(0.0f, f4), changeHpDebtsReason);
            Grasscutter.getLogger().info("[BoL] NA consume " + f + " -> " + f4 + " (client-adopt " + i + "\u00d77.5%)");
            return true;
        }
        return false;
    }

    private static void forcePushBoL(EntityAvatar entityAvatar, float f, ChangeHpDebtsReason._ChangeHpDebtsReason changeHpDebtsReason) {
        ArlecchinoBoLUtil.forcePushBoL(entityAvatar, f, changeHpDebtsReason, false);
    }

    private static void forcePushBoL(EntityAvatar entityAvatar, float f, ChangeHpDebtsReason._ChangeHpDebtsReason changeHpDebtsReason, boolean bl) {
        if (entityAvatar == null) {
            return;
        }
        float f3 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float f2 = Math.min(Math.max(f, 0.0f), ArlecchinoBoLUtil.getBolCap(entityAvatar));
        // Highest priority: ArlecchinoBurstBoL consume-lock — refuse any decrease.
        if (ArlecchinoBurstBoL.isConsumeBlocked(entityAvatar)
                && !ArlecchinoBurstBoL.allowAuthoritativeClear(entityAvatar.getId())
                && f2 < f3 - 0.01f) {
            Grasscutter.getLogger()
                    .info("[BoL] forcePushBoL blocked {}→{} (consume-lock)", f3, f2);
            ArlecchinoBurstBoL.repinClientBoL(entityAvatar);
            return;
        }
        int n = entityAvatar.getId();
        long l = System.currentTimeMillis();
        ArlecchinoBoLUtil.recordAuthoritativeBoL(n, f2);
        entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, f2);
        float f4 = f2 - f3;
        if (Math.abs(f4) < 0.5f) {
            if (!bl) {
                return;
            }
            if (LAST_FORCE_PUSH_MS.containsKey(n) && l - LAST_FORCE_PUSH_MS.get(n) < 200L) {
                return;
            }
            LAST_FORCE_PUSH_MS.put(n, l);
            ArlecchinoBoLUtil.syncHpDebtsAbilityOverrides(entityAvatar, f2);
            PacketEntityFightPropUpdateNotify packetEntityFightPropUpdateNotify = new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            PacketAvatarFightPropUpdateNotify packetAvatarFightPropUpdateNotify = new PacketAvatarFightPropUpdateNotify(entityAvatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            Player player = entityAvatar.getPlayer();
            if (player != null) {
                player.sendPacket(packetEntityFightPropUpdateNotify);
                player.sendPacket(packetAvatarFightPropUpdateNotify);
            }
            if (entityAvatar.getScene() != null) {
                entityAvatar.getScene().broadcastPacket(packetEntityFightPropUpdateNotify);
            } else if (entityAvatar.getWorld() != null) {
                entityAvatar.getWorld().broadcastPacket(packetEntityFightPropUpdateNotify);
            }
            return;
        }
        LAST_FORCE_PUSH_MS.put(n, l);
        ArlecchinoBoLUtil.syncHpDebtsAbilityOverrides(entityAvatar, f2);
        PacketEntityFightPropUpdateNotify packetEntityFightPropUpdateNotify = new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        PacketEntityFightPropChangeReasonNotify packetEntityFightPropChangeReasonNotify = new PacketEntityFightPropChangeReasonNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS, Float.valueOf(f4), PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, changeHpDebtsReason);
        PacketAvatarFightPropUpdateNotify packetAvatarFightPropUpdateNotify = new PacketAvatarFightPropUpdateNotify(entityAvatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (entityAvatar.getScene() != null) {
            entityAvatar.getScene().broadcastPacket(packetEntityFightPropUpdateNotify);
            entityAvatar.getScene().broadcastPacket(packetEntityFightPropChangeReasonNotify);
        } else if (entityAvatar.getWorld() != null) {
            entityAvatar.getWorld().broadcastPacket(packetEntityFightPropUpdateNotify);
            entityAvatar.getWorld().broadcastPacket(packetEntityFightPropChangeReasonNotify);
        }
        Player player = entityAvatar.getPlayer();
        if (player != null) {
            player.sendPacket(packetEntityFightPropUpdateNotify);
            player.sendPacket(packetEntityFightPropChangeReasonNotify);
            player.sendPacket(packetAvatarFightPropUpdateNotify);
        }
    }

    private static void syncHpDebtsAbilityOverrides(EntityAvatar entityAvatar, float f) {
        if (entityAvatar == null) {
            return;
        }
        float f2 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float f3 = f2 > 0.0f ? f / f2 : 0.0f;
        try {
            List<Ability> list = entityAvatar.getInstancedAbilities();
            if (list == null) {
                return;
            }
            for (Ability ability : list) {
                if (ability == null || ability.getAbilitySpecials() == null) continue;
                Object2FloatMap<String> object2FloatMap = ability.getAbilitySpecials();
                String string = ability.getData() != null ? ability.getData().abilityName : null;
                boolean bl = false;
                if (object2FloatMap.containsKey("_HPDebts")) {
                    object2FloatMap.put("_HPDebts", f);
                    bl = true;
                }
                if (object2FloatMap.containsKey("Cur_HPDebts")) {
                    object2FloatMap.put("Cur_HPDebts", f);
                    bl = true;
                }
                if (object2FloatMap.containsKey("Cur_HPDebts_Ratio")) {
                    object2FloatMap.put("Cur_HPDebts_Ratio", f3);
                    bl = true;
                }
                if (bl) continue;
                if (BURST_ATTACK_ABILITY.equals(string)) {
                    object2FloatMap.put("_HPDebts", f);
                    continue;
                }
                if (!"Avatar_Arlecchino_HealToHpDebts".equals(string)) continue;
                object2FloatMap.put("Cur_HPDebts", f);
                object2FloatMap.put("Cur_HPDebts_Ratio", f3);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private static void resyncBoLTo(EntityAvatar entityAvatar, float f) {
        if (entityAvatar == null) {
            return;
        }
        ArlecchinoBoLUtil.forcePushBoL(entityAvatar, f, ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY, true);
    }

    private static void broadcastBoLUpdate(EntityAvatar entityAvatar) {
        if (entityAvatar == null) {
            return;
        }
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        ArlecchinoBoLUtil.forcePushBoL(entityAvatar, f, ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
    }

    public static boolean isBurstPending(int n) {
        // Prefer BurstBoL lock as source of truth for Q hold.
        if (ArlecchinoBurstBoL.isPending(n)) {
            return true;
        }
        if (!Boolean.TRUE.equals(BURST_PENDING.get(n))) {
            return false;
        }
        Long l = BURST_PENDING_SINCE.get(n);
        if (l != null && System.currentTimeMillis() - l > 4000L) {
            Grasscutter.getLogger().info("[BoL] burst pending timeout entity=" + n + " — drop util pending only (BoL clear owned by ArlecchinoBurstBoL)");
            ArlecchinoBoLUtil.clearBurstPendingState(n);
            BURST_DONE.remove(n);
            return false;
        }
        return true;
    }

    private static void clearBurstPendingState(int n) {
        BURST_PENDING.remove(n);
        BURST_PENDING_SINCE.remove(n);
        BURST_PENDING_SNAP.remove(n);
        BURST_PENDING_AVATAR.remove(n);
    }

    public static boolean isBurstPostClear(int n) {
        Long l = BURST_POST_CLEAR_UNTIL.get(n);
        return l != null && System.currentTimeMillis() < l;
    }

    public static void openMarkRecycleWindow(Player player) {
        if (player == null) {
            return;
        }
        int n = player.getUid();
        long l = System.currentTimeMillis();
        Long l2 = MARK_RECYCLE_UNTIL.get(n);
        MARK_RECYCLE_UNTIL.put(n, l + 3000L);
        if (l2 == null || l2 < l) {
            // empty if block
        }
    }

    public static boolean isMarkRecycleAllowed(Player player) {
        if (player == null) {
            return false;
        }
        Long l = MARK_RECYCLE_UNTIL.get(player.getUid());
        return l != null && System.currentTimeMillis() < l;
    }

    public static void openEGrantWindow(Player player) {
        if (player == null) {
            return;
        }
        int n = player.getUid();
        E_GRANT_UNTIL.put(n, System.currentTimeMillis() + 35000L);
        ESkillRound eSkillRound = ArlecchinoBoLUtil.getOrCreateRound(n);
        eSkillRound.grantAccum145 = 0.0f;
    }

    public static boolean isInEGrantWindow(Player player) {
        if (player == null) {
            return false;
        }
        Long l = E_GRANT_UNTIL.get(player.getUid());
        return l != null && System.currentTimeMillis() < l;
    }

    public static float getBolCap(EntityAvatar entityAvatar) {
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        return Math.max(0.0f, f * 2.0f);
    }

    public static boolean isRedDeathBanquet(EntityAvatar entityAvatar) {
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return false;
        }
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float f2 = ArlecchinoBoLUtil.getAuthBoL(entityAvatar);
        return f > 0.0f && f2 >= f * 0.3f;
    }

    public static void applyBoLChange(EntityAvatar entityAvatar, float f, ChangeHpDebtsReason._ChangeHpDebtsReason changeHpDebtsReason) {
        float f2;
        if (entityAvatar == null) {
            return;
        }
        float f3 = Math.min(Math.max(f, 0.0f), ArlecchinoBoLUtil.getBolCap(entityAvatar));
        float f4 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        // Highest priority: BurstBoL consume-lock — refuse decreases.
        if (ArlecchinoBurstBoL.isConsumeBlocked(entityAvatar)
                && !ArlecchinoBurstBoL.allowAuthoritativeClear(entityAvatar.getId())
                && f3 < f4 - 0.01f) {
            Grasscutter.getLogger().info("[BoL] applyBoLChange blocked {}→{} (consume-lock)", f4, f3);
            ArlecchinoBurstBoL.repinClientBoL(entityAvatar);
            return;
        }
        int n = entityAvatar.getId();
        if (ArlecchinoBoLUtil.isBurstPending(n) && !ALLOW_BOL_MUTATE.get().booleanValue() && f3 < (f2 = BURST_PENDING_SNAP.getOrDefault(n, f4)) - 0.5f) {
            ArlecchinoBoLUtil.resyncBoLTo(entityAvatar, f2);
            return;
        }
        f2 = f3 - f4;
        if (f2 == 0.0f) {
            ArlecchinoBoLUtil.markGateMutation(n, f3);
            ArlecchinoBoLUtil.forcePushBoL(entityAvatar, f3, changeHpDebtsReason);
            if (f3 > 0.5f) {
                ArlecchinoBoLUtil.ensureBoLKeepalive(entityAvatar);
            }
            return;
        }
        ArlecchinoBoLUtil.markGateMutation(n, f3);
        entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, f3);
        ArlecchinoBoLUtil.forcePushBoL(entityAvatar, f3, changeHpDebtsReason);
        if (f3 > 0.5f) {
            ArlecchinoBoLUtil.ensureBoLKeepalive(entityAvatar);
        } else {
            ArlecchinoBoLUtil.cancelBoLKeepalive(n);
        }
    }

    public static float applyBurstHeal(EntityAvatar entityAvatar, float f) {
        return applyBurstHeal(entityAvatar, f, false);
    }

    public static float applyBurstHeal(EntityAvatar entityAvatar, float amount, boolean mute) {
        if (entityAvatar == null || amount <= 0.0f) {
            return 0.0f;
        }
        synchronized (entityAvatar) {
            float currentHp = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            if (currentHp <= 0.0f) {
                return 0.0f;
            }
            boolean convertToHpDebt = entityAvatar.isConvertToHpDebt();
            entityAvatar.setConvertToHpDebt(false);
            try {
                float maxHp = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
                float healed = Math.min(Math.max(0.0f, maxHp - currentHp), amount);
                if (healed <= 0.0f) {
                    Grasscutter.getLogger().info("[BoL] burst heal skipped (full HP) cur=" + currentHp + " max=" + maxHp + " asked=" + amount);
                    if (!mute && entityAvatar.getScene() != null) {
                        entityAvatar.getScene().broadcastPacket(new PacketEvtBeingHealedNotify(entityAvatar, entityAvatar, amount, 0.0f));
                    }
                    return 0.0f;
                }
                entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, currentHp + healed);
                var reason = mute
                        ? PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_NONE
                        : PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY;
                if (entityAvatar.getScene() != null) {
                    entityAvatar.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_HP));
                    entityAvatar.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_HP, Float.valueOf(healed), reason, ChangHpReasonOuterClass.ChangHpReason.ChangHpReason_CHANGE_HP_ADD_ABILITY));
                    if (!mute) {
                        entityAvatar.getScene().broadcastPacket(new PacketEvtBeingHealedNotify(entityAvatar, entityAvatar, amount, healed));
                    }
                } else if (entityAvatar.getWorld() != null) {
                    World world = entityAvatar.getWorld();
                    world.broadcastPacket(new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_HP));
                    world.broadcastPacket(new PacketEntityFightPropChangeReasonNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_HP, Float.valueOf(healed), reason, ChangHpReasonOuterClass.ChangHpReason.ChangHpReason_CHANGE_HP_ADD_ABILITY));
                    if (!mute) {
                        world.broadcastPacket(new PacketEvtBeingHealedNotify(entityAvatar, entityAvatar, amount, healed));
                    }
                }
                Grasscutter.getLogger().info("[BoL] burst HP " + currentHp + "/" + maxHp + " -> " + (currentHp + healed) + " (+" + healed + ")");
                return healed;
            }
            finally {
                entityAvatar.setConvertToHpDebt(convertToHpDebt);
            }
        }
    }

    /** No-op: heal clear path is {@link ArlecchinoBurstBoL#prepareBurstHeal}. */
    public static void handleBurstHealAction(EntityAvatar entityAvatar) {}

    public static boolean tryNormalAttackConsumeBoL(EntityAvatar entityAvatar, Ability ability) {
        return ArlecchinoBoLUtil.tryNormalAttackConsumeBoL(entityAvatar, ability, false);
    }

    public static boolean tryNormalAttackConsumeBoL(EntityAvatar entityAvatar, Ability ability, boolean bl) {
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return false;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return false;
        }
        if (!bl && !ArlecchinoBoLUtil.isOfficialFireAttackReduce(ability, entityAvatar)) {
            return false;
        }
        if (!ArlecchinoBoLUtil.isRedDeathBanquet(entityAvatar)) {
            return false;
        }
        if (ArlecchinoBoLUtil.isBurstPending(entityAvatar.getId()) || ArlecchinoBoLUtil.isBurstPostClear(entityAvatar.getId())) {
            return false;
        }
        int n = entityAvatar.getId();
        long l = System.currentTimeMillis();
        Long l2 = SKILL_HIT_BLOCK_UNTIL.get(n);
        if (l2 != null && l < l2) {
            return false;
        }
        Long l3 = CA_NO_CONSUME_UNTIL.get(n);
        if (l3 != null && l < l3) {
            return false;
        }
        if (LAST_NA_REDUCE_MS.containsKey(n) && l - LAST_NA_REDUCE_MS.get(n) < 30L) {
            return false;
        }
        float f = AUTHORITATIVE_BOL.containsKey(n) ? AUTHORITATIVE_BOL.get(n) : entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float f2 = f * 0.075f;
        if (f2 <= 0.0f) {
            return false;
        }
        float f3 = Math.max(0.0f, f - f2);
        LAST_NA_REDUCE_MS.put(n, l);
        ChangeHpDebtsReason._ChangeHpDebtsReason changeHpDebtsReason = f3 <= 0.5f ? ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH : ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY;
        ArlecchinoBoLUtil.applyBoLChange(entityAvatar, f3, changeHpDebtsReason);
        Grasscutter.getLogger().info("[BoL] NA consume " + f + " -> " + f3 + (bl ? " (official hit 7.5%)" : " (FireAttack 7.5%)"));
        return true;
    }

    public static void onNormalAttackHit(EntityAvatar entityAvatar, AttackResultOuterClass.AttackResult attackResult) {
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        int n = entityAvatar.getAvatar().getAvatarId();
        if (n == 10000098) {
            int n2 = entityAvatar.getId();
            long l = System.currentTimeMillis();
            if (!LAST_CLORINDE_HIT_LOG_MS.containsKey(n2) || l - LAST_CLORINDE_HIT_LOG_MS.get(n2) > 800L) {
                LAST_CLORINDE_HIT_LOG_MS.put(n2, l);
                float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
                String string = attackResult != null ? String.valueOf(attackResult.getAnimEventId()) : "";
                Grasscutter.getLogger().info("[BoL][Clorinde] hit bol=" + f + " anim=" + string + " swift=" + ClorindeBoLUtil.isSwiftHuntHit(attackResult));
            }
            ClorindeBoLUtil.onCombatHit(entityAvatar, attackResult);
            return;
        }
        if (n != 10000096) {
            return;
        }
        if (!ArlecchinoBoLUtil.isRedDeathBanquet(entityAvatar)) {
            return;
        }
        int n3 = entityAvatar.getId();
        Long l = SKILL_HIT_BLOCK_UNTIL.get(n3);
        long l2 = System.currentTimeMillis();
        if (l != null && l2 < l) {
            return;
        }
        Long l3 = CA_NO_CONSUME_UNTIL.get(n3);
        if (l3 != null && l2 < l3) {
            return;
        }
        if (attackResult == null) {
            return;
        }
        if (ArlecchinoBoLUtil.looksLikeChargedAttack(attackResult)) {
            ArlecchinoBoLUtil.onConfirmedChargedAttack(entityAvatar.getPlayer(), entityAvatar);
            ArlecchinoBoLUtil.tryBloodMoonWeaponBoL(entityAvatar, attackResult);
            return;
        }
        if (ArlecchinoBoLUtil.looksLikeElementalArtHit(attackResult)) {
            GameEntity gameEntity;
            int n4;
            Player player = entityAvatar.getPlayer();
            if (player != null && (n4 = attackResult.getDefenseId()) > 0 && player.getScene() != null && (gameEntity = player.getScene().getEntityById(n4)) != null) {
                ArlecchinoBoLUtil.onDirectivePlaced(player, gameEntity, 1);
            }
            return;
        }
        if (!ArlecchinoBoLUtil.isOfficialRedDeathNaHit(attackResult)) {
            return;
        }
        ArlecchinoBoLUtil.tryNormalAttackConsumeBoL(entityAvatar, null, true);
    }

    public static void logClorindeBoL(String string, EntityAvatar entityAvatar, float f, float f2) {
        if (entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000098) {
            return;
        }
        Grasscutter.getLogger().info("[BoL][Clorinde] " + string + " " + f + " -> " + f2 + " (delta=" + (f2 - f) + ")");
    }

    public static void onNormalAttackHit(EntityAvatar entityAvatar) {
        ArlecchinoBoLUtil.onNormalAttackHit(entityAvatar, null);
    }

    private static int hashAbilityString(String string) {
        int n = 0;
        for (int i = 0; i < string.length(); ++i) {
            n = n * 131 + string.charAt(i);
        }
        return n;
    }

    private static boolean matchesAbilityEvent(String string, int n, String[] stringArray) {
        if (string != null && !string.isEmpty()) {
            for (String string2 : stringArray) {
                if (!string2.equals(string)) continue;
                return true;
            }
        }
        if (n != 0) {
            for (String string2 : stringArray) {
                if (ArlecchinoBoLUtil.hashAbilityString(string2) != n) continue;
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeChargedAttack(AttackResultOuterClass.AttackResult attackResult) {
        int n;
        if (attackResult == null) {
            return false;
        }
        String string = attackResult.getAnimEventId();
        if (ArlecchinoBoLUtil.matchesAbilityEvent(string, n = ArlecchinoBoLUtil.extractHashedAnimEvent(attackResult), OFFICIAL_CA_ANIM_EVENTS)) {
            return true;
        }
        if (string == null || string.isEmpty()) {
            return false;
        }
        String string2 = string.toLowerCase();
        return string2.contains("extraattack") || string2.contains("charged");
    }

    private static boolean looksLikeElementalArtHit(AttackResultOuterClass.AttackResult attackResult) {
        if (attackResult == null) {
            return false;
        }
        String string = attackResult.getAnimEventId();
        if (string == null || string.isEmpty()) {
            return false;
        }
        String string2 = string.toLowerCase();
        return string2.contains("elementalart") || string2.contains("elemental_art");
    }

    /** Used by {@code Scene} for Red Death NA BoL drain. Must stay public — jar Scene calls this. */
    public static boolean isRedDeathNormalAttackHit(AttackResultOuterClass.AttackResult attackResult) {
        return ArlecchinoBoLUtil.isOfficialRedDeathNaHit(attackResult);
    }

    private static boolean isOfficialRedDeathNaHit(AttackResultOuterClass.AttackResult attackResult) {
        int n;
        if (attackResult == null) {
            return false;
        }
        if (attackResult.getElementType() != 1) {
            return false;
        }
        String string = attackResult.getAnimEventId();
        if (ArlecchinoBoLUtil.matchesAbilityEvent(string, n = ArlecchinoBoLUtil.extractHashedAnimEvent(attackResult), OFFICIAL_NA_ANIM_EVENTS)) {
            return true;
        }
        if (string == null || string.isEmpty()) {
            return false;
        }
        String string2 = string.toLowerCase();
        if (string2.contains("extraattack") || string2.contains("plunge") || string2.contains("burst") || string2.contains("elemental") || string2.contains("charged")) {
            return false;
        }
        return string2.contains("atk") && string2.contains("plus");
    }

    private static int extractHashedAnimEvent(AttackResultOuterClass.AttackResult attackResult) {
        if (attackResult == null) {
            return 0;
        }
        try {
            for (Method method : attackResult.getClass().getMethods()) {
                int n;
                String string;
                if (method.getParameterCount() != 0 || method.getReturnType() != Integer.TYPE || !(string = method.getName()).startsWith("get") || string.equals("getAttackerId") || string.equals("getDefenseId") || string.equals("getElementType") || string.equals("getSerializedSize") || string.equals("hashCode") || string.equals("getHitRetreatAngleCompat") || (n = ((Integer)method.invoke(attackResult, new Object[0])).intValue()) == 0 || !ArlecchinoBoLUtil.matchesAbilityEvent(null, n, OFFICIAL_NA_ANIM_EVENTS) && !ArlecchinoBoLUtil.matchesAbilityEvent(null, n, OFFICIAL_CA_ANIM_EVENTS)) continue;
                return n;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return 0;
    }

    private static boolean looksLikeRedDeathNormalHit(AttackResultOuterClass.AttackResult attackResult) {
        return ArlecchinoBoLUtil.isOfficialRedDeathNaHit(attackResult);
    }

    public static float grantMarkBoL(Player player, EntityAvatar entityAvatar, int n, boolean bl) {
        int n2;
        boolean bl2;
        if (player == null || entityAvatar == null || entityAvatar.getAvatar() == null) {
            return 0.0f;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return 0.0f;
        }
        if (n <= 0) {
            return 0.0f;
        }
        int n3 = entityAvatar.getId();
        int n4 = player.getUid();
        long l = System.currentTimeMillis();
        if (ArlecchinoBoLUtil.isBurstPending(n3) || ArlecchinoBoLUtil.isBurstPostClear(n3)) {
            return 0.0f;
        }
        boolean bl3 = ArlecchinoBoLUtil.isMarkRecycleAllowed(player);
        Long l2 = LAST_CA_SKILL_MS.get(n4);
        boolean bl4 = l2 != null && l - l2 < 3750L;
        Long l3 = RECENT_MARK_CLEAR_MS.get(n4);
        boolean bl5 = bl2 = l3 != null && l - l3 < 750L;
        if (!(bl || bl3 || bl4 || bl2)) {
            Grasscutter.getLogger().info("[BoL] mark grant blocked (no CA window) lv=" + n);
            return 0.0f;
        }
        if (bl && ArlecchinoBoLUtil.hasA1(entityAvatar.getAvatar())) {
            ArlecchinoBoLUtil.popOneDirective(player);
            n2 = 2;
        } else {
            n2 = ArlecchinoBoLUtil.resolveAndConsumeRecycleLevel(player, entityAvatar, n);
        }
        return ArlecchinoBoLUtil.grantMarkBoLResolved(player, entityAvatar, n2, bl);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static float grantMarkBoLResolved(Player player, EntityAvatar entityAvatar, int n, boolean bl) {
        float f;
        float f2;
        float f3;
        if (player == null || entityAvatar == null || entityAvatar.getAvatar() == null) {
            return 0.0f;
        }
        if (n <= 0) {
            return 0.0f;
        }
        int n2 = entityAvatar.getId();
        int n3 = player.getUid();
        long l = System.currentTimeMillis();
        if (ArlecchinoBoLUtil.isBurstPending(n2) || ArlecchinoBoLUtil.isBurstPostClear(n2)) {
            return 0.0f;
        }
        float f4 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float f5 = ArlecchinoBoLUtil.markGrantAmountFromMaxHp(f4, n);
        ESkillRound eSkillRound = E_SKILL_ROUNDS.get(n3);
        if (ArlecchinoBoLUtil.isInEGrantWindow(player) && eSkillRound != null) {
            f3 = eSkillRound.grantAccum145;
            f2 = Math.max(0.0f, f4 * 1.45f - f3);
            if (f2 <= 0.0f) {
                Grasscutter.getLogger().info("[BoL] mark grant blocked: 145% MaxHP cap");
                return 0.0f;
            }
            f5 = Math.min(f5, f2);
            eSkillRound.grantAccum145 = f3 + f5;
        }
        if ((f = (f2 = Math.min((f3 = ArlecchinoBoLUtil.getAuthBoL(entityAvatar)) + f5, f4 * 2.0f)) - f3) <= 0.0f) {
            return 0.0f;
        }
        TRUST_BOL_INCREASE.set(true);
        try {
            ArlecchinoBoLUtil.applyBoLChange(entityAvatar, f2, ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY);
        }
        finally {
            TRUST_BOL_INCREASE.set(false);
        }
        if (ArlecchinoBoLUtil.isBurstPending(n2)) {
            BURST_PENDING_SNAP.put(n2, f2);
        }
        RECENT_MARK_CLEAR_MS.put(n3, l);
        float f6 = n >= 2 ? 130.0f : 65.0f;
        Grasscutter.getLogger().info("[BoL] mark grant " + f3 + " -> " + f2 + " (+" + f + " MaxHP\u00d7" + f6 + "%)");
        ArlecchinoBoLUtil.onMarkRecycled(player, entityAvatar);
        return f;
    }

    public static float markGrantAmountFromMaxHp(float f, int n) {
        if (f <= 0.0f) {
            return 0.0f;
        }
        return f * (n >= 2 ? 1.3f : 0.65f);
    }

    public static float resolveMarkRatio(Avatar avatar, int n) {
        return n >= 2 ? 1.3f : 0.65f;
    }

    public static float resolveReduceRatio(Avatar avatar, Object2FloatMap<String> object2FloatMap) {
        return 0.075f;
    }

    public static float resolveCriticalRatio(Avatar avatar) {
        return 0.3f;
    }

    public static float resolveAbilityParam(Avatar avatar, String string) {
        if (string == null) {
            return 0.0f;
        }
        return switch (string) {
            case "Cur_HPDebts_Reduce_Ratio" -> 0.075f;
            case "HPDebts_CriticalPointRatio" -> 0.3f;
            case "HpDebts_Level_1_Ratio" -> 0.65f;
            case "HpDebts_Level_2_Ratio" -> 1.3f;
            default -> ArlecchinoBoLUtil.resolveFromProudSkills(avatar, string);
        };
    }

    private static float resolveFromProudSkills(Avatar avatar, String string) {
        if (avatar == null) {
            return 0.0f;
        }
        AvatarSkillDepotData avatarSkillDepotData = (AvatarSkillDepotData)GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
        if (avatarSkillDepotData == null) {
            return 0.0f;
        }
        Map<Integer, Integer> map = avatar.getSkillLevelMap();
        float[] fArray = new float[]{0.0f};
        avatarSkillDepotData.getSkillsAndEnergySkill().forEach(n -> {
            if (fArray[0] > 0.0f) {
                return;
            }
            ProudSkillData proudSkillData = ArlecchinoBoLUtil.lookupProudSkill(avatarSkillDepotData, n, map);
            float f = ArlecchinoBoLUtil.readParamFromProudSkill(proudSkillData, string);
            if (f > 0.0f) {
                fArray[0] = f;
            }
        });
        return fArray[0];
    }

    private static ProudSkillData lookupProudSkill(AvatarSkillDepotData avatarSkillDepotData, int n, Map<Integer, Integer> map) {
        int n2;
        int n3;
        ProudSkillData object;
        Int2ObjectMap<ProudSkillData> int2ObjectMap = GameData.getProudSkillDataMap();
        int n4 = map.getOrDefault(n, 1);
        if (n == avatarSkillDepotData.getEnergySkill() && avatarSkillDepotData.getEnergySkillData() != null && (object = (ProudSkillData)int2ObjectMap.get(avatarSkillDepotData.getEnergySkillData().getProudSkillGroupId() * 100 + n4)) != null) {
            return object;
        }
        List<Integer> skills = avatarSkillDepotData.getSkills();
        List<Integer> list = avatarSkillDepotData.getSubSkills();
        if (skills != null && list != null && (n3 = skills.indexOf(n)) >= 0 && n3 < list.size() && (n2 = list.get(n3).intValue()) > 0) {
            return (ProudSkillData)int2ObjectMap.get(n2 * 100 + n4);
        }
        return null;
    }

    private static float readParamFromProudSkill(ProudSkillData proudSkillData, String string) {
        if (proudSkillData == null) {
            return 0.0f;
        }
        Object2FloatMap<String> object2FloatMap = proudSkillData.getParamListMap();
        if (object2FloatMap != null && object2FloatMap.containsKey(string)) {
            return object2FloatMap.getFloat(string);
        }
        if (proudSkillData.getOpenConfig() == null) {
            return 0.0f;
        }
        OpenConfigEntry openConfigEntry = GameData.getOpenConfigEntries().get(proudSkillData.getOpenConfig());
        if (openConfigEntry == null || openConfigEntry.getAbilityVarSetters() == null) {
            return 0.0f;
        }
        float[] fArray = proudSkillData.getParamList();
        if (fArray == null) {
            return 0.0f;
        }
        for (OpenConfigEntry.AbilityVarSetter abilityVarSetter : openConfigEntry.getAbilityVarSetters()) {
            int n;
            if (!string.equals(abilityVarSetter.getVarName()) || (n = abilityVarSetter.getParamIndex()) < 0 || n >= fArray.length) continue;
            return fArray[n];
        }
        return 0.0f;
    }

    public static float resolveBurstHealParam(Avatar avatar, String string) {
        ProudSkillData proudSkillData;
        AvatarSkillDepotData avatarSkillDepotData;
        float f;
        float f2 = f = "HealHp_Atk_Ratio".equals(string) ? 1.5f : 1.5f;
        if (avatar != null && (avatarSkillDepotData = (AvatarSkillDepotData)GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId())) != null && (proudSkillData = ArlecchinoBoLUtil.lookupProudSkill(avatarSkillDepotData, avatarSkillDepotData.getEnergySkill(), avatar.getSkillLevelMap())) != null && proudSkillData.getParamList() != null) {
            float[] fArray = proudSkillData.getParamList();
            float f3 = 0.0f;
            if ("HealHp_Debt_Ratio".equals(string) && fArray.length > 1) {
                f3 = fArray[1];
            } else if ("HealHp_Atk_Ratio".equals(string) && fArray.length > 2) {
                f3 = fArray[2];
            }
            if (f3 > 0.01f && f3 < 0.5f) {
                f3 *= 10.0f;
            }
            if (f3 >= 0.5f) {
                return f3;
            }
        }
        return f;
    }

    public static void onMarkRecycled(Player player, EntityAvatar entityAvatar) {
        if (player == null || entityAvatar == null || entityAvatar.getAvatar() == null) {
            return;
        }
        if (entityAvatar.getAvatar().getAvatarId() != 10000096) {
            return;
        }
        if (!entityAvatar.getAvatar().getTalentIdList().contains(964)) {
            return;
        }
        int n = player.getUid();
        long l = System.currentTimeMillis();
        if (LAST_C4_MS.containsKey(n) && l - LAST_C4_MS.get(n) < 10000L) {
            return;
        }
        LAST_C4_MS.put(n, l);
        float f = entityAvatar.getFightProperty(entityAvatar.GetEnergyProp(entityAvatar.getAvatar()));
        entityAvatar.addEnergy(15.0f);
        float f2 = entityAvatar.getFightProperty(entityAvatar.GetEnergyProp(entityAvatar.getAvatar()));
        Grasscutter.getLogger().info("[BoL] C4 recycle energy " + f + " -> " + f2 + " (+15)");
    }

    public static boolean isBurstAttackAbility(String string) {
        return BURST_ATTACK_ABILITY.equals(string);
    }

    /** No-op: slash clear path is {@link ArlecchinoBurstBoL#onAttack}. */
    public static void onBurstDamageFrame(EntityAvatar entityAvatar) {}

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    /** No-op settle: Q clear/heal owned by {@link ArlecchinoBurstBoL}. */
    public static void settleBurstClearAndHeal(EntityAvatar entityAvatar, String string) {
        if (entityAvatar != null) {
            ArlecchinoBoLUtil.clearBurstPendingState(entityAvatar.getId());
        }
    }

    public static void armBurstDamageFrame(EntityAvatar entityAvatar) {
        if (entityAvatar != null) {
            int n = entityAvatar.getId();
            BURST_DONE.remove(n);
            BURST_PENDING.put(n, true);
            BURST_PENDING_SINCE.put(n, System.currentTimeMillis());
            BURST_PENDING_AVATAR.put(n, entityAvatar);
            BURST_POST_CLEAR_UNTIL.remove(n);
        }
    }

    public static void prepareBurst(Player player, EntityAvatar entityAvatar) {
        ArlecchinoBoLUtil.armBurstDamageFrame(entityAvatar);
        if (player != null) {
            ArlecchinoBoLUtil.openMarkRecycleWindow(player);
        }
    }

    public static void scheduleBurstClearAndHeal(Player player, EntityAvatar entityAvatar) {
        ArlecchinoBoLUtil.prepareBurst(player, entityAvatar);
    }

    public static boolean isBurstResyncSuppressed(int n) {
        return ArlecchinoBoLUtil.isBurstPostClear(n) || ArlecchinoBoLUtil.isBurstPending(n);
    }

    public static void beginBurstResyncSuppress(int n) {
        BURST_PENDING.put(n, true);
    }

    public static void endBurstResyncSuppress(int n) {
        BURST_PENDING.remove(n);
    }

    public static boolean isBurstHealModifier(String string) {
        return "Avatar_Arlecchino_ElementalBurst_HealDelay".equals(string);
    }

    public static boolean isBurstDamageAbility(String string) {
        return ArlecchinoBoLUtil.isBurstAttackAbility(string);
    }

    public static void resyncBoL(EntityAvatar entityAvatar) {
        if (entityAvatar == null || entityAvatar.getWorld() == null) {
            return;
        }
        if (ArlecchinoBoLUtil.isBurstPostClear(entityAvatar.getId())) {
            return;
        }
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (f <= 0.0f) {
            return;
        }
        entityAvatar.getWorld().broadcastPacket(new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
    }

    private static final class ESkillRound {
        long roundStartMs;
        final ConcurrentHashMap<Integer, DirectiveEntry> targets = new ConcurrentHashMap();
        float grantAccum145;

        private ESkillRound() {
        }

        void reset(long l) {
            this.roundStartMs = l;
            this.targets.clear();
            this.grantAccum145 = 0.0f;
        }
    }

    private static final class DirectiveEntry {
        final int clientLevel;
        final long placedAtMs;

        DirectiveEntry(int n, long l) {
            this.clientLevel = n;
            this.placedAtMs = l;
        }
    }
}
