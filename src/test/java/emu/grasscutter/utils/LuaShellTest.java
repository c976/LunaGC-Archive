package emu.grasscutter.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the baked login shell.
 *
 * <p>The chunk stores its branding string with a single length byte. If that byte ever disagrees
 * with the bytes after it the client cannot load the chunk, and because this is the one payload the
 * client executes as Lua, a bad chunk takes the game down rather than being ignored. So the length
 * byte, the version placeholder and the absence of the build hash are all pinned here.
 */
public final class LuaShellTest {
    private static byte[] shell() throws Exception {
        Field f = LuaShell.class.getDeclaredField("luaShell");
        f.setAccessible(true);
        return (byte[]) f.get(null);
    }

    private static String asText(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("branding string length byte matches its contents")
    public void lengthByteAgrees() throws Exception {
        byte[] b = shell();
        String text = asText(b);

        int start = text.indexOf("<color=");
        assertTrue(start > 0, "no branding string in the shell");
        int end = text.indexOf("</color>", text.indexOf('|', start)) + "</color>".length();

        // Tag 20 then a single byte holding the content length plus the terminator.
        assertEquals(20, b[start - 2] & 0xFF, "string type tag");
        assertEquals(end - start + 1, b[start - 1] & 0xFF, "length byte");
    }

    @Test
    @DisplayName("keeps the UID prefix and the version placeholder, drops the build hash")
    public void contentIsAsExpected() throws Exception {
        String text = asText(shell());

        assertTrue(text.contains("UID:"), "the gsub pattern must survive");
        assertTrue(text.contains("0.0.0"), "version placeholder must be present for stamping");
        assertFalse(text.contains("_b47c23"), "the build hash placeholder must be gone");
    }

    @Test
    @DisplayName("stamping the version keeps the byte count identical")
    public void versionStampPreservesLength() throws Exception {
        // Stamping overwrites the placeholder in place, so it only ever finds its target once.
        // Snapshot the chunk and put it back afterwards, otherwise this test would decide what the
        // other tests in this class get to see.
        byte[] pristine = shell().clone();
        try {
            LuaShell.updateLuaShellWithGameVersion("7.0.0");
            byte[] after = shell();

            assertEquals(pristine.length, after.length, "stamping must not resize the chunk");
            assertTrue(asText(after).contains("7.0.0"), "version was not stamped in");
        } finally {
            System.arraycopy(pristine, 0, shell(), 0, pristine.length);
        }
    }
}
