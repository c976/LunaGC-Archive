package emu.grasscutter.net.packet;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.SERVER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.net.proto.EvtBulletDeactiveNotifyOuterClass.EvtBulletDeactiveNotify;
import emu.grasscutter.server.packet.recv.HandlerUnionCmdNotify;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DecodedPacketLoggerTest {
    @Test
    void decodesByOpcodeEvenWhenHandlerHasAnotherName() {
        byte[] payload = EvtBulletDeactiveNotify.newBuilder()
                .setEntityId(42).build().toByteArray();
        String rendered = DecodedPacketLogger.formatPayload(
                PacketOpcodes.EvtBulletDeactiveNotify, payload, new HandlerUnionCmdNotify());
        assertTrue(rendered.contains("entity_id: 42"));
        assertFalse(rendered.contains("payload="));
    }

    @Test
    void unknownOpcodeWithoutHandlerRetainsWireFieldsAndHex() {
        String rendered = DecodedPacketLogger.formatPayload(987654, new byte[] {8, 5}, null);
        assertTrue(rendered.contains("payload=0805"));
        assertTrue(rendered.contains("rawFields="));
        assertTrue(rendered.contains("1: 5"));
    }

    @Test
    void malformedNamedPayloadRetainsItsOriginalBytes() {
        String rendered = DecodedPacketLogger.formatPayload(
                PacketOpcodes.EvtBulletDeactiveNotify, new byte[] {(byte) 0x80}, null);
        assertTrue(rendered.contains("payload=80"));
        assertTrue(rendered.contains("non-protobuf payload"));
    }

    @Test
    void packetLoggingFiltersStillApplyToUnrecognizedOpcodes() {
        var oldMode = GAME_INFO.logPackets;
        var oldLoops = GAME_INFO.isShowLoopPackets;
        var oldWhitelist = SERVER.debugWhitelist;
        var oldBlacklist = SERVER.debugBlacklist;
        try {
            GAME_INFO.isShowLoopPackets = false;
            GAME_INFO.logPackets = ServerDebugMode.ALL;
            assertTrue(DecodedPacketLogger.shouldDump(987654));
            assertFalse(DecodedPacketLogger.shouldDump(PacketOpcodes.PingReq));
            assertFalse(DecodedPacketLogger.shouldDump(
                    PacketOpcodes.WorldOwnerBlossomScheduleInfoNotify));

            SERVER.debugWhitelist = Set.of(987654);
            GAME_INFO.logPackets = ServerDebugMode.WHITELIST;
            assertTrue(DecodedPacketLogger.shouldDump(987654));
            assertFalse(DecodedPacketLogger.shouldDump(123456));

            SERVER.debugBlacklist = Set.of(987654);
            GAME_INFO.logPackets = ServerDebugMode.BLACKLIST;
            assertFalse(DecodedPacketLogger.shouldDump(987654));
            assertTrue(DecodedPacketLogger.shouldDump(123456));
        } finally {
            GAME_INFO.logPackets = oldMode;
            GAME_INFO.isShowLoopPackets = oldLoops;
            SERVER.debugWhitelist = oldWhitelist;
            SERVER.debugBlacklist = oldBlacklist;
        }
    }
}
