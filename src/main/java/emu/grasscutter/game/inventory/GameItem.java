package emu.grasscutter.game.inventory;

import dev.morphia.annotations.*;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.EquipOuterClass.Equip;
import emu.grasscutter.net.proto.FurnitureOuterClass.Furniture;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.ItemOuterClass.Item;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.MaterialOuterClass.Material;
import emu.grasscutter.net.proto.ReliquaryOuterClass.Reliquary;
import emu.grasscutter.net.proto.SceneReliquaryInfoOuterClass.SceneReliquaryInfo;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.WeaponOuterClass.Weapon;
import emu.grasscutter.utils.objects.WeightedList;

import java.util.*;
import lombok.*;
import org.bson.types.ObjectId;

@Entity(value = "items", useDiscriminator = false)
public class GameItem {
    @Id private ObjectId id;
    @Indexed private int ownerId;
    @Getter @Setter private int itemId;
    @Getter @Setter private int count;

    @Transient @Getter private long guid; // Player unique id
    @Transient @Getter @Setter private ItemData itemData;

    // Equips
    @Getter @Setter private int level;
    @Getter @Setter private int exp;
    @Getter @Setter private int totalExp;
    @Getter @Setter private int promoteLevel;
    @Getter @Setter private boolean favourite;
    @Getter @Setter private boolean locked;

    // Weapon
    @Getter private List<Integer> affixes;
    @Getter @Setter private int refinement = 0;

    // Relic
    @Getter @Setter private int mainPropId;
    @Getter private List<Integer> appendPropIdList;
    /** Purple define-mark / star on relic card (Reliquary._is_relic_starred). */
    @Getter @Setter private boolean relicStarred;
    /**
     * Affix ids chosen when defined at the Artifact Transmuter (Reliquary._purchased_append_prop_id_list).
     * Client uses this for the define icon / tooltip.
     */
    @Getter @Setter private List<Integer> purchasedAppendPropIdList;
    /**
     * Affix ids that receive shared upgrade guarantees (Reliquary._definite_append_prop_id_list).
     * Official: at least 2 upgrade hits on these lines by +20, split across them.
     */
    @Getter @Setter private List<Integer> definiteAppendPropIdList;

    @Getter @Setter private int equipCharacter;

    // TPS weapon: unlocked TpsWeaponAccessoryExcelConfigData ids.
    @Setter private List<Integer> tpsAccessoryIds;
    @Transient @Getter @Setter private EntityWeapon weaponEntity;
    @Transient @Getter private boolean newItem = false;

    public GameItem() {
        // Morphia only
    }

    /**
     * Assign an item ID before queueing its first asynchronous write. Reuse it for all writes,
     * so an early delete cannot race with an insert that Morphia gave a different ID.
     */
    public synchronized ObjectId ensurePersistenceId() {
        if (id == null) id = new ObjectId();
        return id;
    }

    public GameItem(int itemId) {
        this(GameData.getItemDataMap().get(itemId));
    }

    public GameItem(int itemId, int count) {
        this(GameData.getItemDataMap().get(itemId), count);
    }

    public GameItem(ItemParamData itemParamData) {
        this(itemParamData.getId(), itemParamData.getCount());
    }

    public GameItem(ItemData data) {
        this(data, 1);
    }

    public GameItem(ItemData data, int count) {
        this.itemId = data.getId();
        this.itemData = data;

        switch (data.getItemType()) {
            case ITEM_VIRTUAL:
                this.count = count;
                break;
            case ITEM_WEAPON:
                this.count = 1;
                this.level = Math.max(this.count, 1); // ??????????????????
                this.affixes = new ArrayList<>(2);
                if (data.getSkillAffix() != null) {
                    for (int skillAffix : data.getSkillAffix()) {
                        if (skillAffix > 0) {
                            this.affixes.add(skillAffix);
                        }
                    }
                }
                break;
            case ITEM_TPS_WEAPON:
                this.count = 1;
                this.level = 1;
                this.tpsAccessoryIds = new ArrayList<>();
                break;
            case ITEM_RELIQUARY:
                this.count = 1;
                this.level = 1;
                this.appendPropIdList = new ArrayList<>();
                // Create main property
                ReliquaryMainPropData mainPropData =
                        GameDepot.getRandomRelicMainProp(data.getMainPropDepotId());
                if (mainPropData != null) {
                    this.mainPropId = mainPropData.getId();
                }
                // Create extra stats
                this.addAppendProps(data.getAppendPropNum());
                break;
            default:
                // Preserve input quantity; inventory admission checks the stack limit.
                this.count = count;
        }
    }

