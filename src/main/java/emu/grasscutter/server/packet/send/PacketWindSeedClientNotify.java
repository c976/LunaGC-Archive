package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.WindSeedType1NotifyOuterClass.WindSeedType1Notify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PacketWindSeedClientNotify extends BasePacket {
    public static final int CMD = PacketOpcodes.WindSeedType1Notify;
    public static final int FIELD = WindSeedType1Notify.PAYLOAD_FIELD_NUMBER;
    /**
     * The message is `WindSeedType1Notify` - in 7.1 CmdId 29056, `{bytes payload = 5, uint32
     * config_id = 10}` (7.0 called it `_PlayerNormalLuaShellNotify`, CmdId 27286, payload 6).
     *
     * <p>It is NOT `WindSeedClientNotify`. That is a different message (a oneof carrying
     * area/refresh/wind-bullet arms), and pointing this at it is what crashed the client: its
     * `area_notify` arm really is executed, so a structurally wrong payload took the game down.
     * The payload here is a single flat bytes field, exactly as the original code had it.
     */
    static int cmdId() {
        int configured = Configuration.GAME_OPTIONS.watermark.cmdId;
        return configured > 0 ? configured : PacketOpcodes.WindSeedType1Notify;
    }

    /** `payload` on WindSeedType1Notify: 5 in 7.1 (7.0 had it at 6, 6.7 at 11, 6.6 at 8). */
    private static final int DEFAULT_PAYLOAD_FIELD = WindSeedType1Notify.PAYLOAD_FIELD_NUMBER;

    /**
     * True when the wind seed notify is switched off entirely - the safe setting if a CmdId hangs
     * the client. Only a NEGATIVE value disables it; 0 means "use the opcode the dump gives us",
     * which is the normal setting now that the number is known.
     */
    public static boolean disabled() {
        return Configuration.GAME_OPTIONS.watermark.cmdId < 0;
    }

    /** Encodes a payload at the configured field number. Shared by the other wind seed packets. */
    public static byte[] encode(byte[] luac) {
        return encode(luac, Configuration.GAME_OPTIONS.watermark.payloadField);
    }

    /**
     * Encodes a payload at an explicit field number, for sweeping candidates in one login.
     *
     * <p>The bytes do NOT sit at the top level. WindSeedClientNotify is a oneof, and the arm the
     * client runs is `area_notify` - so the payload is wrapped: field {@value #AREA_NOTIFY_FIELD}
     * holds a sub-message, and the Lua lives in `field` inside it. Writing the bytes at the top
     * level instead, which is what this did before, produces a message the client parses happily
     * and then ignores, so nothing appears and nothing errors.
     */
    private static final int AREA_NOTIFY_FIELD = 1;

    static byte[] encode(byte[] luac, int field) {
        if (field <= 0) field = DEFAULT_PAYLOAD_FIELD;

        var bos = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bos);
        try {
            out.writeByteArray(field, luac);
            out.flush();
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to encode the wind seed payload.", e);
        }
        return bos.toByteArray();
    }

    public PacketWindSeedClientNotify(String givenPath) {
        super(cmdId());
        final Path path = Paths.get(givenPath, new String[0]);
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (Exception e) {
            data = emu.grasscutter.utils.LuaShell.getLuaShell();
        }

        this.setData(encode(data));
    }

    public PacketWindSeedClientNotify(byte[] data) {
        super(cmdId());
        this.setData(encode(data));
    }

    /** One candidate of a sweep: an explicit CmdId and payload field rather than the configured pair. */
    public PacketWindSeedClientNotify(byte[] data, int cmdId, int payloadField) {
        super(cmdId);
        this.setData(encode(data, payloadField));
    }
}
