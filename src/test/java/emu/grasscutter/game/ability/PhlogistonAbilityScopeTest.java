package emu.grasscutter.game.ability;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.GameConstants;
import emu.grasscutter.utils.Utils;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The client embryo sequence and server instanced-ability sequence share this filter. */
final class PhlogistonAbilityScopeTest {
    private static int[] defaultAbilities(boolean inNatlan) {
        return Arrays.stream(GameConstants.DEFAULT_ABILITY_HASHES)
                .filter(hash -> GameConstants.shouldAttachDefaultAvatarAbility(hash, inNatlan))
                .toArray();
    }

    @Test
    void onlyNatlanAttachesThePhlogistonGaugeAbility() {
        int hash = Utils.abilityHash("DynamicAbility_Phlogiston");
        assertEquals(1, Arrays.stream(GameConstants.DEFAULT_ABILITY_HASHES).filter(h -> h == hash).count());
        assertTrue(GameConstants.shouldAttachDefaultAvatarAbility(hash, true));
        assertFalse(GameConstants.shouldAttachDefaultAvatarAbility(hash, false));
        assertEquals(1, Arrays.stream(defaultAbilities(true)).filter(h -> h == hash).count());
        assertEquals(0, Arrays.stream(defaultAbilities(false)).filter(h -> h == hash).count());
    }

    @Test
    void outsideNatlanOnlyPhlogistonIsRemovedAndOtherAbilitiesKeepTheirOrder() {
        int hash = Utils.abilityHash("DynamicAbility_Phlogiston");
        int[] expected = Arrays.stream(GameConstants.DEFAULT_ABILITY_HASHES)
                .filter(id -> id != hash).toArray();
        assertArrayEquals(GameConstants.DEFAULT_ABILITY_HASHES, defaultAbilities(true));
        assertArrayEquals(expected, defaultAbilities(false));
    }
}
