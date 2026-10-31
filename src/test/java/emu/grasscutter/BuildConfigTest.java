package emu.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class BuildConfigTest {
    @Test
    void revisionIsLoadedFromBundledResource() throws IOException {
        try (var stream = BuildConfig.class.getResourceAsStream("/META-INF/astaps-build.properties")) {
            assertNotNull(stream, "The application classpath must include build metadata");
            var properties = new Properties();
            properties.load(stream);
            var revision = properties.getProperty("gitHash");
            assertNotNull(revision);
            assertFalse(revision.isBlank());
            assertEquals(revision, BuildConfig.GIT_HASH);
            assertEquals("7.1.0", BuildConfig.VERSION);
        }
    }
}
