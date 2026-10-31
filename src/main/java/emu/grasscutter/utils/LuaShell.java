package emu.grasscutter.utils;

import emu.grasscutter.GameConstants;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketWindSeedClientNotify;

import java.nio.charset.StandardCharsets;

/**
 * The compiled-Lua shell sent to the client at login, which rebrands the beta watermark.
 *
 * <p>Unlike a payload that overwrites the watermark wholesale, this chunk runs a {@code gsub} on the
 * existing text and replaces only the {@code "UID:"} prefix. The player's UID stays on screen and
 * only the label in front of it changes.
 *
 * <p>The built-in array is always what goes out; at startup its {@code 0.0.0} placeholder is
 * stamped with the running game version.
 */
public class LuaShell {
    /** Called once from main during startup. */
    public static void addLoginLuaShell() {
        Thread.startVirtualThread(LuaShell::getLoginLuaShell);
    }

    public static void getLoginLuaShell() {
        updateLuaShellWithGameVersion(GameConstants.VERSION);
    }

    public static void sendLuaShell(GameSession session, byte[] shell) {
        session.send(new PacketWindSeedClientNotify(shell));
    }

    public static void sendLoginLuaShell(GameSession session) {
        sendLuaShell(session, luaShell);
    }

    /** Returns the shell currently in use, for callers that build their own packet. */
    public static byte[] getLuaShell() {
        return luaShell;
    }

    /**
     * Replaces the {@code "0.0.0"} placeholder in the baked shell with the real game version.
     *
     * <p>The substitution is in place and must not change the byte count: the chunk stores the
     * string with a single length byte, so a longer or shorter version would leave that byte
     * disagreeing with the bytes after it and the client could not load the chunk. Versions shorter
     * than five characters are padded and longer ones truncated for exactly that reason.
     *
     * @param gameVersion game version such as "7.0.0"
     */
    public static void updateLuaShellWithGameVersion(String gameVersion) {
        if (gameVersion == null) return;
        if (gameVersion.length() < 5) gameVersion += "00000";

        byte[] placeholder = "0.0.0".getBytes(StandardCharsets.UTF_8);
        byte[] verBytes = gameVersion.substring(0, 5).getBytes(StandardCharsets.UTF_8);
        int versionOffset = indexOf(luaShell, placeholder);
        if (versionOffset >= 0) {
            System.arraycopy(verBytes, 0, luaShell, versionOffset, placeholder.length);
        }
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        for (int i = 0; i <= data.length - pattern.length; i++) {
            int j = 0;
            while (j < pattern.length && data[i + j] == pattern[j]) j++;
            if (j == pattern.length) return i;
        }
        return -1;
    }

    /*
     * Equivalent Lua source:
     *
     * uidobj = CS.UnityEngine.GameObject.Find("/BetaWatermarkCanvas(Clone)/Panel/TxtUID")
     *              :GetComponent("Text")
     * uid = uidobj.text
     * uid = uid:gsub("UID:", "<coloured AstaPS prefix> <separator>")
     * uidobj.text = "<colour>" .. uid .. "</color>"
     *
     * The prefix is a pink gradient across "AstaPS" followed by the game version, which is stamped
     * over the "0.0.0" placeholder at startup. The build-hash segment the upstream shell carried has
     * been removed.
     */
    static volatile byte[] luaShell = {
        27, 76, 117, 97, 83, 1, 25, -109, 13, 10, 26, 10, 4, 4, 8, 8, 120, 86, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 40, 119, 64, 1, 27, 64, 67, 58, 92, 66, 92,
        80, 83, 92, 108, 117, 97, 92, 98, 117, 105, 108, 100, 92, 117, 105, 100, 46, 108, 117, 97,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 4, 26, 0, 0, 0, 36, 64, 64, 0, 41,
        -128, 64, 0, 41, -64, 64, 0, 41, 0, 65, 0, 86, 64, 1, 0, 44, -128, 0, 1, 29,
        -128, 65, 0, -106, -64, 1, 0, 44, -128, -128, 1, 34, 0, 0, -128, 36, 0, 64, 0, 41,
        64, 66, 0, 34, 0, 0, -124, 36, 0, 66, 0, 29, -128, 66, 0, -106, -64, 2, 0, -42,
        0, 3, 0, 44, -128, 0, 2, 34, 0, 0, -124, 36, 0, 64, 0, 86, 64, 3, 0, -92,
        0, 66, 0, -42, -128, 3, 0, 80, -64, -128, 0, 31, 64, -128, -124, 25, 0, -128, 0, 15,
        0, 0, 0, 4, 7, 117, 105, 100, 111, 98, 106, 4, 3, 67, 83, 4, 12, 85, 110, 105,
        116, 121, 69, 110, 103, 105, 110, 101, 4, 11, 71, 97, 109, 101, 79, 98, 106, 101, 99, 116,
        4, 5, 70, 105, 110, 100, 4, 41, 47, 66, 101, 116, 97, 87, 97, 116, 101, 114, 109, 97,
        114, 107, 67, 97, 110, 118, 97, 115, 40, 67, 108, 111, 110, 101, 41, 47, 80, 97, 110, 101,
        108, 47, 84, 120, 116, 85, 73, 68, 4, 13, 71, 101, 116, 67, 111, 109, 112, 111, 110, 101,
        110, 116, 4, 5, 84, 101, 120, 116, 4, 4, 117, 105, 100, 4, 5, 116, 101, 120, 116, 4,
        5, 103, 115, 117, 98, 4, 5, 85, 73, 68, 58, 20, -57, 60, 99, 111, 108, 111, 114, 61,
        35, 102, 102, 55, 97, 99, 56, 62, 65, 60, 47, 99, 111, 108, 111, 114, 62, 60, 99, 111,
        108, 111, 114, 61, 35, 102, 102, 56, 54, 99, 102, 62, 115, 60, 47, 99, 111, 108, 111, 114,
        62, 60, 99, 111, 108, 111, 114, 61, 35, 102, 102, 57, 50, 100, 54, 62, 116, 60, 47, 99,
        111, 108, 111, 114, 62, 60, 99, 111, 108, 111, 114, 61, 35, 102, 102, 57, 101, 100, 100, 62,
        97, 60, 47, 99, 111, 108, 111, 114, 62, 60, 99, 111, 108, 111, 114, 61, 35, 102, 102, 99,
        49, 102, 50, 62, 80, 83, 60, 47, 99, 111, 108, 111, 114, 62, 60, 99, 111, 108, 111, 114,
        61, 35, 102, 102, 99, 49, 102, 50, 62, 95, 60, 47, 99, 111, 108, 111, 114, 62, 60, 99,
        111, 108, 111, 114, 61, 35, 102, 102, 99, 49, 102, 50, 62, 48, 46, 48, 46, 48, 60, 47,
        99, 111, 108, 111, 114, 62, 32, 60, 99, 111, 108, 111, 114, 61, 35, 102, 102, 57, 101, 100,
        100, 62, 124, 60, 47, 99, 111, 108, 111, 114, 62, 4, 16, 60, 99, 111, 108, 111, 114, 61,
        35, 102, 102, 99, 49, 102, 50, 62, 4, 9, 60, 47, 99, 111, 108, 111, 114, 62, 1, 0,
        0, 0, 1, 0, 0, 0, 0, 0, 26, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0,
        1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0,
        1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0,
        2, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0,
        3, 0, 0, 0, 3, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0,
        4, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0,
        1, 0, 0, 0, 5, 95, 69, 78, 86
    };
}
