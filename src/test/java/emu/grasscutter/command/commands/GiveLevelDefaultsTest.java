package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins what /give hands out when no level is asked for.
 *
 * <p>The level alone is not the whole story: ascension carries its own stat bonuses and gates the
 * passive talents, so an avatar given at 100 with the wrong promote level is a weak level 100. The
 * promote thresholds live apart from the default, which is what makes them worth pinning together
 * here - moving either one without the other is silent.
 */
public final class GiveLevelDefaultsTest {
    private static int constant(String name) throws Exception {
        Field field = GiveCommand.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    @Test
    @DisplayName("the default level is the cap, so a give needs no follow-up")
    public void defaultIsTheCap() throws Exception {
        assertEquals(100, constant("DEFAULT_LEVEL"));
        assertEquals(100, constant("MAX_LEVEL"));
    }

    @Test
    @DisplayName("an avatar at the default level gets full ascension")
    public void avatarAtDefaultIsFullyAscended() throws Exception {
        // 6 is the top of the promote tables; without it the character is missing both the
        // ascension stat bonuses and the passives gated behind them.
        assertEquals(6, Avatar.getMinPromoteLevel(constant("DEFAULT_LEVEL")));
    }

    @Test
    @DisplayName("a weapon at the default level gets full ascension")
    public void weaponAtDefaultIsFullyAscended() throws Exception {
        assertEquals(6, GameItem.getMinPromoteLevel(constant("DEFAULT_LEVEL")));
    }
}
