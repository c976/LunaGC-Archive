package emu.grasscutter.game.entity;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair;
import emu.grasscutter.net.proto.ChangeEnergyReasonOuterClass.ChangeEnergyReason;
import emu.grasscutter.net.proto.ChangHpReasonOuterClass.ChangHpReason;
import emu.grasscutter.net.proto.EntityAuthorityInfoOuterClass.EntityAuthorityInfo;
import emu.grasscutter.net.proto.EntityClientDataOuterClass.EntityClientData;
import emu.grasscutter.net.proto.EntityRendererChangedInfoOuterClass.EntityRendererChangedInfo;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.PropPairOuterClass.PropPair;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.SceneAvatarInfoOuterClass.SceneAvatarInfo;
import emu.grasscutter.net.proto.SceneEntityAiInfoOuterClass.SceneEntityAiInfo;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.server.event.player.PlayerMoveEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.helpers.ProtoHelper;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import lombok.*;

public class EntityAvatar extends GameEntity {
    @Getter private final Avatar avatar;
    private static long lastExecutionTime = 0;
    public static final long COOLDOWN = 14000;

    @Getter private PlayerDieType killedType;
    @Getter private int killedBy;

    public EntityAvatar(Avatar avatar) {
        this(null, avatar);
    }

    public EntityAvatar(Scene scene, Avatar avatar) {
        super(scene);

        this.avatar = avatar;
        this.avatar.setCurrentEnergy();

        if (scene != null) {
            var world = scene.getWorld();
            this.id = world.getNextEntityId(EntityIdType.AVATAR);

            var weapon = this.getAvatar().getWeapon();
            if (weapon != null) {
                if (!(weapon.getWeaponEntity() != null && weapon.getWeaponEntity().getScene() == scene)) {
                    weapon.setWeaponEntity(
                            new EntityWeapon(this.getPlayer().getScene(), weapon.getItemData().getGadgetId()));
                    scene.getWeaponEntities().put(weapon.getWeaponEntity().getId(), weapon.getWeaponEntity());
                }
            }
            TpsWeaponSystem.ensureWeaponEntities(this.getAvatar(), scene);
        } else {
            Grasscutter.getLogger()
                    .error("Unable to create EntityAvatar instance; provided scene is null.");
        }

        this.initAbilities();

        this.checkIfDead();
    }
    public long getLastExecutionTime() {
        return this.lastExecutionTime;
    }
    @Override
        public float getNyxValue() {
            if (this.getGlobalAbilityValues().containsKey("NyxValue")) {
                return this.getGlobalAbilityValues().get("NyxValue");
            } else {
                Grasscutter.getLogger().info("NyxValue from entityavatar not found");
                return 0f;
            }
        }

    public void setLastExecutionTime(long time) {
        this.lastExecutionTime = time;
    }

    @Override
    public int getEntityTypeId() {
        return this.getAvatar().getAvatarId();
    }

    public Player getPlayer() {
        return this.avatar.getPlayer();
    }

    @Override
    public Position getPosition() {
        return getPlayer().getPosition();
    }

    @Override
    public Position getRotation() {
        return getPlayer().getRotation();
    }

    @Override
    public Int2FloatMap getFightProperties() {
        return getAvatar().getFightProperties();
    }

    public int getWeaponEntityId() {
        var avatar = this.getAvatar();

        if (avatar.getWeapon() != null && avatar.getWeapon().getWeaponEntity() != null) {
            return avatar.getWeapon().getWeaponEntity().getId();
        } else return 0;
    }

    @Override
    public void onDeath(int killerId) {
        boolean alreadyDead = this.isDead();
        var st = Thread.currentThread().getStackTrace();
        Grasscutter.getLogger().info("[DEATH] avatarId={} entityId={} killerId={} | {}  {}  {}  {}  {}",
            this.getAvatar().getAvatarId(), this.getId(), killerId,
            st.length > 2 ? st[2] : "-",
            st.length > 3 ? st[3] : "-",
            st.length > 4 ? st[4] : "-",
            st.length > 5 ? st[5] : "-",
            st.length > 6 ? st[6] : "-");
        super.onDeath(killerId);

        this.killedType = PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER;
        this.killedBy = killerId;
        clearEnergy(ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_NONE);
        notifyTeamWipeIfNeeded(alreadyDead);
    }

