/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.born;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class BornDataConfig {
    private static final Path CONFIG_PATH = Path.of("config-born.json", new String[0]);
    private static volatile String mode = "auto";
    private static volatile int avatarId = 10000007;
    private static volatile boolean randomGender = true;
    private static volatile String nickname = "";

    private BornDataConfig() {
    }

    public static void reload() {
        if (!Files.isRegularFile(CONFIG_PATH, new LinkOption[0])) {
            return;
        }
        try {
            String string = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseString(string).getAsJsonObject();
            if (jsonObject.has("mode")) {
                mode = jsonObject.get("mode").getAsString();
            }
            if (jsonObject.has("avatarId")) {
                avatarId = jsonObject.get("avatarId").getAsInt();
            }
            if (jsonObject.has("randomGender")) {
                randomGender = jsonObject.get("randomGender").getAsBoolean();
            }
            if (jsonObject.has("nickname")) {
                nickname = jsonObject.get("nickname").getAsString();
            }
            Grasscutter.getLogger().info("Born-data config: mode={} randomGender={} avatarId={} nickname={}", mode, randomGender, randomGender ? "(random)" : Integer.valueOf(avatarId), nickname == null || nickname.isBlank() ? "(account name)" : nickname);
        }
        catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to read config-born.json, using defaults", exception);
        }
    }

    public static boolean isSelectionMode() {
        return "select".equalsIgnoreCase(mode);
    }

    public static boolean isRandomGender() {
        return randomGender;
    }

    public static int getAvatarId() {
        return avatarId;
    }

    public static String getNickname(String string) {
        return nickname == null || nickname.isBlank() ? string : nickname;
    }

    static {
        BornDataConfig.reload();
    }
}
