package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.command.Command;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class ProtoCommandTest {
    @Test
    void exposesRuntimePacketLoggingControls() {
        var command = new ProtoCommand().createCommandLine(null, null);

        assertEquals(Set.of("mode", "payload", "loops", "whitelist", "blacklist", "info"),
                command.getSubcommands().keySet());
        assertEquals("grasscutter.command.debug", ProtoCommand.class.getAnnotation(Command.class).permission());
        assertDoesNotThrow(() -> command.parseArgs());
        assertDoesNotThrow(() -> command.parseArgs("mode", "whitelist"));
        assertDoesNotThrow(() -> command.parseArgs("payload", "on"));
        assertDoesNotThrow(() -> command.parseArgs("loops", "off"));
        assertDoesNotThrow(() -> command.parseArgs("whitelist", "add", "123"));
        assertDoesNotThrow(() -> command.parseArgs("blacklist", "remove", "123"));
        assertDoesNotThrow(() -> command.parseArgs("info", "27581"));
    }

    @Test
    void filtersRequireAnActionAndOpcode() {
        var command = new ProtoCommand().createCommandLine(null, null);

        assertThrows(CommandLine.ParameterException.class, () -> command.parseArgs("whitelist", "add"));
        assertThrows(CommandLine.ParameterException.class, () -> command.parseArgs("blacklist", "remove", "abc"));
    }

    @Test
    void commandsUpdateTheLivePacketSettings() {
        var previousMode = GAME_INFO.logPackets;
        var previousPayload = GAME_INFO.isShowPacketPayload;
        var previousLoops = GAME_INFO.isShowLoopPackets;
        var previousWhitelist = SERVER.debugWhitelist;
        var previousBlacklist = SERVER.debugBlacklist;

        try {
            SERVER.debugWhitelist = Set.of();
            SERVER.debugBlacklist = Set.of();
            var command = new ProtoCommand().createCommandLine(null, null);
            command.execute("mode", "whitelist");
            command.execute("payload", "on");
            command.execute("loops", "on");
            command.execute("whitelist", "add", "123");
            command.execute("blacklist", "add", "456");

            assertEquals(ServerDebugMode.WHITELIST, GAME_INFO.logPackets);
            assertTrue(GAME_INFO.isShowPacketPayload);
            assertTrue(GAME_INFO.isShowLoopPackets);
            assertEquals(Set.of(123), SERVER.debugWhitelist);
            assertEquals(Set.of(456), SERVER.debugBlacklist);
        } finally {
            GAME_INFO.logPackets = previousMode;
            GAME_INFO.isShowPacketPayload = previousPayload;
            GAME_INFO.isShowLoopPackets = previousLoops;
            SERVER.debugWhitelist = previousWhitelist;
            SERVER.debugBlacklist = previousBlacklist;
        }
    }

    @Test
    void protoInfoFormatsKnownOpcodeAndMessageNames() {
        String byOpcode = ProtoCommand.formatProtoInfo("27581");
        assertTrue(byOpcode.contains("Opcode: 27581"));
        assertTrue(byOpcode.contains("Name: EvtBulletDeactiveNotify"));
        assertTrue(byOpcode.contains("message EvtBulletDeactiveNotify {\n"
                + "  Vector disappear_pos = 2;\n"
                + "  uint32 entity_id = 12;\n"
                + "  ForwardType forward_type = 13;\n}"));

        String byMessageName = ProtoCommand.formatProtoInfo("ForgeQueueData");
        assertTrue(byMessageName.contains("Opcode: UNKNOWN"));
        assertTrue(byMessageName.contains("Name: ForgeQueueData"));
        assertTrue(byMessageName.contains("message ForgeQueueData {\n"
                + "  uint32 next_finish_timestamp = 7;\n"
                + "  uint32 finish_count = 10;\n"
                + "  uint32 unfinish_count = 3;"));
    }

    @Test
    void protoInfoReportsUnknownOpcodeAndName() {
        String unknownOpcode = ProtoCommand.formatProtoInfo("987654");
        assertTrue(unknownOpcode.contains("Opcode: 987654"));
        assertTrue(unknownOpcode.contains("Name: UNKNOWN"));
        assertTrue(unknownOpcode.contains("no protobuf definition found for UNKNOWN"));

        String unknownName = ProtoCommand.formatProtoInfo("MissingProtoMessage");
        assertTrue(unknownName.contains("Opcode: UNKNOWN"));
        assertTrue(unknownName.contains("Name: MissingProtoMessage"));
        assertTrue(unknownName.contains("no protobuf definition found for MissingProtoMessage"));
    }
}
