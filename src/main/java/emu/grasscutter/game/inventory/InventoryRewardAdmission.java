package emu.grasscutter.game.inventory;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.utils.WeaponSkinData;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/** Validates the entire proposed item batch against current inventory capacity. */
final class InventoryRewardAdmission {
    private static final Set<Integer> VIRTUAL_REWARDS =
            Set.of(101, 102, 105, 106, 107, 121, 201, 202, 203, 204);

    private InventoryRewardAdmission() {}

    static boolean isBoundedVirtualBalance(int id) {
        // Stored player properties use signed 32-bit counters, just like currencies.
        return id == 106 || id == 107 || id == 201 || id == 202 || id == 203 || id == 204;
    }

    static boolean supportsVirtualItem(int itemId) {
        return VIRTUAL_REWARDS.contains(itemId);
    }

    /**
     * Authorization runs only after every item passes admission. The confirmation callback
     * runs before the first write, so a partial persistence failure is not charged twice.
     * This is not a durable database transaction.
     */
    static InventoryAddResult grantIfAccepted(
            Collection<GameItem> items,
            Function<ItemType, InventoryTab> getTab,
            IntUnaryOperator currentBalance,
            BooleanSupplier authorize,
            Runnable confirmed,
            Function<Collection<GameItem>, InventoryAddResult> grant) {
        InventoryAddResult.Status failure = admissionFailure(items, getTab, currentBalance);
        if (failure != null) {
            return InventoryAddResult.rejected(items, failure);
        }
        if (!authorize.getAsBoolean()) {
            return InventoryAddResult.rejected(
                    items, InventoryAddResult.Status.AUTHORIZATION_FAILED);
        }
        confirmed.run();
        return grant.apply(items);
    }

    static boolean canAccept(
            Collection<GameItem> rewards,
            Function<ItemType, InventoryTab> getTab) {
        return admissionFailure(rewards, getTab, ignored -> 0) == null;
    }

