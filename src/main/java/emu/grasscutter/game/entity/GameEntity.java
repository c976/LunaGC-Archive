package emu.grasscutter.game.entity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.ChangeHpDebtsReason._ChangeHpDebtsReason;
import emu.grasscutter.net.proto.ChangHpReasonOuterClass.ChangHpReason;
import emu.grasscutter.net.proto.FightPropPairOuterClass.FightPropPair;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.scripts.data.controller.EntityController;
import emu.grasscutter.net.proto.DetailAbilityInfo._DetailAbilityInfo;
import emu.grasscutter.net.proto.PropChangeDetailInfoOuterClass.PropChangeDetailInfo;
import emu.grasscutter.server.event.entity.*;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import it.unimi.dsi.fastutil.ints.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;

import lombok.*;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;

public abstract class GameEntity {
    @Getter private final Scene scene;
    private boolean restrictedFromHealing = false;
    private boolean convertToHpDebt = false;
    @Getter @Setter public int id;
    @Getter @Setter private SpawnDataEntry spawnEntry;
    @Setter private PropChangeDetailInfo propChangeDetailInfo;
    @Getter @Setter private _DetailAbilityInfo detailAbilityInfo;

    @Getter @Setter private int campId;
    @Getter @Setter private int campType;

    @Getter @Setter private int blockId;
    @Getter @Setter private int configId;
    @Getter @Setter private int groupId;

    @Getter @Setter private MotionState motionState;
    @Getter @Setter private int lastMoveSceneTimeMs;

    @Getter @Setter private int lastMoveReliableSeq;

    @Setter private boolean lockHP;
    private boolean modifierLockHP;
    /** Set when an ability modifier with {@code state: Invincible} is active. */
    @Getter @Setter private boolean modifierInvincible;
    /**
     * HP left in the revival-shield stack this entity is holding, or -1 when it has none.
     *
     * <p>Separate from {@link #modifierInvincible} because that flag only says "damage stops here":
     * a shield stack has to soak the hit and lose a layer, which is the only way the fight opens up.
     */
    @Getter @Setter private float shieldPool = -1f;
    private boolean limbo;
    private float limboHpThreshold;
    /**
     * Limbo modifiers currently applied, keyed by ability + modifier name, with the HP ratio each
     * one pins.
     *
     * <p>{@link #limbo} used to be sticky: it was set when a Limbo modifier landed and never
     * recomputed, so an entity whose stage-control modifier ended (the Perpetual Mechanical Array's
     * split, Hu Tao C6) stayed damage-proof below its threshold forever. Tracking which modifiers
     * are still active lets a removal clear exactly the one that ended.
     */
    private final Map<String, Float> activeLimboModifiers = new ConcurrentHashMap<>();

    @Setter(AccessLevel.PROTECTED)
    @Getter
    private boolean isDead = false;

    @Getter @Setter private EntityController entityController;
    @Getter private ElementType lastAttackType = ElementType.None;

    // Both are read and written from the ability thread pool as well as the game thread. A plain
    // ArrayList threw ConcurrentModificationException out of TriggerAbility, and the fastutil map
    // threw a NullPointerException out of HealHP when a modifier was added mid-iteration.
    @Getter private List<Ability> instancedAbilities = new CopyOnWriteArrayList<>();

    @Getter
    private Map<Integer, AbilityModifierController> instancedModifiers = new ConcurrentHashMap<>();

    // Abilities run on a thread pool, so a plain HashMap here threw ConcurrentModificationException
    // out of whichever action happened to be reading the values while another wrote them
    @Getter private Map<String, Float> globalAbilityValues = new ConcurrentHashMap<>();
    /**
     * Global <em>positions</em>, written by SetGlobalPos and read back by a Summon whose born block
     * is keyed on one. Separate from {@link #globalAbilityValues} because that map holds floats and
     * a position is not one.
     */
    @Getter
    private final Map<String, Position> globalAbilityPositions = new ConcurrentHashMap<>();
    private long convertToHpDebtSetAtMs = 0L;

