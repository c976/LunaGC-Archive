package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import org.junit.jupiter.api.Test;

final class DungeonRestartGuardTest {
    @Test
    void allowsClaimedTalentWeaponAndArtifactRunsWithoutRegionAllowlist() {
        for (var type : new DungeonSubType[] {
                DungeonSubType.DUNGEON_SUB_TALENT,
                DungeonSubType.DUNGEON_SUB_WEAPON,
                DungeonSubType.DUNGEON_SUB_RELIQUARY}) {
            assertTrue(DungeonRestartGuard.isEligible(
                    4471, 40771, 40771, type, false, true, true, 1, 0));
            assertTrue(DungeonRestartGuard.isEligible(
                    4311, 40501, 40501, type, false, true, true, 1, 0));
        }
    }

    @Test
    void rejectsUnfinishedUnclaimedOrMismatchedRequests() {
        var type = DungeonSubType.DUNGEON_SUB_WEAPON;
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                type, false, false, true, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                type, false, true, false, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40770,
                type, false, true, true, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                type, false, true, true, 1, 1));
        assertFalse(DungeonRestartGuard.isEligible(0, 40771, 40771,
                type, false, true, true, 1, 0));
    }

    @Test
    void preventsTowerCoopAndNonRewardDomainsFromUsingRetry() {
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                DungeonSubType.DUNGEON_SUB_NONE, false, true, true, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                DungeonSubType.DUNGEON_SUB_WEAPON, true, true, true, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                DungeonSubType.DUNGEON_SUB_WEAPON, false, true, true, 2, 0));
    }
}
