package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.server.game.GameSession.SessionState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import sun.misc.Unsafe;

@ExtendWith(ServerResourceFixture.class)
@ResourceLock("grasscutter-logger")
public final class GameServerPacketHandlerLoggingTest {
    private static final int UNKNOWN_OPCODE = 65535;
    private static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 54321);
    private static final AtomicInteger HEX_FORMAT_CALLS = new AtomicInteger();
    private static final AtomicInteger LEGACY_FORMAT_CALLS = new AtomicInteger();

    private Logger logger;
    private Level previousLevel;
    private boolean previousAdditive;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void captureLogs() {
        logger = Grasscutter.getLogger();
        previousLevel = logger.getLevel();
        previousAdditive = logger.isAdditive();
        logger.setAdditive(false);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        HEX_FORMAT_CALLS.set(0);
        LEGACY_FORMAT_CALLS.set(0);
        assertFalse(PacketOpcodesUtils.LOOP_PACKETS.contains(UNKNOWN_OPCODE));
    }

    @AfterEach
    void restoreLogs() {
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditive);
    }

    @Test
    void debugDisabledSkipsHexFormattingAndAddressReads() throws Exception {
        logger.setLevel(Level.INFO);
        var handler = instrumentedHandler();
        var session = session();
        var handle = handler.getClass().getMethod(
                "handle", GameSession.class, int.class, byte[].class, byte[].class);

        for (byte[] payload : new byte[][] {null, new byte[0], new byte[1], new byte[128], new byte[129]}) {
            handle.invoke(handler, session, UNKNOWN_OPCODE, null, payload);
        }

        assertEquals(0, HEX_FORMAT_CALLS.get());
        assertEquals(0, LEGACY_FORMAT_CALLS.get());
        assertEquals(0, session.addressReads);
        assertTrue(events.list.isEmpty());
    }

    @Test
    void debugEnabledLogsLegacyLowercaseHexWithoutChangingPayload() throws Exception {
        logger.setLevel(Level.DEBUG);
        var handler = handler();
        var session = session();
        byte[] payload = {0, 1, 15, 16, 127, (byte) 128, (byte) 255};
        byte[] unchanged = payload.clone();

        handler.handle(session, UNKNOWN_OPCODE, null, payload);

        assertLog(payload.length, " hex=00010f107f80ff");
        assertArrayEquals(unchanged, payload);
        assertEquals(1, session.addressReads);
    }

    @Test
    void enabledDebugUsesOneStandardHexConversionForWholePayload() throws Exception {
        logger.setLevel(Level.DEBUG);
        var handler = instrumentedHandler();
        var session = session();
        var handle = handler.getClass().getMethod(
                "handle", GameSession.class, int.class, byte[].class, byte[].class);
        byte[] payload = new byte[128];

        handle.invoke(handler, session, UNKNOWN_OPCODE, null, payload);

        assertEquals(1, HEX_FORMAT_CALLS.get());
        assertEquals(0, LEGACY_FORMAT_CALLS.get());
        assertLog(payload.length, " hex=" + "00".repeat(payload.length));
        assertEquals(1, session.addressReads);
    }

    @Test
    void debugEnabledFormatsInclusiveOneAnd128ByteBoundaries() throws Exception {
        logger.setLevel(Level.DEBUG);
        var handler = handler();
        var session = session();
        for (int length : new int[] {1, 128}) {
            byte[] payload = new byte[length];
            for (int index = 0; index < length; index++) payload[index] = (byte) (index + 128);
            events.list.clear();

            handler.handle(session, UNKNOWN_OPCODE, null, payload);

            assertLog(length, " hex=" + legacyHex(payload));
        }
        assertEquals(2, session.addressReads);
    }

    @Test
    void debugEnabledOmitsHexForNullEmptyAndOversizePayloads() throws Exception {
        logger.setLevel(Level.DEBUG);
        var handler = handler();
        var session = session();
        for (byte[] payload : new byte[][] {null, new byte[0], new byte[129]}) {
            events.list.clear();

            handler.handle(session, UNKNOWN_OPCODE, null, payload);

            assertLog(payload == null ? 0 : payload.length, "");
        }
        assertEquals(3, session.addressReads);
    }

    @Test
    void pingAndLoopPacketsStillSkipUnknownPacketLogging() throws Exception {
        logger.setLevel(Level.DEBUG);
        var handler = handler();
        var session = session();

        handler.handle(session, PacketOpcodes.PingReq, null, new byte[128]);
        handler.handle(session, PacketOpcodes.PingRsp, null, new byte[128]);
        for (int opcode : PacketOpcodesUtils.LOOP_PACKETS) {
            handler.handle(session, opcode, null, new byte[128]);
        }

        assertEquals(0, session.addressReads);
        assertTrue(events.list.isEmpty());
    }

    @Test
    void registeredHandlerStillReceivesOriginalHeaderAndEventPayload() throws Exception {
        logger.setLevel(Level.DEBUG);
        var handler = handler();
        var session = session();
        session.setState(SessionState.ACTIVE);
        byte[] header = {1, 2};
        byte[] payload = {3, 4};
        var receivedHeader = new java.util.concurrent.atomic.AtomicReference<byte[]>();
        var receivedPayload = new java.util.concurrent.atomic.AtomicReference<byte[]>();
        var handlers = new Int2ObjectOpenHashMap<PacketHandler>();
        handlers.put(UNKNOWN_OPCODE, new PacketHandler() {
            @Override
            public void handle(GameSession receivedSession, byte[] incomingHeader, byte[] incomingPayload) {
                assertSame(session, receivedSession);
                receivedHeader.set(incomingHeader);
                receivedPayload.set(incomingPayload);
            }
        });
        setHandlers(handler, handlers);

        handler.handle(session, UNKNOWN_OPCODE, header, payload);

        assertSame(header, receivedHeader.get());
        assertSame(payload, receivedPayload.get());
        assertEquals(0, session.addressReads);
        assertTrue(events.list.isEmpty());
    }

    private void assertLog(int length, String hex) {
        assertEquals(1, events.list.size());
        var event = events.list.get(0);
        assertEquals(Level.DEBUG, event.getLevel());
        assertEquals("Unhandled packet opcode {} ({}) len={} from {}{}", event.getMessage());
        assertArrayEquals(new Object[] {
                UNKNOWN_OPCODE, PacketOpcodesUtils.getOpcodeName(UNKNOWN_OPCODE), length, ADDRESS, hex
        }, event.getArgumentArray());
        assertEquals(
                "Unhandled packet opcode " + UNKNOWN_OPCODE + " ("
                        + PacketOpcodesUtils.getOpcodeName(UNKNOWN_OPCODE) + ") len=" + length
                        + " from " + ADDRESS + hex,
                event.getFormattedMessage());
    }

    private static String legacyHex(byte[] payload) {
        var hex = new StringBuilder(payload.length * 2);
        for (byte value : payload) hex.append(String.format("%02x", value));
        return hex.toString();
    }

    private static GameServerPacketHandler handler() throws Exception {
        var handler = allocate(GameServerPacketHandler.class);
        setHandlers(handler, new Int2ObjectOpenHashMap<PacketHandler>());
        return handler;
    }

    private static RecordingSession session() throws Exception {
        return allocate(RecordingSession.class);
    }

    private static void setHandlers(Object handler, Int2ObjectOpenHashMap<PacketHandler> handlers)
            throws Exception {
        Field field = handler.getClass().getDeclaredField("handlers");
        field.setAccessible(true);
        field.set(handler, handlers);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    // Count the real handler's format calls without adding a production-only test hook.
    private static Object instrumentedHandler() throws Exception {
        String name = GameServerPacketHandler.class.getName();
        var writer = new ClassWriter(0);
        try (var source = GameServerPacketHandler.class.getResourceAsStream(
                "/" + name.replace('.', '/') + ".class")) {
            assertNotNull(source);
            new ClassReader(source).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(
                        int access, String methodName, String descriptor, String signature,
                        String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9,
                            super.visitMethod(access, methodName, descriptor, signature, exceptions)) {
                        @Override
                        public void visitMethodInsn(
                                int opcode, String owner, String invokedName, String descriptor,
                                boolean isInterface) {
                            String hookOwner = GameServerPacketHandlerLoggingTest.class.getName()
                                    .replace('.', '/');
                            if (owner.equals("java/util/HexFormat") && invokedName.equals("formatHex")
                                    && descriptor.equals("([B)Ljava/lang/String;")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, hookOwner, "countHexFormat",
                                        "(Ljava/util/HexFormat;[B)Ljava/lang/String;", false);
                            } else if (owner.equals("java/lang/String") && invokedName.equals("format")
                                    && descriptor.equals(
                                            "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, hookOwner, "countLegacyFormat",
                                        descriptor, false);
                            } else {
                                super.visitMethodInsn(opcode, owner, invokedName, descriptor, isInterface);
                            }
                        }
                    };
                }
            }, 0);
        }
        var loader = new ClassLoader(GameServerPacketHandlerLoggingTest.class.getClassLoader()) {
            Class<?> handlerClass() {
                byte[] bytes = writer.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        var handler = allocate(loader.handlerClass());
        setHandlers(handler, new Int2ObjectOpenHashMap<PacketHandler>());
        return handler;
    }

    public static String countHexFormat(HexFormat format, byte[] payload) {
        HEX_FORMAT_CALLS.incrementAndGet();
        return format.formatHex(payload);
    }

    public static String countLegacyFormat(String format, Object[] arguments) {
        LEGACY_FORMAT_CALLS.incrementAndGet();
        return String.format(format, arguments);
    }

    private static final class RecordingSession extends GameSession {
        private int addressReads;

        private RecordingSession() {
            super(null);
        }

        @Override
        public InetSocketAddress getAddress() {
            addressReads++;
            return ADDRESS;
        }
    }
}
