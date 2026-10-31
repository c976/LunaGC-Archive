package emu.grasscutter.game.entity;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.avatar.VehicleData;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilityAppliedModifierOuterClass.AbilityAppliedModifier;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair;
import emu.grasscutter.net.proto.EntityAuthorityInfoOuterClass.EntityAuthorityInfo;
import emu.grasscutter.net.proto.EntityRendererChangedInfoOuterClass.EntityRendererChangedInfo;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.PropPairOuterClass.PropPair;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.SceneEntityAiInfoOuterClass.SceneEntityAiInfo;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.net.proto.VehicleInfoOuterClass.VehicleInfo;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import emu.grasscutter.utils.helpers.ProtoHelper;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import javax.annotation.Nullable;
import lombok.*;

public class EntityVehicle extends EntityBaseGadget {

    @Getter private final Player owner;

    @Getter(onMethod_ = @Override)
    private final Int2FloatMap fightProperties;

    @Getter private final int pointId;
    @Getter private final int gadgetId;

    @Getter @Setter private float curStamina;
    @Getter @Setter private VehicleData vehicleDataA;
    @Getter @Setter public float curPhlogiston;
    @Getter private List<VehicleMember> vehicleMembers;
    @Nullable @Getter private ConfigEntityGadget configGadget;
    public int transformEntityId;
    public EntityVehicle(
            Scene scene, Player player, int gadgetId, int pointId, Position pos, Position rot) {
                super(scene, pos, rot);
                this.owner = player;
                this.id = getScene().getWorld().getNextEntityId(EntityIdType.GADGET);
                this.fightProperties = new Int2FloatOpenHashMap();
                this.gadgetId = gadgetId;
                this.pointId = pointId;
                this.curStamina = 240; // might be in configGadget.GCALKECLLLP.JBAKBEFIMBN.ANBMPHPOALP
                this.curPhlogiston = 50;
                this.vehicleMembers = new ArrayList<>();
                GadgetData data = GameData.getGadgetDataMap().get(gadgetId);
                if (data != null && data.getJsonName() != null) {
                        this.configGadget = GameData.getGadgetConfigData().get(data.getJsonName());
                }

                fillFightProps(configGadget);
                this.initAbilities();
    }

    @Override
    protected void fillFightProps(ConfigEntityGadget configGadget) {
        super.fillFightProps(configGadget);
        this.addFightProperty(FightProperty.FIGHT_PROP_CUR_SPEED, 0);
        this.addFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 0);

        // Every vehicle gadget in the data is flagged isInvincible, and fillFightProps reads that as an
        // infinite CUR_HP - which made a ridden Saurian unable to be hurt at all. The same config carries
        // a real HP figure (10000 for the Natlan mounts) and that is the bar the client draws, so it is
        // used as the pool instead: the vehicle can be fought, and onDeath puts the rider off when it
        // runs out.
        if (!NatsaurusVehicleHelper.isNatsaurusMount(this)) return;

        float maxHp = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp > 0f && maxHp != Float.POSITIVE_INFINITY) {
            this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, maxHp);
        }
    }

    private void addConfigAbility(ConfigAbilityData abilityData) {
        var data = GameData.getAbilityData(abilityData.getAbilityName());

        if (data != null)
            this.getScene().getWorld().getHost().getAbilityManager().addAbilityToEntity(this, data);
    }

    @Override
    public SceneEntityInfo toProto() {

        VehicleInfo vehicle =
                VehicleInfo.newBuilder()
                        .setOwnerUid(this.owner.getUid())
                        .setCurStamina(getCurStamina())
                        .setCurPhlogiston(getCurPhlogiston())
                        .setTransformEntityId(transformEntityId)
                        .build();

        EntityAuthorityInfo authority =
                EntityAuthorityInfo.newBuilder()
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder())
                        .setRendererChangedInfo(EntityRendererChangedInfo.newBuilder())
                        .setAiInfo(
                                gadgetAiInfo())
                        .setBornPos(getPosition().toProto())
                        .build();

        SceneGadgetInfo.Builder gadgetInfo =
                SceneGadgetInfo.newBuilder()
                        .setGadgetId(this.getGadgetId())
                        .setAuthorityPeerId(this.getOwner().getPeerId())
                        .setIsEnableInteract(true)
                        .setVehicleInfo(vehicle);

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
                        .setGadget(gadgetInfo)
                        .setEntityAuthorityInfo(authority)
                        .setLifeState(1);

        this.injectIntMotionInfo(entityInfo);

        PropPair pair =
                PropPair.newBuilder()
                        .setType(PlayerProperty.PROP_LEVEL.getId())
                        .setPropValue(ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, 90))
                        .build();

        this.addAllFightPropsToEntityInfo(entityInfo);
        entityInfo.addPropList(pair);

        return entityInfo.build();
    }

    @Override
    public void initAbilities() {
        // TODO Auto-generated method stub
        if (this.configGadget != null && this.configGadget.getAbilities() != null) {
            for (var ability : this.configGadget.getAbilities()) {
                this.addConfigAbility(ability);
            }
        }
    }

    /**
     * Puts the riders off once the mount's own death has been announced.
     *
     * <p>Two orderings were tried and lost. Telling the client to dismount while the mount is still in the
     * scene makes it play its "possession ended, the Saurian becomes a soul candle" transformation instead
     * of the death; not telling it at all leaves the player stuck in the dead dragon's camera, unable to
     * switch back, with only a teleport out. {@code Scene.killEntity} broadcasts the death, takes the
     * entity out, and only then gets here - so the collapse is already underway by the time the rider is
     * released, and nothing is left standing to be turned into a candle.
     */
    @Override
    public void onDeath(int killerId) {
        super.onDeath(killerId);

        if (!NatsaurusVehicleHelper.isNatsaurusMount(this)) return;
        if (this.vehicleMembers.isEmpty()) return;

        for (var member : new ArrayList<>(this.vehicleMembers)) {
            Player rider = null;
            for (var player : getScene().getPlayers()) {
                if (player.getUid() == member.getUid()) {
                    rider = player;
                    break;
                }
            }
            if (rider == null) {
                this.vehicleMembers.remove(member);
                continue;
            }
            NatsaurusVehicleHelper.dismount(rider, this, member);
        }
    }
}