    public GameEntity(Scene scene) {
        this.scene = scene;
        this.motionState = MotionState.MotionState_MOTION_NONE;
    }

    public abstract void initAbilities();

    public EntityType getEntityType() {
        return EntityIdType.toEntityType(this.getId() >> ENTITY_ID_BIT_SHIFT);
    }
    public boolean isConvertToHpDebt() {
        Float forbidFoodHeal = this.getGlobalAbilityValues().get("_ABILITY_Avatar_ForbidFoodHeal");
        long now = System.currentTimeMillis();
        long timeoutMs = 5500L;
        if (forbidFoodHeal != null && forbidFoodHeal > 0f) {
            if (convertToHpDebtSetAtMs <= 0L) {
                convertToHpDebtSetAtMs = now;
            } else if (now - convertToHpDebtSetAtMs > timeoutMs) {
                this.getGlobalAbilityValues().put("_ABILITY_Avatar_ForbidFoodHeal", 0f);
                convertToHpDebt = false;
                convertToHpDebtSetAtMs = 0L;
                return false;
            }
            return true;
        }
        if (!convertToHpDebt) return false;
        if (convertToHpDebtSetAtMs > 0L && now - convertToHpDebtSetAtMs > timeoutMs) {
            convertToHpDebt = false;
            return false;
        }
        return true;
    }

    public float getNyxValue() {
        if (this.getGlobalAbilityValues().containsKey("NyxValue")) {
            return this.getGlobalAbilityValues().get("NyxValue");
        } else {
            Grasscutter.getLogger().debug("NyxValue not found");
            return 0f;
        }
    }

    public void setConvertToHpDebt(boolean convertToHpDebt) {
        this.convertToHpDebt = convertToHpDebt;
        this.convertToHpDebtSetAtMs = convertToHpDebt ? System.currentTimeMillis() : 0L;
    }

    public boolean isConvertToHpDebtRaw() {
        return convertToHpDebt;
    }

    /** Refresh the timeout while an ability still actively uses HP debt conversion. */
    public void touchConvertToHpDebt() {
        Float forbidFoodHeal = this.getGlobalAbilityValues().get("_ABILITY_Avatar_ForbidFoodHeal");
        if (convertToHpDebt || (forbidFoodHeal != null && forbidFoodHeal > 0f)) {
            convertToHpDebtSetAtMs = System.currentTimeMillis();
        }
    }

    public abstract int getEntityTypeId();

    public World getWorld() {
        return this.getScene().getWorld();
    }
        public boolean isRestrictedFromHealing() {
            return restrictedFromHealing;
        }

        public void setRestrictedFromHealing(boolean restricted) {
            this.restrictedFromHealing = restricted;
        }

    public boolean isAlive() {
        return !this.isDead;
    }
    public LifeState getLifeState() {
        return this.isAlive() ? LifeState.LIFE_ALIVE : LifeState.LIFE_DEAD;
    }

    public abstract Int2FloatMap getFightProperties();

    public abstract Position getPosition();

    public abstract Position getRotation();

    // Not every entity carries fight properties, and the ones that do can be asked for them before
    // they are built. Reading one used to throw straight out of whatever ability action asked.
    public void setFightProperty(FightProperty prop, float value) {
        this.setFightProperty(prop.getId(), value);
    }

    public void setFightProperty(int id, float value) {
        var properties = this.getFightProperties();
        if (properties == null) return;

        // Infinite HP is used by some invulnerable entities, but NaN must never enter HP state.
        if ((id == FightProperty.FIGHT_PROP_CUR_HP.getId() && Float.isNaN(value))
                || (id == FightProperty.FIGHT_PROP_CUR_HP_DEBTS.getId() && !Float.isFinite(value))) {
            Grasscutter.getLogger().warn("Rejected invalid HP property: entity={} prop={} value={}",
                    getId(), id, value);
            return;
        }
        properties.put(id, value);
    }

