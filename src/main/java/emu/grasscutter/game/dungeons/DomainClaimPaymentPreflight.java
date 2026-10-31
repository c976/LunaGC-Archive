package emu.grasscutter.game.dungeons;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DomainStatueClaimHelper.ClaimMode;
import emu.grasscutter.game.managers.ResinManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;

/** Preflight a claim before writing a receipt; actual payment rechecks balances. */
final class DomainClaimPaymentPreflight {
    private DomainClaimPaymentPreflight() {}

    static boolean canPay(Player player, DungeonData data, ClaimMode mode) {
        if (data == null) return false;
        return canPay(player, data, mode, data.getStatueCostID(), data.getStatueCostCount());
    }

    static boolean canPay(
            Player player, DungeonData data, ClaimMode mode, int costId, int costCount) {
        if (player == null || data == null || mode == null) return false;
        return affordable(
                mode, costId, costCount,
                GAME_OPTIONS.resinOptions.resinUsage,
                player.getProperty(PlayerProperty.PROP_PLAYER_RESIN),
                player.getInventory().getItemCountById(
                        DomainStatueClaimHelper.CONDENSED_RESIN_ITEM_ID),
                player.getInventory().getItemCountById(
                        DomainStatueClaimHelper.FRAGILE_RESIN_ITEM_ID),
                player.getResinBuyCount(), player.getPrimogems());
    }

    static boolean affordable(
            ClaimMode mode, int costId, int costCount, boolean resinUsage,
            int resin, int condensed, int fragile, int resinBuyCount, int primogems) {
        if (mode == null) return false;
        int base = costCount != 0 ? costCount : 20;
        return switch (mode) {
            case NORMAL_1X -> !resinUsage || (costId != 0 && costId != 106)
                    || resin >= (base > 0 ? base : 20);
            case NORMAL_2X -> !resinUsage || (costId != 0 && costId != 106)
                    || resin >= 40;
            case CONDENSE -> base == 20 && (!resinUsage || condensed >= 1);
            // The existing FRAGILE claim charges a material even when resin usage
            // is disabled; preserve that behavior in preflight.
            case FRAGILE -> fragile >= 1;
            case HCOIN -> !resinUsage || (resinBuyCount >= 0
                    && resinBuyCount < ResinManager.MAX_RESIN_BUYING_COUNT
                    && primogems >= ResinManager.HCOIN_NUM_TO_BUY_RESIN[resinBuyCount]);
        };
    }
}
