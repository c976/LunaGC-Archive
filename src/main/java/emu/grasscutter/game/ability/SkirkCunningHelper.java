/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.protobuf.ByteString
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.binout.AbilityData
 *  emu.grasscutter.data.binout.AbilityModifier
 *  emu.grasscutter.data.binout.AbilityModifier$AbilityModifierAction
 *  emu.grasscutter.data.binout.AbilityModifier$AbilityModifierAction$Type
 *  emu.grasscutter.game.ability.Ability
 *  emu.grasscutter.game.ability.AbilityManager
 *  emu.grasscutter.game.ability.AbilityModifierController
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.props.FightProperty
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.AnimatorParameterValueInfoOuterClass$AnimatorParameterValueInfo
 *  emu.grasscutter.net.proto.CombatInvokeEntryOuterClass$CombatInvokeEntry
 *  emu.grasscutter.net.proto.EvtAnimatorParameterInfoOuterClass$EvtAnimatorParameterInfo
 *  emu.grasscutter.net.proto.PropChangeReasonOuterClass$PropChangeReason
 *  emu.grasscutter.server.packet.send.PacketAvatarSkillInfoNotify
 *  emu.grasscutter.server.packet.send.PacketCanUseSkillNotify
 *  emu.grasscutter.server.packet.send.PacketCombatInvocationsNotify
 *  emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify
 *  emu.grasscutter.utils.Utils
 *  it.unimi.dsi.fastutil.ints.Int2IntArrayMap
 *  it.unimi.dsi.fastutil.ints.Int2IntMap
 *  it.unimi.dsi.fastutil.ints.IntSet
 */
