package emu.grasscutter.server.packet.send;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The compiled Lua chunk that writes a custom text into the UID watermark.
 *
 * <p>It sets {@code /BetaWatermarkCanvas(Clone)/Panel/TxtUID}'s text; the string constant sits between
 * the two fixed halves, preceded by its length byte (length + 1, as Lua 5.3 dumps short strings).
 */
public final class UidWatermarkScript {
    private static final byte[] PRE = bytes(
            27, 76, 117, 97, 83, 1, 25, -109, 13, 10, 26, 10, 4, 4, 8, 8, 120, 86, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 40, 119, 64, 1, 21, 64, 67, 58, 92, 87, 105, 110, 100, 121, 92, 76, 117, 110, 97, 71,
            67, 46, 108, 117, 97, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 11, 0, 0, 0, 36, 0, 64, 0, 41, 64, 64,
            0, 41, -128, 64, 0, 41, -64, 64, 0, 86, 0, 1, 0, 44, -128, 0, 1, 29, 64, 65, 0, -106, -128, 1,
            0, 44, -128, -128, 1, 31, 0, -62, -125, 25, 0, -128, 0, 9, 0, 0, 0, 4, 3, 67, 83, 4, 12, 85,
            110, 105, 116, 121, 69, 110, 103, 105, 110, 101, 4, 11, 71, 97, 109, 101, 79, 98, 106, 101, 99,
            116, 4, 5, 70, 105, 110, 100, 4, 41, 47, 66, 101, 116, 97, 87, 97, 116, 101, 114, 109, 97, 114,
            107, 67, 97, 110, 118, 97, 115, 40, 67, 108, 111, 110, 101, 41, 47, 80, 97, 110, 101, 108, 47,
            84, 120, 116, 85, 73, 68, 4, 13, 71, 101, 116, 67, 111, 109, 112, 111, 110, 101, 110, 116, 4,
            5, 84, 101, 120, 116, 4, 5, 116, 101, 120, 116, 4);
    private static final byte[] POST = bytes(
            1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 11, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1,
            0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1,
            0, 0, 0, 5, 95, 69, 78, 86);

    /** A Lua short string's length byte holds length + 1, so the text tops out at 254 bytes. */
    public static final int MAX_TEXT_BYTES = 0xFF - 1;

    private UidWatermarkScript() {}

    /** The chunk for {@code text}, or null when its UTF-8 form is too long for a short string. */
    public static byte[] forText(String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        if (textBytes.length > MAX_TEXT_BYTES) return null;
        var out = new ByteArrayOutputStream(PRE.length + 1 + textBytes.length + POST.length);
        out.writeBytes(PRE);
        out.write(textBytes.length + 1);
        out.writeBytes(textBytes);
        out.writeBytes(POST);
        return out.toByteArray();
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }
}
