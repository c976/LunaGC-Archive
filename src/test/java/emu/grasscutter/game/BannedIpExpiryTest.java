package emu.grasscutter.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public final class BannedIpExpiryTest {
    @Test
    void legacyAndNewIndefiniteBansNeverExpire() {
        var legacy = new BannedIp();
        var indefinite = new BannedIp("127.0.0.1", "test");
        assertEquals(0, legacy.getEndTime());
        assertTrue(legacy.isActiveAt(Long.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, legacy.accountBanEndTime());
        assertEquals(0, indefinite.getEndTime());
        assertTrue(indefinite.isActiveAt(Long.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, indefinite.accountBanEndTime());
    }

    @Test
    void timedBanExpiresAtItsEpochSecond() {
        var timed = new BannedIp("127.0.0.1", "test", 2000000000);
        assertEquals(2000000000, timed.getEndTime());
        assertTrue(timed.isActiveAt(1999999999));
        assertFalse(timed.isActiveAt(2000000000));
        assertFalse(timed.isActiveAt(2000000001));
        assertEquals(2000000000, timed.accountBanEndTime());
    }
}
