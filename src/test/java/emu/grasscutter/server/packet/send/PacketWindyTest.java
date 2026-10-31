package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public final class PacketWindyTest {
    @Test
    void packagedScriptsAreLoadedWithoutFallback() {
        assertTrue(PacketWindy.loadScript("fps").length > 0);
        assertTrue(PacketWindy.loadScript("GameSpeed/speed1.0").length > 0);
    }

    @Test
    void missingOrEmptyScriptCannotMasqueradeAsUid() {
        var missing = assertThrows(IllegalArgumentException.class,
                () -> PacketWindy.loadScript("definitely-missing-script"));
        assertTrue(missing.getMessage().contains("not found"));
        assertThrows(IllegalArgumentException.class, () -> PacketWindy.loadScript("../UID"));
        assertThrows(IllegalArgumentException.class, () -> PacketWindy.loadScript(""));
    }
}
