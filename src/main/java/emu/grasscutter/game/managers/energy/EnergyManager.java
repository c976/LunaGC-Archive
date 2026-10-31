package emu.grasscutter.game.managers.energy;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.monster.MonsterData.HpDrops;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.BurstInvulnHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityActionGenerateElemBallOuterClass.AbilityActionGenerateElemBall;
import emu.grasscutter.net.proto.AbilityIdentifierOuterClass.AbilityIdentifier;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangeEnergyReasonOuterClass.ChangeEnergyReason;
import emu.grasscutter.net.proto.EvtBeingHitInfoOuterClass.EvtBeingHitInfo;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;

public class EnergyManager extends BasePlayerManager {
    private static final Int2ObjectMap<List<EnergyDropInfo>> energyDropData =
            new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<List<SkillParticleGenerationInfo>>
            skillParticleGenerationData = new Int2ObjectOpenHashMap<>();
    private final Object2IntMap<EntityAvatar> avatarNormalProbabilities;
    @Getter private boolean energyUsage;

    /**
     * Deduct energy only after ability confirms the burst actually started. SkillSucc alone can fire
     * during dash recovery without a real cast (energy would vanish with no Q).
     */
    private static final long PENDING_BURST_TIMEOUT_MS = 1200L;

    private int pendingBurstSkillId;
    private long pendingBurstAvatarGuid;
    private float pendingBurstCost;
    private long pendingBurstMs;

    /** Same cast can confirm from AbilityManager + AvatarSkillStart within a short window. */
    private int lastBurstSkillId;
    private long lastBurstAvatarGuid;
    private long lastBurstCastMs;

    public EnergyManager(Player player) {
        super(player);
        this.avatarNormalProbabilities = new Object2IntOpenHashMap<>();
        this.energyUsage = GAME_OPTIONS.energyUsage;
    }

    public static void initialize() {

        try {
            DataLoader.loadList("EnergyDrop.json", EnergyDropEntry.class)
                    .forEach(
                            entry -> {
                                energyDropData.put(entry.getDropId(), entry.getDropList());
                            });

            Grasscutter.getLogger().debug("Energy drop data successfully loaded.");
        } catch (Exception ex) {
            Grasscutter.getLogger().error("Unable to load energy drop data.", ex);
        }

        try {
            DataLoader.loadList("SkillParticleGeneration.json", SkillParticleGenerationEntry.class)
                    .forEach(
                            entry -> {
                                skillParticleGenerationData.put(entry.getAvatarId(), entry.getAmountList());
                            });

            Grasscutter.getLogger().debug("Skill particle generation data successfully loaded.");
        } catch (Exception ex) {
            Grasscutter.getLogger().error("Unable to load skill particle generation data data.", ex);
        }
    }

    private int getBallCountForAvatar(int avatarId) {

        int count = 2;

        if (!skillParticleGenerationData.containsKey(avatarId)) {
            Grasscutter.getLogger().warn("No particle generation data for avatarId {} found.", avatarId);
        }

        else {
            int roll = ThreadLocalRandom.current().nextInt(0, 100);
            int percentageStack = 0;
            for (SkillParticleGenerationInfo info : skillParticleGenerationData.get(avatarId)) {
                int chance = info.getChance();
                percentageStack += chance;
                if (roll < percentageStack) {
                    count = info.getValue();
                    break;
                }
            }
        }

        return count;
    }

    private int getBallIdForElement(ElementType element) {

        if (element == null) {
            return 2024;
        }

        return switch (element) {
            case Fire -> 2017;
            case Water -> 2018;
            case Grass -> 2019;
            case Electric -> 2020;
            case Wind -> 2021;
            case Ice -> 2022;
            case Rock -> 2023;
            default -> 2024;
        };
    }

