package emu.grasscutter.game.entity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.monster.MonsterCurveData;
import emu.grasscutter.game.entity.gadget.*;
import emu.grasscutter.game.entity.gadget.platform.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair;
import emu.grasscutter.net.proto.EntityAuthorityInfoOuterClass.EntityAuthorityInfo;
import emu.grasscutter.net.proto.EntityClientDataOuterClass.EntityClientData;
import emu.grasscutter.net.proto.EntityRendererChangedInfoOuterClass.EntityRendererChangedInfo;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.PropPairOuterClass.PropPair;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.SceneEntityAiInfoOuterClass.SceneEntityAiInfo;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.scripts.EntityControllerScriptManager;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.helpers.ProtoHelper;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import javax.annotation.Nullable;
import lombok.*;

@ToString(callSuper = true, exclude = {"owner", "children", "content", "routeConfig"})
public class EntityGadget extends EntityBaseGadget {
    @Getter private final GadgetData gadgetData;

    @Getter(onMethod_ = @Override)
    @Setter
    private int gadgetId;

    @Getter private final Position bornPos;
    @Getter private final Position bornRot;
    @Getter @Setter private GameEntity owner = null;

    /**
     * Server global values set on top of the config's, such as those an Ice Creature build request
     * asks for. Keyed by ability hash; a later value replaces a config value of the same name.
     */
    @Getter private final Map<Integer, AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry>
            extraGlobalValues = new java.util.LinkedHashMap<>();
    @Getter @Setter private List<GameEntity> children = new ArrayList<>();

    @Getter private int state;
    @Getter @Setter private int pointType;
    @Getter private GadgetContent content;

    @Getter(onMethod_ = @Override, lazy = true)
    private final Int2FloatMap fightProperties = new Int2FloatOpenHashMap();

    @Getter @Setter private SceneGadget metaGadget;
    @Nullable @Getter ConfigEntityGadget configGadget;
    @Getter @Setter private BaseRoute routeConfig;
    /** SceneGadgetInfo field 27: where a curve-move gadget (the Snezhnaya trains) runs. */
    @Getter @Setter
    private emu.grasscutter.net.proto.BMJNJLKCODGOuterClass.BMJNJLKCODG curveMoveInfo;
    private volatile ConfigRouteRun configRouteRun;

    @Getter @Setter private int stopValue = 0; // Controller related, inited to zero
    @Getter @Setter private int startValue = 0; // Controller related, inited to zero
    @Getter @Setter private int ticksSinceChange;

    @Getter private boolean interactEnabled = true;

    public EntityGadget(Scene scene, int gadgetId, Position pos) {
        this(scene, gadgetId, pos, null, null);
    }

    public EntityGadget(Scene scene, int gadgetId, Position pos, Position rot) {
        this(scene, gadgetId, pos, rot, null);
    }

    public EntityGadget(
            Scene scene, int gadgetId, Position pos, Position rot, int campId, int campType) {
        this(scene, gadgetId, pos, rot, null, campId, campType, true);
    }

    public EntityGadget(
            Scene scene, int gadgetId, Position pos, Position rot, GadgetContent content) {
        this(scene, gadgetId, pos, rot, content, 0, 0, true);
    }

    public EntityGadget(
            Scene scene,
            int gadgetId,
            Position pos,
            Position rot,
            GadgetContent content,
            int campId,
            int campType) {
        this(scene, gadgetId, pos, rot, content, campId, campType, true);
    }

