package emu.grasscutter.game.battlepass;

import static emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.GameConstants;
import org.junit.jupiter.api.Test;

class BattlePassPurchaseTest {
    @Test
    void purchaseQuotesUseCurrentLevelRatherThanRequestedLevel() {
        assertEquals(
                new BattlePassManager.LevelPurchase(5, 5 * GameConstants.BATTLE_PASS_LEVEL_PRICE),
                BattlePassManager.quoteLevelPurchase(10, 5));
        assertEquals(
                new BattlePassManager.LevelPurchase(2, 2 * GameConstants.BATTLE_PASS_LEVEL_PRICE),
                BattlePassManager.quoteLevelPurchase(48, 10));
        assertEquals(
                new BattlePassManager.LevelPurchase(50, 50 * GameConstants.BATTLE_PASS_LEVEL_PRICE),
                BattlePassManager.quoteLevelPurchase(0, Integer.MAX_VALUE));
    }

    @Test
    void invalidAndMaximumLevelPurchasesAreRejected() {
        var zero = new BattlePassManager.LevelPurchase(0, 0);
        assertEquals(zero, BattlePassManager.quoteLevelPurchase(50, 5));
        assertEquals(zero, BattlePassManager.quoteLevelPurchase(51, 5));
        assertEquals(zero, BattlePassManager.quoteLevelPurchase(-1, 5));
        assertEquals(zero, BattlePassManager.quoteLevelPurchase(10, 0));
        assertEquals(zero, BattlePassManager.quoteLevelPurchase(10, -10));
    }

    @Test
    void paidFlagAffectsActualSevenPointOneSchedule() {
        var bp = new BattlePassManager();
        assertFalse(bp.isPaid());

        var freeSchedule = SafeBattlePassSchedule.build(bp);
        assertEquals(BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_FREE, freeSchedule.getUnlockStatus());
        assertEquals(0, freeSchedule.getPaidPlatformFlags());

        bp.setPaid(true);
        assertTrue(bp.isPaid());
        var paidSchedule = SafeBattlePassSchedule.build(bp);
        assertEquals(BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID, paidSchedule.getUnlockStatus());
        assertEquals(3, paidSchedule.getPaidPlatformFlags());

        assertTrue(BattlePassCompatHelper.setPaidFlag(bp, false));
        assertFalse(bp.isPaid());
        assertFalse(BattlePassCompatHelper.setPaidFlag(null, true));
        assertEquals(
                BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_FREE,
                SafeBattlePassSchedule.build(bp).getUnlockStatus());
    }
}