    public void handleGenerateElemBall(AbilityInvokeEntry invoke)
            throws InvalidProtocolBufferException {

        AbilityActionGenerateElemBall action =
                AbilityActionGenerateElemBall.parseFrom(invoke.getAbilityData());
        if (action == null) {
            return;
        }

        int itemId = 2024;

        int amount = 2;

        Optional<EntityAvatar> avatarEntity =
                this.getCastingAvatarEntityForEnergy(invoke.getEntityId());

        if (avatarEntity.isPresent()) {
            Avatar avatar = avatarEntity.get().getAvatar();

            if (avatar != null) {
                int avatarId = avatar.getAvatarId();
                AvatarSkillDepotData skillDepotData = avatar.getSkillDepot();

                amount = this.getBallCountForAvatar(avatarId);

                if (skillDepotData != null) {
                    ElementType element = skillDepotData.getElementType();
                    itemId = this.getBallIdForElement(element);
                }
            }
        }

        var pos = new Position(action.getPos());
        for (int i = 0; i < amount; i++) {
            this.generateElemBall(itemId, pos, 1);
        }
    }

    private void generateEnergyForNormalAndCharged(EntityAvatar avatar) {

        WeaponType weaponType = avatar.getAvatar().getAvatarData().getWeaponType();

        if (!this.avatarNormalProbabilities.containsKey(avatar)) {
            this.avatarNormalProbabilities.put(avatar, weaponType.getEnergyGainInitialProbability());
        }

        int currentProbability = this.avatarNormalProbabilities.getInt(avatar);
        int roll = ThreadLocalRandom.current().nextInt(0, 100);

        if (roll < currentProbability) {
            avatar.addEnergy(1.0f, PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, true);
            this.avatarNormalProbabilities.put(avatar, weaponType.getEnergyGainInitialProbability());
        }

        else {
            this.avatarNormalProbabilities.put(
                    avatar, currentProbability + weaponType.getEnergyGainIncreaseProbability());
        }
    }

    public void handleAttackHit(EvtBeingHitInfo hitInfo) {

        AttackResult attackRes = hitInfo.getAttackResult();

        Optional<EntityAvatar> attackerEntity =
                this.getCastingAvatarEntityForEnergy(attackRes.getAttackerId());
        if (attackerEntity.isEmpty()
                || this.player.getTeamManager().getCurrentAvatarEntity().getId()
                        != attackerEntity.get().getId()) {
            return;
        }

        GameEntity targetEntity = this.player.getScene().getEntityById(attackRes.getDefenseId());
        if (!(targetEntity instanceof EntityMonster targetMonster)) {
            return;
        }

        MonsterType targetType = targetMonster.getMonsterData().getType();
        if (targetType != MonsterType.MONSTER_ORDINARY && targetType != MonsterType.MONSTER_BOSS) {
            return;
        }

        AbilityIdentifier ability = attackRes.getAbilityIdentifier();

        if (ability != AbilityIdentifier.getDefaultInstance()) {
            return;
        }

        this.generateEnergyForNormalAndCharged(attackerEntity.get());
    }