    /**
     * @param initAbilitiesNow when false, caller must {@link #initAbilities()} after {@link
     *     #setOwner(GameEntity)} so onAdded CreateGadget can see the owner chain (Furina's Salon
     *     controller otherwise spawns duplicate singers while owner is still null).
     */
    public EntityGadget(
            Scene scene,
            int gadgetId,
            Position pos,
            Position rot,
            GadgetContent content,
            int campId,
            int campType,
            boolean initAbilitiesNow) {
        super(scene, pos, rot, campId, campType);

        this.gadgetData = GameData.getGadgetDataMap().get(gadgetId);
        if (gadgetData != null && gadgetData.getJsonName() != null) {
            this.configGadget = GameData.getGadgetConfigData().get(gadgetData.getJsonName());
        }

        this.id = this.getScene().getWorld().getNextEntityId(EntityIdType.GADGET);
        this.gadgetId = gadgetId;
        this.content = content;
        this.bornPos = this.getPosition().clone();
        this.bornRot = this.getRotation().clone();
        this.fillFightProps(configGadget);

        // Check if this gadget is the abyss defense objective's gadget.
        // That doesn't have a level and defaults to having 5000 hp, so it dies in like 2 hits on 11-1.
        // I'll forgive player skill issues and scale its hp up here.
        // TODO: find out how its fight props are actually scaled
        // Constant first: 7345 of the 30557 gadget rows omit jsonName, so this is null for 24% of
        // gadgets, and the throw escapes into the tick that was spawning one.
        if ("SceneObj_Gear_Operator_Mamolu_Entity".equals(gadgetData.getJsonName())) {
            MonsterCurveData curve = GameData.getMonsterCurveDataMap().get(11);
            if (curve != null) {
                FightProperty[] hpProps = {
                    FightProperty.FIGHT_PROP_MAX_HP,
                    FightProperty.FIGHT_PROP_BASE_HP,
                    FightProperty.FIGHT_PROP_CUR_HP
                };
                for (var prop : hpProps) {
                    setFightProperty(
                            prop, this.getFightProperty(prop) * curve.getMultByProp("GROW_CURVE_HP_ENVIRONMENT"));
                }
            }
        }

        if (GameData.getGadgetMappingMap().containsKey(gadgetId)) {
            var controllerName = GameData.getGadgetMappingMap().get(gadgetId).getServerController();
            this.setEntityController(EntityControllerScriptManager.getGadgetController(controllerName));
            if (this.getEntityController() == null) {
                Grasscutter.getLogger().warn("Gadget controller {} not found.", controllerName);
            }
        }

        if (initAbilitiesNow) {
            this.initAbilities();
        }
    }

    private void addConfigAbility(ConfigAbilityData abilityData) {
        var data = GameData.getAbilityData(abilityData.getAbilityName());
        if (data != null)
            this.getScene().getWorld().getHost().getAbilityManager().addAbilityToEntity(this, data);
    }

    @Override
    public void initAbilities() {
        // TODO: handle pre-dynamic, static and dynamic here
        if (this.configGadget != null && this.configGadget.getAbilities() != null) {
            for (var ability : this.configGadget.getAbilities()) {
                this.addConfigAbility(ability);
            }
        }
    }

    public void setInteractEnabled(boolean enable) {
        this.interactEnabled = enable;
        if (enable) {
            try {
                emu.grasscutter.game.entity.gadget.GatherInteractHelper.markGatherInteractEnabled(this);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                emu.grasscutter.game.entity.gadget.GatherInteractHelper.markGatherInteractDisabled(this);
            } catch (Throwable ignored) {
            }
        }
        this.getScene()
                .broadcastPacket(new PacketGadgetStateNotify(this, this.getState())); // Update the interact
    }

    public void setState(int state) {
        this.state = state;
        // Cache the gadget state
        if (metaGadget != null && metaGadget.group != null) {
            var instance = getScene().getScriptManager().getGroupInstanceById(metaGadget.group.id);
            if (instance != null) instance.cacheGadgetState(metaGadget, state);
        }
    }

    public void updateState(int state) {
        if (state == this.getState()) return; // Don't triggers events

        var oldState = this.getState();
        this.setState(state);
        ticksSinceChange = getScene().getSceneTimeSeconds();
        this.getScene().broadcastPacket(new PacketGadgetStateNotify(this, state));
        getScene()
                .getScriptManager()
                .callEvent(
                        new ScriptArgs(
                                        this.getGroupId(),
                                        EventType.EVENT_GADGET_STATE_CHANGE,
                                        state,
                                        this.getConfigId())
                                .setParam3(oldState));
    }

    /** Replace gadget content even if {@link #buildContent()} already assigned one. */
    public void replaceContent(GadgetContent content) {
        this.content = content;
    }

    @Deprecated(forRemoval = true) // Dont use!
    public void setContent(GadgetContent content) {
        this.content = this.content == null ? content : this.content;
    }

