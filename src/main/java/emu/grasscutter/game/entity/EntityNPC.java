package emu.grasscutter.game.entity;

import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.scripts.data.SceneNPC;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import lombok.Getter;

public class EntityNPC extends GameEntity {
    @Getter(onMethod_ = @Override)
    private final Position position;

    @Getter(onMethod_ = @Override)
    private final Position rotation;

    private final SceneNPC metaNpc;
    private final int npcId;
    @Getter private final int suiteId;

    public EntityNPC(Scene scene, SceneNPC metaNPC, int blockId, int suiteId) {
        super(scene);
        this.id = getScene().getWorld().getNextEntityId(EntityIdType.NPC);
        setConfigId(metaNPC.config_id);
        setGroupId(metaNPC.group.id);
        setBlockId(blockId);
        this.suiteId = suiteId;
        this.position = metaNPC.pos.clone();
        this.rotation = metaNPC.rot.clone();
        this.metaNpc = metaNPC;
        this.npcId = metaNPC.npc_id;
    }

    /**
     * A bare NPC from NpcExcelConfigData, placed by hand rather than by a scene script: it shows the
     * model and nothing else - no talk, no route, no group behind it.
     */
    public EntityNPC(Scene scene, int npcId, Position position, Position rotation) {
        super(scene);
        this.id = getScene().getWorld().getNextEntityId(EntityIdType.NPC);
        this.suiteId = 0;
        this.position = position.clone();
        this.rotation = rotation.clone();
        this.metaNpc = null;
        this.npcId = npcId;
    }

    /** Whether this NPC was placed by hand (the /npc command) instead of by a scene script. */
    public boolean isStandalone() {
        return this.metaNpc == null;
    }

    @Override
    public int getEntityTypeId() {
        return this.npcId;
    }

    /**
     * Nothing fights this entity, but an ability attached to one reads the whole FightProperty set
     * off its owner, and a null threw right through the action instead of reading zeroes.
     */
    @Override
    public Int2FloatMap getFightProperties() {
        return this.fightProperties;
    }

    private final Int2FloatMap fightProperties = new Int2FloatOpenHashMap();

    @Override
    public SceneEntityInfoOuterClass.SceneEntityInfo toProto() {

        EntityAuthorityInfoOuterClass.EntityAuthorityInfo authority =
                EntityAuthorityInfoOuterClass.EntityAuthorityInfo.newBuilder()
                        .setAbilityInfo(AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo.newBuilder())
                        .setRendererChangedInfo(
                                EntityRendererChangedInfoOuterClass.EntityRendererChangedInfo.newBuilder())
                        .setAiInfo(
                                SceneEntityAiInfoOuterClass.SceneEntityAiInfo.newBuilder()
                                        .setIsEnteredCombat(true))
                        .setBornPos(getPosition().toProto())
                        .build();

        SceneEntityInfoOuterClass.SceneEntityInfo.Builder entityInfo =
                SceneEntityInfoOuterClass.SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setEntityType(ProtEntityTypeOuterClass.ProtEntityType.ProtEntityType_PROT_ENTITY_NPC)
                        .setMotionInfo(
                                MotionInfoOuterClass.MotionInfo.newBuilder()
                                        .setPos(getPosition().toProto())
                                        .setRot(getRotation().toProto())
                                        .setSpeed(VectorOuterClass.Vector.newBuilder()))
                        .addAnimatorParaList(
                                AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair
                                        .newBuilder())
                        .setEntityClientData(EntityClientDataOuterClass.EntityClientData.newBuilder())
                        .setEntityAuthorityInfo(authority)
                        .setLifeState(1);

        this.injectIntMotionInfo(entityInfo);

        entityInfo.setNpc(
                SceneNpcInfoOuterClass.SceneNpcInfo.newBuilder()
                        .setNpcId(this.npcId)
                        .setBlockId(getBlockId())
                        .build());

        return entityInfo.build();
    }

    @Override
    public void initAbilities() {
        // An NPC carries no abilities of its own.
    }
}
