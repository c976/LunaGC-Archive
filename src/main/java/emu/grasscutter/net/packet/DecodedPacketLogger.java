package emu.grasscutter.net.packet;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.SERVER;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.game.GameSession;
import java.lang.reflect.Method;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DecodedPacketLogger {
    private static final String PROTO_PACKAGE = "emu.grasscutter.net.proto.";
    private static final Map<String, Optional<Method>> PARSER_CACHE = new ConcurrentHashMap<>();

    private DecodedPacketLogger() {}

    /**
     * Log a packet independently of whether the server has a handler for its opcode.
     * Payload decoding is optional, and undecodable bytes remain available for debugging.
     */
    public static void logDecodedPacket(GameSession session, String direction, int opcode,
            byte[] payload, PacketHandler handler) {
        if (payload == null || !shouldDump(opcode)) {
            return;
        }

        String name = PacketOpcodesUtils.getOpcodeName(opcode);
        String handlerLabel = handler == null ? "" : " via " + handler.getClass().getSimpleName();
        Grasscutter.getLogger().info("{}: {} ({}){}", direction, name, opcode, handlerLabel);

        if (GAME_INFO.isShowPacketPayload) {
            Grasscutter.getLogger().info("{}", formatPayload(opcode, payload, handler));
        }
    }

    static String formatPayload(int opcode, byte[] payload, PacketHandler handler) {
        var parser = findParser(opcode, handler);
        if (parser.isPresent()) {
            try {
                Object result = parser.get().invoke(null, (Object) payload);
                if (result instanceof Message message) {
                    return message.toString();
                }
            } catch (ReflectiveOperationException ignored) {
                // Preserve the wire bytes if this payload cannot be decoded as its named message.
            }
        }

        var raw = new StringBuilder("payload=").append(HexFormat.of().formatHex(payload));
        try {
            raw.append(", rawFields=").append(UnknownFieldSet.parseFrom(payload));
        } catch (InvalidProtocolBufferException ignored) {
            raw.append(" (non-protobuf payload)");
        }
        return raw.toString();
    }

    static Optional<Method> findParser(int opcode, PacketHandler handler) {
        // Opcode names reflect the packet on the wire; handlers may use compatibility suffixes.
        var parser = parserForName(PacketOpcodesUtils.getOpcodeName(opcode));
        if (parser.isPresent() || handler == null) {
            return parser;
        }
        return parserForName(handler.getClass().getSimpleName().replaceFirst("^Handler", ""));
    }

    private static Optional<Method> parserForName(String messageName) {
        if (messageName == null || messageName.isBlank() || messageName.equals("UNKNOWN")) {
            return Optional.empty();
        }

        String className = PROTO_PACKAGE + messageName + "OuterClass$" + messageName;
        return PARSER_CACHE.computeIfAbsent(className, ignored -> {
            try {
                Class<?> type = Class.forName(className);
                if (!Message.class.isAssignableFrom(type)) {
                    return Optional.empty();
                }
                return Optional.of(type.getMethod("parseFrom", byte[].class));
            } catch (ReflectiveOperationException e) {
                return Optional.empty();
            }
        });
    }

    static boolean shouldDump(int opcode) {
        if (PacketOpcodes.BANNED_PACKETS.contains(opcode)) {
            return false;
        }
        return switch (GAME_INFO.logPackets) {
            case ALL -> !PacketOpcodesUtils.LOOP_PACKETS.contains(opcode)
                    || GAME_INFO.isShowLoopPackets;
            case WHITELIST -> SERVER.debugWhitelist.contains(opcode);
            case BLACKLIST -> !SERVER.debugBlacklist.contains(opcode);
            default -> false;
        };
    }
}