    // TODO refactor
    public void buildContent() {
        // Trounce flowers may use a generic GadgetData type. The script's
        // boss_chest metadata is authoritative for claim interaction and proto.
        if (this.metaGadget != null && this.metaGadget.boss_chest != null) {
            if (!(this.content instanceof GadgetChest)) this.content = new GadgetChest(this);
            return;
        }
        emu.grasscutter.game.quest.LiyueTeapotDungeonSupport.prepareCobweb(this);
        if (emu.grasscutter.game.entity.gadget.GadgetLiyueDustBell.matches(this)) {
            if (!(this.content instanceof emu.grasscutter.game.entity.gadget.GadgetLiyueDustBell))
                this.content = new emu.grasscutter.game.entity.gadget.GadgetLiyueDustBell(this);
            return;
        }
        if (emu.grasscutter.game.entity.gadget.GadgetLiyueGuizhong.matches(this)) {
            if (!(this.content instanceof emu.grasscutter.game.entity.gadget.GadgetLiyueGuizhong))
                this.content = new emu.grasscutter.game.entity.gadget.GadgetLiyueGuizhong(this);
            return;
        }
        if (emu.grasscutter.game.entity.gadget.GadgetLiyueJadeMaterials.matches(this)) {
            if (!(this.content instanceof emu.grasscutter.game.entity.gadget.GadgetLiyueJadeMaterials))
                this.content = new emu.grasscutter.game.entity.gadget.GadgetLiyueJadeMaterials(this);
            return;
        }
        if (GadgetLiyueFoodOffering.matches(this)) {
            this.content = new GadgetLiyueFoodOffering(this);
            return;
        }
        // A boss-chest definition takes precedence over GadgetExcel's generic gadget type.
        // Otherwise a spawned trounce blossom can have no chest interaction handler.
        if (this.metaGadget != null && this.metaGadget.boss_chest != null) {
            if (!(this.content instanceof GadgetChest)) {
                this.content = new GadgetChest(this);
            }
            return;
        }
        if (this.getContent() != null
                || this.getGadgetData() == null
                || this.getGadgetData().getType() == null) {
            return;
        }

        this.content =
                switch (this.getGadgetData().getType()) {
                    case GatherPoint -> new GadgetGatherPoint(this);
                    case GatherObject -> new GadgetGatherObject(this);
                    case Worktop, SealGadget -> new GadgetWorktop(this);
                    case RewardStatue -> new GadgetRewardStatue(this);
                    case Chest -> new GadgetChest(this);
                    case OfferingGadget ->
                            new emu.grasscutter.game.entity.gadget.GadgetOffering(this);
                    case Gadget -> new GadgetObject(this);
                    default -> null;
                };
        try {
            emu.grasscutter.game.entity.gadget.OreMiningHelper.prepareOre(this);
        } catch (Throwable ignored) {
        }
        // Do not auto-enable ElementFlora / break-first gathers — they unlock after element/break.
        try {
            if ((this.content instanceof emu.grasscutter.game.entity.gadget.GadgetGatherObject
                            || this.content instanceof emu.grasscutter.game.entity.gadget.GadgetGatherPoint)
                    && !emu.grasscutter.game.entity.gadget.GatherInteractHelper
                            .needsDisabledInteractUntilReady(this)) {
                emu.grasscutter.game.entity.gadget.GatherInteractHelper.markGatherInteractEnabled(this);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onInteract(Player player, GadgetInteractReq interactReq) {
        boolean interactOk = this.interactEnabled;
        try {
            interactOk =
                    emu.grasscutter.game.entity.gadget.GatherInteractHelper.safeClientInteractEnabled(
                            this);
        } catch (Throwable ignored) {
        }
        if (!interactOk) return;

        try {
            if (emu.grasscutter.game.world.AndriusTrialStartHelper.tryStart(player, this, 0)) {
                player.onGadgetInteracted(getGadgetId());
                return;
            }
        } catch (Throwable ignored) {
        }

        // OfferingGadgets such as the Sacred Sakura must still open the offering page even when content is unset.
        try {
            if (emu.grasscutter.game.entity.gadget.OfferingHelper.tryInteract(player, this)) {
                player.onGadgetInteracted(getGadgetId());
                return;
            }
        } catch (Throwable ignored) {
        }

        boolean shouldDelete = this.getContent() != null && this.getContent().onInteract(player, interactReq);
        if (!(this.getContent() instanceof emu.grasscutter.game.entity.gadget.CreditsOwnInteraction)) {
            player.onGadgetInteracted(getGadgetId());
        }

        if (shouldDelete) {
            this.getScene().killEntity(this);
        }
    }

    @Override
    public void onCreate() {
        try {
            emu.grasscutter.game.world.AndriusTrialStartHelper.onGadgetCreated(this);
        } catch (Throwable ignored) {
        }
        try {
            emu.grasscutter.game.world.OfferingSpawnHelper.onGadgetCreated(this);
        } catch (Throwable ignored) {
        }
        // Lua event
        getScene()
                .getScriptManager()
                .callEvent(
                        new ScriptArgs(this.getGroupId(), EventType.EVENT_GADGET_CREATE, this.getConfigId()));
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if (!children.isEmpty()) {
            getScene().removeEntities(children, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
            children.clear();
        }
    }

    /**
     * GatherObject loot is handled by {@link GadgetGatherObject#onMined}. Skip lua DropSubfield
     * for those so Scarlet Quartz / mithril etc. do not double-drop after the weight fix.
     */
    @Override
    public boolean dropSubfield(String subfieldName) {
        if (this.content instanceof emu.grasscutter.game.entity.gadget.GadgetGatherObject) {
            return false;
        }
        return super.dropSubfield(subfieldName);
    }

    @Override
    public void onDeath(int killerId) {
        emu.grasscutter.game.quest.LiyueAmberQuestSupport.onGadgetDeath(this);
        super.onDeath(killerId); // Invoke super class's onDeath() method.

        // Breakable gather ores (incl. Scarlet Quartz): spawn ground drop on death.
        try {
            if (this.content instanceof emu.grasscutter.game.entity.gadget.GadgetGatherObject gather) {
                gather.onMined(killerId);
            } else if (emu.grasscutter.game.entity.gadget.OreMiningHelper.isScarletQuartz(this)) {
                emu.grasscutter.game.entity.gadget.OreMiningHelper.dropScarletQuartzPickup(
                        this, killerId);
            } else {
                emu.grasscutter.game.entity.gadget.GatherInteractHelper.onGatherBreak(this);
            }
        } catch (Throwable t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn(
                            "Gather break drop failed gadgetId={} cfg={}: {}",
                            this.getGadgetId(),
                            this.getConfigId(),
                            t.toString());
        }

        if (this.getSpawnEntry() != null) {
            this.getScene().getDeadSpawnedEntities().add(getSpawnEntry());
        }
        if (getScene().getChallenge() != null) {
            getScene().getChallenge().onGadgetDeath(this);
        }
        getScene()
                .getScriptManager()
                .callEvent(
                        new ScriptArgs(this.getGroupId(), EventType.EVENT_ANY_GADGET_DIE, this.getConfigId()));

        SceneGroupInstance groupInstance =
                getScene().getScriptManager().getGroupInstanceById(this.getGroupId());
        if (groupInstance != null && metaGadget != null)
            groupInstance.getDeadEntities().add(metaGadget.config_id);

        emu.grasscutter.game.entity.gadget.MeltableIceHelper.onShattered(this);
    }

    /**
     * Open-world chests are opened by interact, not destroyed by combat. Without this,
     * AoE hits kill the gadget and explore spawn helpers respawn a fresh chest.
     * Ores / breakables use clamped mining damage so character ATK cannot one-shot them.
     */
    @Override
    public void damage(
            float amount,
            int killerId,
            ElementType attackType,
            PropChangeReasonOuterClass.PropChangeReason propChangeReason,
            ChangHpReasonOuterClass.ChangHpReason changeHpReason) {
        // Sealed bramble/frozen/rock chests clear via EnvironmentalSealHelper; never kill them.
        try {
            boolean isChest =
                    this.content instanceof GadgetChest
                            || (this.gadgetData != null && this.gadgetData.getType() == EntityType.Chest);
            if (isChest) {
                EnvironmentalSealHelper.tryProcessAttack(this, amount, attackType);
                return;
            }
        } catch (Throwable ignored) {
            if (this.content instanceof GadgetChest) {
                return;
            }
            try {
                if (this.gadgetData != null && this.gadgetData.getType() == EntityType.Chest) {
                    return;
                }
            } catch (Throwable ignored2) {
            }
        }
        try {
            if (emu.grasscutter.game.entity.gadget.OreMiningHelper.isBreakableWorldObject(this)) {
                amount =
                        emu.grasscutter.game.entity.gadget.OreMiningHelper.resolveMiningDamage(
                                this, amount, killerId);
            }
        } catch (Throwable ignored) {
        }
        super.damage(amount, killerId, attackType, propChangeReason, changeHpReason);
        try {
            emu.grasscutter.game.entity.gadget.OreMiningHelper.syncHpBar(this);
        } catch (Throwable ignored) {
        }
        // Client often despawns breakable visuals before HP hits 0 (esp. Scarlet Quartz).
        // Force-finish so onDeath → onMined can spawn the ground drop.
        try {
            if (emu.grasscutter.game.entity.gadget.OreMiningHelper.isBreakableWorldObject(this)
                    && !this.isDead()) {
                float cur = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
                boolean scarlet =
                        emu.grasscutter.game.entity.gadget.OreMiningHelper.isScarletQuartz(this);
                // Scarlet Quartz: client despawns model on first hit — always finish here.
                // Other breakables: finish the last chip when nearly broken.
                if (scarlet || (cur > 0.0f && cur < 15.0f)) {
                    this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0.0f);
                    this.checkIfDead();
                    if (this.isDead() && this.getScene() != null) {
                        this.getScene().killEntity(this, killerId);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public synchronized boolean startPlatform() {
        if (routeConfig == null) {
            return false;
        }

        if (routeConfig.isStarted()) {
            return true;
        }

        if (routeConfig instanceof ConfigRoute configRoute
                && !emu.grasscutter.game.quest.LiyueCloudDungeonSupport.isPlatform(this)
                && !emu.grasscutter.game.quest.MondstadtStoryQuestSupport.isAmberWaterPlatform(this)
                && !ConfigRouteRun.usesSpeedLevels(this, configRoute)) {
            return startOrdinaryConfigRoute(configRoute);
        }

        if (routeConfig instanceof ConfigRoute configRoute) {
            var route = this.getScene().getSceneRouteById(configRoute.getRouteId());
            if (route == null || route.getPoints() == null || route.getPoints().length == 0) {
                Grasscutter.getLogger()
                        .warn(
                                "Cannot start platform group={} config={} route={}: route is missing",
                                getGroupId(),
                                getConfigId(),
                                configRoute.getRouteId());
                return false;
            }
            if (route != null) {
                var points = route.getPoints();
                if (configRoute.getStartIndex() == points.length - 1) {
                    configRoute.setStartIndex(0);
                }
                val currIndex = configRoute.getStartIndex();

                Position prevpos;
                if (currIndex == 0) {
                    prevpos = getPosition();
                    this.getScene()
                            .getScriptManager()
                            .callEvent(
                                    new ScriptArgs(
                                                    this.getGroupId(),
                                                    EventType.EVENT_PLATFORM_REACH_POINT,
                                                    this.getConfigId(),
                                                    configRoute.getRouteId())
                                            .setParam3(0)
                                            .setEventSource(this.getConfigId()));
                } else {
                    prevpos = points[currIndex].getPos();
                }

                double time = 0;
                boolean liyuePlatform = emu.grasscutter.game.quest.LiyueCloudDungeonSupport.isPlatform(this);
                boolean timedPlatform = liyuePlatform
                        || emu.grasscutter.game.quest.MondstadtStoryQuestSupport.isAmberWaterPlatform(this);
                int startedRoute = configRoute.getRouteId();
                for (var i = currIndex; i < points.length; ++i) {
                    double distance = points[i].getPos().computeDistance(prevpos);
                    time += timedPlatform
                            ? emu.grasscutter.game.quest.LiyueCloudDungeonSupport.pointSeconds(points[i], distance)
                            : distance / points[i].getTargetVelocity();
                    prevpos = points[i].getPos();
                    val I = i;
                    configRoute
                            .getScheduledIndexes()
                            .add(
                                    this.getScene()
                                            .getScheduler()
                                            .scheduleDelayedTask(
                                                    () -> {
                                                        if (timedPlatform && (!configRoute.isStarted()
                                                                || configRoute.getRouteId() != startedRoute)) return;
                                                        configRoute.setStartIndex(I);
                                                        this.position.set(points[I].getPos());
                                                        if (points[I].isHasReachEvent() && I > currIndex) {
                                                            this.getScene()
                                                                    .getScriptManager()
                                                                    .callEvent(
                                                                            new ScriptArgs(
                                                                                            this.getGroupId(),
                                                                                            EventType.EVENT_PLATFORM_REACH_POINT,
                                                                                            this.getConfigId(),
                                                                                            configRoute.getRouteId())
                                                                                    .setParam3(I)
                                                                                    .setEventSource(this.getConfigId()));
                                                            emitPlatformArrival(configRoute.getRouteId(), I);
                                                        }
                                                        if (liyuePlatform && points[I].getRotRoundLeaveDir() != null)
                                                            this.getRotation().set(points[I].getRotRoundLeaveDir());
                                                        if (I == points.length - 1) {
                                                            configRoute.setStarted(false);
                                                        }
                                                    },
                                                    timedPlatform
                                                            ? emu.grasscutter.game.quest.LiyueCloudDungeonSupport.ticksForSeconds(time + 0.3)
                                                            : (int) time));
                }
            }
        }

        publishPlatformStart();
        return true;
    }

    private boolean startOrdinaryConfigRoute(ConfigRoute config) {
        var route = getScene().getSceneRouteById(config.getRouteId());
        if (route == null || route.getPoints() == null || route.getPoints().length == 0) {
            Grasscutter.getLogger()
                    .warn(
                            "Cannot start ordinary platform group={} config={} route={}: route has no points",
                            getGroupId(),
                            getConfigId(),
                            config.getRouteId());
            return false;
        }
        var run = ConfigRouteRun.plan(this, config, route.getPoints());
        if (run == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Cannot start ordinary platform group={} config={} route={}: invalid distance/velocity duration",
                            getGroupId(),
                            getConfigId(),
                            config.getRouteId());
            return false;
        }
        invalidatePlatformRouteRun();
        // A completed run can leave expired IDs in the installed list. Retire these on a valid
        // restart.
        for (int id : config.getScheduledIndexes()) getScene().getScheduler().cancelTask(id);
        config.getScheduledIndexes().clear();
        config.setStartIndex(run.startIndex);
        configRouteRun = run;
        if (run.startIndex == 0) {
            getScene()
                    .getScriptManager()
                    .callEvent(
                            new ScriptArgs(
                                            getGroupId(),
                                            EventType.EVENT_PLATFORM_REACH_POINT,
                                            getConfigId(),
                                            run.routeId)
                                    .setParam3(0)
                                    .setEventSource(getConfigId()));
        }
        for (int i = run.startIndex; i <= run.endIndex; i++) {
            var task = new ConfigRouteRun.Task(this, run, i);
            int id = getScene().getScheduler().scheduleRepeatingTask(task, 1);
            task.setTaskId(id);
            config.getScheduledIndexes().add(id);
        }
        publishPlatformStart();
        return true;
    }

    boolean isCurrentConfigRouteRun(ConfigRouteRun run) {
        return configRouteRun == run
                && routeConfig == run.config
                && run.config.getRouteId() == run.routeId;
    }

    void advanceConfigRoutePoint(ConfigRouteRun run, int index) {
        // Keep ordinary reach-event/index/position/final-flag behavior unchanged.
        if (run.points[index].isHasReachEvent() && index > run.startIndex) {
            getScene()
                    .getScriptManager()
                    .callEvent(
                            new ScriptArgs(
                                            getGroupId(),
                                            EventType.EVENT_PLATFORM_REACH_POINT,
                                            getConfigId(),
                                            run.config.getRouteId())
                                    .setParam3(index)
                                    .setEventSource(getConfigId()));
            emitPlatformArrival(run.config.getRouteId(), index);
        }
        run.config.setStartIndex(index);
        position.set(run.points[index].getPos());
        if (index == run.points.length - 1) {
            // The client already ran the route to its end; a stop here would refresh the entity
            // (no 7.1 stop opcode) and drop anyone standing on it, so only the state is closed.
            invalidatePlatformRouteRun();
            for (int id : run.config.getScheduledIndexes()) getScene().getScheduler().cancelTask(id);
            run.config.getScheduledIndexes().clear();
            run.config.stopRoute(getScene());
        } else if (index == run.endIndex && index > run.startIndex) {
            // Do not stop again at the zero-distance source point when resuming a stopped segment.
            stopPlatform();
        }
    }

    private void emitPlatformArrival(int routeId, int pointIndex) {
        getScene().getScriptManager().callEvent(
                new ScriptArgs(getGroupId(), EventType.EVENT_PLATFORM_ARRIVAL,
                        getConfigId(), routeId)
                        .setParam3(pointIndex)
                        .setEventSource(getConfigId()));
    }

    /**
     * Invalidate even retained/dequeued callbacks; callers changing a route hold this same monitor.
     */
    public synchronized void invalidatePlatformRouteRun() {
        if (configRouteRun != null) configRouteRun.cancel();
        configRouteRun = null;
    }

    private void publishPlatformStart() {
        getScene().broadcastPacket(new PacketSceneTimeNotify(getScene()));
        routeConfig.startRoute(getScene());
        if (configRouteRun != null && isCurrentConfigRouteRun(configRouteRun)) {
            // Represent the initial off-source approach before the authored timeline origin.
            // Use the same clock sample taken by BaseRoute; preserve its int32 wrap semantics.
            routeConfig.setStartSceneTime(
                    routeConfig.getStartSceneTime() - 300 + configRouteRun.leadMillis);
            configRouteRun.arm();
        }
        if (PacketOpcodes.PlatformStartRouteNotify > 0) {
            getScene().broadcastPacket(new PacketPlatformStartRouteNotify(this));
        } else if (routeConfig instanceof ConfigRoute) {
            getScene()
                    .broadcastPacket(
                            new PacketSceneEntityDisappearNotify(
                                    this, VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH));
            getScene()
                    .broadcastPacket(
                            new PacketSceneEntityAppearNotify(
                                    this, VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH, 0));
        }
    }

    public synchronized boolean stopPlatform() {
        invalidatePlatformRouteRun();
        if (routeConfig == null) {
            return false;
        }

        if (!routeConfig.isStarted()) {
            return true;
        }

        if (routeConfig instanceof ConfigRoute configRoute) {
            for (var task : configRoute.getScheduledIndexes()) {
                this.getScene().getScheduler().cancelTask(task);
            }
            configRoute.getScheduledIndexes().clear();
        }

        routeConfig.stopRoute(getScene());
        if (PacketOpcodes.PlatformStopRouteNotify > 0) {
            getScene().broadcastPacket(new PacketPlatformStopRouteNotify(this));
        } else if (routeConfig instanceof ConfigRoute) {
            getScene()
                    .broadcastPacket(
                            new PacketSceneEntityDisappearNotify(
                                    this, VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH));
            getScene()
                    .broadcastPacket(
                            new PacketSceneEntityAppearNotify(
                                    this, VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH, 0));
        }

        return true;
    }

    /**
     * The server global values the gadget config declares under {@code globalValue.initServerGlobalValues}.
     *
     * <p>The gadget's abilities read these from the first frame - Snezhnaya's meltable ice takes its melt
     * rate from {@code SGV_Meltable_Ratio} and its size from {@code SGV_InitialSize} - and without them
     * every value reads 0, so Fire hits shrank the ice by nothing.
     */
    private AbilitySyncStateInfo.Builder initialAbilityInfo() {
        var info = AbilitySyncStateInfo.newBuilder();
        var globalValue = this.configGadget == null ? null : this.configGadget.getGlobalValue();
        if (globalValue != null && globalValue.getInitServerGlobalValues() != null) {
            globalValue
                    .getInitServerGlobalValues()
                    .forEach(
                            (key, value) -> {
                                if (key == null || value == null) return;
                                if (this.extraGlobalValues.containsKey(emu.grasscutter.utils.Utils.abilityHash(key))) {
                                    return;
                                }
                                info.addSgvDynamicValueMap(
                                        AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry.newBuilder()
                                                .setKey(
                                                        AbilityStringOuterClass.AbilityString.newBuilder()
                                                                .setHash(emu.grasscutter.utils.Utils.abilityHash(key))
                                                                .setStr(key))
                                                .setFloatValue(value));
                            });
        }
        this.extraGlobalValues.values().forEach(info::addSgvDynamicValueMap);
        return info;
    }

    @Override
    public SceneEntityInfo toProto() {
        EntityAuthorityInfo authority =
                EntityAuthorityInfo.newBuilder()
                        .setAbilityInfo(this.initialAbilityInfo())
                        .setRendererChangedInfo(EntityRendererChangedInfo.newBuilder())
                        .setAiInfo(
                                gadgetAiInfo())
                        .setBornPos(bornPos.toProto())
                        .build();

        SceneEntityInfo.Builder entityInfo =
                SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setEntityType(ProtEntityType.ProtEntityType_PROT_ENTITY_GADGET)
                        .setMotionInfo(
                                MotionInfo.newBuilder()
                                        .setPos(getPosition().toProto())
                                        .setRot(getRotation().toProto())
                                        .setSpeed(Vector.newBuilder()))
                        .addAnimatorParaList(AnimatorParameterValueInfoPair.newBuilder())
                        .setEntityClientData(EntityClientData.newBuilder())
                        .setEntityAuthorityInfo(authority)
                        .setLifeState(1);

        PropPair pair =
                PropPair.newBuilder()
                        .setType(PlayerProperty.PROP_LEVEL.getId())
                        .setPropValue(ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, 1))
                        .build();
        entityInfo.addPropList(pair);

        // We do not use the getter to null check because the getter will create a fight prop map if it
        // is null
        if (this.fightProperties != null) {
            addAllFightPropsToEntityInfo(entityInfo);
        }

        var gadgetInfo =
                SceneGadgetInfo.newBuilder()
                        .setGadgetId(this.getGadgetId())
                        .setGroupId(this.getGroupId())
                        .setConfigId(this.getConfigId())
                        .setGadgetState(this.getState())
                        .setIsEnableInteract(
                                emu.grasscutter.game.entity.gadget.GatherInteractHelper
                                        .safeClientInteractEnabled(this))
                        .setAuthorityPeerId(this.getScene().getWorld().getHostPeerId());

        if (this.metaGadget != null) {
            gadgetInfo.setDraftId(this.metaGadget.draft_id);
            // Boss flower / chests with showcutscene=true: client plays appear transition.
            if (this.metaGadget.isShowcutscene()) {
                gadgetInfo.setIsShowCutscene(true);
                gadgetInfo.setBornType(
                        emu.grasscutter.net.proto.GadgetBornTypeOuterClass.GadgetBornType
                                .GadgetBornType_GADGET_BORN_IN_AIR);
            }
        }

        if (owner != null) {
            gadgetInfo.setOwnerEntityId(owner.getId());
        }

        // Script-spawned flowers can acquire their metadata after buildContent().
        // Ensure their network entity advertises the BossChestInfo and eligibility.
        if (this.metaGadget != null && this.metaGadget.boss_chest != null
                && !(this.content instanceof GadgetChest)) {
            this.content = new GadgetChest(this);
        }
        if (this.getContent() != null) {
            this.getContent().onBuildProto(gadgetInfo);
        }

        if (routeConfig != null) {
            gadgetInfo.setPlatform(getPlatformInfo());
        }

        if (this.curveMoveInfo != null) {
            gadgetInfo.setCFOMFPNHLJF(this.curveMoveInfo);
        }

        entityInfo.setGadget(gadgetInfo);

        return entityInfo.build();
    }

    public PlatformInfoOuterClass.PlatformInfo.Builder getPlatformInfo() {
        if (routeConfig != null) {
            return routeConfig.toProto();
        }

        return PlatformInfoOuterClass.PlatformInfo.newBuilder();
    }
}
