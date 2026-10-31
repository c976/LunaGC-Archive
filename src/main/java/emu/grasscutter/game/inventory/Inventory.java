package emu.grasscutter.game.inventory;

import static emu.grasscutter.config.Configuration.INVENTORY_LIMITS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.avatar.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.server.event.player.PlayerObtainItemEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.WeaponSkinData;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import lombok.val;

public class Inventory extends BasePlayerManager implements Iterable<GameItem> {
    /** Material types already reported as missing isUseOnGain, so each is named once. */
    private static final Set<MaterialType> MISSING_USE_ON_GAIN_REPORTED =
            Collections.synchronizedSet(EnumSet.noneOf(MaterialType.class));

    private final Long2ObjectMap<GameItem> store;
    private final Int2ObjectMap<InventoryTab> inventoryTypes;

    public Inventory(Player player) {
        super(player);

        this.store = new Long2ObjectOpenHashMap<>();
        this.inventoryTypes = new Int2ObjectOpenHashMap<>();

        this.createInventoryTab(ItemType.ITEM_WEAPON, new EquipInventoryTab(INVENTORY_LIMITS.weapons));
        this.createInventoryTab(
                ItemType.ITEM_RELIQUARY, new EquipInventoryTab(INVENTORY_LIMITS.relics));
        this.createInventoryTab(
                ItemType.ITEM_MATERIAL, new MaterialInventoryTab(INVENTORY_LIMITS.materials));
        this.createInventoryTab(
                ItemType.ITEM_FURNITURE, new MaterialInventoryTab(INVENTORY_LIMITS.furniture));
        this.createInventoryTab(
                ItemType.ITEM_TPS_WEAPON, new EquipInventoryTab(TpsWeaponSystem.TPS_WEAPON_ITEM_LIMIT));
    }

    public AvatarStorage getAvatarStorage() {
        return this.getPlayer().getAvatars();
    }

    public Long2ObjectMap<GameItem> getItems() {
        return store;
    }

    public Int2ObjectMap<InventoryTab> getInventoryTypes() {
        return inventoryTypes;
    }

    public InventoryTab getInventoryTab(ItemType type) {
        return getInventoryTypes().get(type.getValue());
    }

    public void createInventoryTab(ItemType type, InventoryTab tab) {
        this.getInventoryTypes().put(type.getValue(), tab);
    }

    /**
     * Finds the first item in the inventory with the given item id.
     *
     * @param itemId The item id to search for.
     * @return The first item found with the given item id, or null if no item was
     */
    public GameItem getFirstItem(int itemId) {
        return this.getItems().values().stream()
                .filter(item -> item.getItemId() == itemId)
                .findFirst()
                .orElse(null);
    }

    public GameItem getItemByGuid(long id) {
        return this.getItems().get(id);
    }

    @Nullable public InventoryTab getInventoryTabByItemId(int itemId) {
        val itemData = GameData.getItemDataMap().get(itemId);
        if (itemData == null || itemData.getItemType() == null) {
            return null;
        }
        return getInventoryTab(itemData.getItemType());
    }

    @Nullable public GameItem getItemById(int itemId) {
        val inventoryTab = this.getInventoryTabByItemId(itemId);
        return inventoryTab != null ? inventoryTab.getItemById(itemId) : null;
    }

    public int getItemCountById(int itemId) {
        val inventoryTab = this.getInventoryTabByItemId(itemId);
        return inventoryTab != null ? inventoryTab.getItemCountById(itemId) : 0;
    }

    public boolean addItem(int itemId) {
        return addItem(itemId, 1);
    }

    public boolean addItem(int itemId, int count) {
        return addItem(itemId, count, null);
    }

