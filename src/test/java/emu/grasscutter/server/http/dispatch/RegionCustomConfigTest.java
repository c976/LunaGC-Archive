package emu.grasscutter.server.http.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cur_region regionCustomConfig plaintext must match AstaPS byte for byte.
 *
 * <p>The client decrypts this blob with a built-in public key. Any difference in key names, values
 * or ordering can change hot-update and resource-download behaviour, and when the blob does not
 * decode cleanly the client does not complain - it silently falls back to defaults. So the
 * plaintext is pinned here.
 */
public final class RegionCustomConfigTest {
    /** The literal hardcoded in AstaPS's RegionHandler. */
    private static final String ASTAPS_PLAINTEXT =
            "{\"sdkenv\":\"2\",\"checkdevice\":\"false\",\"loadPatch\":\"false\","
                    + "\"showexception\":\"false\",\"regionConfig\":\"pm\",\"downloadMode\":\"0\","
                    + "\"codeSwitch\":[4334],\"coverSwitch\":[40,41,42]}";

    @Test
    @DisplayName("plaintext matches AstaPS exactly, key order included")
    public void matchesAstaPs() throws Exception {
        Method m = RegionHandler.class.getDeclaredMethod("buildRegionCustomConfig");
        m.setAccessible(true);
        JsonObject config = (JsonObject) m.invoke(null);

        assertEquals(ASTAPS_PLAINTEXT, new Gson().toJson(config));
    }
}
