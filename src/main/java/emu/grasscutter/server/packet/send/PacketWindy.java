package emu.grasscutter.server.packet.send;

import com.google.protobuf.ByteString;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.WindSeedType1NotifyOuterClass.WindSeedType1Notify;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class PacketWindy extends BasePacket {
    public PacketWindy(String script) {
        super(PacketOpcodes.WindSeedType1Notify);
        byte[] bytes = loadScript(script);
        this.setData(WindSeedType1Notify.newBuilder()
                .setPayload(ByteString.copyFrom(bytes))
                .build());
    }

    /** A missing script is an error, never an implicit fallback to the UID script. */
    static byte[] loadScript(String script) {
        if (script == null || script.isBlank() || script.startsWith("/")
                || script.contains("..") || script.contains("\\")) {
            throw new IllegalArgumentException("Invalid Windy script path: " + script);
        }

        String resource = "/lua/" + script + ".luac";
        try (InputStream input = PacketWindy.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Windy script not found: " + resource);
            }
            byte[] bytes = input.readAllBytes();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Windy script is empty: " + resource);
            }
            return bytes;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read Windy script: " + resource, exception);
        }
    }
}
