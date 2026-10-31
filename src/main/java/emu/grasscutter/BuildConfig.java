package emu.grasscutter;

import java.io.IOException;
import java.util.Properties;

public final class BuildConfig {
    public static final String VERSION = "7.1.0";
    public static final String GIT_HASH = readGitHash();
    public static final String BUILD_DATETIME = "2026-10-31T07:59:27Z";

    private BuildConfig() {}

    private static String readGitHash() {
        try (var stream = BuildConfig.class.getResourceAsStream("/META-INF/astaps-build.properties")) {
            if (stream == null) {
                return "GIT_NOT_FOUND";
            }
            var properties = new Properties();
            properties.load(stream);
            return properties.getProperty("gitHash", "GIT_NOT_FOUND");
        } catch (IOException ignored) {
            return "GIT_NOT_FOUND";
        }
    }
}