    public boolean addItem(int itemId, int count, ActionReason reason) {
        ItemData itemData = GameData.getItemDataMap().get(itemId);

        if (itemData == null) {
            return false;
        }

        if (count <= 0) {
            return false;
        }
        ItemType type = itemData.getItemType();
        if ((type == ItemType.ITEM_WEAPON || type == ItemType.ITEM_RELIQUARY
                        || type == ItemType.ITEM_TPS_WEAPON)
                && count > 1) {
            try {
                return addItems(
                        InventoryGrantBuilder.create(itemData, count, 1),
                        reason,
                        InventoryAddPolicy.ALL_OR_NOTHING)
                        .allAccepted();
            } catch (IllegalArgumentException e) {
                Grasscutter.getLogger().warn(
                        "Rejected oversized equipment grant id={} count={}", itemId, count);
                return false;
            }
        }
        return addItem(new GameItem(itemData, count), reason);
    }

    /** Add one item through the common insertion path. */
    public boolean addItem(GameItem item) {
        return addItemsInternal(Collections.singletonList(item), null, false, false).allAccepted();
    }

    public boolean addItem(GameItem item, ActionReason reason) {
        return addItem(item, reason, false);
    }

    /**
     * Keep the legacy forceNotify overload for drop, shop and cooking callers.
     * The shared insertion path already sends hints for accepted grants with a
     * reason. Never send a forced success hint for a rejected grant.
     */
    public boolean addItem(GameItem item, ActionReason reason, boolean forceNotify) {
        return addItemsInternal(Collections.singletonList(item), reason, true, true).allAccepted();
    }

    /** Skip elem-ball spam; keep Primogem/Mora tips. */
    private static boolean shouldSkipItemAddHint(GameItem item) {
        var itemData = item.getItemData();
        if (itemData == null) {
            return false;
        }
        if (itemData.isUseOnGain()) {
            return true;
        }
        int id = item.getItemId();
        if (id == 201 || id == 202) {
            return false;
        }
        return itemData.getMaterialType() == MaterialType.MATERIAL_ADSORBATE;
    }

    public boolean addItem(ItemParamData itemParam) {
        return addItem(itemParam, null);
    }

    public boolean addItem(ItemParamData itemParam, ActionReason reason) {
        if (itemParam == null) return false;
        return addItem(itemParam.getId(), itemParam.getCount(), reason);
    }

    /** Default to complete delivery. Callers must explicitly request partial delivery. */
    public InventoryAddResult addItems(Collection<GameItem> items) {
        return addItems(items, null, InventoryAddPolicy.ALL_OR_NOTHING);
    }

    public InventoryAddResult addItems(Collection<GameItem> items, ActionReason reason) {
        return addItems(items, reason, InventoryAddPolicy.ALL_OR_NOTHING);
    }

    public synchronized InventoryAddResult addItems(
            Collection<GameItem> items, ActionReason reason, InventoryAddPolicy policy) {
        if (policy == InventoryAddPolicy.BEST_EFFORT) {
            return addItemsInternal(items, reason, false, false);
        }
        return addItems(items, reason, policy, () -> true, () -> {});
    }

    /**
     * Run admission under the same inventory monitor as insertion.
     * Authorization and confirmation coordinate the caller's separate cost/claim state.
     * The method cannot roll back a database write or external side effect.
     */
    public synchronized InventoryAddResult addItems(
            Collection<GameItem> items,
            ActionReason reason,
            InventoryAddPolicy policy,
            BooleanSupplier authorize,
            Runnable confirmed) {
        if (policy != InventoryAddPolicy.ALL_OR_NOTHING) {
            throw new IllegalArgumentException("Authorized grant requires ALL_OR_NOTHING");
        }
        Objects.requireNonNull(authorize, "authorize");
        Objects.requireNonNull(confirmed, "confirmed");
        return InventoryRewardAdmission.grantIfAccepted(
                items, this::getInventoryTab, this::getVirtualItemCount, authorize, confirmed,
                accepted -> addItemsInternal(accepted, reason, false, false));
    }

