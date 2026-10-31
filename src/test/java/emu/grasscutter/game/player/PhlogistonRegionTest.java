package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.game.world.Position;
import org.junit.jupiter.api.Test;

final class PhlogistonRegionTest {
    @Test
    void natlanSharesTeyvatSceneThree() {
        assertTrue(PhlogistonRegion.contains(3, new Position(-2300, 300, 8400)));
        assertTrue(PhlogistonRegion.contains(3, new Position(-1800, 200, 7800)));
        assertTrue(PhlogistonRegion.contains(101, new Position(0, 0, 0)));
    }

    @Test
    void otherRegionsAndDomainsDoNotGetTheGauge() {
        assertFalse(PhlogistonRegion.contains(3, new Position(2747, 194, -1719)));
        assertFalse(PhlogistonRegion.contains(3, new Position(2800, 350, 8200)));
        assertFalse(PhlogistonRegion.contains(4, new Position(-2300, 300, 8400)));
        assertFalse(PhlogistonRegion.contains(3, null));
    }
}