    public void addFightProperty(FightProperty prop, float value) {
        this.setFightProperty(prop.getId(), this.getFightProperty(prop) + value);
    }

    public float getFightProperty(FightProperty prop) {
        var properties = this.getFightProperties();
        return properties == null ? 0f : properties.getOrDefault(prop.getId(), 0f);
    }

    public boolean hasFightProperty(FightProperty prop) {
        var properties = this.getFightProperties();
        return properties != null && properties.containsKey(prop.getId());
    }

    public void addAllFightPropsToEntityInfo(SceneEntityInfo.Builder entityInfo) {
        var properties = this.getFightProperties();
        if (properties == null) return;

        properties.forEach(
                        (key, value) -> {
                            if (key == 0) return;
                            entityInfo.addFightPropList(
                                    FightPropPair.newBuilder().setPropType(key).setPropValue(value).build());
                        });
    }

    protected void setLimbo(float hpThreshold) {
        limbo = true;
        limboHpThreshold = hpThreshold;
    }

    /** Clear death-prevention limbo (e.g. after a temporary stage-control modifier ends). */
    public void clearLimbo() {
        limbo = false;
        limboHpThreshold = 0f;
    }

    public boolean isLimbo() {
        return limbo;
    }

    float getLimboHpThreshold() {
        return limboHpThreshold;
    }

    /**
     * Records that a Limbo modifier is active on this entity, so its removal can be told apart from
     * an unrelated one. Called from the same path that sets the limbo flag.
     */
    public void trackLimboModifier(
            emu.grasscutter.game.ability.Ability ability, String modifierName, float hpThreshold) {
        if (ability == null || modifierName == null) return;
        trackLimboModifier(ability.getData().abilityName, modifierName, hpThreshold);
    }

    void trackLimboModifier(String abilityName, String modifierName, float hpThreshold) {
        activeLimboModifiers.put(abilityName + "|" + modifierName, hpThreshold);
        setLimbo(recomputeLimboThreshold());
    }

    /**
     * Drops one Limbo modifier and re-derives the gate from the ones still active.
     *
     * <p>The most restrictive threshold wins: while a modifier pinning HP at 80% is up, damage has
     * to stop there, so taking the maximum is what keeps the entity alive as long as any limbo
     * modifier remains.
     */
    public void onLimboModifierRemoved(
            emu.grasscutter.game.ability.Ability ability, String modifierName) {
        if (ability == null || modifierName == null) return;
        onLimboModifierRemoved(ability.getData().abilityName, modifierName);
    }

    void onLimboModifierRemoved(String abilityName, String modifierName) {
        // ActionRemoveModifier calls this for every removal, not just limbo ones, and limbo can also
        // be set without being tracked (the AttachTo* mixins and SkirkCunningHelper go through
        // onAddAbilityModifier(data)). Only a tracked entry going away may release the gate, or an
        // unrelated removal would clear a limbo nothing here put in place.
        if (activeLimboModifiers.remove(abilityName + "|" + modifierName) == null) {
            return;
        }
        if (activeLimboModifiers.isEmpty()) {
            clearLimbo();
        } else {
            setLimbo(recomputeLimboThreshold());
        }
    }

    private float recomputeLimboThreshold() {
        float max = 0f;
        for (float threshold : activeLimboModifiers.values()) {
            max = Math.max(max, threshold);
        }
        return max;
    }

    /**
     * Applies fight-prop modifier properties when the owning {@link emu.grasscutter.game.ability.Ability}
     * is known (needed to resolve DynamicFloat ability specials).
     */
    public void onAddAbilityModifier(AbilityModifier data, emu.grasscutter.game.ability.Ability ability, String modifierName) {
        onAddAbilityModifier(data);
        if (ability != null && modifierName != null) {
            emu.grasscutter.game.ability.AbilityMaxHpRatioHelper.onModifierAdded(
                    ability, modifierName, data, this);
            if (data != null && data.state == AbilityModifier.State.Limbo) {
                this.trackLimboModifier(ability, modifierName, limboThresholdOf(data));
            }
        }
    }