    public void onDeath(PlayerDieType dieType, int killerId) {
        boolean alreadyDead = this.isDead();
        super.onDeath(killerId);

        this.killedType = dieType;
        this.killedBy = killerId;
        clearEnergy(ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_NONE);
        notifyTeamWipeIfNeeded(alreadyDead);
    }

    private void notifyTeamWipeIfNeeded(boolean alreadyDead) {
        if (alreadyDead || !this.isDead()) return;
        var owner = this.getPlayer();
        if (owner == null || owner.getQuestManager() == null || owner.getTeamManager() == null) return;
        var team = owner.getTeamManager().getActiveTeam();
        if (!team.isEmpty() && team.stream().allMatch(member -> member != null && member.isDead())) {
            owner.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_TEAM_DEAD, 1);
        }
    }

    @Override
    public void initAbilities() {}

    private void addConfigAbility(String abilityName) {
        var data = GameData.getAbilityData(abilityName);
        if (data != null)
            this.getScene().getWorld().getHost().getAbilityManager().addAbilityToEntity(this, data);
    }

    @Override
    public float heal(float amount, boolean mute) {

        var currentHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        if (currentHp <= 0) {
            return 0f;
        }

        if (currentHp > 0 && this.isDead()) {
            this.setDead(false);
            mute = false;
        }

        float healed = super.heal(amount, mute);
        if (healed > 0f) {
            getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropChangeReasonNotify(
                                    this,
                                    FightProperty.FIGHT_PROP_CUR_HP,
                                    healed,
                                    mute
                                            ? PropChangeReason.PropChangeReason_PROP_CHANGE_NONE
                                            : PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                                    ChangHpReason.ChangHpReason_CHANGE_HP_SUB_ABILITY));
        }

        return healed;
    }

    @Override
    public float heal(float amount) {
        return this.heal(amount, false);
    }

    /**
     * Revives a downed character to a fraction of max HP. The ordinary {@link #heal} returns 0 outright at
     * HP &lt;= 0,
     * so the Qiqi and Barbara C6 revives have to come through here to actually bring the character back.
     */
    public float reviveToRatio(float ratio) {
        float curHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        if (curHp > 0f && this.isAlive()) {
            return 0f;
        }
        float maxHp = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 0f) {
            return 0f;
        }
        float hp = Math.max(1f, maxHp * Math.max(0f, ratio));
        if (hp > maxHp) {
            hp = maxHp;
        }
        this.setDead(false);
        this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
        if (this.getScene() != null) {
            this.getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP));
            this.getScene().broadcastPacket(new PacketLifeStateChangeNotify(this, LifeState.LIFE_ALIVE));
        }
        Player player = this.getPlayer();
        if (player != null) {
            player.sendPacket(
                    new PacketAvatarFightPropUpdateNotify(this.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
            player.sendPacket(new PacketAvatarFightPropNotify(this.getAvatar()));
            player.sendPacket(new PacketAvatarLifeStateChangeNotify(this.getAvatar()));
        }
        return hp;
    }
        public FightProperty GetEnergyProp(Avatar avatar) {
        if(avatar.getSkillDepot().getEnergySkillData().getSpecialEnergyMin() > 0){
            return FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY;
        }else{
            return avatar.getSkillDepot().getElementType().getCurEnergyProp();
        }

    }

    public void clearEnergy(ChangeEnergyReason reason) {

        val curEnergyProp = GetEnergyProp(this.getAvatar());
        float curEnergy = this.getFightProperty(curEnergyProp);
        Grasscutter.getLogger().debug("EnergyProp: "+curEnergyProp.name());

        this.avatar.setCurrentEnergy(curEnergyProp, 0);
        getPlayer().sendPacket(new PacketAvatarFightPropNotify(this.getAvatar()));

        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, curEnergyProp));

        if (reason == ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_SKILL_START) {
            this.getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropChangeReasonNotify(this, curEnergyProp, -curEnergy, reason));
        }
    }

    /**
     * Deduct a fixed amount of elemental energy (e.g. Flins special burst costs 30 of 80 max).
     * Special-energy bars still dump to zero via {@link #clearEnergy}.
     */
    public void consumeEnergy(float amount, ChangeEnergyReason reason) {
        val curEnergyProp = GetEnergyProp(this.getAvatar());
        if (curEnergyProp == FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY) {
            clearEnergy(reason);
            return;
        }

        float curEnergy = this.getFightProperty(curEnergyProp);
        float newEnergy = Math.max(0f, curEnergy - amount);
        float delta = newEnergy - curEnergy;
        if (delta == 0f) {
            return;
        }

        this.avatar.setCurrentEnergy(curEnergyProp, newEnergy);
        getPlayer().sendPacket(new PacketAvatarFightPropNotify(this.getAvatar()));
        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, curEnergyProp));

        if (reason == ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_SKILL_START) {
            this.getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropChangeReasonNotify(this, curEnergyProp, delta, reason));
        }
    }

    public boolean addEnergy(float amount) {
        var elementType = this.getAvatar().getSkillDepot().getElementType();
        var curEnergyProp = elementType.getCurEnergyProp();
        var curEnergy = this.getFightProperty(curEnergyProp);
        var maxEnergy = this.getFightProperty(elementType.getMaxEnergyProp());
        // Quest refills add a whole burst cost; past the max a single burst would leave the bar full.
        var next = maxEnergy > 0 ? Math.min(curEnergy + amount, maxEnergy) : curEnergy + amount;
        if (next == curEnergy) return false;

        this.getAvatar().setCurrentEnergy(curEnergyProp, next);

        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, curEnergyProp));
        return true;
    }

    public void addEnergy(float amount, PropChangeReason reason) {
        this.addEnergy(amount, reason, false);
    }

    /** Flat elemental energy from an ability; special-energy gauges are left alone. */
    public void addAbilityEnergy(float amount) {
        var energySkill = this.getAvatar().getSkillDepot().getEnergySkillData();
        if (!this.getPlayer().getEnergyManager().isEnergyUsage()
                || (energySkill != null && energySkill.getSpecialEnergyMin() > 0)) return;
        this.addEnergy(
                amount,
                PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                true,
                ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_ABILITY);
    }

    public void addEnergy(float amount, PropChangeReason reason, boolean isFlat) {
        this.addEnergy(amount, reason, isFlat, null);
    }

    public void addEnergy(
            float amount, PropChangeReason reason, boolean isFlat, ChangeEnergyReason energyReason) {

        val elementType = this.getAvatar().getSkillDepot().getElementType();
        val curEnergyProp = elementType.getCurEnergyProp();
        val maxEnergyProp = elementType.getMaxEnergyProp();

        float curEnergy = this.getFightProperty(curEnergyProp);
        float maxEnergy = this.getFightProperty(maxEnergyProp);

        if (!isFlat) {
            amount *= this.getFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY);
        }

        float newEnergy = Math.min(curEnergy + amount, maxEnergy);

        if (newEnergy != curEnergy) {
            float delta = newEnergy - curEnergy;
            this.avatar.setCurrentEnergy(curEnergyProp, newEnergy);

            // Absolute sync for avatar panel / off-field sidebar icons.
            this.getScene()
                    .broadcastPacket(new PacketAvatarFightPropUpdateNotify(this.getAvatar(), curEnergyProp));
            // Absolute sync for the scene entity (critical on character swap).
            this.getScene()
                    .broadcastPacket(new PacketEntityFightPropUpdateNotify(this, curEnergyProp));
            // PropDelta must be the change amount, NOT the absolute energy.
            // Sending absolute as delta made the client over-charge; swapping then
            // re-synced to the real server value and looked like energy "vanished".
            this.getScene()
                    .broadcastPacket(
                            energyReason == null
                                    ? new PacketEntityFightPropChangeReasonNotify(this, curEnergyProp, delta, reason)
                                    : new PacketEntityFightPropChangeReasonNotify(
                                            this, curEnergyProp, delta, reason, energyReason));
        }
    }

    public SceneAvatarInfo getSceneAvatarInfo() {
        val avatar = this.getAvatar();
        val player = this.getPlayer();
        SceneAvatarInfo.Builder avatarInfo =
                SceneAvatarInfo.newBuilder()
                        .setUid(player.getUid())
                        .setAvatarId(avatar.getAvatarId())
                        .setGuid(avatar.getGuid())
                        .setPeerId(player.getPeerId())
                        .addAllTalentIdList(avatar.getTalentIdList())
                        .setCoreProudSkillLevel(avatar.getCoreProudSkillLevel())
                        .putAllSkillLevelMap(avatar.getSkillLevelMap())
                        .setSkillDepotId(avatar.getSkillDepotId())
                        .addAllInherentProudSkillList(avatar.getProudSkillList())
                        .putAllProudSkillExtraLevelMap(avatar.getProudSkillBonusMap())
                        .addAllTeamResonanceList(player.getTeamManager().getTeamResonances())
                        .setWearingFlycloakId(avatar.getFlyCloak())
                        .setCostumeId(avatar.getCostume())
                        .setTraceEffectId(avatar.getTraceEffect())
                        .setWeaponSkinId(avatar.getWeaponSkinId())
                        .setBornTime(avatar.getBornTime());

        for (GameItem item : avatar.getEquips().values()) {
            if (item.getItemData().getEquipType() == EquipType.EQUIP_WEAPON) {
                avatarInfo.setWeapon(item.createSceneWeaponInfo(avatar.getWeaponSkinId()));
            } else {
                avatarInfo.addReliquaryList(item.createSceneReliquaryInfo());
            }
            avatarInfo.addEquipIdList(item.getItemId());
        }
        avatarInfo.addAllTpsWeaponList(TpsWeaponSystem.getSceneWeaponInfos(avatar));

        return avatarInfo.build();
    }

    @Override
    public SceneEntityInfo toProto() {
        EntityAuthorityInfo authority =
                EntityAuthorityInfo.newBuilder()
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder())
                        .setRendererChangedInfo(EntityRendererChangedInfo.newBuilder())
                        .setAiInfo(
                                SceneEntityAiInfo.newBuilder().setIsEnteredCombat(true))
                        .setBornPos(Vector.newBuilder())
                        .build();

        SceneEntityInfo.Builder entityInfo =
                SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setEntityType(ProtEntityType.ProtEntityType_PROT_ENTITY_AVATAR)
                        .addAnimatorParaList(AnimatorParameterValueInfoPair.newBuilder())
                        .setEntityClientData(EntityClientData.newBuilder())
                        .setEntityAuthorityInfo(authority)
                        .setLastMoveSceneTimeMs(this.getLastMoveSceneTimeMs())
                        .setLastMoveReliableSeq(this.getLastMoveReliableSeq())
                        .setLifeState(this.getLifeState().getValue());

        if (this.getScene() != null) {
            entityInfo.setMotionInfo(this.getMotionInfo());
            this.injectIntMotionInfo(entityInfo);
        }

        this.addAllFightPropsToEntityInfo(entityInfo);

        PropPair pair =
                PropPair.newBuilder()
                        .setType(PlayerProperty.PROP_LEVEL.getId())
                        .setPropValue(
                                ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, getAvatar().getLevel()))
                        .build();
        entityInfo.addPropList(pair);

        entityInfo.setAvatar(this.getSceneAvatarInfo());

        return entityInfo.build();
    }

    public AbilityControlBlock getAbilityControlBlock() {
        AvatarData data = this.getAvatar().getAvatarData();
        AbilityControlBlock.Builder abilityControlBlock = AbilityControlBlock.newBuilder();
        int embryoId = 0;

        if (data.getAbilities() != null) {
            for (int id : data.getAbilities()) {
                AbilityEmbryo emb =
                        AbilityEmbryo.newBuilder()
                                .setAbilityId(++embryoId)
                                .setAbilityNameHash(id)
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build();
                abilityControlBlock.addAbilityEmbryoList(emb);
            }
        }

        boolean inNatlan = this.getPlayer().isInNatlan();
        for (int id : GameConstants.DEFAULT_ABILITY_HASHES) {
            // The client attaches this ability and draws the bar even without an SGV value.
            if (!GameConstants.shouldAttachDefaultAvatarAbility(id, inNatlan)) continue;
            AbilityEmbryo emb =
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++embryoId)
                            .setAbilityNameHash(id)
                            .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                            .build();
            abilityControlBlock.addAbilityEmbryoList(emb);
        }

        for (int id : this.getPlayer().getTeamManager().getTeamResonancesConfig()) {
            AbilityEmbryo emb =
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++embryoId)
                            .setAbilityNameHash(id)
                            .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                            .build();
            abilityControlBlock.addAbilityEmbryoList(emb);
        }

        AvatarSkillDepotData skillDepot =
                GameData.getAvatarSkillDepotDataMap().get(this.getAvatar().getSkillDepotId());
        if (skillDepot != null && skillDepot.getAbilities() != null) {
            for (int id : skillDepot.getAbilities()) {
                AbilityEmbryo emb =
                        AbilityEmbryo.newBuilder()
                                .setAbilityId(++embryoId)
                                .setAbilityNameHash(id)
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build();
                abilityControlBlock.addAbilityEmbryoList(emb);
            }
        }

        if (this.getAvatar().getExtraAbilityEmbryos().size() > 0) {
            for (String skill : this.getAvatar().getExtraAbilityEmbryos()) {
                AbilityEmbryo emb =
                        AbilityEmbryo.newBuilder()
                                .setAbilityId(++embryoId)
                                .setAbilityNameHash(Utils.abilityHash(skill))
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build();
                abilityControlBlock.addAbilityEmbryoList(emb);
            }
        }

        try {
            embryoId =
                    emu.grasscutter.game.ability.IceTravelerAbilityHelper.appendSkillUpgradeEmbryos(
                            this.getAvatar(), abilityControlBlock, embryoId);
        } catch (Throwable ignored) {
        }

        try {
            embryoId =
                    emu.grasscutter.game.player.DiveAbilityHelper.appendDiveEmbryos(
                            this, abilityControlBlock, embryoId);
        } catch (Throwable ignored) {
        }

        embryoId =
                emu.grasscutter.game.icecreature.IceCreatureAbilities.appendEmbryos(
                        this, abilityControlBlock, embryoId);

        return abilityControlBlock.build();
    }

    @Override
    public void move(Position newPosition, Position rotation) {

        PlayerMoveEvent event =
                new PlayerMoveEvent(
                        this.getPlayer(), PlayerMoveEvent.MoveType.PLAYER, this.getPosition(), newPosition);
        event.call();

        super.move(event.getDestination(), rotation);
        this.getPlayer().refreshPhlogistonRegion();
        emu.grasscutter.game.player.SnezhnayaFrigid.refresh(this.getPlayer());
    }

    @Override
    public void onAbilityValueUpdate() {
        super.onAbilityValueUpdate();

        if (this.getGlobalAbilityValues().containsKey("_ABILITY_UziExplode_Count")) {
            var count = this.getGlobalAbilityValues().get("_ABILITY_UziExplode_Count");
            if (count == 2f) {
                this.getGlobalAbilityValues().remove("_ABILITY_UziExplode_Count");
                this.getPlayer().getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_SKILL, 10006);
            }
        }
    }
}