    /**
     * Common insertion and notification path. Reports actual amounts and refusal reasons.
     * ALL_OR_NOTHING runs a full preflight before calling this method.
     */
    private synchronized InventoryAddResult addItemsInternal(
            Collection<GameItem> items,
            ActionReason reason,
            boolean notifyAvatarCard,
            boolean allowCurrencyFallback) {
        if (items == null || items.isEmpty()) {
            return new InventoryAddResult(List.of());
        }
        Set<GameItem> changedItems = new LinkedHashSet<>();
        Set<GameItem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<GameItem> hintedItems = new ArrayList<>();
        List<InventoryAddResult.Entry> outcome = new ArrayList<>();

        for (GameItem item : items) {
            InsertResult inserted =
                    item != null && !seen.add(item)
                            ? InsertResult.refused(InventoryAddResult.Status.INVALID_ITEM)
                            : putItem(item);
            int itemId = item == null ? 0 : item.getItemId();
            int requested = item == null ? 0 : item.getCount();
            if (!inserted.accepted()) {
                outcome.add(new InventoryAddResult.Entry(
                        itemId, requested, 0, inserted.status()));
                continue;
            }
            outcome.add(new InventoryAddResult.Entry(
                    itemId, requested, requested, inserted.status()));
            try {
                this.player.getProgressManager().addItemObtainedHistory(itemId, requested);
            } catch (Exception e) {
                Grasscutter.getLogger().debug("addItemObtainedHistory failed", e);
            }
            if (inserted.changedItem() != null) {
                changedItems.add(inserted.changedItem());
                // Use this grant's quantity, not the total of an existing stack.
                triggerAddItemEvents(item);
            }
            // Plugin listeners are not part of inventory persistence. Their failure must not
            // interrupt a grant after the payment/claim has already been confirmed.
            try {
                new PlayerObtainItemEvent(getPlayer(), item).call();
            } catch (RuntimeException listenerFailure) {
                Grasscutter.getLogger().error(
                        "PlayerObtainItemEvent failed after granting item {} to uid={}",
                        itemId, getPlayer().getUid(), listenerFailure);
            }

            if (notifyAvatarCard && reason != null
                    && item.getItemData().getMaterialType() == MaterialType.MATERIAL_AVATAR) {
                try {
                    getPlayer().sendPacket(
                            new PacketAddNoGachaAvatarCardNotify(
                                    (itemId % 1000) + 10000000, reason, item));
                } catch (RuntimeException notifyFailure) {
                    Grasscutter.getLogger().error(
                            "Avatar card notification failed after granting item {} to uid={}",
                            itemId, getPlayer().getUid(), notifyFailure);
                }
            }
            if (!shouldSkipItemAddHint(item)) {
                hintedItems.add(item);
            }
        }

        if (!changedItems.isEmpty()) {
            try {
                getPlayer().sendPacket(new PacketStoreItemChangeNotify(changedItems));
            } catch (RuntimeException notifyFailure) {
                Grasscutter.getLogger().error(
                        "Inventory change notification failed for uid={}",
                        getPlayer().getUid(), notifyFailure);
            }
        }
        if (!hintedItems.isEmpty()) {
            ActionReason hintReason = reason;
            if (allowCurrencyFallback && hintReason == null && hintedItems.size() == 1
                    && (hintedItems.get(0).getItemId() == 201
                            || hintedItems.get(0).getItemId() == 202)) {
                hintReason = ActionReason.OpenChest;
            }
            if (hintReason != null) {
                try {
                    getPlayer().sendPacket(new PacketItemAddHintNotify(hintedItems, hintReason));
                } catch (RuntimeException notifyFailure) {
                    Grasscutter.getLogger().error(
                            "Item hint notification failed for uid={}",
                            getPlayer().getUid(), notifyFailure);
                }
            }
        }
        return new InventoryAddResult(outcome);
    }

    private record InsertResult(
            InventoryAddResult.Status status, @Nullable GameItem changedItem) {
        private boolean accepted() {
            return status == InventoryAddResult.Status.ADDED
                    || status == InventoryAddResult.Status.EFFECT_APPLIED;
        }

        private static InsertResult refused(InventoryAddResult.Status status) {
            return new InsertResult(status, null);
        }

        private static InsertResult stored(GameItem item) {
            return new InsertResult(InventoryAddResult.Status.ADDED, item);
        }
    }

    /**
     * Checks to see if the player has the item in their inventory. This will succeed if the player
     * has at least the minimum count of the item.
     *
     * @param itemId The item id to check for.
     * @param minCount The minimum count of the item to check for.
     * @return True if the player has the item, false otherwise.
     */
    public boolean hasItem(int itemId, int minCount) {
        return hasItem(itemId, minCount, false);
    }