    public GameEntity getTrueOwner() {
    if (this instanceof EntityClientGadget gadget) {
        GameEntity owner = gadget.getScene().getEntityById(gadget.getOwnerEntityId());

        return (owner instanceof EntityClientGadget) ? owner.getTrueOwner() : owner;
    }
    return this;
}

    public void onAddAbilityModifier(AbilityModifier data) {
        if (data == null) {
            return;
        }
        // LockHP / Invincible / shield-bar are combat states; they must apply even when the
        // modifier has no Actor_* properties (Hypostasis ShieldModifier is LockHP-only).
        if (data.state == AbilityModifier.State.LockHP) {
            this.modifierLockHP = true;
        }
        if (data.state == AbilityModifier.State.Invincible) {
            this.setModifierInvincible(true);
        }
        try {
            if (emu.grasscutter.game.world.EffigyCombatHelper.isProtectiveModifier(data)
                    && this instanceof EntityMonster monster
                    && emu.grasscutter.game.world.EffigyCombatHelper.isEffigy(monster)) {
                this.setLockHP(true);
            }
        } catch (Throwable ignored) {
        }

        if (data.state == AbilityModifier.State.Limbo) {
            // No ability instance here to resolve a named special against, so an unresolvable
            // one reads as zero. Limbo modifiers without an explicit threshold (e.g. Hu Tao C6)
            // still need death-prevention, so fall back to a tiny floor.
            Grasscutter.getLogger().debug("Limbo set to {}", limboThresholdOf(data));
            this.setLimbo(limboThresholdOf(data));
        }
    }

    /** The HP ratio a Limbo modifier pins, with a tiny floor so death-prevention still applies. */
    private static float limboThresholdOf(AbilityModifier data) {
        float hpThresholdRatio =
                data.properties != null ? data.properties.Actor_HpThresholdRatio.get(0f) : 0f;
        return hpThresholdRatio <= 0.0f ? 1e-6f : hpThresholdRatio;
    }

    /**
     * A gadget whose combat config locks its HP still breaks on a hit that would kill it; Lua groups
     * wait for those deaths. HP locked by a modifier, or on a monster, holds.
     */
    private boolean configLockedGadgetDies(float curHp, float damage) {
        return lockHP && !modifierLockHP && this instanceof EntityBaseGadget && curHp <= damage;
    }

    public boolean isLockHP() {
        return lockHP || modifierLockHP;
    }

    /** Recompute modifier protection without clearing locks set directly by scene mechanics. */
    public void refreshModifierLockHP() {
        this.modifierLockHP = this.instancedModifiers.values().stream()
                .filter(java.util.Objects::nonNull)
                .map(AbilityModifierController::getModifierData)
                .filter(java.util.Objects::nonNull)
                .anyMatch(data -> data.state == AbilityModifier.State.LockHP);
    }

    /** Recompute Invincible from remaining instanced modifiers after a remove. */
    public void refreshModifierInvincible() {
        boolean inv = false;
        if (this.instancedModifiers != null) {
            for (var ctrl : this.instancedModifiers.values()) {
                if (ctrl != null
                        && ctrl.getModifierData() != null
                        && ctrl.getModifierData().state == AbilityModifier.State.Invincible) {
                    inv = true;
                    break;
                }
            }
        }
        this.modifierInvincible = inv;
    }

    protected MotionInfo getMotionInfo() {
        return MotionInfo.newBuilder()
                .setPos(this.getPosition().toProto())
                .setRot(this.getRotation().toProto())
                .setSpeed(Vector.newBuilder())
                .setState(this.getMotionState())
                .build();
    }