    private static InventoryAddResult.Status admissionFailure(
            Collection<GameItem> rewards,
            Function<ItemType, InventoryTab> getTab,
            IntUnaryOperator currentBalance) {
        if (rewards == null || rewards.isEmpty()) {
            return InventoryAddResult.Status.INVALID_ITEM;
        }
        Set<GameItem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<InventoryTab, Integer> reservedSlots = new IdentityHashMap<>();
        Map<InventoryTab, Map<Integer, Long>> projectedStacks = new IdentityHashMap<>();
        Map<Integer, Long> projectedCurrencies = new HashMap<>();

        for (GameItem item : rewards) {
            if (item == null || item.getCount() <= 0 || item.getItemId() <= 0
                    || !seen.add(item)) {
                return InventoryAddResult.Status.INVALID_ITEM;
            }
            ItemData data = item.getItemData();
            if (data == null || data.getId() != item.getItemId()
                    || data.getItemType() == null) {
                return InventoryAddResult.Status.INVALID_ITEM;
            }
            if (data.isUseOnGain() && WeaponSkinData.isWeaponSkinItem(item.getItemId())) {
                if (item.getCount() != 1 || data.getItemType() != ItemType.ITEM_MATERIAL) {
                    return InventoryAddResult.Status.INVALID_ITEM;
                }
                continue;
            }
            if (data.isUseOnGain()) {
                // Mail and other reward sources legitimately deliver avatar, namecard,
                // glider and costume unlock cards. They consume no inventory slot.
                // Count must be one: putItem invokes the direct-use action once per item.
                if (item.getCount() != 1) {
                    return InventoryAddResult.Status.INVALID_ITEM;
                }
                if (data.getItemType() != ItemType.ITEM_MATERIAL
                        || data.getMaterialType() == null
                        || data.getItemUseActions() == null
                        || data.getItemUseActions().isEmpty()) {
                    return InventoryAddResult.Status.UNSUPPORTED_TYPE;
                }
                ItemUseOp expected = switch (data.getMaterialType()) {
                    case MATERIAL_AVATAR -> ItemUseOp.ITEM_USE_GAIN_AVATAR;
                    case MATERIAL_FLYCLOAK -> ItemUseOp.ITEM_USE_GAIN_FLYCLOAK;
                    case MATERIAL_COSTUME -> ItemUseOp.ITEM_USE_GAIN_COSTUME;
                    case MATERIAL_NAMECARD -> ItemUseOp.ITEM_USE_GAIN_NAME_CARD;
                    default -> null;
                };
                if (expected == null || data.getItemUseActions().stream()
                        .noneMatch(action -> action.getItemUseOp() == expected)) {
                    // Refuse unknown and mismatched direct-use effects.
                    return InventoryAddResult.Status.UNSUPPORTED_TYPE;
                }
                continue;
            }
            ItemType type = data.getItemType();
            if (type == ItemType.ITEM_VIRTUAL) {
                if (!supportsVirtualItem(item.getItemId())) {
                    return InventoryAddResult.Status.UNSUPPORTED_TYPE;
                }
                int currencyId = item.getItemId();
                if (isBoundedVirtualBalance(currencyId)) {
                    long current = projectedCurrencies.computeIfAbsent(
                            currencyId, id -> (long) currentBalance.applyAsInt(id));
                    long next = current + item.getCount();
                    if (current < 0 || next > Integer.MAX_VALUE) {
                        return InventoryAddResult.Status.STACK_LIMIT;
                    }
                    projectedCurrencies.put(currencyId, next);
                }
                continue;
            }
            if (type == ItemType.ITEM_TPS_WEAPON || type == ItemType.ITEM_NONE
                    || type == ItemType.ITEM_DISPLAY) {
                return InventoryAddResult.Status.UNSUPPORTED_TYPE;
            }
            InventoryTab tab = getTab.apply(type);
            if (tab == null) {
                return InventoryAddResult.Status.UNSUPPORTED_TYPE;
            }

            if (type == ItemType.ITEM_WEAPON || type == ItemType.ITEM_RELIQUARY) {
                if (item.getCount() != 1) {
                    return InventoryAddResult.Status.INVALID_ITEM;
                }
                if (!reserveSlot(tab, reservedSlots)) {
                    return InventoryAddResult.Status.CAPACITY_FULL;
                }
                continue;
            }
            MaterialType materialType = data.getMaterialType();
            if (materialType == null) {
                return InventoryAddResult.Status.INVALID_ITEM;
            }
            switch (materialType) {
                case MATERIAL_AVATAR, MATERIAL_FLYCLOAK, MATERIAL_COSTUME, MATERIAL_NAMECARD -> {
                    return InventoryAddResult.Status.UNSUPPORTED_TYPE;
                }
                default -> {
                    // Material and furniture items share a slot for each distinct item ID.
                }
            }
            long limit = data.getStackLimit();
            if (limit <= 0) {
                return InventoryAddResult.Status.STACK_LIMIT;
            }
            Map<Integer, Long> stacks =
                    projectedStacks.computeIfAbsent(tab, unused -> new HashMap<>());
            long current = stacks.computeIfAbsent(
                    item.getItemId(), id -> (long) tab.getItemCountById(id));
            long next = current + item.getCount();
            if (next > limit) {
                return InventoryAddResult.Status.STACK_LIMIT;
            }
            if (current == 0 && tab.getItemById(item.getItemId()) == null
                    && !reserveSlot(tab, reservedSlots)) {
                return InventoryAddResult.Status.CAPACITY_FULL;
            }
            stacks.put(item.getItemId(), next);
        }
        return null;
    }

    private static boolean reserveSlot(
            InventoryTab tab, Map<InventoryTab, Integer> reservedSlots) {
        int next = reservedSlots.getOrDefault(tab, 0) + 1;
        if ((long) tab.getSize() + next > tab.getMaxCapacity()) {
            return false;
        }
        reservedSlots.put(tab, next);
        return true;
    }
}