    /**
     * Create an unowned, unsaved copy for a different inventory recipient. Share immutable
     * item definition data, but never share identity, owner or mutable affix collections.
     * This also preserves a rolled artifact's stats rather than rolling them again.
     */
    public GameItem copyForGrant() {
        GameItem copy = new GameItem();
        copy.itemId = this.itemId;
        copy.itemData = this.itemData;
        copy.count = this.count;
        copy.level = this.level;
        copy.exp = this.exp;
        copy.totalExp = this.totalExp;
        copy.promoteLevel = this.promoteLevel;
        copy.favourite = this.favourite;
        copy.locked = this.locked;
        copy.affixes = this.affixes == null ? null : new ArrayList<>(this.affixes);
        copy.refinement = this.refinement;
        copy.mainPropId = this.mainPropId;
        copy.appendPropIdList =
                this.appendPropIdList == null ? null : new ArrayList<>(this.appendPropIdList);
        copy.relicStarred = this.relicStarred;
        copy.purchasedAppendPropIdList =
                this.purchasedAppendPropIdList == null
                        ? null : new ArrayList<>(this.purchasedAppendPropIdList);
        copy.definiteAppendPropIdList =
                this.definiteAppendPropIdList == null
                        ? null : new ArrayList<>(this.definiteAppendPropIdList);
        // A new recipient must never inherit another owner's equipped-avatar linkage.
        copy.equipCharacter = 0;
        copy.tpsAccessoryIds =
                this.tpsAccessoryIds == null ? null : new ArrayList<>(this.tpsAccessoryIds);
        return copy;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwner(Player player) {
        this.ownerId = player.getUid();
        this.guid = player.getNextGameGuid();
    }

    public void checkIsNew(Inventory inventory) {
        // display notification when player obtain new item
        if (inventory.getItemById(this.itemId) == null) {
            this.newItem = true;
        }
    }

    public List<Integer> getTpsAccessoryIds() {
        if (this.tpsAccessoryIds == null) {
            this.tpsAccessoryIds = new ArrayList<>();
        }
        return this.tpsAccessoryIds;
    }

    public ObjectId getObjectId() {
        return id;
    }

    public ItemType getItemType() {
        return this.itemData.getItemType();
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

    public int getEquipSlot() {
        return this.getItemData().getEquipType().getValue();
    }

    public boolean isEquipped() {
        return this.getEquipCharacter() > 0;
    }

    public boolean isDestroyable() {
        return !this.isLocked() && !this.isEquipped();
    }

    public void addAppendProp() {
        this.addAppendProp(ArtifactRollBias.NONE);
    }

    public void addAppendProp(ArtifactRollBias bias) {
        if (this.appendPropIdList == null) {
            this.appendPropIdList = new ArrayList<>();
        }

        if (this.appendPropIdList.size() < 4) {
            this.addNewAppendProp(bias);
        } else {
            this.upgradeRandomAppendProp(bias);
        }
    }

    public void addAppendProps(int quantity) {
        this.addAppendProps(quantity, ArtifactRollBias.NONE);
    }

    public void addAppendProps(int quantity, ArtifactRollBias bias) {
        int num = Math.max(quantity, 0);
        for (int i = 0; i < num; i++) {
            this.addAppendProp(bias);
        }
    }

    private Set<FightProperty> getAppendFightProperties() {
        Set<FightProperty> props = new HashSet<>();
        // Previously this would check no more than the first four affixes, however custom artifacts may
        // not respect this order.
        for (int appendPropId : this.appendPropIdList) {
            ReliquaryAffixData affixData = GameData.getReliquaryAffixDataMap().get(appendPropId);
            if (affixData != null) {
                props.add(affixData.getFightProp());
            }
        }
        return props;
    }

    private void addNewAppendProp(ArtifactRollBias bias) {
        List<ReliquaryAffixData> affixList =
                GameDepot.getRelicAffixList(this.itemData.getAppendPropDepotId());

        if (affixList == null) {
            return;
        }

        // Build blacklist - Dont add same stat as main/sub stat
        Set<FightProperty> blacklist = this.getAppendFightProperties();
        ReliquaryMainPropData mainPropData =
                GameData.getReliquaryMainPropDataMap().get(this.mainPropId);
        if (mainPropData != null) {
            blacklist.add(mainPropData.getFightProp());
        }

        // Build random list
        WeightedList<ReliquaryAffixData> randomList = new WeightedList<>();
        for (ReliquaryAffixData affix : affixList) {
            if (!blacklist.contains(affix.getFightProp())) {
                randomList.add(affix.getWeight() * bias.weigh(affix), affix);
            }
        }

        if (randomList.size() == 0) {
            return;
        }

        // Add random stat
        ReliquaryAffixData affixData = randomList.next();
        this.appendPropIdList.add(affixData.getId());
    }

    private void upgradeRandomAppendProp(ArtifactRollBias bias) {
        List<ReliquaryAffixData> affixList =
                GameDepot.getRelicAffixList(this.itemData.getAppendPropDepotId());

        if (affixList == null) {
            return;
        }

        // Build whitelist of fight props already on the relic
        Set<FightProperty> whitelist = this.getAppendFightProperties();

        // Defined artifacts: the chosen substats must gain at least 2 upgrades in total before max level,
        // split evenly when several are chosen.
        Set<FightProperty> guarantee = this.getDefiniteFightPropsNeedingGuarantee();
        if (!guarantee.isEmpty()) {
            Set<FightProperty> forced = new HashSet<>(whitelist);
            forced.retainAll(guarantee);
            if (!forced.isEmpty()) {
                whitelist = forced;
            }
        }

        // Build random list
        WeightedList<ReliquaryAffixData> randomList = new WeightedList<>();
        for (ReliquaryAffixData affix : affixList) {
            if (whitelist.contains(affix.getFightProp())) {
                randomList.add(affix.getUpgradeWeight() * bias.weigh(affix), affix);
            }
        }

        if (randomList.size() == 0) {
            return;
        }

        // Add random stat
        ReliquaryAffixData affixData = randomList.next();
        this.appendPropIdList.add(affixData.getId());
    }

    /** Fight props from definite append ids that still need guaranteed upgrade hits. */
    private Set<FightProperty> getDefiniteFightPropsNeedingGuarantee() {
        Set<FightProperty> defs = this.resolveDefiniteFightProps();
        if (defs.isEmpty()) {
            return Set.of();
        }
        int hits = 0;
        for (int appendPropId : this.appendPropIdList) {
            ReliquaryAffixData affixData = GameData.getReliquaryAffixDataMap().get(appendPropId);
            if (affixData != null && defs.contains(affixData.getFightProp())) {
                hits++;
            }
        }
        // Initial define rolls already occupy one slot each; only count extra upgrades.
        int upgradeHits = Math.max(0, hits - defs.size());
        if (upgradeHits >= 2) {
            return Set.of();
        }
        return defs;
    }

    private Set<FightProperty> resolveDefiniteFightProps() {
        Set<FightProperty> out = new HashSet<>();
        if (this.definiteAppendPropIdList == null) {
            return out;
        }
        for (int id : this.definiteAppendPropIdList) {
            ReliquaryAffixData affix = GameData.getReliquaryAffixDataMap().get(id);
            if (affix != null && affix.getFightProp() != null) {
                out.add(affix.getFightProp());
            } else {
                // Allow storing FightProperty ids directly (e.g. 20/22/28).
                FightProperty fp = FightProperty.getPropById(id);
                if (fp != null && fp != FightProperty.FIGHT_PROP_NONE) {
                    out.add(fp);
                }
            }
        }
        return out;
    }

    /**
     * Mark as Artifact Transmuter defined: purchased/definite drive the purple guarantee UI.
     *
     * <p>Do not set {@code relicStarred}. That bit is the bag star toggle
     * ({@code SetReliquaryStarState}); leaving it true on define pushes clients into a broken
     * StarUp enhance panel (Lv.00 / Option A / ? cost). e3a3240 set both; production shows only
     * purchased+definite is safe for enhance.
     */
    public void markAsDefinedReliquary(List<Integer> chosenAffixIds) {
        if (chosenAffixIds == null || chosenAffixIds.isEmpty()) {
            return;
        }
        List<Integer> copy = new ArrayList<>(chosenAffixIds);
        this.purchasedAppendPropIdList = new ArrayList<>(copy);
        this.definiteAppendPropIdList = new ArrayList<>(copy);
        // Keep existing player star as-is; never force-on at define time.
    }

    @PostLoad
    public void onLoad() {
        if (this.itemData == null) {
            this.itemData = GameData.getItemDataMap().get(getItemId());
        }
        if (this.purchasedAppendPropIdList == null) {
            this.purchasedAppendPropIdList = new ArrayList<>();
        }
        if (this.definiteAppendPropIdList == null) {
            this.definiteAppendPropIdList = new ArrayList<>();
        }
    }

    public void save() {
        if (this.count > 0 && this.ownerId > 0) {
            DatabaseHelper.saveItem(this);
        } else if (this.getObjectId() != null) {
            DatabaseHelper.deleteItem(this);
        }
    }

    public SceneWeaponInfo createSceneWeaponInfo() {
        return createSceneWeaponInfo(0);
    }

    /** {@code weaponSkinId} is the avatar's equipped weapon skin (7.1 SceneWeaponInfo field 11). */
    public SceneWeaponInfo createSceneWeaponInfo(int weaponSkinId) {
        var weaponInfo =
                SceneWeaponInfo.newBuilder()
                        .setEntityId(this.getWeaponEntity() != null ? this.getWeaponEntity().getId() : 0)
                        .setItemId(this.getItemId())
                        .setGuid(this.getGuid())
                        .setLevel(this.getLevel())
                        .setGadgetId(this.getItemData().getGadgetId())
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder().setIsInited(getAffixes().size() > 0));

        if (this.getAffixes() != null && this.getAffixes().size() > 0) {
            for (int affix : this.getAffixes()) {
                weaponInfo.putAffixMap(affix, this.getRefinement());
            }
        }

        if (weaponSkinId != 0) {
            weaponInfo.setWeaponSkinId(weaponSkinId);
        }

        return weaponInfo.build();
    }

    public SceneReliquaryInfo createSceneReliquaryInfo() {
        SceneReliquaryInfo relicInfo =
                SceneReliquaryInfo.newBuilder()
                        .setItemId(this.getItemId())
                        .setGuid(this.getGuid())
                        .setLevel(this.getLevel())
                        .build();

        return relicInfo;
    }

    public Weapon toWeaponProto() {
        Weapon.Builder weapon =
                Weapon.newBuilder()
                        .setLevel(this.getLevel())
                        .setExp(this.getExp())
                        .setPromoteLevel(this.getPromoteLevel());

        if (this.getAffixes() != null && this.getAffixes().size() > 0) {
            for (int affix : this.getAffixes()) {
                weapon.putAffixMap(affix, this.getRefinement());
            }
        }

        return weapon.build();
    }

    public Reliquary toReliquaryProto() {
        Reliquary.Builder relic =
                Reliquary.newBuilder()
                        .setLevel(this.getLevel())
                        .setExp(this.getExp())
                        .setPromoteLevel(this.getPromoteLevel())
                        .setMainPropId(this.getMainPropId())
                        .addAllAppendPropIdList(this.getAppendPropIdList());
        // Defined relics: purple guarantee = purchased/definite. Do NOT wire _is_relic_starred
        // for them - that bit opens a broken StarUp enhance UI on current clients.
        boolean defined =
                this.purchasedAppendPropIdList != null && !this.purchasedAppendPropIdList.isEmpty();
        ReliquaryProtoCompat.applyStarred(relic, !defined && this.isRelicStarred());
        return ReliquaryProtoCompat.finish(
                relic, this.purchasedAppendPropIdList, this.definiteAppendPropIdList);
    }

    public Item toProto() {
        Item.Builder proto = Item.newBuilder().setGuid(this.getGuid()).setItemId(this.getItemId());

        switch (getItemType()) {
            case ITEM_WEAPON:
                Weapon weapon = this.toWeaponProto();
                proto.setEquip(Equip.newBuilder().setWeapon(weapon).setIsLocked(this.isLocked()).build());
                break;
            case ITEM_RELIQUARY:
                Reliquary relic = this.toReliquaryProto();
                proto.setEquip(Equip.newBuilder().setReliquary(relic).setIsLocked(this.isLocked()).build());
                break;
            case ITEM_TPS_WEAPON:
                proto.setTpsWeapon(TpsWeaponSystem.toTpsWeaponProto(this));
                break;
            case ITEM_FURNITURE:
                Furniture furniture = Furniture.newBuilder().setCount(getCount()).build();
                proto.setFurniture(furniture);
                break;
            default:
                Material material = Material.newBuilder().setCount(getCount()).build();
                proto.setMaterial(material);
                break;
        }

        return proto.build();
    }

    public ItemHint toItemHintProto() {
        var proto = ItemHint.newBuilder()
            .setItemId(getItemId())
            .setGuid(this.getGuid())
            .setCount(getCount())
            .setIsNew(this.isNewItem())
            .build();
        return proto;
    }

    public ItemParam toItemParam() {
        return ItemParam.newBuilder().setItemId(this.getItemId()).setCount(this.getCount()).build();
    }
}
