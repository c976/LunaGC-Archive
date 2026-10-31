package emu.grasscutter.server.http.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import emu.grasscutter.utils.JsonUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the localisation bundles behind /admin/mi18n.
 *
 * <p>These carry the text on the SDK login screen. When the request is answered with an empty
 * bundle the client has nothing to render and prints the raw keys instead, which is what the
 * Android client did while plat_os returned "{}".
 */
public final class WebStaticBundleTest {
    /**
     * WebStaticVersionResponse resolves a request to a file by taking everything after the last
     * dash in the path, so this mirrors that rule rather than re-deriving it.
     */
    private static String bundleNameFrom(String requestPath) {
        return requestPath.substring(requestPath.lastIndexOf("-") + 1);
    }

    private static JsonObject loadBundle(String name) throws Exception {
        try (InputStream stream =
                WebStaticBundleTest.class.getResourceAsStream("/webstatic/" + name)) {
            assertNotNull(stream, "/webstatic/" + name + " is not on the classpath");
            return JsonUtils.decode(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    @Test
    @DisplayName("both mi18n paths resolve to the same bundle file")
    public void bothPlatformsResolveTheSameFile() {
        // The Android client asks under plat_os, the PC client under plat_oversea.
        var android = "/admin/mi18n/plat_os/mi18n/m202003048/m202003048-en.json";
        var pc = "/admin/mi18n/plat_oversea/mi18n/m202003048/m202003048-en.json";

        assertEquals("en.json", bundleNameFrom(android));
        assertEquals(bundleNameFrom(pc), bundleNameFrom(android));
    }

    @Test
    @DisplayName("the English bundle carries the login screen's strings")
    public void englishBundleHasLoginStrings() throws Exception {
        var bundle = loadBundle("en.json");

        // An empty object parses fine and serves a 200, which is exactly how this failed before:
        // the client rendered key names because the bundle had no entries.
        assertFalse(bundle.keySet().isEmpty(), "an empty bundle makes the client print raw keys");

        for (var key : new String[] {"account_login", "agree", "another_account"}) {
            assertTrue(bundle.has(key), "login screen string missing: " + key);
        }
    }

    @Test
    @DisplayName("every shipped language bundle has entries")
    public void everyBundleHasEntries() throws Exception {
        for (var language :
                new String[] {
                    "de.json", "en.json", "es.json", "fr.json", "id.json", "ja.json", "ko.json",
                    "pt.json", "ru.json", "th.json", "vi.json", "zh-cn.json", "zh-tw.json"
                }) {
            assertFalse(loadBundle(language).keySet().isEmpty(), language + " is empty");
        }
    }
}
