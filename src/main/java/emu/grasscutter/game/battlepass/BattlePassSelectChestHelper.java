package emu.grasscutter.game.battlepass;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.common.ItemUseData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.ItemUseOp;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolve MATERIAL_SELECTABLE_CHEST BP rewards into real inventory items.
 *
 * <p>Client option_idx for specialty chests (e.g. 116018) is the 1-based slot id from
 * GroupedItemSelectionExcelConfig (can be up to 61), not necessarily bounded by a truncated
 * MaterialExcel useParam list. We extend consecutive GRANT_SELECT reward ids when present.
 */
public final class BattlePassSelectChestHelper {
    private BattlePassSelectChestHelper() {}

    public static boolean isSelectableReward(int rewardId) {
        return chestItemData(rewardId) != null;
    }

    public static ItemData chestItemData(int rewardId) {
        RewardData reward = GameData.getRewardDataMap().get(rewardId);
        if (reward == null || reward.getRewardItemList() == null) {
            return null;
        }
        for (ItemParamData ip : reward.getRewardItemList()) {
            if (ip == null || ip.getItemId() <= 0) {
                continue;
            }
            ItemData item = GameData.getItemDataMap().get(ip.getItemId());
            if (item != null && item.getMaterialType() == MaterialType.MATERIAL_SELECTABLE_CHEST) {
                return item;
            }
        }
        return null;
    }

    /**
     * Grant the chosen entry from a selectable BP chest.
     *
     * @param optionIdx 1-based slot id from the client UI / GroupedItemSelection
     * @return granted game items (empty on failure)
     */
    public static List<GameItem> grant(Player player, int rewardId, int optionIdx, boolean paid) {
        List<GameItem> granted = new ArrayList<>();
        if (player == null || rewardId <= 0 || optionIdx < 1) {
            return granted;
        }
        ItemData chest = chestItemData(rewardId);
        if (chest == null) {
            Grasscutter.getLogger().warn("TakeBP select: reward {} is not a selectable chest", rewardId);
            return granted;
        }
        ItemUseData use = firstUse(chest);
        if (use == null || use.getUseParam() == null || use.getUseParam().length < 1) {
            Grasscutter.getLogger().warn("TakeBP select: chest {} has no useParam", chest.getId());
            return granted;
        }

        int chosen = resolveChoiceRewardOrItem(chest.getId(), use, optionIdx);
        if (chosen <= 0) {
            return granted;
        }

        ItemUseOp op = use.getUseOp();
        Inventory inv = player.getInventory();
        ActionReason reason = paid ? ActionReason.BattlePassPaidReward : ActionReason.BattlePassLevelReward;

        if (op == ItemUseOp.ITEM_USE_ADD_SELECT_ITEM) {
            ItemData itemData = GameData.getItemDataMap().get(chosen);
            if (itemData == null) {
                Grasscutter.getLogger().warn("TakeBP select: missing item {}", chosen);
                return granted;
            }
            int count = 1;
            RewardData outer = GameData.getRewardDataMap().get(rewardId);
            if (outer != null && outer.getRewardItemList() != null) {
                for (ItemParamData ip : outer.getRewardItemList()) {
                    if (ip != null && ip.getItemId() == chest.getId() && ip.getItemCount() > 0) {
                        count = ip.getItemCount();
                        break;
                    }
                }
            }
            GameItem gi = new GameItem(itemData, count);
            inv.addItem(gi, reason);
            granted.add(gi);
            Grasscutter.getLogger()
                    .info(
                            "TakeBP select ADD_ITEM uid={} chest={} choiceItem={} x{}",
                            player.getUid(),
                            chest.getId(),
                            chosen,
                            count);
            return granted;
        }

        if (op == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            RewardData chosenReward = GameData.getRewardDataMap().get(chosen);
            if (chosenReward == null || chosenReward.getRewardItemList() == null) {
                Grasscutter.getLogger().warn("TakeBP select: missing reward {}", chosen);
                return granted;
            }
            for (ItemParamData ip : chosenReward.getRewardItemList()) {
                if (ip == null || ip.getItemId() <= 0 || ip.getItemCount() <= 0) {
                    continue;
                }
                ItemData itemData = GameData.getItemDataMap().get(ip.getItemId());
                if (itemData == null) {
                    Grasscutter.getLogger()
                            .warn("TakeBP select: missing item {} from reward {}", ip.getItemId(), chosen);
                    continue;
                }
                GameItem gi = new GameItem(itemData, ip.getItemCount());
                boolean ok = inv.addItem(gi, reason);
                if (ok) {
                    granted.add(gi);
                } else {
                    Grasscutter.getLogger()
                            .warn(
                                    "TakeBP select: addItem failed item={} x{}",
                                    ip.getItemId(),
                                    ip.getItemCount());
                }
            }
            Grasscutter.getLogger()
                    .info(
                            "TakeBP select GRANT_REWARD uid={} chest={} choiceReward={} optionIdx={} items={}",
                            player.getUid(),
                            chest.getId(),
                            chosen,
                            optionIdx,
                            granted.size());
            return granted;
        }

        Grasscutter.getLogger()
                .error(
                        "TakeBP select: unsupported useOp {} on chest {}",
                        op,
                        chest.getId());
        return granted;
    }