    protected void injectIntMotionInfo(SceneEntityInfo.Builder entityInfo) {
        try {
            Position pos = this.getPosition();
            Position rot = this.getRotation();
            if (pos == null || rot == null) return;

            int px = Math.round(pos.getX() * 1000f);
            int py = Math.round(pos.getY() * 1000f);
            int pz = Math.round(pos.getZ() * 1000f);
            int rx = Math.round(rot.getX() * 1000f);
            int ry = Math.round(rot.getY() * 1000f);
            int rz = Math.round(rot.getZ() * 1000f);

            ByteArrayOutputStream posOut = new ByteArrayOutputStream();
            CodedOutputStream posCos = CodedOutputStream.newInstance(posOut);
            posCos.writeInt32(1, px);
            posCos.writeInt32(2, py);
            posCos.writeInt32(3, pz);
            posCos.flush();

            ByteArrayOutputStream rotOut = new ByteArrayOutputStream();
            CodedOutputStream rotCos = CodedOutputStream.newInstance(rotOut);
            rotCos.writeInt32(1, rx);
            rotCos.writeInt32(2, ry);
            rotCos.writeInt32(3, rz);
            rotCos.flush();

            ByteArrayOutputStream msgOut = new ByteArrayOutputStream();
            CodedOutputStream msgCos = CodedOutputStream.newInstance(msgOut);
            msgCos.writeUInt32(1, this.getId());
            msgCos.writeBytes(2, ByteString.copyFrom(posOut.toByteArray()));
            msgCos.writeBytes(3, ByteString.copyFrom(rotOut.toByteArray()));
            msgCos.writeEnum(4, this.getMotionState().getNumber());
            msgCos.flush();

            entityInfo.mergeUnknownFields(
                UnknownFieldSet.newBuilder()
                    .addField(25, UnknownFieldSet.Field.newBuilder()
                        .addLengthDelimited(ByteString.copyFrom(msgOut.toByteArray()))
                        .build())
                    .build());
        } catch (Exception e) {
            Grasscutter.getLogger().error("Failed to inject EntityIntMotionInfo", e);
        }
    }

    public float heal(float amount) {
        return heal(amount, false);
    }