    /**
     * Checks to see if the player has the item in their inventory.
     *
     * @param itemId The item id to check for.
     * @param count The count of the item to check for.
     * @param enforce If true, the player must have the exact amount. If false, the player must have
     *     at least the amount.
     * @return True if the player has the item, false otherwise.
     */
    public boolean hasItem(int itemId, int count, boolean enforce) {
        var item = this.getFirstItem(itemId);
        if (item == null) return false;

        return enforce ? item.getCount() == count : item.getCount() >= count;
    }

    /**
     * Checks to see if the player has the item in their inventory. This is not exact.
     *
     * @param items A map of item game IDs to their count.
     * @return True if the player has the items, false otherwise.
     */
    public boolean hasAllItems(Collection<ItemParam> items) {
        for (var item : items) {
            if (!this.hasItem(item.getItemId(), item.getCount(), false)) return false;
        }

        return true;
    }

    private void triggerAddItemEvents(GameItem result) {
        try {
            getPlayer()
                    .getBattlePassManager()
                    .triggerMission(
                            WatcherTriggerType.TRIGGER_OBTAIN_MATERIAL_NUM,
                            result.getItemId(),
                            result.getCount());
            getPlayer()
                    .getActivityManager()
                    .triggerWatcher(
                            WatcherTriggerType.TRIGGER_OBTAIN_MATERIAL_NUM,
                            String.valueOf(result.getItemId()),
                            String.valueOf(result.getCount()));
            getPlayer()
                    .getQuestManager()
                    .queueEvent(
                            QuestContent.QUEST_CONTENT_OBTAIN_ITEM, result.getItemId(), result.getCount());
            if (result.getItemType() == ItemType.ITEM_RELIQUARY && result.getItemData() != null) {
                int rank = result.getItemData().getRankLevel();
                getPlayer().getPlayerProgress().addHandbookReliquaryHistory(rank, 1);
                emu.grasscutter.game.player.InvestigationHandbookHelper.trigger(
                        getPlayer(),
                        WatcherTriggerType.TRIGGER_OBTAIN_RELIQUARY_HISTORY_COUNT,
                        rank,
                        getPlayer().getPlayerProgress().getHandbookReliquaryHistoryCount(rank));
            }
        } catch (Exception e) {
            Grasscutter.getLogger().debug("triggerAddItemEvents failed", e);
        }
    }

    private void triggerRemItemEvents(GameItem item, int removeCount) {
        try {
            getPlayer()
                    .getBattlePassManager()
                    .triggerMission(WatcherTriggerType.TRIGGER_COST_MATERIAL, item.getItemId(), removeCount);
            getPlayer()
                    .getQuestManager()
                    .queueEvent(QuestContent.QUEST_CONTENT_ITEM_LESS_THAN, item.getItemId(), item.getCount());
        } catch (Exception e) {
            Grasscutter.getLogger().debug("triggerRemItemEvents failed", e);
        }
    }

    public void addItemParams(Collection<ItemParam> items) {
        addItems(
                items.stream().map(param -> new GameItem(param.getItemId(), param.getCount())).toList(),
                null,
                InventoryAddPolicy.BEST_EFFORT);
    }

    public void addItemParamDatas(Collection<ItemParamData> items) {
        addItemParamDatas(items, null);
    }

    public void addItemParamDatas(Collection<ItemParamData> items, ActionReason reason) {
        addItems(
                items.stream().map(param -> new GameItem(param.getItemId(), param.getCount())).toList(),
                reason,
                InventoryAddPolicy.BEST_EFFORT);
    }

