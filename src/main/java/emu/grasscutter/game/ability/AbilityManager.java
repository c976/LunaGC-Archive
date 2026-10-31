package emu.grasscutter.game.ability;

import com.google.protobuf.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.ability.mixins.*;
import emu.grasscutter.game.avatar.Avatar;

import emu.grasscutter.net.proto.AbilityMetaSpecialEnergyOuterClass;
import emu.grasscutter.net.proto.DetailAbilityInfo._DetailAbilityInfo;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaAddAbilityOuterClass.AbilityMetaAddAbility;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.server.packet.send.PacketMonsterSummonTagNotify;
import emu.grasscutter.net.proto.AbilityMetaReInitOverrideMapOuterClass.AbilityMetaReInitOverrideMap;
import emu.grasscutter.net.proto.AbilityMetaSetKilledStateOuterClass.AbilityMetaSetKilledState;
import emu.grasscutter.net.proto.AbilityMetaUpdateMoonOvergrowValueOuterClass.AbilityMetaUpdateMoonOvergrowValue;
import emu.grasscutter.net.proto.AbilityScalarTypeOuterClass.AbilityScalarType;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.server.event.player.PlayerUseSkillEvent;
import emu.grasscutter.net.proto.AbilityStringOuterClass;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.utils.Utils;

import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketAbilityInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneInfoNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.game.props.*;
import io.netty.util.concurrent.FastThreadLocalThread;

import java.util.*;
import java.util.concurrent.*;

import lombok.Getter;