package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.ability.SpecialEnergyBarHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoOuterClass;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass;
import emu.grasscutter.net.proto.EvtAnimatorParameterInfoOuterClass;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketAvatarSkillInfoNotify;
import emu.grasscutter.server.packet.send.PacketCanUseSkillNotify;
import emu.grasscutter.server.packet.send.PacketCombatInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SkirkCunningHelper {
    public static final int SKIRK_AVATAR_ID = 10000114;
    public static final int ELEMENTAL_SKILL_ID = 11142;
    public static final int ELEMENTAL_SKILL_ALT_ID = 11146;
    public static final int BURST_SKILL_ID = 11145;
    public static final int BURST_SKILL_ALT_ID = 11147;
    public static final int C2_TALENT_ID = 1142;
    public static final float SKILL_GAIN = 45.0f;
    public static final float C2_EXTRA_GAIN = 10.0f;
    public static final float BAR_MAX = 100.0f;
    public static final float BURST_COST = 50.0f;
    public static final String C2_EXTRA_ENERGY_KEY = "SkirkNew_Constellation_2_ExtraEnergy";
    public static final String PICKABLE_ENERGY_REVIVE_KEY = "SkirkNew_Pickable_Energy_Revive";
    public static final float PICKABLE_ENERGY_REVIVE = 8.0f;
    public static final int PERMANENT_SKILL_1_ID = 1142101;
    private static final String NYX_KEY = "NyxValue";
    private static final String NYX_MAX = "NyxValueMax";
    private static final String NYX_MIN = "NyxValueMin";
    private static final String TRANSFORM_FLAG = "_ABILITY_SkirkNew_ElementalArt_Transform_Flag";
    private static final String CUR_SPECIAL_GV = "_SKIRKNEW_CUR_SPECIAL_ENERGY";
    private static final String OVERFLOW_GV = "_SKIRKNEW_CUR_SPECIAL_ENERGY_OVERFLOW";
    private static final long DEBOUNCE_MS = 900L;
    private static final ConcurrentHashMap<Integer, Long> lastSkillGrantMs = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Long> lastC2GrantMs = new ConcurrentHashMap();

    private SkirkCunningHelper() {
    }

    public static boolean isSkirk(GameEntity gameEntity) {
        if (!(gameEntity instanceof EntityAvatar)) {
            return false;
        }
        EntityAvatar entityAvatar = (EntityAvatar)gameEntity;
        Avatar avatar = entityAvatar.getAvatar();
        return avatar != null && avatar.getAvatarId() == 10000114;
    }

    public static boolean hasConstellation2(Avatar avatar) {
        if (avatar == null) {
            return false;
        }
        try {
            IntSet intSet = avatar.getTalentIdList();
            if (intSet != null && intSet.contains(1142)) {
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            return avatar.getCoreProudSkillLevel() >= 2;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public static float skillGainFor(Avatar avatar) {
        float f = 45.0f;
        if (SkirkCunningHelper.hasConstellation2(avatar)) {
            f += 10.0f;
        }
        return f;
    }

    public static void ensureBarCap(GameEntity gameEntity) {
        if (!SkirkCunningHelper.isSkirk(gameEntity)) {
            return;
        }
        Map map = gameEntity.getGlobalAbilityValues();
        map.put(NYX_MIN, Float.valueOf(0.0f));
        map.put(NYX_MAX, Float.valueOf(100.0f));
        SpecialEnergyBarHelper.ensureAndSync(gameEntity);
        SkirkCunningHelper.syncNyxFromSpecial(gameEntity);
    }

    public static boolean ignoreClientMetaAdd(GameEntity gameEntity, float f) {
        if (!SkirkCunningHelper.isSkirk(gameEntity)) {
            return false;
        }
        SkirkCunningHelper.ensureBarCap(gameEntity);
        if (f < -0.01f) {
            Grasscutter.getLogger().debug("SkirkCunning: allow client meta consume {} (cur={})", (Object)Float.valueOf(f), (Object)Float.valueOf(gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY)));
            return false;
        }
        if (Math.abs(f) < 0.01f) {
            SkirkCunningHelper.syncNyxFromSpecial(gameEntity);
            return true;
        }
        float f2 = gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
        float f3 = Math.max(0.0f, Math.min(100.0f, f));
        float f4 = f3 - f2;
        if (Math.abs(f4) >= 0.05f) {
            gameEntity.addSpecialEnergy(f4);
            SpecialEnergyBarHelper.ensureAndSync(gameEntity);
            Grasscutter.getLogger().debug("SkirkCunning: client meta SET {} -> {} (delta={})", new Object[]{Float.valueOf(f2), Float.valueOf(f3), Float.valueOf(f4)});
        }
        SkirkCunningHelper.syncNyxFromSpecial(gameEntity);
        return true;
    }

    public static void grantFromSkillPublic(GameEntity gameEntity) {
        EntityAvatar entityAvatar;
        Avatar avatar;
        if (!SkirkCunningHelper.isSkirk(gameEntity)) {
            return;
        }
        if (gameEntity instanceof EntityAvatar) {
            EntityAvatar entityAvatar2 = (EntityAvatar)gameEntity;
            avatar = entityAvatar2.getAvatar();
        } else {
            avatar = null;
        }
        Avatar avatar2 = avatar;
        float f = SkirkCunningHelper.skillGainFor(avatar2);
        SkirkCunningHelper.grantFromSkillOnly(gameEntity, f);
        if (gameEntity instanceof EntityAvatar) {
            entityAvatar = (EntityAvatar)gameEntity;
            try {
                float f2 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_ICE_ENERGY);
                if (f2 < 1.0f) {
                    f2 = 100.0f;
                }
                entityAvatar.addEnergy(f2, PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, true);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        SkirkCunningHelper.ensureBarCap(gameEntity);
        SpecialEnergyBarHelper.ensureAndSync(gameEntity);
        SkirkCunningHelper.syncNyxFromSpecial(gameEntity);
        if (gameEntity instanceof EntityAvatar && (entityAvatar = (EntityAvatar)gameEntity).getPlayer() != null) {
            Grasscutter.getLogger().debug("SkirkCunning: +{} from E (bridge) uid={} cur={}", new Object[]{(int)f, entityAvatar.getPlayer().getUid(), Float.valueOf(gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY))});
        }
    }

    public static void onSkillStart(Player player, int n, EntityAvatar entityAvatar) {
        if (player == null || entityAvatar == null || !SkirkCunningHelper.isSkirk((GameEntity)entityAvatar)) {
            return;
        }
        if (n == 11145) {
            SkirkCunningBridge.onBurstSkill(player, entityAvatar);
        } else if (n == 11142 || n == 11147) {
            SkirkCunningBridge.onESkillUiSync(player, entityAvatar, n);
        }
        SkirkCunningBridge.tickModeDrain(player, entityAvatar);
    }

    public static boolean isInSevenPhaseFlash(GameEntity gameEntity) {
        if (gameEntity == null) {
            return false;
        }
        try {
            Float f = (Float)gameEntity.getGlobalAbilityValues().get(TRANSFORM_FLAG);
            return f != null && f.floatValue() > 0.5f;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public static void clearFlashForCast(Player player, GameEntity gameEntity) {
        if (gameEntity == null) {
            return;
        }
        try {
            gameEntity.getGlobalAbilityValues().put(TRANSFORM_FLAG, Float.valueOf(0.0f));
            gameEntity.onAbilityValueUpdate();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (player != null) {
            try {
                player.sendPacket((BasePacket)new PacketServerGlobalValueChangeNotify(gameEntity, TRANSFORM_FLAG, 0.0f));
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        Grasscutter.getLogger().debug("SkirkCunning: clear Flash flag only uid={}", (Object)(player != null ? player.getUid() : -1));
    }

    public static void unlockBurstUi(Player player, GameEntity gameEntity) {
        EntityAvatar entityAvatar;
        if (player == null || gameEntity == null || !SkirkCunningHelper.isSkirk(gameEntity)) {
            return;
        }
        SpecialEnergyBarHelper.ensureAndSync(gameEntity);
        SkirkCunningHelper.syncNyxFromSpecial(gameEntity);
        try {
            player.sendPacket((BasePacket)new PacketCanUseSkillNotify(true));
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (gameEntity instanceof EntityAvatar && (entityAvatar = (EntityAvatar)gameEntity).getAvatar() != null) {
            try {
                Avatar avatar = entityAvatar.getAvatar();
                Int2IntArrayMap int2IntArrayMap = new Int2IntArrayMap();
                int2IntArrayMap.put(11145, 1);
                int2IntArrayMap.put(11147, 1);
                player.sendPacket((BasePacket)new PacketAvatarSkillInfoNotify(avatar.getGuid(), (Int2IntMap)int2IntArrayMap));
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().warn("Skirk unlockBurstUi skillInfo failed", throwable);
            }
        }
        Grasscutter.getLogger().debug("SkirkCunning: unlockBurstUi uid={} curSE={}", (Object)player.getUid(), (Object)Float.valueOf(gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY)));
    }

    public static void onBurstSkillStart(Player player, int n, GameEntity gameEntity) {
        EntityAvatar entityAvatar;
        if (player == null || !(gameEntity instanceof EntityAvatar) || !SkirkCunningHelper.isSkirk((GameEntity)(entityAvatar = (EntityAvatar)gameEntity))) {
            return;
        }
        SkirkCunningBridge.onBurstSkill(player, entityAvatar);
    }

    private static void grantFromSkillOnly(GameEntity gameEntity, float f) {
        if (gameEntity == null || f == 0.0f) {
            return;
        }
        int n = gameEntity.getId();
        long l = System.currentTimeMillis();
        Long l2 = lastSkillGrantMs.get(n);
        if (l2 != null && l - l2 < 900L) {
            Grasscutter.getLogger().debug("SkirkCunning: skip duplicate E grant within debounce");
            return;
        }
        lastSkillGrantMs.put(n, l);
        Map map = gameEntity.getGlobalAbilityValues();
        map.put(NYX_MIN, Float.valueOf(0.0f));
        map.put(NYX_MAX, Float.valueOf(100.0f));
        SpecialEnergyBarHelper.ensureAndSync(gameEntity);
        gameEntity.addSpecialEnergy(f);
        SpecialEnergyBarHelper.ensureAndSync(gameEntity);
        SkirkCunningHelper.syncNyxFromSpecial(gameEntity);
    }

    public static void ensureC2ExtraEnergySpecial(Ability ability, GameEntity gameEntity) {
        Avatar avatar;
        if (ability == null || !SkirkCunningHelper.isSkirk(gameEntity)) {
            return;
        }
        if (gameEntity instanceof EntityAvatar entityAvatar) {
            avatar = entityAvatar.getAvatar();
        } else {
            avatar = null;
        }
        if (!SkirkCunningHelper.hasConstellation2(avatar)) {
            return;
        }
        try {
            ability.getAbilitySpecials().put(C2_EXTRA_ENERGY_KEY, 10.0f);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    /**
     * The passive talent should restore 8 Serpent's Subtlety when a Void Rift is absorbed.
     *
     * <p>In the config {@code SkirkNew_Pickable_Energy_Revive} defaults to 0, and {@code AddSpecialEnergy}
     * carries both {@code "ratio": 0.0} and {@code "value": "SkirkNew_Pickable_Energy_Revive"}.
     * Gson binds only ratio and drops the string key, so special is supplied here and read explicitly in
     * {@link emu.grasscutter.game.ability.actions.ActionAddSpecialEnergy}.
     */
    public static void ensurePickableEnergyReviveSpecial(Ability ability, GameEntity gameEntity) {
        if (ability == null || !SkirkCunningHelper.isSkirk(gameEntity)) {
            return;
        }
        if (!(gameEntity instanceof EntityAvatar entityAvatar)) {
            return;
        }
        Avatar avatar = entityAvatar.getAvatar();
        if (avatar == null || avatar.getProudSkillList() == null
                || !avatar.getProudSkillList().contains(PERMANENT_SKILL_1_ID)) {
            return;
        }
        if (!SkirkCunningHelper.isPickableEnergyAbility(ability)) {
            return;
        }
        try {
            Float current = ability.getAbilitySpecials().get(PICKABLE_ENERGY_REVIVE_KEY);
            if (current == null || current.floatValue() < 0.5f) {
                ability.getAbilitySpecials().put(PICKABLE_ENERGY_REVIVE_KEY, PICKABLE_ENERGY_REVIVE);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Whether this is Avatar_SkirkNew_Pickable_Handler, which declares the rift energy special. */
    public static boolean isPickableEnergyAbility(Ability ability) {
        if (ability == null) {
            return false;
        }
        try {
            if (ability.getAbilitySpecials() != null
                    && ability.getAbilitySpecials().containsKey(PICKABLE_ENERGY_REVIVE_KEY)) {
                return true;
            }
            AbilityData data = ability.getData();
            return data != null
                    && data.abilityName != null
                    && data.abilityName.contains("Pickable");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * How much Serpent's Subtlety absorbing a Void Rift should restore.
     * Returns 0 for a non-Pickable ability, a missing passive, or the placeholder 0 still in the config.
     */
    public static float resolvePickableEnergyRevive(Ability ability) {
        if (!SkirkCunningHelper.isPickableEnergyAbility(ability)) {
            return 0.0f;
        }
        try {
            Float revive = ability.getAbilitySpecials().get(PICKABLE_ENERGY_REVIVE_KEY);
            if (revive != null && revive.floatValue() >= 0.5f) {
                return revive.floatValue();
            }
        } catch (Throwable ignored) {
        }
        return 0.0f;
    }

    /** Finds Avatar_SkirkNew_Pickable_Handler on the entity, for the meta-energy fallback. */
    public static Ability findPickableHandlerAbility(GameEntity gameEntity) {
        if (gameEntity == null) {
            return null;
        }
        try {
            List<?> list = gameEntity.getInstancedAbilities();
            if (list == null) {
                return null;
            }
            for (Object entry : list) {
                if (!(entry instanceof Ability ability)) {
                    continue;
                }
                AbilityData data = ability.getData();
                if (data != null
                        && data.abilityName != null
                        && data.abilityName.contains("Pickable_Handler")) {
                    return ability;
                }
                if (SkirkCunningHelper.isPickableEnergyAbility(ability)
                        && ability.getAbilitySpecials() != null
                        && ability.getAbilitySpecials().containsKey(PICKABLE_ENERGY_REVIVE_KEY)) {
                    return ability;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Safety net kept for call sites that detect a Pickable absorb without going through
     * {@code AddSpecialEnergy}. Prefer {@link AbilityManager#fireAbilityOnAbilityStart}.
     */
    public static void onPickableAbilityTriggered(Player player, GameEntity target, String abilityName) {
        if (player == null || abilityName == null || !abilityName.contains("Pickable_Handler")) {
            return;
        }
        grantPickableAbsorb(player, target, "trigger:" + abilityName);
    }

    /**
     * Client muteRemoteAction often skips AddSpecialEnergy. Only trust the absorb latch
     * {@code _ABILITY_SkirkNew_Pickable_Count} (0 to 1 when Avatar_SkirkNew_Pickable starts).
     *
     * <p>Do NOT use {@code Pickable_Count_Temp}: that is a nearby-rift scan counter updated every
     * think interval and was incorrectly topping the bar to 100.
     */
    public static void onPickableGlobalFloat(
            Player player, GameEntity entity, String key, float previous, float value) {
        if (player == null || entity == null || key == null) {
            return;
        }
        if ("_ABILITY_SkirkNew_Pickable_Count".equals(key)) {
            if (value >= 0.5f && previous < 0.5f) {
                grantPickableAbsorb(player, entity, "gv:Pickable_Count");
            }
        }
    }

    private static void grantPickableAbsorb(Player player, GameEntity target, String reason) {
        EntityAvatar skirk = null;
        if (target instanceof EntityAvatar entityAvatar && isSkirk(entityAvatar)) {
            skirk = entityAvatar;
        } else if (player.getTeamManager() != null) {
            for (EntityAvatar member : player.getTeamManager().getActiveTeam()) {
                if (isSkirk(member)) {
                    skirk = member;
                    break;
                }
            }
        }
        if (skirk == null) {
            return;
        }
        Avatar avatar = skirk.getAvatar();
        if (avatar == null || avatar.getProudSkillList() == null
                || !avatar.getProudSkillList().contains(PERMANENT_SKILL_1_ID)) {
            return;
        }
        Ability handler = findPickableHandlerAbility(skirk);
        if (handler != null) {
            ensurePickableEnergyReviveSpecial(handler, skirk);
        }
        float revive = handler != null ? resolvePickableEnergyRevive(handler) : PICKABLE_ENERGY_REVIVE;
        if (revive < 0.5f) {
            revive = PICKABLE_ENERGY_REVIVE;
        }
        if (!tryMarkPickableGrant(skirk.getId())) {
            return;
        }
        SkirkCunningBridge.applyDelta(player, skirk, revive);
        Grasscutter.getLogger().debug(
                "Skirk pickable absorb +{} ({}) uid={} cur={}",
                (int) revive,
                reason,
                player.getUid(),
                skirk.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    /** Shared dedupe for GV latch and AddSpecialEnergy so one absorb cannot grant twice. */
        /** Only one +8 per absorb, so the GV and AddSpecialEnergy paths cannot both fire. */
    public static boolean tryMarkPickableGrant(int entityId) {
        long now = System.currentTimeMillis();
        Long last = lastPickableSafetyGrantMs.put(entityId, now);
        return last == null || now - last >= 150L;
    }

    private static final ConcurrentHashMap<Integer, Long> lastPickableSafetyGrantMs = new ConcurrentHashMap<>();

        /** Still recognised as rift energy when the config writes ratio as a DynamicFloat of 0. */
    public static boolean isPickableEnergyReviveRatio(DynamicFloat dynamicFloat) {
        if (dynamicFloat == null || !dynamicFloat.isDynamic()) {
            return false;
        }
        try {
            List<DynamicFloat.StackOp> list = dynamicFloat.getOps();
            if (list == null) {
                return false;
            }
            for (DynamicFloat.StackOp stackOp : list) {
                if (stackOp == null || stackOp.sValue == null) {
                    continue;
                }
                if (PICKABLE_ENERGY_REVIVE_KEY.equals(stackOp.sValue)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean tryDebouncedSkillGain(GameEntity gameEntity, float f) {
        if (!SkirkCunningHelper.isSkirk(gameEntity)) {
            return false;
        }
        if (f < -0.01f) {
            return false;
        }
        int n = gameEntity.getId();
        long l = System.currentTimeMillis();
        if (f >= 40.0f && f <= 70.0f) {
            Avatar avatar;
            Long l2 = lastSkillGrantMs.get(n);
            if (l2 != null && l - l2 < 900L) {
                Grasscutter.getLogger().debug("SkirkCunning: skip duplicate base +{} within debounce; cur={}", (Object)Float.valueOf(f), (Object)Float.valueOf(gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY)));
                SkirkCunningHelper.ensureBarCap(gameEntity);
                return true;
            }
            lastSkillGrantMs.put(n, l);
            if (gameEntity instanceof EntityAvatar) {
                EntityAvatar entityAvatar = (EntityAvatar)gameEntity;
                avatar = entityAvatar.getAvatar();
            } else {
                avatar = null;
            }
            Avatar avatar2 = avatar;
            boolean bl = SkirkCunningHelper.hasConstellation2(avatar2);
            float f2 = f;
            if (bl) {
                f2 += 10.0f;
                lastC2GrantMs.put(n, l);
            }
            SkirkCunningHelper.doGrant(gameEntity, f2);
            Grasscutter.getLogger().debug("SkirkCunning: config +{} (base={} c2={}) entity={} cur={}", new Object[]{Float.valueOf(f2), Float.valueOf(f), bl, n, Float.valueOf(gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY))});
            return true;
        }
        if (f >= 9.5f && f <= 10.5f) {
            Avatar avatar;
            Object object;
            if (gameEntity instanceof EntityAvatar entityAvatar) {
                avatar = entityAvatar.getAvatar();
            } else {
                avatar = null;
            }
            if (!SkirkCunningHelper.hasConstellation2(avatar)) {
                SkirkCunningHelper.ensureBarCap(gameEntity);
                return true;
            }
            object = lastC2GrantMs.get(n);
            if (object != null && l - (Long)object < 900L) {
                Grasscutter.getLogger().debug("SkirkCunning: skip duplicate C2 +{} within debounce", (Object)Float.valueOf(f));
                SkirkCunningHelper.ensureBarCap(gameEntity);
                return true;
            }
            lastC2GrantMs.put(n, l);
            SkirkCunningHelper.doGrant(gameEntity, f);
            Grasscutter.getLogger().debug("SkirkCunning: config C2 +{} cur={}", (Object)Float.valueOf(f), (Object)Float.valueOf(gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY)));
            return true;
        }
        return false;
    }

    private static void doGrant(GameEntity gameEntity, float f) {
        Map map = gameEntity.getGlobalAbilityValues();
        map.put(NYX_MIN, Float.valueOf(0.0f));
        map.put(NYX_MAX, Float.valueOf(100.0f));
        SpecialEnergyBarHelper.ensureAndSync(gameEntity);
        gameEntity.addSpecialEnergy(f);
        SpecialEnergyBarHelper.ensureAndSync(gameEntity);
        SkirkCunningHelper.syncNyxFromSpecial(gameEntity);
    }

    public static void syncNyxFromSpecial(GameEntity gameEntity) {
        if (gameEntity == null) {
            return;
        }
        Map map = gameEntity.getGlobalAbilityValues();
        map.put(NYX_MIN, Float.valueOf(0.0f));
        map.put(NYX_MAX, Float.valueOf(100.0f));
        float f = gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
        float f2 = Math.max(0.0f, Math.min(100.0f, f));
        Float f3 = (Float)map.get(NYX_KEY);
        Float f4 = (Float)map.get(CUR_SPECIAL_GV);
        boolean bl = f3 == null || Math.abs(f3.floatValue() - f2) >= 0.05f;
        boolean bl2 = f4 == null || Math.abs(f4.floatValue() - f2) >= 0.05f;
        map.put(NYX_KEY, Float.valueOf(f2));
        map.put(CUR_SPECIAL_GV, Float.valueOf(f2));
        map.put(OVERFLOW_GV, Float.valueOf(f2));
        map.put("_ABILITY_Mavuika_BurstEnergy", Float.valueOf(f2));
        if (bl || bl2) {
            gameEntity.onAbilityValueUpdate();
        }
        Player player = null;
        if (gameEntity.getScene() != null) {
            player = gameEntity.getScene().getHost();
        }
        if (player == null && gameEntity instanceof EntityAvatar) {
            EntityAvatar entityAvatar = (EntityAvatar)gameEntity;
            player = entityAvatar.getPlayer();
        }
        if (player != null && (bl || bl2)) {
            player.sendPacket((BasePacket)new PacketServerGlobalValueChangeNotify(gameEntity, NYX_KEY, f2));
            player.sendPacket((BasePacket)new PacketServerGlobalValueChangeNotify(gameEntity, CUR_SPECIAL_GV, f2));
            player.sendPacket((BasePacket)new PacketServerGlobalValueChangeNotify(gameEntity, OVERFLOW_GV, f2));
            player.sendPacket((BasePacket)new PacketServerGlobalValueChangeNotify(gameEntity, "_ABILITY_Mavuika_BurstEnergy", f2));
        }
    }

    public static void forcePlayBurst(Player player, GameEntity gameEntity, int n) {
        if (player == null || gameEntity == null || !SkirkCunningHelper.isSkirk(gameEntity)) {
            return;
        }
        SkirkCunningHelper.syncNyxFromSpecial(gameEntity);
        try {
            player.sendPacket((BasePacket)new PacketCanUseSkillNotify(true));
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        SkirkCunningHelper.broadcastAnimatorTrigger(gameEntity, "DoSkill");
        SkirkCunningHelper.broadcastAnimatorTrigger(gameEntity, "SkinControl_ElementalBurst");
        AbilityManager abilityManager = player.getAbilityManager();
        Ability ability = SkirkCunningHelper.findAbility(gameEntity, "Avatar_SkirkNew_ElementalBurst");
        if (ability == null) {
            try {
                abilityManager.addAbilityToEntity(gameEntity, "Avatar_SkirkNew_ElementalBurst");
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().warn("Skirk FORCE add ElementalBurst failed", throwable);
            }
            ability = SkirkCunningHelper.findAbility(gameEntity, "Avatar_SkirkNew_ElementalBurst");
        }
        if (ability == null) {
            Grasscutter.getLogger().warn("Skirk FORCE: ElementalBurst ability missing on entity");
            return;
        }
        SkirkCunningHelper.applyModifierOnAdded(abilityManager, ability, gameEntity, "SkirkNew_ElementalBurst_Special", true);
        SkirkCunningHelper.applyModifierOnAdded(abilityManager, ability, gameEntity, "SkirkNew_ElementalBurst_BS_VFX", true);
        SkirkCunningHelper.applyModifierOnAdded(abilityManager, ability, gameEntity, "SkirkNew_ElementalBurst_Special_Camera", true);
        SkirkCunningHelper.applyModifierOnAdded(abilityManager, ability, gameEntity, "SkirkNew_ElementalBurst_Special_Buff_Handler", true);
        SkirkCunningHelper.applyModifierOnAdded(abilityManager, ability, gameEntity, "SkirkNew_ElementalBurst_Special_FX_OnTeam", true);
        Grasscutter.getLogger().debug("SkirkCunning: FORCE play burst skill={} uid={} flash={} curSE={}", new Object[]{n, player.getUid(), SkirkCunningHelper.isInSevenPhaseFlash(gameEntity), Float.valueOf(gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY))});
    }

    private static Ability findAbility(GameEntity gameEntity, String string) {
        List<?> list = gameEntity.getInstancedAbilities();
        if (list == null) {
            return null;
        }
        for (Object entry : list) {
            if (!(entry instanceof Ability ability)) {
                continue;
            }
            if (ability.getData() == null || !string.equals(ability.getData().abilityName)) continue;
            return ability;
        }
        return null;
    }

    private static void applyModifierOnAdded(AbilityManager abilityManager, Ability ability, GameEntity gameEntity, String string, boolean bl) {
        if (abilityManager == null || ability == null || gameEntity == null || string == null) {
            return;
        }
        AbilityData abilityData = ability.getData();
        if (abilityData == null || abilityData.modifiers == null) {
            return;
        }
        AbilityModifier abilityModifier = (AbilityModifier)abilityData.modifiers.get(string);
        if (abilityModifier == null) {
            Grasscutter.getLogger().debug("Skirk FORCE missing modifier {}", (Object)string);
            return;
        }
        try {
            AbilityModifierController abilityModifierController = new AbilityModifierController(ability, abilityData, abilityModifier);
            ability.getModifiers().put(string, abilityModifierController);
            if (abilityModifier.onAdded != null) {
                for (AbilityModifier.AbilityModifierAction abilityModifierAction : abilityModifier.onAdded) {
                    if (abilityModifierAction == null || bl && abilityModifierAction.type == AbilityModifier.AbilityModifierAction.Type.AvatarSkillStart || bl && abilityModifierAction.type != null && abilityModifierAction.type.name().contains("ForceUseSkill")) continue;
                    abilityManager.executeAction(ability, abilityModifierAction, ByteString.EMPTY, gameEntity);
                }
            }
            gameEntity.onAddAbilityModifier(abilityModifier);
            Grasscutter.getLogger().debug("Skirk FORCE applied modifier {}", (Object)string);
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("Skirk FORCE apply {} failed", (Object)string, (Object)throwable);
        }
    }

    public static void broadcastAnimatorTrigger(GameEntity gameEntity, String string) {
        if (gameEntity == null || string == null || string.isEmpty()) {
            return;
        }
        if (gameEntity.getScene() == null) {
            return;
        }
        try {
            AnimatorParameterValueInfoOuterClass.AnimatorParameterValueInfo animatorParameterValueInfo = AnimatorParameterValueInfoOuterClass.AnimatorParameterValueInfo.newBuilder().setParaType(9).setBoolVal(true).build();
            EvtAnimatorParameterInfoOuterClass.EvtAnimatorParameterInfo evtAnimatorParameterInfo = EvtAnimatorParameterInfoOuterClass.EvtAnimatorParameterInfo.newBuilder().setEntityId(gameEntity.getId()).setNameId(Utils.animatorHash((String)string)).setValue(animatorParameterValueInfo).build();
            CombatInvokeEntryOuterClass.CombatInvokeEntry combatInvokeEntry = CombatInvokeEntryOuterClass.CombatInvokeEntry.newBuilder().setCombatData(evtAnimatorParameterInfo.toByteString()).setArgumentTypeValue(6).build();
            gameEntity.getScene().broadcastPacket((BasePacket)new PacketCombatInvocationsNotify(combatInvokeEntry));
            Grasscutter.getLogger().debug("Skirk FORCE animator trigger={} hash={} entity={}", new Object[]{string, Utils.animatorHash((String)string), gameEntity.getId()});
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("Skirk FORCE animator {} failed", (Object)string, (Object)throwable);
        }
    }

    public static void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        lastSkillGrantMs.remove(player.getUid());
        lastC2GrantMs.remove(player.getUid());
        if (player.getTeamManager() != null) {
            for (EntityAvatar entityAvatar : player.getTeamManager().getActiveTeam()) {
                if (entityAvatar != null) {
                    clearEntityState(entityAvatar.getId());
                }
            }
        }
    }

    public static void clearEntityState(int entityId) {
        lastSkillGrantMs.remove(entityId);
        lastC2GrantMs.remove(entityId);
    }
}

