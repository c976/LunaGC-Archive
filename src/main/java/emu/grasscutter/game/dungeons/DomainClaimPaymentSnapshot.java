package emu.grasscutter.game.dungeons;

import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.game.dungeons.DomainStatueClaimHelper.ClaimMode;
import emu.grasscutter.game.managers.ResinManager;
import java.util.List;

/** Expected resource charges recorded with a claim, not verified account debits. */
final class DomainClaimPaymentSnapshot {
    private DomainClaimPaymentSnapshot() {}

    static List<RewardGrantJournal.Debit> debits(
            ClaimMode mode, int costId, int costCount, boolean resinUsage, int buyCount) {
        if (mode == null) throw new IllegalArgumentException("Missing claim mode");
        int base = costCount != 0 ? costCount : 20;
        return switch (mode) {
            case NORMAL_1X -> resinUsage && (costId == 0 || costId == 106)
                    ? List.of(new RewardGrantJournal.Debit(106, base > 0 ? base : 20))
                    : List.of();
            case NORMAL_2X -> resinUsage && (costId == 0 || costId == 106)
                    ? List.of(new RewardGrantJournal.Debit(106, 40))
                    : List.of();
            case CONDENSE -> resinUsage && base == 20
                    ? List.of(new RewardGrantJournal.Debit(
                            DomainStatueClaimHelper.CONDENSED_RESIN_ITEM_ID, 1))
                    : List.of();
            case FRAGILE -> List.of(new RewardGrantJournal.Debit(
                    DomainStatueClaimHelper.FRAGILE_RESIN_ITEM_ID, 1));
            case HCOIN -> {
                if (!resinUsage) yield List.of();
                if (buyCount < 0 || buyCount >= ResinManager.MAX_RESIN_BUYING_COUNT) {
                    throw new IllegalArgumentException("Invalid resin purchase step");
                }
                yield List.of(new RewardGrantJournal.Debit(
                        201, ResinManager.HCOIN_NUM_TO_BUY_RESIN[buyCount]));
            }
        };
    }
}