    /**
     * Sole insertion implementation. Persisted inventory loading uses registerStoredItem.
     */
    private synchronized InsertResult putItem(GameItem item) {
        if (item == null || item.getCount() <= 0 || item.getItemId() <= 0) {
            return InsertResult.refused(InventoryAddResult.Status.INVALID_ITEM);
        }
        ItemData data = item.getItemData();
        if (data == null || data.getItemType() == null || data.getId() != item.getItemId()) {
            return InsertResult.refused(InventoryAddResult.Status.INVALID_ITEM);
        }
        if (data.isUseOnGain()) {
            // 7.1 weapon skin grants use an operation unknown to ItemUseOp.
            // Preserve the upstream unlock route rather than rejecting the item.
            var skinIds = WeaponSkinData.skinIdsForItem(item.getItemId());
            if (!skinIds.isEmpty()) {
                if (item.getCount() != 1) {
                    return InsertResult.refused(InventoryAddResult.Status.INVALID_ITEM);
                }
                this.player.addWeaponSkins(skinIds);
                return new InsertResult(InventoryAddResult.Status.EFFECT_APPLIED, null);
            }
            var params = new UseItemParams(this.player, data.getUseTarget());
            params.usedItemId = data.getId();
            if (!this.player.getServer().getInventorySystem().useItemDirect(data, params)) {
                Grasscutter.getLogger()
                        .warn("useOnGain failed for item {} (buff/use action rejected)", data.getId());
                return InsertResult.refused(InventoryAddResult.Status.EFFECT_FAILED);
            }
            return new InsertResult(InventoryAddResult.Status.EFFECT_APPLIED, null);
        }

        ItemType type = data.getItemType();
        InventoryTab tab = getInventoryTab(type);
        switch (type) {
            case ITEM_WEAPON:
            case ITEM_RELIQUARY:
                if (item.getCount() != 1) {
                    return InsertResult.refused(InventoryAddResult.Status.INVALID_ITEM);
                }
                if (tab == null || tab.getSize() >= tab.getMaxCapacity()) {
                    return InsertResult.refused(InventoryAddResult.Status.CAPACITY_FULL);
                }
                registerStoredItem(item, tab);
                item.save();
                return InsertResult.stored(item);
            case ITEM_TPS_WEAPON:
                if (TpsWeaponSystem.findOwnedWeapon(this.player, item.getItemId()) != null) {
                    return InsertResult.refused(InventoryAddResult.Status.ALREADY_OWNED);
                }
                if (tab == null || tab.getSize() >= tab.getMaxCapacity()) {
                    return InsertResult.refused(InventoryAddResult.Status.CAPACITY_FULL);
                }
                item.setCount(1);
                registerStoredItem(item, tab);
                item.save();
                return InsertResult.stored(item);
            case ITEM_VIRTUAL:
                if (!InventoryRewardAdmission.supportsVirtualItem(item.getItemId())) {
                    return InsertResult.refused(InventoryAddResult.Status.UNSUPPORTED_TYPE);
                }
                if (InventoryRewardAdmission.isBoundedVirtualBalance(item.getItemId())) {
                    long current = getVirtualItemCount(item.getItemId());
                    if (current < 0 || current + item.getCount() > Integer.MAX_VALUE) {
                        return InsertResult.refused(InventoryAddResult.Status.STACK_LIMIT);
                    }
                }
                addVirtualItem(item.getItemId(), item.getCount());
                return InsertResult.stored(item);
            case ITEM_NONE:
            case ITEM_DISPLAY:
                return InsertResult.refused(InventoryAddResult.Status.UNSUPPORTED_TYPE);
            default:
                if (data.getMaterialType() == null) {
                    return InsertResult.refused(InventoryAddResult.Status.INVALID_ITEM);
                }
                switch (data.getMaterialType()) {
                    case MATERIAL_AVATAR:
                    case MATERIAL_FLYCLOAK:
                    case MATERIAL_COSTUME:
                    case MATERIAL_NAMECARD:
                        if (MISSING_USE_ON_GAIN_REPORTED.add(data.getMaterialType())) {
                            Grasscutter.getLogger()
                                    .warn(
                                            "Attempted to add a {} to inventory, but item definition"
                                                + " lacks isUseOnGain. This indicates a Resources"
                                                + " error. Further {} items are logged at debug.",
                                            data.getMaterialType().name(),
                                            data.getMaterialType().name());
                        } else {
                            Grasscutter.getLogger()
                                    .debug(
                                            "Attempted to add a {} to inventory, but item definition"
                                                + " lacks isUseOnGain.",
                                            data.getMaterialType().name());
                        }
                        return InsertResult.refused(InventoryAddResult.Status.UNSUPPORTED_TYPE);
                    default:
                        if (tab == null) {
                            return InsertResult.refused(InventoryAddResult.Status.UNSUPPORTED_TYPE);
                        }
                        if (data.getStackLimit() <= 0 || item.getCount() > data.getStackLimit()) {
                            return InsertResult.refused(InventoryAddResult.Status.STACK_LIMIT);
                        }
                        GameItem existingItem = tab.getItemById(item.getItemId());
                        if (existingItem == null) {
                            if (tab.getSize() >= tab.getMaxCapacity()) {
                                return InsertResult.refused(InventoryAddResult.Status.CAPACITY_FULL);
                            }
                            registerStoredItem(item, tab);
                            item.save();
                            return InsertResult.stored(item);
                        }
                        if ((long) existingItem.getCount() + item.getCount()
                                > data.getStackLimit()) {
                            return InsertResult.refused(InventoryAddResult.Status.STACK_LIMIT);
                        }
                        existingItem.setCount(existingItem.getCount() + item.getCount());
                        existingItem.save();
                        return InsertResult.stored(existingItem);
                }
        }
    }