    /**
     * Map client option_idx to a reward id (GRANT_SELECT) or item id (ADD_SELECT).
     */
    private static int resolveChoiceRewardOrItem(int chestId, ItemUseData use, int optionIdx) {
        String raw = use.getUseParam()[0];
        if (raw == null || raw.isBlank()) {
            Grasscutter.getLogger().warn("TakeBP select: chest {} empty useParam[0]", chestId);
            return -1;
        }
        String[] parts = raw.split(",");
        List<Integer> choices = new ArrayList<>(parts.length + 16);
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p.trim());
                if (v > 0) {
                    choices.add(v);
                }
            } catch (Exception ignored) {
                // skip malformed
            }
        }
        if (choices.isEmpty()) {
            Grasscutter.getLogger().warn("TakeBP select: chest {} no parseable choices", chestId);
            return -1;
        }

        // Auto-extend consecutive reward ids when MaterialExcel list is truncated vs client UI.
        if (use.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            int last = choices.get(choices.size() - 1);
            while (true) {
                int next = last + 1;
                if (GameData.getRewardDataMap().get(next) == null) {
                    break;
                }
                choices.add(next);
                last = next;
                if (choices.size() > 128) {
                    break;
                }
            }
        }

        if (optionIdx <= choices.size()) {
            return choices.get(optionIdx - 1);
        }

        // Fallback: treat optionIdx as 1-based offset from the first reward/item id.
        int extrapolated = choices.get(0) + (optionIdx - 1);
        if (use.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            if (GameData.getRewardDataMap().get(extrapolated) != null) {
                Grasscutter.getLogger()
                        .info(
                                "TakeBP select: optionIdx {} beyond list ({}) chest {}, using reward {}",
                                optionIdx,
                                choices.size(),
                                chestId,
                                extrapolated);
                return extrapolated;
            }
        } else if (GameData.getItemDataMap().get(extrapolated) != null) {
            Grasscutter.getLogger()
                    .info(
                            "TakeBP select: optionIdx {} beyond list ({}) chest {}, using item {}",
                            optionIdx,
                            choices.size(),
                            chestId,
                            extrapolated);
            return extrapolated;
        }

        Grasscutter.getLogger()
                .warn(
                        "TakeBP select: optionIdx {} out of range {} for chest {} (extrapolated {} missing)",
                        optionIdx,
                        choices.size(),
                        chestId,
                        extrapolated);
        return -1;
    }

    private static ItemUseData firstUse(ItemData chest) {
        if (chest.getItemUse() == null) {
            return null;
        }
        for (ItemUseData u : chest.getItemUse()) {
            if (u == null || u.getUseOp() == null) {
                continue;
            }
            if (u.getUseOp() == ItemUseOp.ITEM_USE_NONE) {
                continue;
            }
            return u;
        }
        return null;
    }
}