    public synchronized float heal(float amount, boolean mute) {
        if (!Float.isFinite(amount)) {
            Grasscutter.getLogger().warn("Rejected non-finite healing: entity={} scene={} amount={}",
                    getId(), getScene() == null ? 0 : getScene().getId(), amount);
            return 0f;
        }
        if (amount <= 0f) return 0f;
        ClorindeBoLUtil.beforeHeal(this);
        try {
        if (this.getFightProperties() == null) {
            return 0f;
        }

        float toHeal = 0f;
        float toRepay = 0f;
        float curHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float maxHp = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float curHpDebt = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (!Float.isFinite(curHp) || !Float.isFinite(maxHp) || !Float.isFinite(curHpDebt)) {
            Grasscutter.getLogger().warn("Rejected healing with invalid HP state: entity={} hp={} max={} debt={}",
                    getId(), curHp, maxHp, curHpDebt);
            return 0f;
        }

        if (curHp >= maxHp && curHpDebt <= 0) {
            return 0f;
        }

        toRepay = Math.min(amount, curHpDebt);
        toHeal = Math.min(maxHp - curHp, amount - toRepay);
        this.addFightProperty(FightProperty.FIGHT_PROP_CUR_HP, toHeal);
        this.addFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, -toRepay);

        if (toHeal > 0) {
            this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP));
        }
        if (toRepay > 0) {
            this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));

            if (this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) > 0) {
                this.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS, toRepay,
                                                        mute
                                                                ? PropChangeReason.PropChangeReason_PROP_CHANGE_NONE
                                                                : PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,

                                                        _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY
                ));
            } else {
                this.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS, toRepay,
                                                        mute
                                                                ? PropChangeReason.PropChangeReason_PROP_CHANGE_NONE
                                                                : PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,

                                                        _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH
                                                       ));
            }
        }

        return toHeal;
        } finally {
            ClorindeBoLUtil.afterHeal(this);
        }
    }

    public void damage(float amount) {
        GameEntity ownerEntity = resolveOwnerEntity(this);
        this.damage(amount, 0, ElementType.None);
    }
    private GameEntity resolveOwnerEntity(GameEntity owner) {
        if (owner instanceof EntityClientGadget ownerGadget) {

            GameEntity nextOwner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId());
            return resolveOwnerEntity(nextOwner);
        }
        return owner;
    }
      public void addSpecialEnergy(float energy){
       float curSpecialEnergy = getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
       float maxSpecialEnergy = getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY);
       // Nightsoul is spent through here too, as a negative, so it needs a floor as well as a cap.
       curSpecialEnergy = Math.max(0, Math.min(maxSpecialEnergy, curSpecialEnergy + energy));
       setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, curSpecialEnergy);
       this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    public void clearSpecialEnergy(){
        setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 0);
        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    public void damage(float amount, ElementType attackType) {
        this.damage(amount, 0, attackType);
    }

    public void damage(float amount, int killerId, ElementType attackType) {
        this.damage(amount, killerId, attackType, PropChangeReason.PropChangeReason_PROP_CHANGE_NONE, ChangHpReason.ChangHpReason_CHANGE_HP_NONE);
    }

    public void damage(float amount, PropChangeReason propChangeReason, ChangHpReason changeHpReason) {
        this.damage(amount, 0, ElementType.None, propChangeReason, changeHpReason);
    }

    public void damage(float amount, int killerId, ElementType attackType, PropChangeReason propChangeReason, ChangHpReason changeHpReason) {
        damage(amount, killerId, attackType, propChangeReason, changeHpReason, true);
    }

    /** Ability HP costs can ignore LockHP when their resource leaves enableLockHP disabled. */
    public void loseHpByAbility(float amount, boolean respectLockHp) {
        damage(amount, 0, ElementType.None,
                PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                ChangHpReason.ChangHpReason_CHANGE_HP_SUB_ABILITY, respectLockHp);
    }

    private void damage(float amount, int killerId, ElementType attackType,
            PropChangeReason propChangeReason, ChangHpReason changeHpReason, boolean respectLockHp) {

        if (!Float.isFinite(amount)) {
            Grasscutter.getLogger().warn("Rejected non-finite damage: entity={} scene={} attacker={} amount={}",
                    getId(), getScene() == null ? 0 : getScene().getId(), killerId, amount);
            return;
        }
        if (this.getFightProperties() == null || !hasFightProperty(FightProperty.FIGHT_PROP_CUR_HP)) {
            return;
        }

        if (this.modifierInvincible) {
            // A shield stack soaks the hit and loses a layer; a plain Invincible modifier still
            // blocks outright.
            var shieldSetup = MultiShieldHelper.findSetup(this);
            if (shieldSetup != null
                    && MultiShieldHelper.absorb(
                            this,
                            shieldSetup,
                            amount,
                            MultiShieldHelper.effectivenessFor(this, amount, killerId, attackType))) {
                return;
            }
            if (shieldSetup == null) return;
            // The stack just ran out. Breaking it recomputed the flag from the modifiers left, so a
            // separate Invincible modifier (the boss's return-to-born one, say) still blocks the hit.
            if (this.modifierInvincible) return;
            // Nothing else protects it - fall through so this hit lands on the HP bar.
        }

        EntityDamageEvent event =
                new EntityDamageEvent(this, amount, attackType, this.getScene().getEntityById(killerId));
        if (this.getScene() != null) {
            MavuikaSpiritHelper.onAttackHit(this.getScene().getEntityById(killerId));
        }
        event.call();
        if (event.isCanceled()) {
            return;
        }
        if (!Float.isFinite(event.getDamage())) {
            Grasscutter.getLogger().warn("Rejected non-finite damage event: entity={} attacker={} amount={}",
                    getId(), killerId, event.getDamage());
            return;
        }

        float effectiveDamage = 0;
        float curHp = getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        if (limbo) {
            float maxHp = getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            float curRatio = curHp / maxHp;
            if (curRatio > limboHpThreshold) {

                effectiveDamage = event.getDamage();
            }
            if (effectiveDamage >= curHp && limboHpThreshold > .0f) {

                effectiveDamage = curHp - 1;
            }
        } else if (curHp != Float.POSITIVE_INFINITY && (!isLockHP() || !respectLockHp)
                || configLockedGadgetDies(curHp, event.getDamage())) {
            effectiveDamage = event.getDamage();
        }

        if (this instanceof EntityAvatar avatarCandidate) {
            try {
                effectiveDamage =
                        HutaoC6Helper.filterDamage(avatarCandidate, curHp, effectiveDamage);
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[HutaoC6] filterDamage failed", t);
            }
            try {
                effectiveDamage =
                        ShinobuC6Helper.filterDamage(avatarCandidate, curHp, effectiveDamage);
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[ShinobuC6] filterDamage failed", t);
            }
            // Barbara C6 does not cheat death here: officially the character goes down first and is then
            // revived immediately. See Scene.killEntity.
        }

        if (!Float.isFinite(effectiveDamage)) return;
        this.addFightProperty(FightProperty.FIGHT_PROP_CUR_HP, -effectiveDamage);

        this.lastAttackType = attackType;
        this.checkIfDead();
        this.runLuaCallbacks(event);

        this.getScene()
                .broadcastPacket(
                        new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP));

        if (effectiveDamage > 0) {
            GameEntity attacker = this.getScene().getEntityById(killerId);
            ChangHpReason dmgHpReason;
            if (attacker instanceof EntityAvatar) {
                dmgHpReason = ChangHpReason.ChangHpReason_CHANGE_HP_SUB_AVATAR;
            } else if (attacker instanceof EntityMonster) {
                dmgHpReason = ChangHpReason.ChangHpReason_CHANGE_HP_SUB_MONSTER;
            } else {
                dmgHpReason = ChangHpReason.ChangHpReason_CHANGE_HP_SUB_ABILITY;
            }
            this.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
                this, FightProperty.FIGHT_PROP_CUR_HP, -effectiveDamage,
                PropChangeReason.PropChangeReason_PROP_CHANGE_NONE, dmgHpReason));
        }

        if (this instanceof EntityAvatar entityAvatar) {
            // On damage, update HP with a single-property notify so a full AvatarFightPropNotify cannot
            // wipe the Bond of Life bar;
            // when BoL is present, onDamaged separately re-pushes HP_DEBTS and the enhancement tier.
            entityAvatar
                    .getPlayer()
                    .sendPacket(
                            new PacketAvatarFightPropUpdateNotify(
                                    entityAvatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
            try {
                ClorindeBoLUtil.onDamaged(entityAvatar);
            } catch (Throwable ignored) {
            }
        }

        if (this.isDead) {
            this.getScene().killEntity(this, killerId);
        }
    }

    public void checkIfDead() {
        if (this.getFightProperties() == null || !hasFightProperty(FightProperty.FIGHT_PROP_CUR_HP)) {
            return;
        }

        if (this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0f) {
            this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
            float debt = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            if (debt >= 0) {
                this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0f);
                this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
                this.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS, -debt, PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_CLEAR));
            }
            this.isDead = true;
        }
    }

    public void runLuaCallbacks(EntityDamageEvent event) {
        if (entityController != null) {
            entityController.onBeHurt(this, event.getAttackElementType(), true);
        }
    }

    public void move(Position position, Position rotation) {

        this.getPosition().set(position);
        this.getRotation().set(rotation);
    }

    public void onInteract(Player player, GadgetInteractReq interactReq) {}

    public void onCreate() {}

    public void onRemoved() {}

    private int[] parseCountRange(String range) {
        var split = range.split(";");
        if (split.length == 1)
            return new int[] {Integer.parseInt(split[0]), Integer.parseInt(split[0])};
        return new int[] {Integer.parseInt(split[0]), Integer.parseInt(split[1])};
    }

    public boolean dropSubfieldItem(int dropId) {
        var drop = GameData.getDropSubfieldMappingMap().get(dropId);
        if (drop == null) return false;
        var dropTableEntry = GameData.getDropTableExcelConfigDataMap().get(drop.getItemId());
        if (dropTableEntry == null) return false;

        Int2ObjectMap<Integer> itemsToDrop = new Int2ObjectOpenHashMap<>();
        switch (dropTableEntry.getRandomType()) {
            case 0:
                {
                    int weightCount = 0;
                    for (var entry : dropTableEntry.getDropVec()) weightCount += entry.getWeight();

                    int randomValue = new Random().nextInt(weightCount);

                    weightCount = 0;
                    for (var entry : dropTableEntry.getDropVec()) {
                        if (randomValue >= weightCount && randomValue < (weightCount + entry.getWeight())) {
                            var countRange = parseCountRange(entry.getCountRange());
                            itemsToDrop.put(
                                    entry.getItemId(),
                                    Integer.valueOf((new Random().nextBoolean() ? countRange[0] : countRange[1])));
                        }
                    }
                }
                break;
            case 1:
                {
                    // weight is chance out of 10000 (10000 = always). Old check was inverted
                    // (weight < roll), so SubfieldDrop ores/wood never dropped.
                    for (var entry : dropTableEntry.getDropVec()) {
                        if (new Random().nextInt(10000) < entry.getWeight()) {
                            var countRange = parseCountRange(entry.getCountRange());
                            itemsToDrop.put(
                                    entry.getItemId(),
                                    Integer.valueOf((new Random().nextBoolean() ? countRange[0] : countRange[1])));
                        }
                    }
                }
                break;
        }

        for (var entry : itemsToDrop.int2ObjectEntrySet()) {
            var item =
                    new EntityItem(
                            scene,
                            null,
                            GameData.getItemDataMap().get(entry.getIntKey()),
                            getPosition().nearby2d(1f).addY(0.5f),
                            entry.getValue(),
                            true);

            scene.addEntity(item);
        }

        return true;
    }

    public boolean dropSubfield(String subfieldName) {
        var subfieldMapping = GameData.getSubfieldMappingMap().get(getEntityTypeId());
        if (subfieldMapping == null || subfieldMapping.getSubfields() == null) return false;

        for (var entry : subfieldMapping.getSubfields()) {
            if (entry.getSubfieldName().compareTo(subfieldName) == 0) {
                return dropSubfieldItem(entry.getDrop_id());
            }
        }

        return false;
    }

    public void onTick(int sceneTime) {
        if (entityController != null) {
            entityController.onTimer(this, sceneTime);
        }
    }

    public int onClientExecuteRequest(int param1, int param2, int param3) {
        if (entityController != null) {
            return entityController.onClientExecuteRequest(this, param1, param2, param3);
        }
        return 0;
    }

    public void onDeath(int killerId) {

        EntityDeathEvent event = new EntityDeathEvent(this, killerId);
        event.call();

        if (entityController != null) {
            entityController.onDie(this, getLastAttackType());
        }

        this.isDead = true;
    }

    public void onAbilityValueUpdate() {

    }

    public abstract SceneEntityInfo toProto();

    @Override
    public String toString() {
        return "Entity ID: %s; Group ID: %s; Config ID: %s"
                .formatted(this.getId(), this.getGroupId(), this.getConfigId());
    }
}