    private synchronized void registerStoredItem(GameItem item, InventoryTab tab) {
        this.player.getCodex().checkAddedItem(item);
        item.setOwner(this.player);
        item.checkIsNew(this);
        getItems().put(item.getGuid(), item);
        if (tab != null) {
            tab.onAddItem(item);
        }
    }

    private void addVirtualItem(int itemId, int count) {
        switch (itemId) {
            case 101 -> // Character exp
            this.player.getTeamManager().getActiveTeam().stream()
                    .map(e -> e.getAvatar())
                    .forEach(
                            avatar ->
                                    this.player
                                            .getServer()
                                            .getInventorySystem()
                                            .upgradeAvatar(this.player, avatar, count));
            case 102 -> // Adventure exp
            this.player.addExpDirectly(count);
            case 105 -> // Companionship exp
            this.player.getTeamManager().getActiveTeam().stream()
                    .map(e -> e.getAvatar())
                    .forEach(
                            avatar ->
                                    this.player
                                            .getServer()
                                            .getInventorySystem()
                                            .upgradeAvatarFetterLevel(
                                                    this.player, avatar, count * (this.player.isInMultiplayer() ? 2 : 1)));
            case 106 -> // Resin
            this.player.getResinManager().addResin(count);
            case 107 -> // Legendary Key
            this.player.addLegendaryKey(count);
            case 121 -> // Home exp
            this.player.getHome().addExp(this.player, count);
            case 201 -> // Primogem
            this.player.setPrimogems(this.player.getPrimogems() + count);
            case 202 -> // Mora
            this.player.setMora(this.player.getMora() + count);
            case 203 -> // Genesis Crystals
            this.player.setCrystals(this.player.getCrystals() + count);
            case 204 -> // Home Coin
            this.player.setHomeCoin(this.player.getHomeCoin() + count);
        }
    }

    private GameItem payVirtualItem(int itemId, int count) {
        switch (itemId) {
            case 201 -> // Primogem
            player.setPrimogems(player.getPrimogems() - count);
            case 202 -> // Mora
            player.setMora(player.getMora() - count);
            case 203 -> // Genesis Crystals
            player.setCrystals(player.getCrystals() - count);
            case 106 -> // Resin
            player.getResinManager().useResin(count);
            case 107 -> // LegendaryKey
            player.useLegendaryKey(count);
            case 204 -> // Home Coin
            player.setHomeCoin(player.getHomeCoin() - count);
            default -> {
                var gameItem = getInventoryTab(ItemType.ITEM_MATERIAL).getItemById(itemId);
                removeItem(gameItem, count);
                return gameItem;
            }
        }
        return null;
    }

