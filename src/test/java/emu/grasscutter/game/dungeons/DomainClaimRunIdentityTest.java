package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class DomainClaimRunIdentityTest {
    @Test
    void oneDungeonRunHasStableIdentity() {
        var id = new DomainClaimRunIdentity();
        String first = id.operationId(4473);
        assertEquals(first, id.operationId(4473));
        assertTrue(first.startsWith("4473."));
        assertNotEquals(first, new DomainClaimRunIdentity().operationId(4473));
    }

    @Test
    void restartingReplacesTheClaimIdentity() {
        var id = new DomainClaimRunIdentity();
        String previous = id.operationId(4473);
        id.restart();
        assertNotEquals(previous, id.operationId(4473));
        assertEquals(id.operationId(4473), id.operationId(4473));
    }

    @Test
    void operationIdIncludesDungeonAndRejectsInvalidIds() {
        var id = new DomainClaimRunIdentity();
        assertNotEquals(id.operationId(4470), id.operationId(4473));
        assertThrows(IllegalArgumentException.class, () -> id.operationId(0));
    }
}
