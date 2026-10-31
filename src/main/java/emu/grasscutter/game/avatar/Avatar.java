package emu.grasscutter.game.avatar;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import dev.morphia.annotations.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ExtraTalents;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.binout.OpenConfigEntry.SkillPointModifier;
import emu.grasscutter.data.common.FightPropData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.ItemData.WeaponProperty;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData.InherentProudSkillOpens;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.data.excels.trial.TrialAvatarTemplateData;
import emu.grasscutter.data.excels.weapon.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.ability.AbilityMaxHpRatioHelper;
import emu.grasscutter.game.ability.SymphonistWeaponHelper;
import emu.grasscutter.game.ability.WaterResonanceHelper;
import emu.grasscutter.game.avatar.AvatarStatePersist;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.proto.AvatarFetterInfoOuterClass.AvatarFetterInfo;
import emu.grasscutter.net.proto.AvatarInfoOuterClass.AvatarInfo;
import emu.grasscutter.net.proto.AvatarSkillInfoOuterClass.AvatarSkillInfo;
import emu.grasscutter.net.proto.FetterDataOuterClass.FetterData;
import emu.grasscutter.net.proto.GrantReasonOuterClass;
import emu.grasscutter.net.proto.ShowAvatarInfoOuterClass;
import emu.grasscutter.net.proto.ShowAvatarInfoOuterClass.ShowAvatarInfo;
import emu.grasscutter.net.proto.ShowEquipOuterClass.ShowEquip;
import emu.grasscutter.net.proto.TrialAvatarGrantRecordOuterClass.TrialAvatarGrantRecord;
import emu.grasscutter.net.proto.TrialAvatarInfoOuterClass.TrialAvatarInfo;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.helpers.ProtoHelper;
import it.unimi.dsi.fastutil.ints.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.*;
import lombok.*;
import org.bson.types.ObjectId;

@Entity(value = "avatars", useDiscriminator = false)
public class Avatar {
    @Transient @Getter private final Int2ObjectMap<GameItem> equips;
    @Transient @Getter private final Int2FloatOpenHashMap fightProperties;
    @Transient @Getter private final Int2FloatOpenHashMap fightPropOverrides;
    @Id private ObjectId id;
    @Indexed @Getter private int ownerId; // Id of player that this avatar belongs to
    @Transient private Player owner;
    @Transient @Getter private AvatarData avatarData;
    @Nullable @Transient @Getter private AvatarSkillDepotData skillDepot;
    @Transient @Getter private long guid; // Player unique id
    @Getter private int avatarId; // Id of avatar
    @Getter @Setter public float nyxValue;
    @Getter @Setter private int level = 1;
    @Getter @Setter private int exp;
    @Getter @Setter private int promoteLevel;
    @Getter @Setter private int satiation; // Fullness
    @Getter @Setter private int satiationPenalty; // When eating too much
    @Getter @Setter private float currentHp;
    private float currentEnergy;
    @Transient @Getter private Set<String> extraAbilityEmbryos;

    private List<Integer> fetters;

    // TPS weapons this avatar wears, by item id (each TPS weapon is unique per player).
    private List<Integer> tpsWeaponIds;

    private Map<Integer, Integer> skillLevelMap = new Int2IntArrayMap(7); // Talent levels

    @Transient @Getter
    private Map<Integer, Integer> skillExtraChargeMap = new Int2IntArrayMap(2); // Charges

    @Transient
    private Map<Integer, Integer> proudSkillBonusMap =
            new Int2IntArrayMap(2); // Talent bonus levels (from const)

    @Getter private int skillDepotId;
    private Set<Integer> talentIdList; // Constellation id list
    @Getter private Set<Integer> proudSkillList; // Character passives

    @Getter @Setter private int flyCloak;
    @Getter @Setter private int costume;
    @Getter @Setter private int weaponSkinId;
    @Getter private int bornTime;

    @Getter @Setter private int fetterLevel = 1;
    @Getter @Setter private int fetterExp;

    @Getter @Setter private int nameCardRewardId;
    @Getter @Setter private int nameCardId;
    private Set<Integer> rewardedFetterLevels = new HashSet<>();
    @Getter @Setter private int traceEffect;

    // trial avatar property
    @Getter @Setter private int trialAvatarId = 0;
    // cannot store to db if grant reason is not integer
    @Getter @Setter
    private int grantReason = GrantReasonOuterClass.GrantReason.GRANT_REASON_INVALID.getNumber();

    @Getter @Setter private int fromParentQuestId = 0;
    // so far no outer class or prop value has information of this, but from packet:
    // 1 = normal, 2 = trial avatar
    @Transient @Getter @Setter private int avatarType = Type.NORMAL.getNumber();

    @Deprecated // Do not use. Morhpia only!
    public Avatar() {
        this.equips = new Int2ObjectOpenHashMap<>();
        this.fightProperties = new Int2FloatOpenHashMap();
        this.fightPropOverrides = new Int2FloatOpenHashMap();
        this.extraAbilityEmbryos = new HashSet<>();
        this.fetters = new ArrayList<>(); // TODO Move to avatar
    }

    // On creation
    public Avatar(int avatarId) {
        this(GameData.getAvatarDataMap().get(avatarId));
    }