    private int getVirtualItemCount(int itemId) {
        switch (itemId) {
            case 201: // Primogem
                return this.player.getPrimogems();
            case 202: // Mora
                return this.player.getMora();
            case 203: // Genesis Crystals
                return this.player.getCrystals();
            case 106: // Resin
                return this.player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
            case 107: // Legendary Key
                return this.player.getProperty(PlayerProperty.PROP_PLAYER_LEGENDARY_KEY);
            case 204: // Home Coin
                return this.player.getHomeCoin();
            default:
                GameItem item =
                        getInventoryTab(ItemType.ITEM_MATERIAL)
                                .getItemById(
                                        itemId); // What if we ever want to operate on weapons/relics/furniture? :S
                return (item == null) ? 0 : item.getCount();
        }
    }

    public synchronized boolean payItem(int id, int count) {
        if (id <= 0 || count < 0) return false;
        if (count == 0) return true;
        if (this.getVirtualItemCount(id) < count) return false;
        this.payVirtualItem(id, count);
        return true;
    }

    public boolean payItem(ItemParamData costItem) {
        return costItem != null && this.payItem(costItem.getId(), costItem.getCount());
    }

    public boolean payItems(ItemParamData[] costItems) {
        return this.payItems(costItems, 1, null);
    }

    public boolean payItems(ItemParamData[] costItems, int quantity) {
        return this.payItems(costItems, quantity, null);
    }

    public synchronized boolean payItems(
            ItemParamData[] costItems, int quantity, ActionReason reason) {
        return costItems != null && payItems(Arrays.asList(costItems), quantity, reason);
    }

    public boolean payItems(Iterable<ItemParamData> costItems) {
        return this.payItems(costItems, 1, null);
    }

    public boolean payItems(Iterable<ItemParamData> costItems, int quantity) {
        return this.payItems(costItems, quantity, null);
    }

    /** Check affordability under the inventory monitor without making a payment. */
    public synchronized boolean canPayItems(Iterable<ItemParamData> costItems, int quantity) {
        Map<Integer, Integer> totals = InventoryPaymentQuote.calculate(costItems, quantity);
        return totals != null && canPayQuote(totals);
    }