public final class AbilityManager extends BasePlayerManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbilityManager.class);

    private static final HashMap<AbilityModifierAction.Type, AbilityActionHandler> actionHandlers =
        new HashMap<>();
    public static final HashMap<AbilityMixinData.Type, AbilityMixinHandler> mixinHandlers =
        new HashMap<>();

    /**
     * onAdded actions the server is responsible for even when the modifier orchestrates nothing.
     *
     * <p>SetGlobalValue is here because a modifier's marks are what its own later blocks test: the
     * one that spawns Sandrone's robot clears a mark, searches for a target, then spawns only if
     * the mark came back set. Skipping the clear left the mark absent on the first hold, so the
     * spawn never fired until a later attack had written it. Both reasons it used to be left out
     * are gone - writes made here no longer reach the client, and a bare SetGlobalValue no longer
     * reads as 1. Damage, healing and energy stay off the list: the client resolves those itself.
     */
    private static final java.util.EnumSet<AbilityModifierAction.Type> SERVER_OWNED_ON_ADDED =
        java.util.EnumSet.of(
            AbilityModifierAction.Type.Predicated,
            AbilityModifierAction.Type.CreateGadget,
            AbilityModifierAction.Type.SetGlobalValue,
            // A Summon whose born block is keyed on a global position reads that position back from
            // the server's copy. Leaving SetGlobalPos out meant the key was never written, so the
            // summon fell back to the position packed in the notify - none, for a modifier removal -
            // and spawned at the world origin thousands of units from its caster, where it fell
            // through the floor and was culled by die_y. The Perpetual Mechanical Array's split
            // minions were gone before the player could see them.
            AbilityModifierAction.Type.SetGlobalPos);

    /**
     * What a server owned chain may run, at any depth.
     *
     * <p>The nested actions need the same restriction as the top level ones, and more sharply: a
     * Predicated block hands its children whichever entity its search picked, so an unfiltered
     * child that kills or hurts its target would fire straight at the nearest enemy - killing it
     * outright, with no damage event behind it. Only state and spawning is allowed through.
     */
    private static final java.util.EnumSet<AbilityModifierAction.Type> SERVER_OWNED_CHAIN =
        java.util.EnumSet.of(
            AbilityModifierAction.Type.Predicated,
            AbilityModifierAction.Type.CreateGadget,
            AbilityModifierAction.Type.SetGlobalValue,
            AbilityModifierAction.Type.AddGlobalValue,
            AbilityModifierAction.Type.ClearGlobalValue,
            AbilityModifierAction.Type.CopyGlobalValue);

    public static boolean isAllowedInServerOwnedChain(AbilityModifierAction.Type type) {
        return type != null && SERVER_OWNED_CHAIN.contains(type);
    }

    /**
     * Set while the server runs a modifier chain the client did not ask it to run.
     *
     * <p>Values written in there are the server's own bookkeeping - a mark one block sets so a later
     * block can test it - and the client has already worked them out for itself. Pushing them back
     * overwrites state that was correct, so those writes stay local.
     */
    private static final ThreadLocal<Boolean> serverOwnedChain =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isServerOwnedChain() {
        return serverOwnedChain.get();
    }

    /**
     * Runs a block as the server's own bookkeeping.
     *
     * <p>Restores whatever the flag was rather than clearing it, so a chain that opens inside
     * another one does not hand the rest of its parent back to the client half way through.
     */
    public static void runServerOwned(Runnable body) {
        var previous = serverOwnedChain.get();
        serverOwnedChain.set(Boolean.TRUE);
        try {
            body.run();
        } finally {
            serverOwnedChain.set(previous);
        }
    }

    public static final ExecutorService eventExecutor;

    static {
        eventExecutor =
            new ThreadPoolExecutor(
                4,
                4,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(1000),
                FastThreadLocalThread::new,
                new ThreadPoolExecutor.AbortPolicy());

        registerHandlers();
    }

    private boolean abilityInvulnerable = false;
    private int burstCasterId;
    private int burstSkillId;

    private long arlecchinoChargedAttackTime = 0L;
    private long arlecchinoESkillTime = 0L;

    /**
     * Mixins the server runs itself when the client attaches the modifier carrying them.
     *
     * <p>Most mixins are a reaction the client reports: it invokes the local id and the server runs the
     * handler. {@code TryEnterVehicleMixin} is the other shape - the client attaches {@code Avatar_Perform}
     * and then only plays an animation, so nothing ever invokes it. Captures of Natlan's soul-attach
     * (龙之魂附) show the hold producing a scan invoke, four modifier attaches and a
     * {@code MOTION_NATSAURUS_ENTERING} state change, and no invoke against local id 4 at all. Without the
     * server firing it the animation runs out and the camera falls back to the character.
     */
    private static final java.util.EnumSet<AbilityMixinData.Type> SERVER_OWNED_MIXIN_ON_ATTACH =
        java.util.EnumSet.of(AbilityMixinData.Type.TryEnterVehicleMixin);

    /**
     * The entity the scan mixin last picked up, held between the scan invoke and the modifier attach that
     * follows it. The attach carries no reference to the Saurian, so this is the only link between the two.
     */
    @Getter private volatile GameEntity scanTarget;

    public void setScanTarget(GameEntity scanTarget) {
        this.scanTarget = scanTarget;
    }

    public AbilityManager(Player player) {
        super(player);
        removePendingEnergyClear();
    }

    public void removePendingEnergyClear() {
        this.burstCasterId = 0;
        this.burstSkillId = 0;
    }

    public boolean isAbilityInvulnerable() {
        try {
            if (BurstInvulnHelper.isArmed(this)) return true;
        } catch (Throwable ignored) {
            // Compatibility hook must never interrupt ability processing.
        }
        return this.abilityInvulnerable;
    }

    private void onPossibleElementalBurst(Ability ability, AbilityModifier modifier, int entityId) {

        if (ability == null) {
            Grasscutter.getLogger().trace("possible elemental burst is null");
            return;
        }

        if (this.burstCasterId == 0) return;

        boolean skillInvincibility = modifier.state == AbilityModifier.State.Invincible;
        if (modifier.onAdded != null) {
            skillInvincibility |=
                Arrays.stream(modifier.onAdded)
                    .filter(
                        action ->
                            action.type == AbilityModifierAction.Type.AttachAbilityStateResistance
                                && action.resistanceListID == 11002)
                    .toList()
                    .size()
                    > 0;
        }

        if (this.burstCasterId == entityId
            && (ability.getAvatarSkillStartIds().contains(this.burstSkillId) || skillInvincibility)) {
            Grasscutter.getLogger()
                .trace(
                    "Caster ID's {} burst successful, setting invulnerability",
                    entityId);
            this.abilityInvulnerable = true;
            int confirmedSkillId = this.burstSkillId;
            this.removePendingEnergyClear();
            try {
                GameEntity entity = this.player.getScene().getEntityById(entityId);
                if (entity instanceof EntityAvatar entityAvatar) {
                    this.player
                            .getEnergyManager()
                            .confirmBurstCast(entityAvatar.getAvatar(), confirmedSkillId);
                }
            } catch (Throwable ignored) {
                // Energy confirm must not break invulnerability arming.
            }
        }
    }

    public static void registerHandlers() {
        var handlerClassesAction = Grasscutter.reflector.getSubTypesOf(AbilityActionHandler.class);

        for (var obj : handlerClassesAction) {
            try {
                if (obj.isAnnotationPresent(AbilityAction.class)) {
                    var handler = obj.getDeclaredConstructor().newInstance();
                    for (var abilityAction : obj.getAnnotation(AbilityAction.class).value()) {
                        actionHandlers.put(abilityAction, handler);
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                Grasscutter.getLogger().error("Unable to register handler.", e);
            }
        }

        var handlerClassesMixin = Grasscutter.reflector.getSubTypesOf(AbilityMixinHandler.class);
        for (var obj : handlerClassesMixin) {
            try {
                if (obj.isAnnotationPresent(AbilityMixin.class)) {
                    AbilityMixinData.Type abilityMixin = obj.getAnnotation(AbilityMixin.class).value();
                    mixinHandlers.put(abilityMixin, obj.getDeclaredConstructor().newInstance());
                } else {
                    continue;
                }
            } catch (Exception e) {
                Grasscutter.getLogger().error("Unable to register handler.", e);
            }
        }
    }

    public void executeAction(
        Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        try {
            if (ability != null && target != null) ClorindeBoLUtil.onAbilityAction(ability, target);
        } catch (Throwable ignored) {
            // Character compatibility hooks are best-effort.
        }
        var handler = actionHandlers.get(action.type);
        if (handler == null || ability == null) {
            if (DebugConstants.LOG_MISSING_ABILITY_HANDLERS) {
                Grasscutter.getLogger()
                    .debug("Missing ability action handler for {} (invoker: {}).", action.type, ability);
            }

            return;
        }

        boolean ownerWasLive = isLive(ability.getOwner());
        eventExecutor.submit(
            () -> {
                try {
                    if (ownerWasLive && !isLive(ability.getOwner())) return;
                    if (!handler.execute(ability, action, abilityData, target)) {
                        Grasscutter.getLogger()
                            .debug("Ability execute action failed for {} at {}.", action.type, ability);
                    }
                } catch (Throwable e) {
                    // submit() parks anything thrown in a Future nobody reads, so an action that
                    // blew up left no trace at all - the skill just quietly did nothing.
                    Grasscutter.getLogger()
                        .error("Ability action {} threw at {}.", action.type, ability, e);
                }
            });
    }

    /**
     * Runs an action on the calling thread instead of handing it to the pool.
     *
     * <p>A modifier's actions are a sequence, not a set: one block sets a value the next block
     * reads. Submitted individually they race, and the read lands before the write often enough
     * that whatever they were guarding happens an invocation late, or not at all.
     */
    public void executeActionNow(
        Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var handler = actionHandlers.get(action.type);
        if (handler == null || ability == null) {
            if (DebugConstants.LOG_MISSING_ABILITY_HANDLERS) {
                Grasscutter.getLogger()
                    .debug("Missing ability action handler for {} (invoker: {}).", action.type, ability);
            }

            return;
        }

        try {
            if (!handler.execute(ability, action, abilityData, target)) {
                Grasscutter.getLogger()
                    .debug("Ability execute action failed for {} at {}.", action.type, ability);
            }
        } catch (Throwable e) {
            Grasscutter.getLogger().error("Ability action {} threw at {}.", action.type, ability, e);
        }
    }

    public void executeMixin(Ability ability, AbilityMixinData mixinData, ByteString abilityData) {

        var handler = mixinHandlers.get(mixinData.type);
        GameEntity target = ability.getOwner();

        // This mixin has no handler; it only marks Clorinde and Arlecchino as converting heals to HP debt.
        if (mixinData.type == AbilityMixinData.Type.SwitchHealToHPDebtsMixin
                && target instanceof EntityAvatar avatar
                && (avatar.getAvatar().getAvatarId() == 10000098 || avatar.getAvatar().getAvatarId() == 10000096)) {
            target.setConvertToHpDebt(true);
        }

        if (handler == null) {
            Grasscutter.getLogger()
                .trace("Could not execute ability mixin {} at {}", mixinData.type, ability);
            return;
        }

        boolean ownerWasLive = isLive(target);
        eventExecutor.submit(
            () -> {
                try {
                    if (ownerWasLive && !isLive(target)) return;
                    if (!handler.execute(ability, mixinData, abilityData, target)) {
                        Grasscutter.getLogger()
                            .error("Ability execute action failed for {} at {}.", mixinData.type, ability);
                    }
                } catch (Throwable e) {
                    Grasscutter.getLogger()
                        .error("Ability mixin {} threw at {}.", mixinData.type, ability, e);
                }
            });
    }

    /** Queued work is dropped for an entity that has since left the player's scene or world. */
    private boolean isLive(GameEntity entity) {
        if (entity instanceof EntityAvatar a) {
            return this.player.getWorld() != null && this.player.getTeamManager().getActiveTeam().contains(a);
        }
        // Scene identity, not entity-map membership: queued actions run on a pool in no fixed order,
        // so a gadget's HealHP can run after its own KillSelf removed it, and that heal still counts.
        var scene = this.player.getWorld() == null ? null : this.player.getScene();
        return entity != null && scene != null && entity.getScene() == scene;
    }

    /**
     * Fires the mixins the server owns on a modifier the client just attached.
     *
     * <p>See {@link #SERVER_OWNED_MIXIN_ON_ATTACH} for why these never arrive as an invoke.
     */
    private void runServerOwnedMixinsOnAttach(
            Ability ability, AbilityModifier modifierData, ByteString abilityData) {
        if (ability == null || modifierData == null || modifierData.modifierMixins == null) return;

        for (var mixin : modifierData.modifierMixins) {
            if (mixin == null || mixin.type == null) continue;
            if (!SERVER_OWNED_MIXIN_ON_ATTACH.contains(mixin.type)) continue;
            if (!mixinHandlers.containsKey(mixin.type)) continue;

            executeMixin(ability, mixin, abilityData);
        }
    }

    public void onAbilityInvoke(AbilityInvokeEntry invoke) throws Exception {
        if (invoke.getArgumentType()
            == AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_CHANGE_NYX_VALUE) {
            NyxHelper.handleChangeNyxValue(this.player, invoke);
            return;
        }
        if (invoke.getArgumentType()
            == AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_ADD_SPECIAL_ENERGY_VALUE) {
            this.handleAddSpecialEnergy(invoke);
            return;
        }
        if (invoke.getArgumentType()
            == AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION) {
            TpsWeaponSystem.onAmmunitionInvoke(this.player, invoke);
            return;
        }
        Grasscutter.getLogger()
            .trace(
                "Ability invoke: "
                    + invoke
                    + " "
                    + invoke.getArgumentType()
                    + " ("
                    + invoke.getArgumentTypeValue()
                    + "): "
                    + this.player.getScene().getEntityById(invoke.getEntityId()));
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        try {
            SkirkInvokeLog.maybeLog(this.player, invoke, entity);
        } catch (Throwable ignored) {
            // Logging must never affect the invoke path.
        }
        if (entity instanceof EntityAvatar avatarEntity && avatarEntity.getInstancedAbilities().isEmpty()) {
            initializeEntityAbilities(avatarEntity);
        }
        if (entity != null) {
            Grasscutter.getLogger()
                .trace(
                    "Entity {} has a group of {} and a config of {}.",
                    invoke.getEntityId(),
                    entity.getGroupId(),
                    entity.getConfigId());

            Grasscutter.getLogger()
                .trace(
                    "Invoke type of {} ({}) has entity {}.",
                    invoke.getArgumentType(),
                    invoke.getArgumentTypeValue(),
                    entity.getId());
        } else if (DebugConstants.LOG_ABILITIES) {
            Grasscutter.getLogger()
                .debug(
                    "Invoke type of {} ({}) has no entity. (referring to {})",
                    invoke.getArgumentType(),
                    invoke.getArgumentTypeValue(),
                    invoke.getEntityId());
        }

        if (invoke.getHead().getTargetId() != 0) {
            Grasscutter.getLogger()
                .trace("Target: " + this.player.getScene().getEntityById(invoke.getHead().getTargetId()));
        }
        if (invoke.getArgumentType()
                == AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_CHANGE_SNEZHNAYA_FRIGID_VAR) {
            this.logSnezhnayaFrigidInvoke(invoke);
            emu.grasscutter.game.player.SnezhnayaFrigid.onInvoke(this.player, invoke);
            return;
        }

        if (invoke.getHead().getLocalId() != 0) {
            this.handleServerInvoke(invoke);
            return;
        }

        switch (invoke.getArgumentType()) {
            case AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM -> this.handleOverrideParam(invoke);

            case AbilityInvokeArgument_ABILITY_META_REINIT_OVERRIDEMAP -> this.handleReinitOverrideMap(invoke);
            case AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE -> this.handleModifierChange(invoke);
            case AbilityInvokeArgument_ABILITY_MIXIN_COST_STAMINA -> this.handleMixinCostStamina(invoke);
            case AbilityInvokeArgument_ABILITY_ACTION_GENERATE_ELEM_BALL -> this.handleGenerateElemBall(invoke);
            case AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE -> this.handleGlobalFloatValue(invoke);
            case AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE ->
                this.handleClearGlobalFloatValue(invoke);
            case AbilityInvokeArgument_ABILITY_META_MODIFIER_DURABILITY_CHANGE -> this
                .handleModifierDurabilityChange(invoke);
            case AbilityInvokeArgument_ABILITY_META_ADD_NEW_ABILITY -> this.handleAddNewAbility(invoke);

            case AbilityInvokeArgument_ABILITY_META_SET_KILLED_SETATE -> this.handleKillState(invoke);
            case AbilityInvokeArgument_ABILITY_META_ADD_SPECIAL_ENERGY_VALUE -> this.handleAddSpecialEnergy(invoke);
            case AbilityInvokeArgument_ABILITY_META_UPDATE_MOON_OVERGROW_VALUE ->
                this.handleUpdateMoonOvergrowValue(invoke);

            default -> {
                int typeVal = invoke.getArgumentTypeValue();
                if (typeVal > 100) {
                    Grasscutter.getLogger().trace("UnknownAbilityInvoke: type={} entityId={} dataSize={}",
                        typeVal, invoke.getEntityId(), invoke.getAbilityData().size());
                }
                if (DebugConstants.LOG_MISSING_ABILITIES) {
                    Grasscutter.getLogger()
                        .trace("Missing invoke handler for ability {}.", invoke.getArgumentType().name());
                }
            }
        }
    }

    /** Frigid invokes logged so far this session, capped so a long stay does not flood the log. */
    private int snezhnayaFrigidInvokesLogged = 0;

    /**
     * Logs ABILITY_MIXIN_CHANGE_SNEZHNAYA_FRIGID_VAR invokes raw at debug level (start the server with
     * -debug). Their payload has no named message in the 7.1 protocol, and it is what the server must act
     * on to move the Frigid meter.
     */
    private void logSnezhnayaFrigidInvoke(AbilityInvokeEntry invoke) {
        if (!Grasscutter.getLogger().isDebugEnabled() || this.snezhnayaFrigidInvokesLogged >= 200) return;
        this.snezhnayaFrigidInvokesLogged++;
        var head = invoke.getHead();
        Grasscutter.getLogger()
                .debug(
                        "[SnezhnayaFrigid] invoke #{} entity={} ability={} modifier={} localId={} data={}",
                        this.snezhnayaFrigidInvokesLogged,
                        invoke.getEntityId(),
                        head.getInstancedAbilityId(),
                        head.getInstancedModifierId(),
                        head.getLocalId(),
                        java.util.HexFormat.of().formatHex(invoke.getAbilityData().toByteArray()));
    }

    private void handleClearGlobalFloatValue(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());

        if (entity == null) return;

        var entry = AbilityScalarValueEntry.parseFrom(invoke.getAbilityData());

        if (entry == null) return;
        String key = null;

        if (entry.getKey().hasStr()) {
            key = entry.getKey().getStr();
        } else if (entry.getKey().hasHash()) {
            key = GameData.getAbilityHashes().get(entry.getKey().getHash());
        }

        if (key == null) return;

        // A global reset alone does not identify a burst; it also occurs during normal sync.
        if (isArlecchinoBoLFloatKey(key)
                && entity instanceof EntityAvatar av
                && av.getAvatar() != null
                && av.getAvatar().getAvatarId() == 10000096) {
            synchronized (av) {
                float cur = av.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
                if (cur > 0.5f) {
                    if (!ArlecchinoBurstBoL.isConsumeBlocked(av)) {
                        ArlecchinoBurstBoL.tryPreArmFromAbility(
                                resolveInvocationAbility(entity, invoke), av);
                    }
                    av.getGlobalAbilityValues().put(key, cur);
                    ArlecchinoBoLSync.repinUiBar(av);
                    return;
                }
            }
        }

        entity.getGlobalAbilityValues().remove(key);
        entity.onAbilityValueUpdate();
    }

    private static boolean isArlecchinoBoLFloatKey(String key) {
        return "Cur_HPDebts".equals(key)
                || "_HPDebts".equals(key)
                || "_ABILITY_Cur_HPDebts".equals(key);
    }

    private static Ability resolveInvocationAbility(GameEntity entity, AbilityInvokeEntry invoke) {
        var head = invoke.getHead();
        var modifier = entity.getInstancedModifiers().get(head.getInstancedModifierId());
        if (modifier != null && modifier.getAbility() != null) {
            return modifier.getAbility();
        }
        int index = head.getInstancedAbilityId() - 1;
        var abilities = entity.getInstancedAbilities();
        return index >= 0 && index < abilities.size() ? abilities.get(index) : null;
    }

    /**
     * Mirrors the party's Verdant Dew, which the client owns. Recorded, never echoed: the server
     * evaluates ByTargetGlobalValue(MoonOvergrowPoint_All, Team) and would otherwise read zero.
     */
    private void handleUpdateMoonOvergrowValue(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        var update = AbilityMetaUpdateMoonOvergrowValue.parseFrom(invoke.getAbilityData());
        if (update.getUpdateType() != AbilityMetaUpdateMoonOvergrowValue._UpdateType._UpdateType_SET) return;

        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) entity = this.player.getTeamManager().getEntity();

        entity.getGlobalAbilityValues().put(TeamManager.VERDANT_DEW, update.getFOMPMBNENPH());
        entity.onAbilityValueUpdate();
        try {
            LaumaC1HealHelper.onMoonBloom(this.player);
        } catch (Throwable ignored) {
        }
    }

    private void handleAddSpecialEnergy(AbilityInvokeEntry invoke) throws InvalidProtocolBufferException {
        var head = invoke.getHead();
        AbilityMetaSpecialEnergyOuterClass.AbilityMetaSpecialEnergy abilityMetaSpecialEnergy = AbilityMetaSpecialEnergyOuterClass.AbilityMetaSpecialEnergy.parseFrom(invoke.getAbilityData());
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) {
            Grasscutter.getLogger().trace("Entity not found: {}", invoke.getEntityId());
            return;
        }
        var target = this.player.getScene().getEntityById(head.getTargetId());
        if (target == null) target = entity;
        float specialEnergyAdd = abilityMetaSpecialEnergy.getValue();
        if (NyxHelper.isSkirkEntity(target) && target instanceof EntityAvatar) {
            // Client often sends 0: ability JSON has ratio=0.0 alongside value=Pickable_Energy_Revive,
            // and Gson keeps only ratio. Recover from the Pickable_Handler ability when present.
            if (Math.abs(specialEnergyAdd) < 0.01f) {
                Ability ability = null;
                if (head.getInstancedAbilityId() != 0
                        && (head.getInstancedAbilityId() - 1) < entity.getInstancedAbilities().size()) {
                    ability = entity.getInstancedAbilities().get(head.getInstancedAbilityId() - 1);
                }
                if (ability == null) {
                    ability = SkirkCunningHelper.findPickableHandlerAbility(entity);
                }
                if (ability != null && SkirkCunningHelper.isPickableEnergyAbility(ability)) {
                    SkirkCunningHelper.ensurePickableEnergyReviveSpecial(ability, target);
                    float revive = SkirkCunningHelper.resolvePickableEnergyRevive(ability);
                    if (revive >= 0.5f) {
                        specialEnergyAdd = revive;
                    }
                }
            }
            NyxHelper.syncFromSpecialEnergyInvoke(this.player, target, specialEnergyAdd);
            return;
        }
        target.addSpecialEnergy(specialEnergyAdd);
    }

    public void handleServerInvoke(AbilityInvokeEntry invoke) {
        var head = invoke.getHead();

        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) {
            Grasscutter.getLogger().trace(
                "handleServerInvoke: entity not found: entityId={} localId={} type={}",
                invoke.getEntityId(), head.getLocalId(), invoke.getArgumentType());
            return;
        }

        var target = this.player.getScene().getEntityById(head.getTargetId());
        if (target == null) target = entity;

        Ability ability = null;

        if (head.getInstancedModifierId() != 0
            && entity.getInstancedModifiers().containsKey(head.getInstancedModifierId())) {
            ability = entity.getInstancedModifiers().get(head.getInstancedModifierId()).getAbility();
        }

        if (ability == null
            && head.getInstancedAbilityId() != 0
            && (head.getInstancedAbilityId() - 1) < entity.getInstancedAbilities().size()) {
            ability = entity.getInstancedAbilities().get(head.getInstancedAbilityId() - 1);
        }

        if (ability == null) {
            Grasscutter.getLogger().trace(
                "[InvokeMiss] ability not found: entity={} abilId={} modId={} listSize={}",
                entity.getId(), head.getInstancedAbilityId(), head.getInstancedModifierId(),
                entity.getInstancedAbilities().size());
            return;
        }
        if (ability != null && target != null) {

            var data = ability.getData();

            var detailAbility = _DetailAbilityInfo.newBuilder()
                .setParentAbilityName(AbilityString.newBuilder().setStr(data.abilityName))
                .setInstancedAbilityId(head.getInstancedAbilityId())
                .setInstancedModifierId(head.getInstancedModifierId())
                .setLocalId(head.getLocalId())
                .build();

            target.setDetailAbilityInfo(detailAbility);
        }

        var action = ability.getData().localIdToAction.get(head.getLocalId());
        if (action != null) {
            this.executeAction(ability, action, invoke.getAbilityData(), target);
            return;
        } else {
            var mixin = ability.getData().localIdToMixin.get(head.getLocalId());

            if (mixin != null) {
                Grasscutter.getLogger().trace("Executing mixin: {}", mixin);
                executeMixin(ability, mixin, invoke.getAbilityData());

                return;
            }
        }

        Grasscutter.getLogger().trace(
            "handleServerInvoke: action/mixin not found: localId={} ability={} knownActionIds={} knownMixinIds={}",
            head.getLocalId(),
            ability.getData().abilityName,
            ability.getData().localIdToAction.keySet(),
            ability.getData().localIdToMixin.keySet());
    }

    public void onSkillStart(Player player, int skillId, int casterId) {

        if (player.getUid() != this.player.getUid()) {
            return;
        }

        var currentAvatar = player.getTeamManager().getCurrentAvatarEntity();
        if (currentAvatar == null || currentAvatar.getId() != casterId) {
            return;
        }

        try {
            NyxSkillHook.handle(this, player, skillId, casterId);
        } catch (Throwable ignored) {
            // Compatibility hook must not cancel the normal skill event.
        }
        try {
            ClorindeBoLUtil.onSkillStart(currentAvatar, skillId);
        } catch (Throwable ignored) {
            // Compatibility hook must not cancel the normal skill event.
        }
        try {
            LaumaC1HealHelper.onSkillStart(player, currentAvatar, skillId);
        } catch (Throwable ignored) {
            // Compatibility hook must not cancel the normal skill event.
        }

        // Clorinde BoL: E, Q and Swift Hunt all go through ClorindeBoLUtil. E grants nothing, Q follows the
        // passive, and Swift Hunt grants on firing.
        // The old stock applyClorindeBoL(0.35/0.66) wrongly granted on E and has been dropped.

        var skillData = GameData.getAvatarSkillDataMap().get(skillId);
        if (skillData == null) {
            return;
        }

        var event = new PlayerUseSkillEvent(player, skillData, currentAvatar.getAvatar());
        if (!event.call()) return;

        if (skillData.getCostElemVal() <= 0) {
            return;
        }

        this.burstSkillId = skillId;
        this.burstCasterId = casterId;
        try {
            BurstInvulnHelper.arm(this);
        } catch (Throwable ignored) {
            // Compatibility hook must not cancel the normal skill event.
        }
    }

    public void onSkillEnd(Player player) {

        if (player.getUid() != this.player.getUid()) {
            return;
        }

        if (!this.abilityInvulnerable) {
            try {
                BurstInvulnHelper.clear(this);
            } catch (Throwable ignored) {
                // Compatibility hook is best-effort.
            }
            return;
        }

        this.abilityInvulnerable = false;
        try {
            BurstInvulnHelper.clear(this);
        } catch (Throwable ignored) {
            // Compatibility hook is best-effort.
        }
    }

    private void setAbilityOverrideValue(Ability ability, AbilityScalarValueEntry valueChange) {
        String key = null;
        if (valueChange.getKey().hasStr()) {
            key = valueChange.getKey().getStr();
        } else if (valueChange.getKey().hasHash()) {
            key = GameData.getAbilityHashes().get(valueChange.getKey().getHash());
        }
        if (key == null || key.isEmpty()) {
            Grasscutter.getLogger().trace("TODO: Calculate all the ability value hashes");
            return;
        }

        ability.getAbilitySpecials().put(key, valueChange.getFloatValue());
        Grasscutter.getLogger()
            .trace(
                "Ability {} changed {} to {}",
                ability.getData().abilityName,
                key,
                valueChange.getFloatValue());
    }

    private void handleOverrideParam(AbilityInvokeEntry invoke) throws Exception {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        var head = invoke.getHead();

        if (entity == null) {
            Grasscutter.getLogger().trace("Entity not found: {}", invoke.getEntityId());
            return;
        }

        var instancedAbilityIndex = head.getInstancedAbilityId() - 1;
        if (instancedAbilityIndex < 0 || instancedAbilityIndex >= entity.getInstancedAbilities().size()) {
            Grasscutter.getLogger().trace("Ability not found {}", head.getInstancedAbilityId());
            return;
        }

        var valueChange = AbilityScalarValueEntry.parseFrom(invoke.getAbilityData());

        var ability = entity.getInstancedAbilities().get(instancedAbilityIndex);
        if (ability == null) return;
        setAbilityOverrideValue(ability, valueChange);
    }

    private void handleReinitOverrideMap(AbilityInvokeEntry invoke) throws Exception {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        var head = invoke.getHead();

        if (entity == null) {
            Grasscutter.getLogger().trace("Entity not found: {}", invoke.getEntityId());
            return;
        }

        var valueChanges = AbilityMetaReInitOverrideMap.parseFrom(invoke.getAbilityData());
        Map<String, Float> computedVarOverrides = new HashMap<>();

        if (entity instanceof EntityClientGadget clientGadget) {
            var ownerEntity = player.getScene().getEntityById(clientGadget.getOriginalOwnerEntityId());
            if (ownerEntity instanceof EntityAvatar avatarOwner) {
                var avatar = avatarOwner.getAvatar();
                AvatarSkillDepotData depot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
                List<AbilityInvokeEntry> overrides = new ArrayList<>();

                for (var variableChange : valueChanges.getOverrideMapList()) {
                    String varName = variableChange.getKey().hasStr()
                        ? variableChange.getKey().getStr()
                        : GameData.getAbilityHashes().get(variableChange.getKey().getHash());
                    if (varName == null || varName.isEmpty()) continue;

                    var talentVarList = GameData.getVarNameToTalentVars().get(varName);
                    if (talentVarList == null || talentVarList.isEmpty()) continue;

                    for (var tv : talentVarList) {
                        Integer groupId = GameData.getOpenConfigToProudSkillGroup().get(tv.openConfigName());
                        if (groupId == null) continue;

                        int skillLevel = 1;
                        if (depot != null) {
                            int resolvedGroupId = groupId;
                            skillLevel = depot.getSkillsAndEnergySkill()
                                .filter(sid -> {
                                    var sc = GameData.getAvatarSkillDataMap().get(sid);
                                    return sc != null && sc.getProudSkillGroupId() == resolvedGroupId;
                                })
                                .mapToObj(sid -> avatar.getSkillLevelMap().getOrDefault(sid, 1))
                                .findFirst()
                                .orElse(1);
                        }

                        ProudSkillData proudSkill = GameData.getProudSkillDataMap().get(groupId * 100 + skillLevel);
                        if (proudSkill == null) continue;
                        float[] paramList = proudSkill.getParamList();
                        if (paramList == null || tv.paramIndex() >= paramList.length) continue;

                        float value = paramList[tv.paramIndex()];
                        computedVarOverrides.put(varName, value);
                        overrides.add(AbilityInvokeEntry.newBuilder()
                            .setEntityId(entity.getId())
                            .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM)
                            .setHead(AbilityInvokeEntryHead.newBuilder()
                                .setInstancedAbilityId(head.getInstancedAbilityId())
                                .build())
                            .setAbilityData(AbilityScalarValueEntry.newBuilder()
                                .setKey(AbilityString.newBuilder()
                                    .setStr(varName)
                                    .setHash(Utils.abilityHash(varName))
                                    .build())
                                .setFloatValue(value)
                                .build()
                                .toByteString())
                            .build());
                        break;
                    }
                }

                if (!overrides.isEmpty()) {

                    player.sendPacket(new PacketAbilityInvocationsNotify(overrides));
                }

                if (!computedVarOverrides.isEmpty()) {

                    var talentVarMap = GameData.getAbilityTalentVarMap();
                    Map<String, Set<String>> varToAbilityNames = new HashMap<>();
                    for (var mapEntry : talentVarMap.entrySet()) {
                        String abilName = mapEntry.getKey();
                        for (var tv : mapEntry.getValue()) {
                            if (computedVarOverrides.containsKey(tv.varName())) {
                                varToAbilityNames
                                    .computeIfAbsent(tv.varName(), k -> new HashSet<>())
                                    .add(abilName);
                            }
                        }
                    }

                    List<AbilityInvokeEntry> avatarPatch = new ArrayList<>();
                    var avatarAbils = avatarOwner.getInstancedAbilities();
                    for (int i = 0; i < avatarAbils.size(); i++) {
                        var ab = avatarAbils.get(i);
                        if (ab == null || ab.getData() == null) continue;
                        String abName = ab.getData().abilityName;
                        if (abName == null) continue;

                        boolean matched = false;
                        for (var e : computedVarOverrides.entrySet()) {
                            Set<String> owners = varToAbilityNames.get(e.getKey());
                            if (owners == null || !owners.contains(abName)) continue;
                            avatarPatch.add(AbilityInvokeEntry.newBuilder()
                                .setEntityId(avatarOwner.getId())
                                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM)
                                .setHead(AbilityInvokeEntryHead.newBuilder()
                                    .setInstancedAbilityId(i + 1)
                                    .build())
                                .setAbilityData(AbilityScalarValueEntry.newBuilder()
                                    .setKey(AbilityString.newBuilder()
                                        .setStr(e.getKey())
                                        .setHash(Utils.abilityHash(e.getKey()))
                                        .build())
                                    .setFloatValue(e.getValue())
                                    .build()
                                    .toByteString())
                                .build());
                            ab.getAbilitySpecials().put(e.getKey(), e.getValue());
                            matched = true;
                        }
                        if (matched) {

                        }
                    }
                    if (!avatarPatch.isEmpty()) {
                        player.sendPacket(new PacketAbilityInvocationsNotify(avatarPatch));
                    }
                }
            }
        }

        var instancedAbilityIndex = head.getInstancedAbilityId() - 1;
        if (instancedAbilityIndex < 0 || instancedAbilityIndex >= entity.getInstancedAbilities().size()) {
            Grasscutter.getLogger().trace("Ability not found {}", head.getInstancedAbilityId());
            return;
        }

        var ability = entity.getInstancedAbilities().get(instancedAbilityIndex);
        if (ability != null) {
            for (var variableChange : valueChanges.getOverrideMapList()) {
                setAbilityOverrideValue(ability, variableChange);
            }
            // Avatar REINIT is not covered by the gadget talent patch above; re-apply proud-skill
            // specials so values like Hutao CrimsonPlum_LifeCostRatio are not left at 0.
            if (entity instanceof EntityAvatar avatarOwner) {
                applyAvatarTalentSpecials(ability, avatarOwner.getAvatar());
            }
        }

        var resolvedAbility = ability;
        if (resolvedAbility == null) {
            for (var ab : entity.getInstancedAbilities()) {
                if (ab != null) { resolvedAbility = ab; break; }
            }
        }
        final var finalAbility = resolvedAbility;
        if (finalAbility != null) {
            computedVarOverrides.forEach((k, v) -> finalAbility.getAbilitySpecials().put(k, v));
        }
    }

    private void handleModifierChange(AbilityInvokeEntry invoke) throws Exception {

        var modChange = AbilityMetaModifierChange.parseFrom(invoke.getAbilityData());
        var head = invoke.getHead();

        boolean isRemove = modChange.getAction() == ModifierAction.ModifierAction_REMOVED;
        if ((head.getInstancedAbilityId() == 0 && !isRemove) || head.getInstancedModifierId() > 2000) {
            return;
        }

        // is_serverbuff_modifier has no named counterpart in the 7.0 AbilityInvokeEntryHead.
        if (false) {

            this.player.getScene().broadcastPacket(new PacketAbilityInvocationsNotify(invoke));
            return;
        }

        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) {
            if (DebugConstants.LOG_ABILITIES) {
                Grasscutter.getLogger().debug("Entity not found: {}", invoke.getEntityId());
            }

            return;
        }

        if (modChange.getAction() == ModifierAction.ModifierAction_ADDED) {
            AbilityData instancedAbilityData = null;
            Ability instancedAbility = null;
            boolean fromParentName = false;

            String resolvedParentName = null;
            var parentAbStr = modChange.getParentAbilityName();
            if (!parentAbStr.getStr().isEmpty()) {
                resolvedParentName = parentAbStr.getStr();
            } else if (parentAbStr.hasHash()) {
                resolvedParentName = GameData.getAbilityHashes().get(parentAbStr.getHash());
            }

            if (resolvedParentName != null) {
                instancedAbilityData = GameData.getAbilityData(resolvedParentName);
                fromParentName = true;
            }

            if (instancedAbilityData == null) {
                if (head.getTargetId() != 0) {
                    var targetEntity = this.player.getScene().getEntityById(head.getTargetId());
                    if (targetEntity != null) {
                        // An id of 0 means "no instanced ability" and is common - without the lower
                        // bound that becomes get(-1) rather than a miss.
                        var index = head.getInstancedAbilityId() - 1;
                        if (index >= 0 && index < targetEntity.getInstancedAbilities().size()) {
                            instancedAbility = targetEntity.getInstancedAbilities().get(index);
                            if (instancedAbility != null) instancedAbilityData = instancedAbility.getData();
                        }
                    }
                }
            }

            if (instancedAbilityData == null) {
                var index = head.getInstancedAbilityId() - 1;
                if (index >= 0 && index < entity.getInstancedAbilities().size()) {
                    instancedAbility = entity.getInstancedAbilities().get(index);
                    if (instancedAbility != null) instancedAbilityData = instancedAbility.getData();
                }
            }

            var parentAbilityName = resolvedParentName != null ? resolvedParentName : parentAbStr.getStr();

            if (instancedAbilityData == null) {
                Grasscutter.getLogger().trace("handleModifierChange: no ability data found for entityId={} parentAbility={}", invoke.getEntityId(), parentAbilityName);
                return;
            }

            if (instancedAbility == null || fromParentName) {
                instancedAbility = new Ability(instancedAbilityData, entity, player);
            }

            if (instancedAbilityData.modifiers == null) {
                Grasscutter.getLogger().trace("handleModifierChange: modifiers map is null for ability={} entityId={}", instancedAbilityData.abilityName, invoke.getEntityId());
                return;
            }
            var modifierLocalId = modChange.getModifierLocalId();
            var modifierData = instancedAbilityData.modifierAt(modifierLocalId);
            if (modifierData == null) {
                Grasscutter.getLogger().trace(
                    "handleModifierChange: modifierLocalId={} out of bounds for ability={} (modifierCount={}), entityId={} modId={}",
                    modifierLocalId, instancedAbilityData.abilityName, instancedAbilityData.modifierCount(),
                    invoke.getEntityId(), head.getInstancedModifierId());
                return;
            }

            if (entity.getInstancedModifiers().containsKey(head.getInstancedModifierId())) {
                Grasscutter.getLogger()
                    .trace(
                        "Replacing entity {} modifier id {} with ability {} modifier {}",
                        invoke.getEntityId(),
                        head.getInstancedModifierId(),
                        instancedAbilityData.abilityName,
                        modifierData);
            } else {
                Grasscutter.getLogger()
                    .trace(
                        "Adding entity {} modifier id {} with ability {} modifier {}",
                        invoke.getEntityId(),
                        head.getInstancedModifierId(),
                        instancedAbilityData.abilityName,
                        modifierData);
            }

            if (instancedAbility != null) {
                onPossibleElementalBurst(instancedAbility, modifierData, invoke.getEntityId());
            } else {
                Grasscutter.getLogger().trace("no instanced ability for modifier");
            }

            onPossibleElementalBurst(instancedAbility, modifierData, invoke.getEntityId());

            boolean hasOrchestration = false;
            if (fromParentName && modifierData.onAdded != null) {
                for (var a : modifierData.onAdded) {
                    if (a.type == AbilityModifierAction.Type.AttachModifier
                            || a.type == AbilityModifierAction.Type.ApplyModifier) {
                        hasOrchestration = true;
                        break;
                    }
                }
            }

            if (fromParentName && !hasOrchestration && resolvedParentName != null) {
                outer:
                for (var avatarEntity : this.player.getTeamManager().getActiveTeam()) {
                    for (var a : avatarEntity.getInstancedAbilities()) {
                        if (a != null
                                && a.getData() != null
                                && resolvedParentName.equals(a.getData().abilityName)) {
                            instancedAbility = a;
                            break outer;
                        }
                    }
                }
            }

            AbilityModifierController modifier =
                new AbilityModifierController(instancedAbility, instancedAbilityData, modifierData);

            if (!fromParentName || !hasOrchestration) {
                entity.getInstancedModifiers().put(head.getInstancedModifierId(), modifier);
            }

            runServerOwnedMixinsOnAttach(instancedAbility, modifierData, invoke.getAbilityData());

            // Light-lock gadgets report modifier additions by instance ID, without parentAbilityName.
            // Their Lua receiver and follower cleanup must also run for that explicit client request.
            if (((fromParentName && hasOrchestration)
                    || isLightLockAbility(instancedAbilityData))
                    && modifierData.onAdded != null) {
                final var finalAbility = instancedAbility;
                final var finalEntity = entity;
                for (var a : modifierData.onAdded) {
                    executeActionNow(finalAbility, a, invoke.getAbilityData(), finalEntity);
                }
            } else if (modifierData.onAdded != null) {
                // A modifier whose onAdded neither attaches nor applies another modifier used to run
                // nothing at all here, so anything the server alone is meant to do - spawning an
                // avatar's summon, most visibly - never happened. Only the state and spawn actions
                // are run: damage, healing and energy stay off this path, since the client already
                // resolves those and running them again would double them up.
                final var ownedAbility = instancedAbility;
                final var ownedEntity = entity;
                runServerOwned(
                    () -> {
                        for (var a : modifierData.onAdded) {
                            if (a != null && SERVER_OWNED_ON_ADDED.contains(a.type)) {
                                executeActionNow(ownedAbility, a, invoke.getAbilityData(), ownedEntity);
                            }
                        }
                    });
            }

            // Lauma C1: HealHP usually arrives only as MODIFIER_CHANGE, never through ApplyModifier.
            try {
                if (LaumaC1HealHelper.isLaumaC1Ability(instancedAbilityData.abilityName)) {
                    LaumaC1HealHelper.onModifierAdded(this.player, instancedAbilityData.abilityName, modifierData);
                }
            } catch (Throwable ignored) {
            }

            try {
                String lohenModName =
                        instancedAbilityData.modifierNameAt(modChange.getModifierLocalId());
                if (LohenExtraArtSkillLevelHelper.isExtraArtModifier(lohenModName) && instancedAbility != null) {
                    LohenExtraArtSkillLevelHelper.onModifierApplied(
                            instancedAbility, head.getInstancedModifierId());
                }
            } catch (Throwable ignored) {
            }

            // Max HP ratio props (Yelan C4 / Furina overflow / Columbina C2, etc.) arrive as
            // client MODIFIER_CHANGE, not via ActionAttachModifier - apply here.
            String resolvedModName = null;
            try {
                for (var e : instancedAbilityData.modifiers.entrySet()) {
                    if (e.getValue() == modifierData) {
                        resolvedModName = e.getKey();
                        break;
                    }
                }
                if (resolvedModName == null) {
                    resolvedModName =
                            instancedAbilityData.modifierNameAt(modChange.getModifierLocalId());
                }
                if (resolvedModName != null && AbilityMaxHpRatioHelper.hasMaxHpRatio(modifierData)) {
                    AbilityMaxHpRatioHelper.onModifierAdded(
                            instancedAbility, resolvedModName, modifierData, entity);
                }
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[MaxHPRatio] modifierChange add failed: {}", t.toString());
            }

            // Hypostasis LockHP / Invincible / ShieldBar arrive as client MODIFIER_CHANGE.
            try {
                // Same resolved name the MaxHPRatio block above uses: the limbo bookkeeping keys on
                // ability + modifier name, and a null name would leave the modifier untracked.
                entity.onAddAbilityModifier(modifierData, instancedAbility, resolvedModName);
                emu.grasscutter.game.world.EffigyCombatHelper.onModifiersChanged(entity);
            } catch (Throwable ignored) {
            }

            // Arlecchino 重击回收 (CA mark reclaim): the client only sends the modifier ADD for
            // ExtraAttack_AddHpDebts_1/2 (65%/130%) and never runs the onAdded AddHPDebts itself.
            // AddHPDebts is not in SERVER_OWNED_ON_ADDED, so nobody granted the Bond and 重击回收
            // looked dead. The modifier is attached to the CA's client gadget, so a plain
            // target="Caster" resolves to a non-avatar and the grant is dropped ("Cannot add HP
            // debt to non-avatar entity"). Retarget at the gadget's true owner (Arlecchino) and run
            // the onAdded AddHPDebts on it to restore the reclaim.
            if (resolvedModName != null
                    && ArlecchinoBoLUtil.isExtraAttackAddHpDebtsModifier(resolvedModName)
                    && modifierData.onAdded != null) {
                GameEntity bolCaster = entity != null ? entity.getTrueOwner() : null;
                if (!(bolCaster instanceof EntityAvatar)) {
                    EntityAvatar cur = this.player.getTeamManager().getCurrentAvatarEntity();
                    if (cur != null) {
                        bolCaster = cur;
                    }
                }
                if (bolCaster instanceof EntityAvatar bolAvatar
                        && bolAvatar.getAvatar() != null
                        && bolAvatar.getAvatar().getAvatarId() == 10000096
                        && instancedAbilityData != null) {
                    Ability bolAbility = new Ability(instancedAbilityData, bolAvatar, this.player);
                    for (var bolAction : modifierData.onAdded) {
                        if (bolAction != null
                                && bolAction.type == AbilityModifierAction.Type.AddHPDebts) {
                            executeActionNow(
                                    bolAbility, bolAction, invoke.getAbilityData(), bolAvatar);
                        }
                    }
                    Grasscutter.getLogger()
                            .info(
                                    "[BoL] ExtraAttack reclaim {} → caster uid {} onAdded AddHPDebts",
                                    resolvedModName,
                                    bolAvatar.getAvatar().getAvatarId());
                } else {
                    Grasscutter.getLogger()
                            .info(
                                    "[BoL] ExtraAttack reclaim {} caster unresolved (trueOwner={})",
                                    resolvedModName,
                                    bolCaster);
                }
            }
        } else if (modChange.getAction() == ModifierAction.ModifierAction_REMOVED) {
            AbilityModifierController removed =
                    entity.getInstancedModifiers().remove(head.getInstancedModifierId());
            if (removed != null) {
                emu.grasscutter.game.quest.LiyueAmberQuestSupport.onModifierRemoved(
                        this.player, entity, removed.getAbilityData(), removed.getModifierData());
            }
            if (removed != null && isLightLockAbility(removed.getAbilityData())
                    && removed.getModifierData().onRemoved != null) {
                for (var action : removed.getModifierData().onRemoved) {
                    executeActionNow(removed.getAbility(), action, invoke.getAbilityData(), entity);
                }
            }
            try {
                LohenExtraArtSkillLevelHelper.onModifierRemovedByInstance(
                        this.player, head.getInstancedModifierId());
            } catch (Throwable ignored) {
            }
            try {
                if (removed != null && AbilityMaxHpRatioHelper.hasMaxHpRatio(removed.getModifierData())) {
                    String modName = null;
                    if (removed.getAbilityData() != null) {
                        for (var e : removed.getAbilityData().modifiers.entrySet()) {
                            if (e.getValue() == removed.getModifierData()) {
                                modName = e.getKey();
                                break;
                            }
                        }
                    }
                    if (modName == null) {
                        modName = "instanced_" + head.getInstancedModifierId();
                    }
                    AbilityMaxHpRatioHelper.onModifierRemoved(removed.getAbility(), modName, entity);
                }
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[MaxHPRatio] modifierChange remove failed: {}", t.toString());
            }
            try {
                entity.refreshModifierLockHP();
                entity.refreshModifierInvincible();
                // Same story for limbo: the flag is sticky on the entity, so a limbo modifier the
                // client just retired has to release the gate or the entity never takes damage
                // below its threshold again.
                if (removed != null && removed.getModifierData() != null) {
                    boolean wasLimbo =
                            removed.getModifierData().state
                                    == emu.grasscutter.data.binout.AbilityModifier.State.Limbo;
                    if (wasLimbo) {
                        String limboModName = null;
                        if (removed.getAbilityData() != null) {
                            for (var e : removed.getAbilityData().modifiers.entrySet()) {
                                if (e.getValue() == removed.getModifierData()) {
                                    limboModName = e.getKey();
                                    break;
                                }
                            }
                        }
                        if (limboModName == null && removed.getAbilityData() != null) {
                            limboModName =
                                    removed.getAbilityData()
                                            .modifierNameAt(modChange.getModifierLocalId());
                        }
                        if (limboModName != null) {
                            entity.onLimboModifierRemoved(removed.getAbility(), limboModName);
                        }
                    }
                }
                emu.grasscutter.game.world.EffigyCombatHelper.onModifiersChanged(entity);
            } catch (Throwable ignored) {
            }
        } else {

            Grasscutter.getLogger().debug("Unknown action");
        }
        try {
            ClorindeBoLUtil.onModifierChange(this.player, invoke);
        } catch (Throwable ignored) {
            // Compatibility hook must not affect modifier state.
        }
    }

    private static boolean isLightLockAbility(AbilityData data) {
        if (data == null || data.abilityName == null) return false;
        return switch (data.abilityName) {
            case "Test_LightLock_Point", "Test_LightLock_Seal",
                    "Test_LightLock_Seal_Logic", "Test_LightLock_Bullet" -> true;
            default -> false;
        };
    }

    private void handleMixinCostStamina(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
    }

    private void handleGenerateElemBall(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        // Client energy orbs arrive as GENERATE_ELEM_BALL invokes; the ability-action
        // path alone misses many avatars (Arlecchino / Nicole / etc.).
        this.player.getEnergyManager().handleGenerateElemBall(invoke);
    }

    private void handleGlobalFloatValue(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null && this.player.getTeamManager() != null) {
            var teamEntity = this.player.getTeamManager().getEntity();
            if (teamEntity != null && teamEntity.getId() == invoke.getEntityId()) {
                entity = teamEntity;
            }
        }
        if (entity == null) return;

        var entry = AbilityScalarValueEntry.parseFrom(invoke.getAbilityData());
        if (entry == null) return;

        String key = null;
        if (entry.getKey().hasStr()) {
            key = entry.getKey().getStr();
        } else if (entry.getKey().hasHash()) {
            key = GameData.getAbilityHashes().get(entry.getKey().getHash());
        }

        if (key == null) return;
        if (key.startsWith("SGV_")) return;

        float value = entry.getFloatValue();
        if (Float.isNaN(value)) return;

        // MoonOvergrowPoint_All (dew points) is client-owned and capped at 3 in ability
        // configs. Never force-echo a fake value - that wiped Columbina PermanentSkill_2's
        // mountain dew grants and broke AddGlobalValue(max=3) bookkeeping.

        if ("_ABILITY_Clorinde_Dodge_HealFlag".equals(key) && value == 0f
                && entity instanceof EntityAvatar clorinde
                && clorinde.getAvatar().getAvatarId() == 10000098) {
            // Stock clearing the flag would wrongly grant BoL - delegated to the util, which no-ops.
            applyClorindeBoL(clorinde);
        }

        // Skirk void-rift absorb: AddSpecialEnergy is muteRemoteAction and never reaches the server.
        // The client still syncs pickable count GVs - use those as the grant signal.
        float previous = entity.getGlobalAbilityValues().getOrDefault(key, 0f);
        try {
            SkirkCunningHelper.onPickableGlobalFloat(this.player, entity, key, previous, value);
        } catch (Throwable ignored) {
        }

        // Mavuika: the client drains Nightsoul through GLOBAL_FLOAT, not NyxSet/ChangeNyxValue.
        // The hook must run before the put, otherwise Fighting Spirit cannot convert from that drain.
        try {
            if ("NyxValue".equals(key)
                    || "_ABILITY_Mavuika_IsNyxState".equals(key)
                    || "_ABILITY_Mavuika_IsSpecialMove".equals(key)) {
                MavuikaSpiritHelper.beforeGlobalFloatPut(this.player, entity, key, value);
            }
        } catch (Throwable ignored) {
        }

        if ("NyxValue".equals(key) && value < previous) {
            NightsoulStaminaExempt.markNyxCostActive(entity);
        }

        // Official reclaim is ExtraAttack_AddHpDebts_* via ActionAddHPDebts only.
        // A second grant here previously double-stacked 65%/130% and capped BoL instantly.

        // Preserve authoritative Bond on predicted resets, but only a burst source may arm Q.
        if (isArlecchinoBoLFloatKey(key)
                && entity instanceof EntityAvatar av
                && av.getAvatar() != null
                && av.getAvatar().getAvatarId() == 10000096) {
            synchronized (av) {
                float cur = av.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
                if (cur > 0.5f && value <= Math.max(0.5f, cur * 0.15f)) {
                    if (!ArlecchinoBurstBoL.isConsumeBlocked(av)) {
                        ArlecchinoBurstBoL.tryPreArmFromAbility(
                                resolveInvocationAbility(entity, invoke), av);
                    }
                    entity.getGlobalAbilityValues().put(key, cur);
                    entity.onAbilityValueUpdate();
                    ArlecchinoBoLSync.repinUiBar(av);
                    return;
                }
            }
        }

        entity.getGlobalAbilityValues().put(key, value);
        entity.onAbilityValueUpdate();

        try {
            emu.grasscutter.game.world.EffigyCombatHelper.onGlobalValue(entity, key, value);
        } catch (Throwable ignored) {
        }

        try {
            if (("MoonOvergrowPoint_All".equals(key) || "RGV_TempMoonOvergrowPoint".equals(key))
                    && value > previous + 0.01f) {
                LaumaC1HealHelper.onMoonBloom(this.player);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyClorindeBoL(EntityAvatar clorinde) {
        ClorindeBoLUtil.applyNoArgHook(clorinde);
    }

    private void applyClorindeBoL(EntityAvatar clorinde, float ratio) {
        ClorindeBoLUtil.applyFromSkillHook(clorinde, ratio);
    }

    public void flushPendingBoL() {}

    public void onArlecchinoSkillNotify(int skillId) {
        // ys7.0 skill timestamps (CA / E). Burst arming is PR ArlecchinoBurstBoL.
        if (skillId == 10961) {
            arlecchinoChargedAttackTime = System.currentTimeMillis();
        } else if (skillId == 10962) {
            arlecchinoESkillTime = System.currentTimeMillis();
        }
    }

    private void addAbilityByHash(EntityAvatar entity, int hash) {
        var name = GameData.getAbilityHashes().get(hash);
        var data = name != null ? GameData.getAbilityData(name) : null;
        entity.getInstancedAbilities().add(data != null ? new Ability(data, entity, player) : null);
    }

    /** Keep already-initialized server ability IDs in sync with changed avatar embryos. */
    public void syncPhlogistonAvatarAbility(EntityAvatar avatar, boolean inNatlan) {
        var abilities = avatar.getInstancedAbilities();
        if (abilities.isEmpty()) return; // Lazy initialization will use the current region.
        var base = avatar.getAvatar().getAvatarData().getAbilities();
        int index = base == null ? 0 : base.size();
        int phlogistonHash = Utils.abilityHash("DynamicAbility_Phlogiston");
        for (int hash : GameConstants.DEFAULT_ABILITY_HASHES) {
            if (hash == phlogistonHash) break;
            index++;
        }
        if (index >= abilities.size()) return; // No fully initialized default-ability sequence.
        var current = abilities.get(index);
        boolean present = current != null && current.getData() != null
                && "DynamicAbility_Phlogiston".equals(current.getData().abilityName);
        if (inNatlan && !present) {
            var name = GameData.getAbilityHashes().get(phlogistonHash);
            var data = name == null ? null : GameData.getAbilityData(name);
            abilities.add(index, data == null ? null : new Ability(data, avatar, player));
        } else if (!inNatlan && present) {
            abilities.remove(index);
        }
    }

    private void initializeEntityAbilities(EntityAvatar avatar) {
        var avatarData = avatar.getAvatar().getAvatarData();
        if (avatarData.getAbilities() != null) {
            for (int hash : avatarData.getAbilities()) addAbilityByHash(avatar, hash);
        }
        // Match EntityAvatar.getAbilityControlBlock() exactly, including dense ability IDs.
        boolean inNatlan = player.isInNatlan();
        for (int hash : GameConstants.DEFAULT_ABILITY_HASHES) {
            if (!GameConstants.shouldAttachDefaultAvatarAbility(hash, inNatlan)) continue;
            addAbilityByHash(avatar, hash);
        }
        for (int hash : player.getTeamManager().getTeamResonancesConfig()) {
            addAbilityByHash(avatar, hash);
        }
        var skillDepot = GameData.getAvatarSkillDepotDataMap().get(avatar.getAvatar().getSkillDepotId());
        if (skillDepot != null && skillDepot.getAbilities() != null) {
            for (int hash : skillDepot.getAbilities()) addAbilityByHash(avatar, hash);
        }
        for (String name : avatar.getAvatar().getExtraAbilityEmbryos()) {
            var data = GameData.getAbilityData(name);
            avatar.getInstancedAbilities().add(data != null ? new Ability(data, avatar, player) : null);
        }
        emu.grasscutter.game.icecreature.IceCreatureAbilities.addInstances(player, avatar);
    }

    private void invokeAction(
        AbilityModifierAction action, GameEntity target, GameEntity sourceEntity) {
    }

    private void handleModifierDurabilityChange(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
    }

    private static volatile Set<Integer> moonLightAbilityHashes;

    private static Set<Integer> getMoonLightAbilityHashes() {
        Set<Integer> s = moonLightAbilityHashes;
        if (s == null) {
            s = new HashSet<>();
            for (var e : GameData.getAbilityHashes().int2ObjectEntrySet()) {
                String name = e.getValue();
                if (name != null && name.startsWith("Avatar_") && name.endsWith("_MoonLight")) {
                    s.add(e.getIntKey());
                }
            }
            moonLightAbilityHashes = s;
        }
        return s;
    }

    private void handleAddNewAbility(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());

        if (entity == null) {
            Grasscutter.getLogger().trace("handleAddNewAbility: entity not found: {}", invoke.getEntityId());
            return;
        }

        var addAbility = AbilityMetaAddAbility.parseFrom(invoke.getAbilityData());
        var abString = addAbility.getAbility().getAbilityName();
        var abilityName = Ability.getAbilityName(abString);
        var abilityData = GameData.getAbilityData(abilityName);

        var abilities = entity.getInstancedAbilities();
        int targetIndex = invoke.getHead().getInstancedAbilityId() - 1;
        if (targetIndex < 0) return;

        while (abilities.size() < targetIndex) abilities.add(null);

        int abHash = abString.hasHash() ? abString.getHash() : 0;
        boolean isTeamMoonPhase = "TeamAbility_MoonPhase".equals(abilityName)
            || abHash == Utils.abilityHash("TeamAbility_MoonPhase");
        boolean isMoonLightAbility =
            (abilityName != null && abilityName.startsWith("Avatar_") && abilityName.endsWith("_MoonLight"))
            || getMoonLightAbilityHashes().contains(abHash);

        // Repeated here: the client only wires the Moonsign up once its ability exists.
        if (isTeamMoonPhase || isMoonLightAbility) {
            var teamManager = this.player.getTeamManager();
            teamManager.sendMoonsignState();
            log.debug("{} loaded: re-sent Moonsign level={} to team entity={}",
                abilityName, teamManager.getMoonsignLevel(), teamManager.getEntity().getId());
        }

        if (abilityData == null) {
            Grasscutter.getLogger().trace(
                "handleAddNewAbility: ability data not found (stub at {}): entity={} name={} hash={}",
                targetIndex, entity.getId(), abilityName,
                abString.hasHash() ? abString.getHash() : 0);
            if (targetIndex == abilities.size()) abilities.add(null);
            else if (targetIndex < abilities.size() && abilities.get(targetIndex) == null) abilities.set(targetIndex, null);
            return;
        }

        var newAbility = new Ability(abilityData, entity, player);
        if (targetIndex < abilities.size() && abilities.get(targetIndex) == null) {
            abilities.set(targetIndex, newAbility);
        } else {
            abilities.add(newAbility);
        }

        if (abilityName != null && entity instanceof EntityClientGadget clientGadget) {
            var talentVars = GameData.getAbilityTalentVarMap().get(abilityName);
            if (talentVars != null && !talentVars.isEmpty()) {
                var ownerEntity = player.getScene().getEntityById(clientGadget.getOriginalOwnerEntityId());
                if (ownerEntity instanceof EntityAvatar avatarOwner) {
                    var avatar = avatarOwner.getAvatar();
                    AvatarSkillDepotData depot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
                    List<AbilityInvokeEntry> overrides = new ArrayList<>();

                    for (var tv : talentVars) {
                        Integer groupId = GameData.getOpenConfigToProudSkillGroup().get(tv.openConfigName());
                        if (groupId == null) continue;

                        int skillLevel = 1;
                        if (depot != null) {
                            int resolvedGroupId = groupId;
                            skillLevel = depot.getSkillsAndEnergySkill()
                                .filter(skillId -> {
                                    var sc = GameData.getAvatarSkillDataMap().get(skillId);
                                    return sc != null && sc.getProudSkillGroupId() == resolvedGroupId;
                                })
                                .mapToObj(skillId -> avatar.getSkillLevelMap().getOrDefault(skillId, 1))
                                .findFirst()
                                .orElse(1);
                        }

                        ProudSkillData proudSkill = GameData.getProudSkillDataMap().get(groupId * 100 + skillLevel);
                        if (proudSkill == null) continue;
                        float[] paramList = proudSkill.getParamList();
                        if (paramList == null || tv.paramIndex() >= paramList.length) continue;

                        float value = paramList[tv.paramIndex()];
                        overrides.add(AbilityInvokeEntry.newBuilder()
                            .setEntityId(entity.getId())
                            .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM)
                            .setHead(AbilityInvokeEntryHead.newBuilder()
                                .setInstancedAbilityId(targetIndex + 1)
                                .build())
                            .setAbilityData(AbilityScalarValueEntry.newBuilder()
                                .setKey(AbilityString.newBuilder()
                                    .setStr(tv.varName())
                                    .setHash(Utils.abilityHash(tv.varName()))
                                    .build())
                                .setFloatValue(value)
                                .build()
                                .toByteString())
                            .build());
                    }

                    if (!overrides.isEmpty()) {
                        player.sendPacket(new PacketAbilityInvocationsNotify(overrides));
                    }
                }
            }
        }

    }

    private void handleKillState(AbilityInvokeEntry invoke) throws InvalidProtocolBufferException {
        var scene = this.getPlayer().getScene();
        var entity = scene.getEntityById(invoke.getEntityId());
        if (entity == null) {
            Grasscutter.getLogger()
                .trace("Entity of ID {} was not found in the scene.", invoke.getEntityId());
            return;
        }

        var killState = AbilityMetaSetKilledState.parseFrom(invoke.getAbilityData());
        if (killState.getKilled()) {
            // Avatar / client gadgets: never. Monsters: only when HP is already depleted -
            // BurstAtk / shield cleanup often sends SetKilled on the boss entity id and must not
            // clear weekly domains (Raiden 20125) or stack trounce flowers.
            if (entity instanceof EntityAvatar || entity instanceof EntityClientGadget) {
                return;
            }
            if (entity instanceof EntityMonster monster) {
                if (monster.isAlive()) {
                    Grasscutter.getLogger()
                            .debug(
                                    "Ignored SetKilled on living monster entityId={} monsterId={}",
                                    entity.getId(),
                                    monster.getEntityTypeId());
                    return;
                }
            }
            scene.killEntity(entity);
        } else if (!entity.isAlive()) {
            if (entity instanceof EntityAvatar) {

                Grasscutter.getLogger()
                    .trace("Entity of ID {} is EntityAvatar. Ignoring", invoke.getEntityId());
                return;
            }
            // Entities with no combat state of their own used to answer null here; they now answer
            // an empty map, and reviving something that never had hit points is still meaningless.
            if (entity.getFightProperties().isEmpty()) return;
            entity.setFightProperty(
                FightProperty.FIGHT_PROP_CUR_HP,
                entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        }
    }

    /** Re-applies ModifyAbility proud-skill params onto an avatar ability's specials map. */
    private void applyAvatarTalentSpecials(Ability ability, Avatar avatar) {
        if (ability == null || ability.getData() == null || avatar == null) return;
        String abilityName = ability.getData().abilityName;
        if (abilityName == null) return;

        var talentVars = GameData.getAbilityTalentVarMap().get(abilityName);
        if (talentVars == null || talentVars.isEmpty()) return;

        AvatarSkillDepotData depot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
        for (var tv : talentVars) {
            Integer groupId = GameData.getOpenConfigToProudSkillGroup().get(tv.openConfigName());
            if (groupId == null) continue;

            int skillLevel = 1;
            if (depot != null) {
                int resolvedGroupId = groupId;
                skillLevel = depot.getSkillsAndEnergySkill()
                    .filter(sid -> {
                        var sc = GameData.getAvatarSkillDataMap().get(sid);
                        return sc != null && sc.getProudSkillGroupId() == resolvedGroupId;
                    })
                    .mapToObj(sid -> avatar.getSkillLevelMap().getOrDefault(sid, 1))
                    .findFirst()
                    .orElse(1);
            }

            ProudSkillData proudSkill = GameData.getProudSkillDataMap().get(groupId * 100 + skillLevel);
            if (proudSkill == null) continue;
            float[] paramList = proudSkill.getParamList();
            if (paramList == null || tv.paramIndex() < 0 || tv.paramIndex() >= paramList.length) continue;
            ability.getAbilitySpecials().put(tv.varName(), paramList[tv.paramIndex()]);
        }
    }

    public Map<String, Float> computeGadgetVarOverrides(EntityClientGadget gadget) {
        Map<String, Float> result = new HashMap<>();
        var ownerEntity = player.getScene().getEntityById(gadget.getOriginalOwnerEntityId());
        if (!(ownerEntity instanceof EntityAvatar avatarOwner)) return result;
        var avatar = avatarOwner.getAvatar();
        AvatarSkillDepotData depot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());

        List<String> abilityNames = new ArrayList<>();
        if (gadget.getConfigGadget() != null && gadget.getConfigGadget().getAbilities() != null) {
            for (var a : gadget.getConfigGadget().getAbilities()) {
                if (a.getAbilityName() != null) abilityNames.add(a.getAbilityName());
            }
        }
        var gd = GameData.getGadgetDataMap().get(gadget.getGadgetId());
        if (abilityNames.isEmpty() && gd != null && gd.getJsonName() != null) {
            abilityNames.add("Bullet_" + gd.getJsonName());
        }

        for (String abilityName : abilityNames) {
            var talentVars = GameData.getAbilityTalentVarMap().get(abilityName);
            if (talentVars == null) continue;
            for (var tv : talentVars) {
                Integer groupId = GameData.getOpenConfigToProudSkillGroup().get(tv.openConfigName());
                if (groupId == null) continue;
                int skillLevel = 1;
                if (depot != null) {
                    int resolvedGroupId = groupId;
                    skillLevel = depot.getSkillsAndEnergySkill()
                        .filter(sid -> {
                            var sc = GameData.getAvatarSkillDataMap().get(sid);
                            return sc != null && sc.getProudSkillGroupId() == resolvedGroupId;
                        })
                        .mapToObj(sid -> avatar.getSkillLevelMap().getOrDefault(sid, 1))
                        .findFirst()
                        .orElse(1);
                }
                ProudSkillData proudSkill = GameData.getProudSkillDataMap().get(groupId * 100 + skillLevel);
                if (proudSkill == null) continue;
                float[] paramList = proudSkill.getParamList();
                if (paramList == null || tv.paramIndex() >= paramList.length) continue;
                result.put(tv.varName(), paramList[tv.paramIndex()]);
            }
        }
        return result;
    }

    public void addAbilityToEntity(GameEntity entity, String name) {
        AbilityData data = GameData.getAbilityData(name);
        if (data != null) addAbilityToEntity(entity, data);
    }

    public void addAbilityToEntity(GameEntity entity, AbilityData abilityData) {
        var ability = new Ability(abilityData, entity, this.player);
        entity.getInstancedAbilities().add(ability);
        // Client SkillObj bullets (e.g. Venti Hexenzirkel infused NA) load with abilitySpecials at
        // config defaults (Hexenzirkel_NormalAttack_Ratio=0). Without proud-skill Apply here the
        // client may hit for 0 and the server combat fallback also sees ratio 0.
        if (entity instanceof EntityClientGadget clientGadget) {
            var ownerEntity = player.getScene() != null
                    ? player.getScene().getEntityById(clientGadget.getOriginalOwnerEntityId())
                    : null;
            if (ownerEntity instanceof EntityAvatar avatarOwner) {
                applyAvatarTalentSpecials(ability, avatarOwner.getAvatar());
            }
        }
        fireAbilityOnAdded(ability, entity);
    }

    /** Runs {@code onAbilityStart} - used when TriggerAbility re-fires an existing ability. */
    public void fireAbilityOnAbilityStart(Ability ability, GameEntity entity) {
        var data = ability.getData();
        if (data == null || data.onAbilityStart == null) {
            return;
        }
        for (var action : data.onAbilityStart) {
            if (action == null || action.type == null) {
                continue;
            }
            executeAction(ability, action, com.google.protobuf.ByteString.EMPTY, entity);
        }
    }

    private void fireAbilityOnAdded(Ability ability, GameEntity entity) {
        var data = ability.getData();
        if (data == null) return;

        if (data.onAdded != null) {
            for (var action : data.onAdded) {
                if (action.type == null) continue;
                executeAction(ability, action, com.google.protobuf.ByteString.EMPTY, entity);
            }
        }

        if (data.modifiers != null) {
            var defaultMod = data.modifiers.get("Default");
            if (defaultMod != null && defaultMod.onAdded != null) {
                for (var action : defaultMod.onAdded) {
                    if (action.type == null) continue;
                    executeAction(ability, action, com.google.protobuf.ByteString.EMPTY, entity);
                }
            }
        }
    }
}