    public Avatar(AvatarData data) {
        this();

        this.avatarId = data.getId();
        this.nameCardRewardId = data.getNameCardRewardId();
        this.nameCardId = data.getNameCardId();
        this.avatarData = data;
        this.bornTime = (int) (System.currentTimeMillis() / 1000);
        this.flyCloak = 140001;

        this.talentIdList = new HashSet<>();
        this.proudSkillList = new HashSet<>();

        // Combat properties
        Stream.of(FightProperty.values())
                .map(FightProperty::getId)
                .filter(id -> (id > 0) && (id < 3000))
                .forEach(id -> this.setFightProperty(id, 0f));

        this.setSkillDepotData(
                switch (this.getAvatarId()) {
                    case GameConstants.MAIN_CHARACTER_MALE -> GameData.getAvatarSkillDepotDataMap().get(501);
                    case GameConstants.MAIN_CHARACTER_FEMALE -> GameData.getAvatarSkillDepotDataMap()
                            .get(701);
                    default -> data.getSkillDepot();
                });

        // Set stats
        this.recalcStats();
        this.currentHp = getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, this.currentHp);
        setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 100f);
        this.currentEnergy = 0f;
        // Load handler
        this.onLoad();
    }

    public static int getMinPromoteLevel(int level) {
        if (level > 80) {
            return 6;
        } else if (level > 70) {
            return 5;
        } else if (level > 60) {
            return 4;
        } else if (level > 50) {
            return 3;
        } else if (level > 40) {
            return 2;
        } else if (level > 20) {
            return 1;
        }
        return 0;
    }

    /**
     * @return True if the avatar is a main character.
     */
    public boolean isMainCharacter() {
        return List.of(GameConstants.MAIN_CHARACTER_MALE, GameConstants.MAIN_CHARACTER_FEMALE)
                .contains(this.getAvatarId());
    }

    public Player getPlayer() {
        return this.owner;
    }

    public ObjectId getObjectId() {
        return this.id;
    }

    protected void setAvatarData(AvatarData data) {
        if (this.avatarData != null) return;
        this.avatarData = data; // Used while loading this from the database
    }

    public void setOwner(Player player) {
        this.owner = player;
        this.ownerId = player.getUid();
        this.guid = player.getNextGameGuid();

        if (this.getAvatarId() == player.getMainCharacterId()) {
            // Apply skill depot based on player resonance.
            this.changeElement(player.getMainCharacterElement(), false);
        }
    }

    public boolean addSatiation(int value) {
        if (this.satiation >= 10000) return false;
        this.satiation += value;
        return true;
    }

    public float reduceSatiation(int value) {
        if (this.satiation == 0) return 0;
        this.satiation -= value;
        if (this.satiation < 0) {
            this.satiation = 0;
        }
        return this.satiation;
    }

    public float reduceSatiationPenalty(int value) {
        if (this.satiationPenalty == 0) return 0;
        this.satiationPenalty -= value;
        if (this.satiationPenalty < 0) {
            this.satiationPenalty = 0;
        }
        return this.satiationPenalty;
    }

    public GameItem getEquipBySlot(EquipType slot) {
        return this.getEquips().get(slot.getValue());
    }

    private GameItem getEquipBySlot(int slotId) {
        return this.getEquips().get(slotId);
    }

    /**
     * @return The avatar's equipped weapon.
     */
    @Nullable public GameItem getWeapon() {
        return this.getEquipBySlot(EquipType.EQUIP_WEAPON);
    }

    /**
     * @return The avatar's equipped weapon.
     * @throws NullPointerException If the avatar does not have a weapon.
     */
    public GameItem getWeaponNotNull() {
        return Objects.requireNonNull(this.getWeapon(), "Avatar does not have a weapon.");
    }

    protected void setSkillDepot(AvatarSkillDepotData skillDepot) {
        if (this.skillDepot != null) return;
        this.skillDepot = skillDepot; // Used while loading this from the database
    }

    /**
     * Changes this avatar's skill depot. Does not notify the player of the change.
     *
     * @param skillDepot The new skill depot.
     */
    public void setSkillDepotData(AvatarSkillDepotData skillDepot) {
        this.setSkillDepotData(skillDepot, false);
    }

    /**
     * Changes this avatar's skill depot.
     *
     * @param skillDepot The new skill depot.
     * @param notify Whether to notify the player of the change.
     */
    public void setSkillDepotData(AvatarSkillDepotData skillDepot, boolean notify) {
        // Set id and depot
        this.skillDepotId = skillDepot.getId();
        this.skillDepot = skillDepot;
        // Add any missing skills
        this.skillDepot
                .getSkillsAndEnergySkill()
                .forEach(skillId -> this.skillLevelMap.putIfAbsent(skillId, 1));
        // Add proud skills
        this.proudSkillList.clear();
        skillDepot.getInherentProudSkillOpens().stream()
                .filter(openData -> openData.getProudSkillGroupId() > 0)
                .filter(openData -> openData.getNeedAvatarPromoteLevel() <= this.getPromoteLevel())
                .mapToInt(openData -> (openData.getProudSkillGroupId() * 100) + 1)
                .filter(proudSkillId -> GameData.getProudSkillDataMap().containsKey(proudSkillId))
                .forEach(proudSkillId -> this.proudSkillList.add(proudSkillId));
        skillDepot.getQuestProudSkillGroupIds().intStream()
                .map(groupId -> (groupId * 100) + 1)
                .filter(proudSkillId -> GameData.getProudSkillDataMap().containsKey(proudSkillId))
                .forEach(proudSkillId -> this.proudSkillList.add(proudSkillId));
        this.recalcStats();

        if (notify) {
            owner.sendPacket(new PacketAvatarSkillDepotChangeNotify(this));
        }
    }

    /**
     * Changes the avatar's element to the target element. Only applies if the avatar has the element
     * in its 'candSkillDepot's.
     *
     * @param newElement The new element to change to.
     * @return True if the element was changed, false otherwise.
     */
    public boolean changeElement(@Nonnull ElementType newElement) {
        return this.changeElement(newElement, true);
    }

    /**
     * Changes the avatar's element to the target element. Only applies if the avatar has the element
     * in its 'candSkillDepot's.
     *
     * @param elementTypeToChange The new element to change to.
     * @param notify Whether to notify the player of the change.
     * @return True if the element was changed, false otherwise.
     */
    public boolean changeElement(@Nonnull ElementType elementTypeToChange, boolean notify) {
        var candSkillDepotIdsList = this.getAvatarData().getCandSkillDepotIds();
        var candSkillDepotIndex = elementTypeToChange.getDepotIndex();

        // if no candidate skill to change or index out of bound
        if (candSkillDepotIdsList == null || candSkillDepotIndex >= candSkillDepotIdsList.size()) {
            return false;
        }

        var candSkillDepotId = candSkillDepotIdsList.get(candSkillDepotIndex);

        // Sanity checks for skill depots
        val skillDepot = GameData.getAvatarSkillDepotDataMap().get((int) candSkillDepotId);
        if (skillDepot == null || skillDepot.getId() == skillDepotId) {
            return false;
        }

        // Set skill depot
        this.setSkillDepotData(skillDepot, notify);
        this.recalcStats(true);
        // Set element.
        this.getPlayer().setMainCharacterElement(elementTypeToChange);
        return true;
    }

    public List<Integer> getFetterList() {
        return fetters;
    }

    public void setFetterList(List<Integer> fetterList) {
        this.fetters = fetterList;
    }

    public void setCurrentEnergy() {
        if (GAME_OPTIONS.energyUsage) {
            this.setCurrentEnergy(this.currentEnergy);
        }
    }

    public void setCurrentEnergy(float currentEnergy) {
        var depot = this.skillDepot;
        if (depot != null && depot.getEnergySkillData() != null) {
            ElementType element = depot.getElementType();
            var maxEnergy = depot.getEnergySkillData().getCostElemVal();
            // Saves from before the refill cap can hold more than the max.
            if (maxEnergy > 0 && currentEnergy > maxEnergy) currentEnergy = maxEnergy;
            this.setFightProperty(element.getMaxEnergyProp(), maxEnergy);
            this.setFightProperty(
                    element.getCurEnergyProp(), GAME_OPTIONS.energyUsage ? currentEnergy : maxEnergy);
        }
    }

    public void setCurrentEnergy(FightProperty curEnergyProp, float currentEnergy) {
        if (GAME_OPTIONS.energyUsage) {
            this.setFightProperty(curEnergyProp, currentEnergy);
            this.currentEnergy = currentEnergy;
            this.save();
        }
    }


    public void setFightProperty(FightProperty prop, float value) {
        this.getFightProperties().put(prop.getId(), value);
    }

    private void setFightProperty(int id, float value) {
        this.getFightProperties().put(id, value);
    }

    public void addFightProperty(FightProperty prop, float value) {
        this.getFightProperties().put(prop.getId(), getFightProperty(prop) + value);
    }
    public void addSpecialEnergy(float energy){
       float curSpecialEnergy = getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
       float maxSpecialEnergy = getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY);
       curSpecialEnergy+=energy;
        if (curSpecialEnergy >= maxSpecialEnergy){
            curSpecialEnergy = maxSpecialEnergy;
        }
       setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, curSpecialEnergy);
       getPlayer().sendPacket(new PacketAvatarFightPropNotify(this));
    }

    public void clearSpecialEnergy(){
       float curSpecialEnergy = getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
       float maxSpecialEnergy = getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY);
       curSpecialEnergy = 0;
       setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, curSpecialEnergy);
       getPlayer().sendPacket(new PacketAvatarFightPropNotify(this));
    }

    public float getFightProperty(FightProperty prop) {
        return getFightProperties().getOrDefault(prop.getId(), 0f);
    }

    public Map<Integer, Integer>
            getSkillLevelMap() { // Returns a copy of the skill levels for the current skillDepot.
        var map = new Int2IntOpenHashMap();
        this.skillDepot
                .getSkillsAndEnergySkill()
                .forEach(
                        // putIfAbsent returns the old value, null for a skill the save has never
                        // seen, so every avatar saved before a depot gained a skill (the attack
                        // mode skill, for one) failed to load and its player could not log in.
                        skillId -> map.put(skillId, this.skillLevelMap.computeIfAbsent(skillId, id -> 1).intValue()));
        return map;
    }

    // Returns a copy of the skill bonus levels for the current skillDepot, capped to avoid invalid
    // levels.
    public Map<Integer, Integer> getProudSkillBonusMap() {
        var map = new Int2IntArrayMap();
        this.skillDepot
                .getSkillsAndEnergySkill()
                .forEach(
                        skillId -> {
                            val skillData = GameData.getAvatarSkillDataMap().get(skillId);
                            if (skillData == null) return;
                            int proudSkillGroupId = skillData.getProudSkillGroupId();
                            int bonus = this.proudSkillBonusMap.getOrDefault(proudSkillGroupId, 0);
                            int maxLevel = GameData.getProudSkillGroupMaxLevel(proudSkillGroupId);
                            int curLevel = this.skillLevelMap.getOrDefault(skillId, 0);
                            if (maxLevel > 0) {
                                bonus = Math.min(bonus, maxLevel - curLevel);
                            }
                            map.put(proudSkillGroupId, bonus);
                        });
        return map;
    }

    public IntSet getTalentIdList() { // Returns a copy of the unlocked constellations for the current
        if (this.getSkillDepot() != null) {
            // skillDepot.
            var talents = new IntOpenHashSet(this.getSkillDepot().getTalents());
            talents.removeIf(id -> !this.talentIdList.contains(id));
            return talents;
        } else return new IntOpenHashSet();
    }

    public int getCoreProudSkillLevel() {
        if (this.getSkillDepot() != null) {
            var lockedTalents = new IntOpenHashSet(this.getSkillDepot().getTalents());
            lockedTalents.removeAll(this.getTalentIdList());
            // One below the lowest locked talent, or 6 if there are no locked talents.
            return lockedTalents.intStream().map(i -> i % 10).min().orElse(7) - 1;
        } else return 0;
    }

    public List<Integer> getTpsWeaponIds() {
        if (this.tpsWeaponIds == null) {
            this.tpsWeaponIds = new ArrayList<>();
        }
        return this.tpsWeaponIds;
    }

    public boolean equipItem(GameItem item, boolean shouldRecalc) {
        // Sanity check equip type
        EquipType itemEquipType = item.getItemData().getEquipType();
        if (itemEquipType == EquipType.EQUIP_NONE) {
            return false;
        }

        // Check if other avatars have this item equipped
        Avatar otherAvatar = getPlayer().getAvatars().getAvatarById(item.getEquipCharacter());
        if (otherAvatar != null) {
            // Unequip other avatar's item
            if (otherAvatar.unequipItem(item.getItemData().getEquipType())) {
                getPlayer()
                        .sendPacket(
                                new PacketAvatarEquipChangeNotify(otherAvatar, item.getItemData().getEquipType()));
            }
            // Swap with other avatar
            if (getEquips().containsKey(itemEquipType.getValue())) {
                GameItem toSwap = this.getEquipBySlot(itemEquipType);
                otherAvatar.equipItem(toSwap, false);
            }
            // Recalc
            otherAvatar.recalcStats();
        } else if (getEquips().containsKey(itemEquipType.getValue())) {
            // Unequip item in current slot if it exists
            unequipItem(itemEquipType);
        }

        // Set equip
        this.getEquips().put(itemEquipType.getValue(), item);

        if (itemEquipType == EquipType.EQUIP_WEAPON && getPlayer().getWorld() != null) {
            if (!(item.getWeaponEntity() != null
                    && item.getWeaponEntity().getScene() == getPlayer().getScene())) {
                item.setWeaponEntity(
                        new EntityWeapon(this.getPlayer().getScene(), item.getItemData().getGadgetId()));
                this.getPlayer()
                        .getScene()
                        .getWeaponEntities()
                        .put(item.getWeaponEntity().getId(), item.getWeaponEntity());
            }
            // item.setWeaponEntityId(this.getPlayer().getWorld().getNextEntityId(EntityIdType.WEAPON));
        }

        item.setEquipCharacter(this.getAvatarId());
        item.save();

        if (this.getPlayer().hasSentLoginPackets()) {
            this.getPlayer().sendPacket(new PacketAvatarEquipChangeNotify(this, item));
        }

        if (shouldRecalc) {
            this.recalcStats();
        }

        return true;
    }

    public boolean unequipItem(EquipType slot) {
        GameItem item = getEquips().remove(slot.getValue());

        if (item != null) {
            item.setEquipCharacter(0);
            item.save();
            return true;
        }

        return false;
    }

    // --- AvatarFightPropNotify coalescing ---------------------------------
    // recalcStats() used to send one AvatarFightPropNotify per call. Combat calls it once per
    // modifier add/remove, so a single ClientAbilityChangeNotify fanned out into dozens of these
    // - measured at 550/s for one avatar, which starved the client's frame loop. Coalesced here:
    // the first send goes out immediately, further ones inside the window collapse into a single
    // trailing send so the client always ends on the final state.
    private static final long FIGHT_PROP_COALESCE_MS = 120L;
    private static final ConcurrentHashMap<Long, Long> FIGHT_PROP_LAST_SENT =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Avatar> FIGHT_PROP_PENDING =
            new ConcurrentHashMap<>();
    private static final ScheduledExecutorService FIGHT_PROP_FLUSH =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "avatar-fightprop-flush");
                        thread.setDaemon(true);
                        return thread;
                    });

    private void sendFightPropNotifyNow() {
        Player player = this.getPlayer();
        if (player == null || !player.hasSentLoginPackets()) {
            return;
        }
        player.sendPacket(new PacketAvatarFightPropNotify(this));
    }

    /**
     * Coalesced full-props send. Prefer this over building a {@link PacketAvatarFightPropNotify} by
     * hand: the first call in a window goes out immediately, later calls inside the same window
     * collapse into one trailing send that carries the final state.
     */
    public void queueFightPropNotify() {
        long guid = this.getGuid();
        long now = System.nanoTime();
        Long last = FIGHT_PROP_LAST_SENT.get(guid);
        if (last == null || now - last >= TimeUnit.MILLISECONDS.toNanos(FIGHT_PROP_COALESCE_MS)) {
            FIGHT_PROP_LAST_SENT.put(guid, now);
            this.sendFightPropNotifyNow();
            return;
        }
        if (FIGHT_PROP_PENDING.putIfAbsent(guid, this) == null) {
            FIGHT_PROP_FLUSH.schedule(
                    () -> {
                        Avatar pending = FIGHT_PROP_PENDING.remove(guid);
                        if (pending == null) {
                            return;
                        }
                        FIGHT_PROP_LAST_SENT.put(guid, System.nanoTime());
                        try {
                            pending.sendFightPropNotifyNow();
                        } catch (Throwable ignored) {
                            // The player may have gone offline between queue and flush.
                        }
                    },
                    FIGHT_PROP_COALESCE_MS,
                    TimeUnit.MILLISECONDS);
        }
    }

    /** Drops this player coalescing state so the static maps do not outlive the session. */
    public static void clearFightPropNotifyState(Player player) {
        if (player == null || player.getAvatars() == null) {
            return;
        }
        for (Avatar avatar : player.getAvatars().getAvatars().values()) {
            if (avatar == null) {
                continue;
            }
            long guid = avatar.getGuid();
            FIGHT_PROP_PENDING.remove(guid);
            FIGHT_PROP_LAST_SENT.remove(guid);
        }
    }

    public void recalcStats() {
        recalcStats(false);
    }

    public void recalcStats(boolean forceSendAbilityChange) {
        // Setup
        var data = this.getAvatarData();
        var promoteData =
                GameData.getAvatarPromoteData(data.getAvatarPromoteId(), this.getPromoteLevel());
        var setMap = new Int2IntOpenHashMap();

        // Extra ability embryos
        Set<String> prevExtraAbilityEmbryos = this.getExtraAbilityEmbryos();
        this.extraAbilityEmbryos = new HashSet<>();

        // Fetters
        this.setFetterList(data.getFetters());
        this.setNameCardRewardId(data.getNameCardRewardId());
        this.setNameCardId(data.getNameCardId());

        // Get hp percent, set to 100% if none
        float hpPercent =
                this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) <= 0
                        ? 1f
                        : this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                                / this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);

        // Store current energy value for later
        float currentEnergy =
                (this.getSkillDepot() != null)
                        ? this.getFightProperty(this.getSkillDepot().getElementType().getCurEnergyProp())
                        : 0f;

        float hpDebt = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float specialEnergy = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
        float nyxValue = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_NATLAN_HP);

        // Clear properties
        this.getFightProperties().clear();

        // Base stats
        this.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, data.getBaseHp(this.getLevel()));
        this.setFightProperty(
                FightProperty.FIGHT_PROP_BASE_ATTACK, data.getBaseAttack(this.getLevel()));
        this.setFightProperty(
                FightProperty.FIGHT_PROP_BASE_DEFENSE, data.getBaseDefense(this.getLevel()));
        this.setFightProperty(FightProperty.FIGHT_PROP_CRITICAL, data.getBaseCritical());
        this.setFightProperty(FightProperty.FIGHT_PROP_CRITICAL_HURT, data.getBaseCriticalHurt());
        this.setFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 1f);
        // Character-sheet base EM (AvatarExcelConfigData.elementMastery), e.g. Lauma 200 / Nefer 100.
        // Ascension EM (if any) is added below via promoteData.
        this.setFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, data.getElementMastery());
        this.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, specialEnergy);
        this.setFightProperty(FightProperty.FIGHT_PROP_CUR_NATLAN_HP, nyxValue);
        // The burst's own specialEnergyMin, not a flat 200: Mavuika's Fighting Spirit does want 200
        // but Skirk's wants 100, and a doubled bar never fills.
        float maxSpecialEnergy = 0f;
        if (this.getSkillDepot() != null && this.getSkillDepot().getEnergySkillData() != null) {
            maxSpecialEnergy = this.getSkillDepot().getEnergySkillData().getSpecialEnergyMin();
        }
        this.setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, maxSpecialEnergy);
        this.setFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY, maxSpecialEnergy / 2f);

        if (promoteData != null) {
            for (FightPropData fightPropData : promoteData.getAddProps()) {
                this.addFightProperty(fightPropData.getProp(), fightPropData.getValue());
            }
        }

        // Set energy usage
        setCurrentEnergy(currentEnergy);

        // Artifacts
        for (int slotId = 1; slotId <= 5; slotId++) {
            // Get artifact
            GameItem equip = this.getEquipBySlot(slotId);
            if (equip == null) {
                continue;
            }
            // Artifact main stat
            ReliquaryMainPropData mainPropData =
                    GameData.getReliquaryMainPropDataMap().get(equip.getMainPropId());
            if (mainPropData != null) {
                ReliquaryLevelData levelData =
                        GameData.getRelicLevelData(equip.getItemData().getRankLevel(), equip.getLevel());
                if (levelData != null) {
                    this.addFightProperty(
                            mainPropData.getFightProp(), levelData.getPropValue(mainPropData.getFightProp()));
                }
            }
            // Artifact sub stats
            for (int appendPropId : equip.getAppendPropIdList()) {
                ReliquaryAffixData affixData = GameData.getReliquaryAffixDataMap().get(appendPropId);
                if (affixData != null) {
                    this.addFightProperty(affixData.getFightProp(), affixData.getPropValue());
                }
            }
            // Set bonus
            if (equip.getItemData().getSetId() > 0) {
                setMap.addTo(equip.getItemData().getSetId(), 1);
            }
        }

        // Set stuff
        setMap.forEach(
                (setId, amount) -> {
                    ReliquarySetData setData = GameData.getReliquarySetDataMap().get((int) setId);
                    if (setData == null) return;

                    // Calculate how many items are from the set
                    // Add affix data from set bonus
                    val setNeedNum = setData.getSetNeedNum();
                    for (int setIndex = 0; setIndex < setNeedNum.length; setIndex++) {
                        if (amount < setNeedNum[setIndex]) break;

                        int affixId = (setData.getEquipAffixId() * 10) + setIndex;
                        EquipAffixData affix = GameData.getEquipAffixDataMap().get(affixId);
                        if (affix == null) {
                            continue;
                        }

                        // Add properties from this affix to our avatar
                        for (FightPropData prop : affix.getAddProps()) {
                            this.addFightProperty(prop.getProp(), prop.getValue());
                        }

                        // Add any skill strings from this affix
                        this.addToExtraAbilityEmbryos(affix.getOpenConfig(), true);
                    }
                });

        // Weapon
        GameItem weapon = this.getWeapon();
        if (weapon != null) {
            // Add stats
            WeaponCurveData curveData = GameData.getWeaponCurveDataMap().get(weapon.getLevel());
            if (curveData != null) {
                for (WeaponProperty weaponProperty : weapon.getItemData().getWeaponProperties()) {
                    this.addFightProperty(
                            weaponProperty.getPropType(),
                            weaponProperty.getInitValue() * curveData.getMultByProp(weaponProperty.getType()));
                }
            }
            // Weapon promotion stats
            WeaponPromoteData wepPromoteData =
                    GameData.getWeaponPromoteData(
                            weapon.getItemData().getWeaponPromoteId(), weapon.getPromoteLevel());
            if (wepPromoteData != null) {
                for (FightPropData prop : wepPromoteData.getAddProps()) {
                    if (prop.getValue() == 0f || prop.getProp() == null) {
                        continue;
                    }
                    this.addFightProperty(prop.getProp(), prop.getValue());
                }
            }
            // Add weapon skill from affixes
            if (weapon.getAffixes() != null && weapon.getAffixes().size() > 0) {
                // Weapons usually dont have more than one affix but just in case...
                for (int af : weapon.getAffixes()) {
                    if (af == 0) {
                        continue;
                    }
                    // Calculate affix id
                    int affixId = (af * 10) + weapon.getRefinement();
                    EquipAffixData affix = GameData.getEquipAffixDataMap().get(affixId);
                    if (affix == null) {
                        continue;
                    }

                    // Add properties from this affix to our avatar
                    for (FightPropData prop : affix.getAddProps()) {
                        this.addFightProperty(prop.getProp(), prop.getValue());
                    }

                    // Add any skill strings from this affix
                    this.addToExtraAbilityEmbryos(affix.getOpenConfig(), true);
                }
            }
        }

        // TPS weapons: their affix openConfigs carry the Avatar_TPS_* aim, shoot and reload abilities
        TpsWeaponSystem.applyAffixes(this);

        // Add proud skills and unlock them if needed
        var talentOwners = new HashSet<String>();
        AvatarSkillDepotData skillDepot =
                GameData.getAvatarSkillDepotDataMap().get(this.getSkillDepotId());
        this.getProudSkillList().clear();
        if (skillDepot != null) {
            for (InherentProudSkillOpens openData : skillDepot.getInherentProudSkillOpens()) {
                if (openData.getProudSkillGroupId() == 0) {
                    continue;
                }
                if (openData.getNeedAvatarPromoteLevel() <= this.getPromoteLevel()) {
                    int proudSkillId = (openData.getProudSkillGroupId() * 100) + 1;
                    if (GameData.getProudSkillDataMap().containsKey(proudSkillId)) {
                        this.getProudSkillList().add(proudSkillId);
                    }
                }
            }
            for (int groupId : skillDepot.getQuestProudSkillGroupIds()) {
                int proudSkillId = (groupId * 100) + 1;
                if (GameData.getProudSkillDataMap().containsKey(proudSkillId)) {
                    this.getProudSkillList().add(proudSkillId);
                }
            }
        }

        // Proud skills
        for (int proudSkillId : this.getProudSkillList()) {
            ProudSkillData proudSkillData = GameData.getProudSkillDataMap().get(proudSkillId);
            if (proudSkillData == null) {
                continue;
            }

            // Add properties from this proud skill to our avatar
            for (FightPropData prop : proudSkillData.getAddProps()) {
                this.addFightProperty(prop.getProp(), prop.getValue());
            }

            // Add any embryos from this proud skill
            this.addToExtraAbilityEmbryos(proudSkillData.getOpenConfig());
            talentOwners.add(ExtraTalents.owner(proudSkillData.getOpenConfig()));
        }

        // Skill openConfigs (e.g. PlayerGirl_Ice_SkillUpgrade_1 AddAbility for
        // StarSuperconducted_Attack01..05). Without these embryos, ice-infusion pattern
        // replacement has no attack abilities → visual infusion, zero hits.
        if (skillDepot != null) {
            var skillLevelMap = this.getSkillLevelMap();
            skillDepot
                    .getSkillsAndEnergySkill()
                    .forEach(
                            skillId -> {
                                var skillCfg = GameData.getAvatarSkillDataMap().get(skillId);
                                if (skillCfg == null || skillCfg.getProudSkillGroupId() == 0) {
                                    return;
                                }
                                int level = skillLevelMap.getOrDefault(skillId, 1);
                                ProudSkillData skillProud =
                                        GameData.getProudSkillDataMap()
                                                .get(skillCfg.getProudSkillGroupId() * 100 + level);
                                if (skillProud != null) {
                                    this.addToExtraAbilityEmbryos(skillProud.getOpenConfig());
                                    talentOwners.add(ExtraTalents.owner(skillProud.getOpenConfig()));
                                }
                            });
        }

        // Constellations
        this.getTalentIdList()
                .intStream()
                .mapToObj(GameData.getAvatarTalentDataMap()::get)
                .filter(Objects::nonNull)
                .map(AvatarTalentData::getOpenConfig)
                .filter(Objects::nonNull)
                .forEach(
                        openConfig -> {
                            this.addToExtraAbilityEmbryos(openConfig);
                            talentOwners.add(ExtraTalents.owner(openConfig));
                        });

        ExtraTalents.forOwners(talentOwners, this.getAvatarData().getName())
                .forEach(this::addToExtraAbilityEmbryos);
        // Add any skill strings from this constellation

        // Set % stats
        FightProperty.forEachCompoundProperty(
                c ->
                        this.setFightProperty(
                                c.getResult(),
                                this.getFightProperty(c.getFlat())
                                        + (this.getFightProperty(c.getBase())
                                                * (1f + this.getFightProperty(c.getPercent())))));

        // Reapply all overrides
        this.fightProperties.putAll(this.fightPropOverrides);

        // Set current hp
        // Clamped to the freshly computed max: a max-HP-ratio modifier (Actor_MaxHPRatio, e.g.
        // Columbina C2 / Yelan C4 / Furina overflow) can leave CUR_HP above MAX_HP once it expires,
        // and an unclamped value survives every recalc - hpPercent then stays above 1 and is fed
        // straight back in - and gets persisted. Such a character's HP bar reads full while the real
        // value sits above the cap, so damage appears to do nothing until the surplus is eaten.
        float restoredHp =
                this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * hpPercent;
        this.setFightProperty(
                FightProperty.FIGHT_PROP_CUR_HP,
                Math.min(restoredHp, this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP)));

        // Set HP Debts
        this.setFightProperty(
                FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                hpDebt);



        // Packet
        if (getPlayer() != null && getPlayer().hasSentLoginPackets()) {
            // Update stats for client (coalesced, see queueFightPropNotify)
            this.queueFightPropNotify();
            // Update client abilities
            EntityAvatar entity = this.getAsEntity();
            if (entity != null
                    && (!this.getExtraAbilityEmbryos().equals(prevExtraAbilityEmbryos)
                            || forceSendAbilityChange)) {
                getPlayer().sendPacket(new PacketAbilityChangeNotify(entity));
            }
        }

        runRecalcHook("Symphonist recalc hook", () -> SymphonistWeaponHelper.onAfterRecalc(this));
        runRecalcHook("MaxHPRatio afterRecalc", () -> AbilityMaxHpRatioHelper.afterRecalc(this));
        runRecalcHook("WaterResonance afterRecalc", () -> WaterResonanceHelper.afterRecalc(this));
        AvatarStatePersist.afterRecalc(this);
    }

    /**
     * Runs one post-recalc hook, and reports a failure once rather than once per recalc.
     *
     * <p>Stats are recalculated constantly - on every equip, level, constellation and talent - so a
     * hook that fails for one avatar fails for every recalc of it. One broken hook produced 1206 of
     * the 1363 lines in a single server log.
     */
    private static void runRecalcHook(String name, Runnable hook) {
        try {
            hook.run();
        } catch (Throwable t) {
            var line = name + ": " + t;
            if (REPORTED_RECALC_FAILURES.add(line)) {
                Grasscutter.getLogger().warn("{} (further identical failures are at debug)", line);
            } else {
                Grasscutter.getLogger().debug(line);
            }
        }
    }

    public void addToExtraAbilityEmbryos(String openConfig) {
        this.addToExtraAbilityEmbryos(openConfig, false);
    }

    public void addToExtraAbilityEmbryos(String openConfig, boolean forceAdd) {
        if (openConfig == null || openConfig.length() == 0) {
            return;
        }

        OpenConfigEntry entry = GameData.getOpenConfigEntries().get(openConfig);
        if (entry == null) {
            if (forceAdd) {
                // Add config string to ability skill list anyways
                this.getExtraAbilityEmbryos().add(openConfig);
            }
            return;
        }

        if (entry.getAddAbilities() != null) {
            for (String ability : entry.getAddAbilities()) {
                this.getExtraAbilityEmbryos().add(ability);
            }
        }
    }

    public void calcConstellation(OpenConfigEntry entry, boolean notifyClient) {
        if (entry == null) return;
        if (this.getPlayer() == null) notifyClient = false;

        // Check if new constellation adds +3 to a skill level
        if (this.calcConstellationExtraLevels(entry) && notifyClient) {
            // Packet
            this.getPlayer()
                    .sendPacket(new PacketProudSkillExtraLevelNotify(this, entry.getExtraTalentIndex()));
        }
        // Check if new constellation adds skill charges
        if (this.calcConstellationExtraCharges(entry) && notifyClient) {
            // Packet
            Stream.of(entry.getSkillPointModifiers())
                    .mapToInt(SkillPointModifier::getSkillId)
                    .forEach(
                            skillId -> {
                                this.getPlayer()
                                        .sendPacket(
                                                new PacketAvatarSkillMaxChargeCountNotify(
                                                        this, skillId, this.getSkillExtraChargeMap().getOrDefault(skillId, 0)));
                            });
        }
    }

    public void recalcConstellations() {
        // Team talent / temporary ExtraLevel bonuses store negative-key contributions in this map.
        // Clearing without restore leaves Skirk/Tartaglia/Lohen bonuses missing until refresh.
        java.util.HashMap<Integer, Integer> teamBonusPreserve = new java.util.HashMap<>();
        for (var entry : this.proudSkillBonusMap.entrySet()) {
            if (entry.getKey() != null && entry.getKey() < 0 && entry.getValue() != null && entry.getValue() > 0) {
                teamBonusPreserve.put(entry.getKey(), entry.getValue());
            }
        }

        // Clear first
        this.proudSkillBonusMap.clear();
        this.skillExtraChargeMap.clear();

        // Sanity checks
        if (this.avatarData == null || this.skillDepot == null) {
            return;
        }

        this.getTalentIdList()
                .intStream()
                .mapToObj(GameData.getAvatarTalentDataMap()::get)
                .filter(Objects::nonNull)
                .map(AvatarTalentData::getOpenConfig)
                .filter(Objects::nonNull)
                .filter(openConfig -> openConfig.length() > 0)
                .map(GameData.getOpenConfigEntries()::get)
                .filter(Objects::nonNull)
                .forEach(e -> this.calcConstellation(e, false));

        for (var entry : teamBonusPreserve.entrySet()) {
            int groupId = -entry.getKey();
            int contrib = entry.getValue();
            this.proudSkillBonusMap.put(entry.getKey(), contrib);
            this.proudSkillBonusMap.merge(groupId, contrib, Integer::sum);
        }

        try {
            if (this.getPlayer() != null) {
                SkirkTeamBonusHelper.onConstellationRecalc(this.getPlayer());
            }
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("Avatar recalcConstellations Skirk bonus: {}", throwable.toString());
        }
        try {
            if (this.getPlayer() != null) {
                TartagliaTeamBonusHelper.onConstellationRecalc(this.getPlayer());
            }
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("Avatar recalcConstellations Tartaglia bonus: {}", throwable.toString());
        }
        try {
            emu.grasscutter.game.ability.LohenExtraArtSkillLevelHelper.onConstellationRecalc(this);
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("Avatar recalcConstellations Lohen bonus: {}", throwable.toString());
        }
    }

    private boolean calcConstellationExtraCharges(OpenConfigEntry entry) {
        var skillPointModifiers = entry.getSkillPointModifiers();
        if (skillPointModifiers == null) return false;

        for (var mod : skillPointModifiers) {
            AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(mod.getSkillId());

            if (skillData == null) continue;

            int charges = skillData.getMaxChargeNum() + mod.getDelta();

            this.getSkillExtraChargeMap().put(mod.getSkillId(), charges);
        }
        return true;
    }

    private boolean calcConstellationExtraLevels(OpenConfigEntry entry) {
        int skillId =
                switch (entry.getExtraTalentIndex()) {
                    case 9 -> this.skillDepot.getEnergySkill(); // Ult skill
                    case 2 -> (this.skillDepot.getSkills().size() >= 2)
                            ? this.skillDepot.getSkills().get(1)
                            : 0; // E skill
                    case 1 -> (this.skillDepot.getSkills().size() >= 1)
                            ? this.skillDepot.getSkills().get(0)
                            : 0; // Normal Attack (Liney)
                    default -> 0;
                };
        // Sanity check
        if (skillId == 0) {
            return false;
        }

        // Get proud skill group id
        AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(skillId);

        if (skillData == null) {
            return false;
        }

        // Add to bonus list
        this.addProudSkillLevelBonus(skillData.getProudSkillGroupId(), 3);
        return true;
    }

    private int addProudSkillLevelBonus(int proudSkillGroupId, int bonus) {
        return this.proudSkillBonusMap.compute(
                proudSkillGroupId, (k, v) -> (v == null) ? bonus : v + bonus);
    }

    public boolean upgradeSkill(int skillId) {
        AvatarSkillData skillData = GameData.getAvatarSkillDataMap().get(skillId);
        if (skillData == null) return false;

        // Get data for next skill level
        int newLevel = this.skillLevelMap.getOrDefault(skillId, 0) + 1;
        if (newLevel > 10) return false;

        // Proud skill data
        int proudSkillId = (skillData.getProudSkillGroupId() * 100) + newLevel;
        ProudSkillData proudSkill = GameData.getProudSkillDataMap().get(proudSkillId);
        if (proudSkill == null) return false;

        // Make sure break level is correct
        if (this.getPromoteLevel() < proudSkill.getBreakLevel()) return false;

        // Pay materials and mora if possible
        if (!this.getPlayer().getInventory().payItems(proudSkill.getTotalCostItems())) return false;

        // Upgrade skill
        this.setSkillLevel(skillId, newLevel);
        return true;
    }

    public boolean setSkillLevel(int skillId, int level) {
        if (level < 0 || level > 15) return false;
        var validLevels = GameData.getAvatarSkillLevels(skillId);
        if (validLevels != null && !validLevels.contains(level)) return false;
        int oldLevel =
                this.skillLevelMap.getOrDefault(
                        skillId, 0); // just taking the return value of put would have null concerns
        this.skillLevelMap.put(skillId, level);
        this.save();

        // Packet
        val player = this.getPlayer();
        if (player != null) {
            player.sendPacket(new PacketAvatarSkillChangeNotify(this, skillId, oldLevel, level));
            player.sendPacket(new PacketAvatarSkillUpgradeRsp(this, skillId, oldLevel, level));
        }
        return true;
    }

    public boolean unlockConstellation() {
        return this.unlockConstellation(false);
    }

    public boolean unlockConstellation(boolean skipPayment) {
        int currentTalentLevel = this.getCoreProudSkillLevel();
        if (currentTalentLevel < 0) return false;
        int talentId = this.skillDepot.getTalents().get(currentTalentLevel);
        return this.unlockConstellation(talentId, skipPayment);
    }

    public boolean unlockConstellation(int talentId) {
        return unlockConstellation(talentId, false);
    }

    public boolean unlockConstellation(int talentId, boolean skipPayment) {
        return unlockConstellation(talentId, skipPayment, true);
    }

    private boolean unlockConstellation(int talentId, boolean skipPayment, boolean finalizeChange) {
        // Get talent
        AvatarTalentData talentData = GameData.getAvatarTalentDataMap().get(talentId);
        if (talentData == null) return false;
        var player = this.getPlayer();

        // Pay constellation item if possible
        if (!skipPayment
                && (player != null)
                && !player.getInventory().payItem(talentData.getMainCostItemId(), 1)) {
            return false;
        }

        // Apply + recalc
        this.talentIdList.add(talentData.getId());

        // Packet
        if (player != null) {
            player.sendPacket(new PacketAvatarUnlockTalentNotify(this, talentId));
            player.sendPacket(new PacketUnlockAvatarTalentRsp(this, talentId));
        }

        // Proud skill bonus map (Extra skills)
        this.calcConstellation(GameData.getOpenConfigEntries().get(talentData.getOpenConfig()), true);

        // Ordinary unlocks commit immediately; GM bulk changes finalize once.
        if (finalizeChange) {
            this.recalcStats(true);
            this.save();
        }
        return true;
    }

    public void forceConstellationLevel(int level) {
        if (level > 6) return; // Sanity check

        if (level < 0) { // Reset also removes constellations from inactive depots.
            this.talentIdList.clear();
        } else {
            this.talentIdList.removeAll(this.getTalentIdList());
            for (int i = 0; i < level; i++) {
                int nextLevel = this.getCoreProudSkillLevel();
                if (this.skillDepot == null || nextLevel < 0
                        || nextLevel >= this.skillDepot.getTalents().size()
                        || !this.unlockConstellation(
                                this.skillDepot.getTalents().get(nextLevel), true, false)) break;
            }
        }
        this.recalcConstellations();
        this.recalcStats(true);
        this.save();
    }

    public boolean sendSkillExtraChargeMap() {
        val map = this.getSkillExtraChargeMap();
        if (map.isEmpty()) return false;
        this.getPlayer()
                .sendPacket(
                        new PacketAvatarSkillInfoNotify(
                                this.guid,
                                new Int2IntArrayMap(
                                        map))); // TODO: Remove this allocation when updating interfaces to FastUtils
        // later
        return true;
    }

    /** Post-recalc hook failures already reported, so each distinct one is named once. */
    private static final Set<String> REPORTED_RECALC_FAILURES = ConcurrentHashMap.newKeySet();

    public EntityAvatar getAsEntity() {
        // An avatar that is not attached to a player has no entity in any world. /give builds one
        // before handing it over, and every recalc on it used to throw here and be caught and
        // logged one layer up - 1206 warnings from a single /give all.
        var owner = getPlayer();
        if (owner == null || owner.getTeamManager() == null) return null;

        for (EntityAvatar entity : owner.getTeamManager().getActiveTeam()) {
            if (entity.getAvatar() == this) {
                return entity;
            }
        }
        return null;
    }

    public int getEntityId() {
        EntityAvatar entity = getAsEntity();
        return entity != null ? entity.getId() : 0;
    }

    public void save() {
        DatabaseHelper.saveAvatar(this);
    }

    /**
     * Fetter levels whose reward was claimed. The name card counts once the player owns it, so a
     * card granted by command or claimed before this was tracked clears the red dot too.
     */
    public Set<Integer> getRewardedFetterLevels() {
        var levels = new TreeSet<Integer>();
        if (this.rewardedFetterLevels != null) levels.addAll(this.rewardedFetterLevels);
        if (this.nameCardId != 0 && this.getPlayer().getNameCardList().contains(this.nameCardId)) {
            levels.add(10);
        }
        return levels;
    }

    public void addRewardedFetterLevel(int fetterLevel) {
        if (this.rewardedFetterLevels == null) this.rewardedFetterLevels = new HashSet<>();
        this.rewardedFetterLevels.add(fetterLevel);
    }

    public AvatarInfo toProto() {
        int fetterLevel = this.getFetterLevel();
        AvatarFetterInfo.Builder avatarFetter = AvatarFetterInfo.newBuilder().setExpLevel(fetterLevel);

        if (fetterLevel != 10) {
            avatarFetter.setExpNumber(this.getFetterExp());
        }

        if (this.fetters != null) {
            this.fetters.forEach(
                    fetterId ->
                            avatarFetter.addFetterList(
                                    FetterData.newBuilder()
                                            .setFetterId(fetterId)
                                            .setFetterState(FetterState.FINISH.getValue())));
        }

        avatarFetter.addAllRewardedFetterLevelList(this.getRewardedFetterLevels());

        AvatarInfo.Builder avatarInfo =
                AvatarInfo.newBuilder()
                        .setAvatarId(this.getAvatarId())
                        .setGuid(this.getGuid())
                        .setLifeState(1)
                        .addAllTalentIdList(this.getTalentIdList())
                        .putAllFightPropMap(this.getFightProperties())
                        .setSkillDepotId(this.getSkillDepotId())
                        .setCoreProudSkillLevel(this.getCoreProudSkillLevel())
                        .putAllSkillLevelMap(this.getSkillLevelMap())
                        .addAllInherentProudSkillList(this.getProudSkillList())
                        .putAllProudSkillExtraLevelMap(this.getProudSkillBonusMap())
                        .setAvatarType(this.getAvatarType())
                        .setBornTime(this.getBornTime())
                        .setFetterInfo(avatarFetter)
                        .setWearingFlycloakId(this.getFlyCloak())
                        .setCostumeId(this.getCostume())
                        .setTraceEffectId(this.getTraceEffect())
                        .setWeaponSkinId(this.getWeaponSkinId());

        this.getSkillExtraChargeMap()
                .forEach(
                        (skillId, count) ->
                                avatarInfo.putSkillMap(
                                        skillId, AvatarSkillInfo.newBuilder().setMaxChargeCount(count).build()));

        this.getEquips().forEach((k, item) -> avatarInfo.addEquipGuidList(item.getGuid()));
        avatarInfo.addAllTpsWeaponList(TpsWeaponSystem.getSceneWeaponInfos(this));

        avatarInfo.putPropMap(
                PlayerProperty.PROP_LEVEL.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, this.getLevel()));
        avatarInfo.putPropMap(
                PlayerProperty.PROP_EXP.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_EXP, this.getExp()));
        avatarInfo.putPropMap(
                PlayerProperty.PROP_BREAK_LEVEL.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_BREAK_LEVEL, this.getPromoteLevel()));
        avatarInfo.putPropMap(
                PlayerProperty.PROP_SATIATION_VAL.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_SATIATION_VAL, this.getSatiation()));
        avatarInfo.putPropMap(
                PlayerProperty.PROP_SATIATION_PENALTY_TIME.getId(),
                ProtoHelper.newPropValue(
                        PlayerProperty.PROP_SATIATION_PENALTY_TIME, this.getSatiationPenalty()));

        var expeditionInfo = this.getPlayer().getExpeditionInfo(this.getGuid());
        if (expeditionInfo != null) {
            avatarInfo.setExpeditionStateValue(expeditionInfo.getState());
        }

        // 7.x extra level (90->95->100): send the effective max level / extra tier so the
        // character screen shows the breakthrough; without this the client never enables it.
        emu.grasscutter.game.avatar.AvatarExtraLevelHelper.applyAvatarInfoExtraLevel(avatarInfo, this);

        return avatarInfo.build();
    }


    // used only in character showcase
    public ShowAvatarInfo toShowAvatarInfoProto() {
        AvatarFetterInfo.Builder avatarFetter =
                AvatarFetterInfo.newBuilder().setExpLevel(this.getFetterLevel());

        ShowAvatarInfo.Builder showAvatarInfo =
                ShowAvatarInfoOuterClass.ShowAvatarInfo.newBuilder()
                        .setAvatarId(avatarId)
                        .addAllTalentIdList(this.getTalentIdList())
                        .putAllFightPropMap(this.getFightProperties())
                        .setSkillDepotId(this.getSkillDepotId())
                        .setCoreProudSkillLevel(this.getCoreProudSkillLevel())
                        .addAllInherentProudSkillList(this.getProudSkillList())
                        .putAllSkillLevelMap(this.getSkillLevelMap())
                        .putAllProudSkillExtraLevelMap(this.getProudSkillBonusMap())
                        .setFetterInfo(avatarFetter)
                        .setCostumeId(this.getCostume())
                        .setWeaponSkinId(this.getWeaponSkinId());

        showAvatarInfo.putPropMap(
                PlayerProperty.PROP_LEVEL.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, this.getLevel()));
        showAvatarInfo.putPropMap(
                PlayerProperty.PROP_EXP.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_EXP, this.getExp()));
        showAvatarInfo.putPropMap(
                PlayerProperty.PROP_BREAK_LEVEL.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_BREAK_LEVEL, this.getPromoteLevel()));
        showAvatarInfo.putPropMap(
                PlayerProperty.PROP_SATIATION_VAL.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_SATIATION_VAL, this.getSatiation()));
        showAvatarInfo.putPropMap(
                PlayerProperty.PROP_SATIATION_PENALTY_TIME.getId(),
                ProtoHelper.newPropValue(
                        PlayerProperty.PROP_SATIATION_PENALTY_TIME, this.getSatiationPenalty()));
        int maxStamina = this.getPlayer().getProperty(PlayerProperty.PROP_MAX_STAMINA);
        showAvatarInfo.putPropMap(
                PlayerProperty.PROP_MAX_STAMINA.getId(),
                ProtoHelper.newPropValue(PlayerProperty.PROP_MAX_STAMINA, maxStamina));

        for (GameItem item : this.getEquips().values()) {
            if (item.getItemType() == ItemType.ITEM_RELIQUARY) {
                showAvatarInfo.addEquipList(
                        ShowEquip.newBuilder()
                                .setItemId(item.getItemId())
                                .setReliquary(item.toReliquaryProto()));
            } else if (item.getItemType() == ItemType.ITEM_WEAPON) {
                showAvatarInfo.addEquipList(
                        ShowEquip.newBuilder().setItemId(item.getItemId()).setWeapon(item.toWeaponProto()));
            }
        }

        return showAvatarInfo.build();
    }

    /**
     * Converts this avatar into a trial avatar.
     *
     * @param level The avatar's level.
     * @param avatarId The ID of the avatar.
     * @param grantReason The reason for granting the avatar.
     * @param questId The ID of the quest that granted the avatar.
     */
    public void setTrialAvatarInfo(
        int level, int avatarId, GrantReasonOuterClass.GrantReason grantReason, int questId) {
        this.setLevel(level);
        this.setPromoteLevel(getMinPromoteLevel(level));
        this.setTrialAvatarId(avatarId);
        this.setGrantReason(grantReason.getNumber());
        this.setFromParentQuestId(questId);
        this.setAvatarType(Type.TRIAL.getNumber());
        this.applyTrialSkillLevels();
        this.applyTrialItems();
    }

    /**
     * Gets the gear template based on the avatar's level.
     *
     * @return The avatar's template.
     */
    private int getTrialTemplate() {
        return this.getLevel() <= 9
                ? 1
                : (int)
                        (Math.floor(this.getLevel() / 10f) * 10); // round trial level to fit template levels
    }

    /**
     * @return The level to be used for the avatar's skills (talents).
     */
    public int getTrialSkillLevel() {
        // Use default data if custom data not available.
        if (GameData.getTrialAvatarCustomData().get(this.getTrialAvatarId()) == null) {
            var template = getTrialTemplate(); // round trial level to fit template levels

            var templateData = GameData.getTrialAvatarTemplateDataMap().get(template);
            return templateData == null ? 1 : templateData.getTrialAvatarSkillLevel();
        }

        // Use custom data.
        var trialData = GameData.getTrialAvatarCustomData().get(this.getTrialAvatarId());
        if (trialData == null) return 1;

        return trialData.getCoreProudSkillLevel(); // enhanced version of weapon
    }

    /** Applies the correct skill level for the trial avatar. */
    public void applyTrialSkillLevels() {
        this.getSkillLevelMap()
                .keySet()
                .forEach(skill -> this.setSkillLevel(skill, this.getTrialSkillLevel()));
    }

    /**
     * @return The weapon to use with the avatar.
     */
    public int getTrialWeaponId() {
        // Use default data if custom data not available.
        if (GameData.getTrialAvatarCustomData().get(this.getTrialAvatarId()) == null) {
            if (GameData.getTrialAvatarDataMap().get(this.getTrialAvatarId()) == null)
                return this.getAvatarData().getInitialWeapon();

            return GameData.getItemDataMap().get(this.getAvatarData().getInitialWeapon() + 100) == null
                    ? getAvatarData().getInitialWeapon()
                    : getAvatarData().getInitialWeapon() + 100; // enhanced version of weapon
        }

        // Use custom data.
        var trialData = GameData.getTrialAvatarCustomData().get(this.getTrialAvatarId());
        if (trialData == null) return 0;

        var trialCustomParams = trialData.getTrialAvatarParamList();
        return trialCustomParams.size() < 2
                ? getAvatarData().getInitialWeapon()
                : Integer.parseInt(trialCustomParams.get(1).split(";")[0]);
    }

    /**
     * @return A list of artifact IDs to use with the avatar.
     */
    public List<Integer> getTrialReliquary() {
        // Use default data if custom data not available.
        if (GameData.getTrialAvatarCustomData().get(this.getTrialAvatarId()) == null) {
            int trialAvatarTemplateLevel = getTrialTemplate();

            TrialAvatarTemplateData templateData =
                    GameData.getTrialAvatarTemplateDataMap().get(trialAvatarTemplateLevel);
            return templateData == null ? List.of() : templateData.getTrialReliquaryList();
        }

        // Use custom data.
        var trialData = GameData.getTrialAvatarCustomData().get(this.getTrialAvatarId());
        if (trialData == null) return List.of();

        var trialCustomParams =
                GameData.getTrialAvatarCustomData().get(getTrialAvatarId()).getTrialAvatarParamList();
        return trialCustomParams.size() < 3
                ? List.of()
                : Stream.of(trialCustomParams.get(2).split(";")).map(Integer::parseInt).toList();
    }

    /** Applies the correct items for the trial avatar. */
    public void applyTrialItems() {
        // Use an enhanced version of the weapon if available.
        var weapon = new GameItem(this.getTrialWeaponId());
        weapon.setLevel(this.getLevel());
        weapon.setExp(0);
        weapon.setPromoteLevel(getMinPromoteLevel(this.getLevel()));
        this.getEquips().put(weapon.getEquipSlot(), weapon);

        // Add artifacts for the trial avatar.
        this.getTrialReliquary()
                .forEach(
                        id -> {
                            var reliquaryData = GameData.getTrialReliquaryDataMap().get((int) id);
                            if (reliquaryData == null) return;

                            var relic = new GameItem(reliquaryData.getReliquaryId());
                            relic.setLevel(reliquaryData.getLevel());
                            relic.setMainPropId(reliquaryData.getMainPropId());
                            relic.getAppendPropIdList().addAll(reliquaryData.getAppendPropList());
                            this.getEquips().put(relic.getEquipSlot(), relic);
                        });

        // Add costume if avatar has a costume.
        if (GAME_OPTIONS.trialCostumes) {
            GameData.getAvatarCostumeDataItemIdMap()
                    .values()
                    .forEach(
                            costumeData -> {
                                if (costumeData.getCharacterId() != this.getAvatarId()) return;
                                this.setCostume(costumeData.getId());
                            });
        }
    }

    /** Equips the items applied from {@link Avatar#applyTrialItems()}. */
    public void equipTrialItems() {
        var player = this.getPlayer();

        this.getEquips()
                .values()
                .forEach(
                        item -> {
                            item.setEquipCharacter(this.getAvatarId());
                            item.setOwner(player);
                            if (item.getItemData().getEquipType() == EquipType.EQUIP_WEAPON) {
                                if (!(item.getWeaponEntity() != null
                                        && item.getWeaponEntity().getScene() == player.getScene())) {
                                    item.setWeaponEntity(
                                            new EntityWeapon(player.getScene(), item.getItemData().getGadgetId()));
                                    player
                                            .getScene()
                                            .getWeaponEntities()
                                            .put(item.getWeaponEntity().getId(), item.getWeaponEntity());
                                }

                                player.sendPacket(new PacketAvatarEquipChangeNotify(this, item));
                            }
                        });
    }

    /**
     * Converts this (trial) avatar into a trial info protocol buffer.
     *
     * @return The trial info protocol buffer.
     */
    public TrialAvatarInfo toTrialInfo() {
        var trialAvatar =
                TrialAvatarInfo.newBuilder()
                        .setTrialAvatarId(this.getTrialAvatarId())
                        .setGrantRecord(
                                TrialAvatarGrantRecord.newBuilder()
                                        .setGrantReason(this.getGrantReason())
                                        .setFromParentQuestId(this.getFromParentQuestId()));

        // Check if the avatar is a trial avatar.
        if (this.getTrialAvatarId() > 0) {
            // Add the artifacts and weapons for the avatar.
            trialAvatar.addAllTrialEquipList(
                    this.getEquips().values().stream().map(GameItem::toProto).toList());
        }

        return trialAvatar.build();
    }

    @PostLoad
    private void onLoad() {
        // 存档里的 currentHp 是权威值。构造期 recalc 已经把 CUR_HP 写成过
        // “属性表还没加载完时算出的临时上限”（例如 5311），如果直接 stash 这个临时值，
        // 后续 recalc 会把临时值当成“玩家当前血”恢复回去，存档血就永远丢了。
        // 这里先把存档血写回战斗属性，再 stash，保证恢复链拿到的是存档血。
        float miaoSavedHp = this.currentHp;
        if (miaoSavedHp > 1.0f) {
            this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, miaoSavedHp);
        }
        AvatarStatePersist.stash(this, miaoSavedHp, this.currentEnergy, this.nyxValue);
    }

    @PrePersist
    private void prePersist() {
        float miaoCurProp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float miaoMax = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (Math.abs(miaoCurProp - this.currentHp) > 1.0f) {
        }
        // Never persist an above-max HP: a max-HP-ratio modifier (Actor_MaxHPRatio) raises the cap
        // while it is active, and persisting that inflated value makes it permanent once the
        // modifier is gone. MAX_HP is fully computed by the time we save, so clamping here is safe.
        this.currentHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float persistedMaxHp = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (persistedMaxHp > 0f && this.currentHp > persistedMaxHp) {
            this.currentHp = persistedMaxHp;
        }
        try {
            AvatarSkillDepotData depot = this.getSkillDepot();
            if (depot != null && depot.getElementType() != null) {
                this.currentEnergy = this.getFightProperty(depot.getElementType().getCurEnergyProp());
            }
        } catch (Throwable ignored) {
            // Keep persistence of HP even when a partially loaded depot is present.
        }
        this.nyxValue = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_NATLAN_HP);
    }

    @AllArgsConstructor
    @Getter
    enum Type {
        NORMAL(1),
        TRIAL(2);

        final int number;
    }
}
