package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import emu.grasscutter.game.inventory.GameItem;
import org.junit.jupiter.api.Test;

public final class GameItemPersistenceIdTest {
    @Test
    void firstWriteReservesStableItemId() {
        GameItem item = new GameItem();
        var first = item.ensurePersistenceId();
        assertNotNull(first);
        assertSame(first, item.ensurePersistenceId());
    }
}