    private boolean canPayQuote(Map<Integer, Integer> totals) {
        for (var cost : totals.entrySet()) {
            if (getVirtualItemCount(cost.getKey()) < cost.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate a whole payment, including repeated item IDs and arithmetic overflow,
     * before consuming anything. All payment entry points share the same inventory lock.
     */
    public synchronized boolean payItems(
            Iterable<ItemParamData> costItems, int quantity, ActionReason reason) {
        Map<Integer, Integer> totals = InventoryPaymentQuote.calculate(costItems, quantity);
        if (totals == null || !canPayQuote(totals)) {
            return false;
        }
        for (var cost : totals.entrySet()) {
            payVirtualItem(cost.getKey(), cost.getValue());
        }
        return true;
    }

    public void removeItems(List<GameItem> items) {
        // TODO Bulk delete
        for (GameItem item : items) {
            this.removeItem(item, item.getCount());
        }
    }

    /**
     * Performs a bulk delete of items.
     *
     * @param items A map of item game IDs to the amount of items to remove.
     */
    public void removeItems(Collection<ItemParam> items) {
        for (var entry : items) {
            this.removeItem(entry.getItemId(), entry.getCount());
        }
    }

    public boolean removeItem(long guid) {
        return removeItem(guid, 1);
    }

    /**
     * Removes an item from the player's inventory. This uses the item ID to find the first stack of
     * the item's type.
     *
     * @param itemId The ID of the item to remove.
     * @param count The amount of items to remove.
     * @return True if the item was removed, false otherwise.
     */
    public synchronized boolean removeItem(int itemId, int count) {
        var item = this.getItems().values().stream().filter(i -> i.getItemId() == itemId).findFirst();

        // Check if the item is in the player's inventory.
        return item.filter(gameItem -> this.removeItem(gameItem, count)).isPresent();
    }

    public synchronized boolean removeItem(long guid, int count) {
        var item = this.getItemByGuid(guid);

        if (item == null) {
            return false;
        }

        return removeItem(item, count);
    }

    /**
     * Removes an item by its item ID.
     *
     * @param itemId The ID of the item to remove.
     * @param count The amount of items to remove.
     * @return True if the item was removed, false otherwise.
     */
    public synchronized boolean removeItemById(int itemId, int count) {
        var item = this.getItems().values().stream().filter(i -> i.getItemId() == itemId).findFirst();

        // Check if the item is in the player's inventory.
        return item.filter(gameItem -> this.removeItem(gameItem, count)).isPresent();
    }

    public synchronized boolean removeItem(GameItem item) {
        return removeItem(item, item.getCount());
    }

    public synchronized boolean removeItem(GameItem item, int count) {
        // Sanity check
        if (count <= 0 || item == null) {
            return false;
        }

        int countBefore = item.getCount();

        if (item.getItemData().isEquip()) {
            item.setCount(0);
        } else {
            item.setCount(item.getCount() - count);
        }

        if (item.getCount() <= 0) {
            // Remove from inventory tab too
            InventoryTab tab = null;
            if (item.getItemData() != null) {
                tab = getInventoryTab(item.getItemData().getItemType());
            }
            // Remove if less than 0
            deleteItem(item, tab);
            //
            getPlayer().sendPacket(new PacketStoreItemDelNotify(item));
        } else {
            getPlayer().sendPacket(new PacketStoreItemChangeNotify(item));
        }

        // Battle pass trigger
        // Must be measured against the count from before the removal, otherwise this reports the
        // leftover stack size (or a negative number) instead of how many were actually taken.
        int removeCount = Math.min(count, countBefore);
        this.triggerRemItemEvents(item, removeCount);

        // Update in db
        item.save();

        // Returns true on success
        return true;
    }

    private void deleteItem(GameItem item, InventoryTab tab) {
        getItems().remove(item.getGuid());
        if (tab != null) {
            tab.onRemoveItem(item);
        }
    }

    public boolean equipItem(long avatarGuid, long equipGuid) {
        Avatar avatar = getPlayer().getAvatars().getAvatarByGuid(avatarGuid);
        GameItem item = this.getItemByGuid(equipGuid);

        if (avatar != null && item != null) {
            return avatar.equipItem(item, true);
        }

        return false;
    }

    public boolean unequipItem(long avatarGuid, int slot) {
        Avatar avatar = getPlayer().getAvatars().getAvatarByGuid(avatarGuid);
        EquipType equipType = EquipType.getTypeByValue(slot);

        if (avatar != null && equipType != EquipType.EQUIP_WEAPON) {
            if (avatar.unequipItem(equipType)) {
                getPlayer().sendPacket(new PacketAvatarEquipChangeNotify(avatar, equipType));
                avatar.recalcStats();
                return true;
            }
        }

        return false;
    }

    public void loadFromDatabase() {
        if (this.isLoaded()) return;

        // Wait for avatars to load.
        Utils.waitFor(this.getPlayer().getAvatars()::isLoaded);

        List<GameItem> items = DatabaseHelper.getInventoryItems(getPlayer());

        for (GameItem item : items) {
            // Should never happen
            if (item.getObjectId() == null) {
                continue;
            }

            ItemData itemData = GameData.getItemDataMap().get(item.getItemId());
            if (itemData == null) {
                continue;
            }

            item.setItemData(itemData);

            InventoryTab tab = null;
            if (item.getItemData() != null) {
                tab = getInventoryTab(item.getItemData().getItemType());
            }

            this.registerStoredItem(item, tab);

            // Equip to a character if possible
            if (item.isEquipped()) {
                Avatar avatar = getPlayer().getAvatars().getAvatarById(item.getEquipCharacter());
                boolean hasEquipped = false;

                if (avatar != null) {
                    hasEquipped = avatar.equipItem(item, false);
                }

                if (!hasEquipped) {
                    item.setEquipCharacter(0);
                    item.save();
                }
            }
        }

        // Load avatars after inventory.
        this.getPlayer().getAvatars().postLoad();
        this.setLoaded(true);
    }

    @Override
    public Iterator<GameItem> iterator() {
        return this.getItems().values().iterator();
    }
}