    private void handleBurstCast(Avatar avatar, int skillId) {

        if (!GAME_OPTIONS.energyUsage || !this.energyUsage) {
            return;
        }

        this.expirePendingBurstIfNeeded();

        var skillData = GameData.getAvatarSkillDataMap().get(skillId);
        float cost = 0f;
        if (skillData != null && skillData.getCostElemVal() > 0) {
            // Prefer the cast skill's cost (e.g. Flins 11209 = 30, not depot energySkill 11205 = 80).
            cost = skillData.getCostElemVal();
        } else if (avatar.getSkillDepot() != null
                && skillId == avatar.getSkillDepot().getEnergySkill()
                && avatar.getSkillDepot().getEnergySkillData() != null) {
            cost = avatar.getSkillDepot().getEnergySkillData().getCostElemVal();
        }

        if (cost <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long guid = avatar.getGuid();
        // Already confirmed/consumed for this cast (late SkillSucc after ability confirm).
        if (skillId == this.lastBurstSkillId
                && guid == this.lastBurstAvatarGuid
                && now - this.lastBurstCastMs < 1500L) {
            return;
        }

        this.pendingBurstSkillId = skillId;
        this.pendingBurstAvatarGuid = guid;
        this.pendingBurstCost = cost;
        this.pendingBurstMs = now;

        Grasscutter.getLogger()
                .debug(
                        "[Energy] burst pending skillId={} cost={} avatarId={} (wait ability confirm)",
                        skillId,
                        cost,
                        avatar.getAvatarId());

        try {
            if (this.player != null && this.player.getAbilityManager() != null) {
                BurstInvulnHelper.arm(this.player.getAbilityManager());
            }
        } catch (Throwable ignored) {
            // Compatibility hook must not affect energy consumption.
        }
    }

    /**
     * Ability layer confirmed the burst started (AvatarSkillStart / invuln modifier). Deduct now.
     *
     * @return true if energy was consumed on this call
     */
    public boolean confirmBurstCast(Avatar avatar, int skillId) {
        if (avatar == null || !GAME_OPTIONS.energyUsage || !this.energyUsage) {
            return false;
        }

        this.expirePendingBurstIfNeeded();

        long now = System.currentTimeMillis();
        long guid = avatar.getGuid();

        if (skillId == this.lastBurstSkillId
                && guid == this.lastBurstAvatarGuid
                && now - this.lastBurstCastMs < 1500L) {
            return false;
        }

        if (this.pendingBurstCost <= 0f
                || skillId != this.pendingBurstSkillId
                || guid != this.pendingBurstAvatarGuid) {
            return false;
        }

        float cost = this.pendingBurstCost;
        this.clearPendingBurst();
        this.lastBurstSkillId = skillId;
        this.lastBurstAvatarGuid = guid;
        this.lastBurstCastMs = now;

        Grasscutter.getLogger()
                .debug(
                        "[Energy] burst consume (confirmed) skillId={} cost={} avatarId={}",
                        skillId,
                        cost,
                        avatar.getAvatarId());

        avatar.getAsEntity()
                .consumeEnergy(cost, ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_SKILL_START);
        return true;
    }

    /** Drop unconfirmed SkillSucc pending (dash-recovery false start, cancelled cast, etc.). */
    public void onTick() {
        this.expirePendingBurstIfNeeded();
    }

    private void expirePendingBurstIfNeeded() {
        if (this.pendingBurstCost <= 0f) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.pendingBurstMs < PENDING_BURST_TIMEOUT_MS) {
            return;
        }

        Grasscutter.getLogger()
                .debug(
                        "[Energy] burst pending expired (no ability confirm) skillId={} cost={} guid={}",
                        this.pendingBurstSkillId,
                        this.pendingBurstCost,
                        this.pendingBurstAvatarGuid);

        long expiredGuid = this.pendingBurstAvatarGuid;
        this.clearPendingBurst();

        // Re-sync energy UI in case the client locally blanked the bar on SkillSucc.
        try {
            if (this.player != null && this.player.getTeamManager() != null) {
                for (EntityAvatar entityAvatar : this.player.getTeamManager().getActiveTeam()) {
                    if (entityAvatar != null && entityAvatar.getAvatar().getGuid() == expiredGuid) {
                        this.player.sendPacket(new PacketAvatarFightPropNotify(entityAvatar.getAvatar()));
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // UI re-sync is best-effort.
        }

        try {
            if (this.player != null && this.player.getAbilityManager() != null) {
                this.player.getAbilityManager().removePendingEnergyClear();
                BurstInvulnHelper.clear(this.player.getAbilityManager());
            }
        } catch (Throwable ignored) {
            // Best-effort cleanup for a cast that never started.
        }
    }

    private void clearPendingBurst() {
        this.pendingBurstSkillId = 0;
        this.pendingBurstAvatarGuid = 0L;
        this.pendingBurstCost = 0f;
        this.pendingBurstMs = 0L;
    }

    public void handleEvtDoSkillSuccNotify(GameSession session, int skillId, int casterId) {

        Optional<EntityAvatar> caster =
                this.player.getTeamManager().getActiveTeam().stream()
                        .filter(character -> character.getId() == casterId)
                        .findFirst();

        if (caster.isEmpty()) {
            return;
        }

        EntityAvatar casterEntity = caster.get();
        Avatar avatar = casterEntity.getAvatar();

        this.handleBurstCast(avatar, skillId);

        if (avatar.getAvatarId() == 10000096) {
            this.player.getAbilityManager().onArlecchinoSkillNotify(skillId);
            // Matched by id rather than derived from the depot and the skill excel: both lookups
            // can come back empty on the first cast after login, and then neither -1 nor -1f looks
            // like a burst - the cast went unregistered and that Q consumed nothing.
            if (skillId == ArlecchinoBurstBoL.ARLECCHINO_BURST_SKILL_ID) {
                // Arming, not clearing: the client scales the burst by the debt it still sees, so
                // zeroing it here - a second before the slash lands - would gut the damage.
                ArlecchinoBurstBoL.onBurstCast(casterEntity);
            }
        }
    }

    private void generateElemBallDrops(EntityMonster monster, int dropId) {

        if (!energyDropData.containsKey(dropId)) {
            Grasscutter.getLogger().warn("No drop data for dropId {} found.", dropId);
            return;
        }

        for (EnergyDropInfo info : energyDropData.get(dropId)) {
            this.generateElemBall(info.getBallId(), monster.getPosition(), info.getCount());
        }
    }

    public void handleMonsterEnergyDrop(
            EntityMonster monster, float hpBeforeDamage, float hpAfterDamage) {

        MonsterType type = monster.getMonsterData().getType();
        if (type != MonsterType.MONSTER_ORDINARY && type != MonsterType.MONSTER_BOSS) {
            return;
        }

        float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float thresholdBefore = hpBeforeDamage / maxHp;
        float thresholdAfter = hpAfterDamage / maxHp;

        for (HpDrops drop : monster.getMonsterData().getHpDrops()) {
            if (drop.getDropId() == 0) {
                continue;
            }

            float threshold = drop.getHpPercent() / 100.0f;
            if (threshold < thresholdBefore && threshold >= thresholdAfter) {
                this.generateElemBallDrops(monster, drop.getDropId());
            }
        }

        if (hpAfterDamage <= 0 && monster.getMonsterData().getKillDropId() != 0) {
            this.generateElemBallDrops(monster, monster.getMonsterData().getKillDropId());
        }
    }

    private void generateElemBall(int ballId, Position position, int count) {

        ItemData itemData = GameData.getItemDataMap().get(ballId);
        if (itemData == null) {
            return;
        }

        EntityItem energyBall =
                new EntityItem(this.getPlayer().getScene(), this.getPlayer(), itemData, position, count);
        this.getPlayer().getScene().addEntity(energyBall);
    }

    private Optional<EntityAvatar> getCastingAvatarEntityForEnergy(int invokeEntityId) {

        GameEntity entity = this.player.getScene().getEntityById(invokeEntityId);

        int avatarEntityId =
                (!(entity instanceof EntityClientGadget))
                        ? invokeEntityId
                        : ((EntityClientGadget) entity).getOriginalOwnerEntityId();

        return this.player.getTeamManager().getActiveTeam().stream()
                .filter(character -> character.getId() == avatarEntityId)
                .findFirst();
    }

    public boolean refillActiveEnergy() {
        var activeEntity = this.player.getTeamManager().getCurrentAvatarEntity();
        return activeEntity.addEnergy(
                activeEntity.getAvatar().getSkillDepot().getEnergySkillData().getCostElemVal());
    }

    public void refillTeamEnergy(PropChangeReason changeReason, boolean isFlat) {
        for (var entityAvatar : this.player.getTeamManager().getActiveTeam()) {

            var skillDepot = entityAvatar.getAvatar().getSkillDepot();
            if (skillDepot != null) {
                entityAvatar.addEnergy(
                        skillDepot.getEnergySkillData().getCostElemVal(), changeReason, isFlat);
            }
        }
    }

    public void setEnergyUsage(boolean energyUsage) {
        this.energyUsage = energyUsage;
        if (!energyUsage) {
            this.refillTeamEnergy(PropChangeReason.PropChangeReason_PROP_CHANGE_GM, true);
        }
    }
}
