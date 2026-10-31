package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class SayCommandTest {
    @Test
    void broadcastNeverTreatsMessageTextAsARecipient() {
        assertEquals("hello world", SayCommand.joinMessage(List.of("hello", "world")));
        assertEquals("hello @10001", SayCommand.joinMessage(List.of("hello", "@10001")));
        assertEquals("hello alice@", SayCommand.joinMessage(List.of("hello", "alice@")));
        assertEquals("hello alice@10001",
                SayCommand.joinMessage(List.of("hello", "alice@10001")));
        assertEquals("contact admin@", SayCommand.joinMessage(List.of("contact", "admin@")));
        assertEquals("mail alice@example.com",
                SayCommand.joinMessage(List.of("mail", "alice@example.com")));
        assertThrows(IllegalArgumentException.class, () -> SayCommand.joinMessage(List.of()));
        assertThrows(IllegalArgumentException.class, () -> SayCommand.joinMessage(List.of(" ")));
    }

    @Test
    void directedMessagesRequireExplicitLeadingSelector() {
        assertEquals(10001, SayToCommand.parseRecipient("@10001").uid());
        assertEquals("alice", SayToCommand.parseRecipient("alice@").username());
        CommandMap.TargetSelector both = SayToCommand.parseRecipient("alice@10001");
        assertEquals("alice", both.username());
        assertEquals(10001, both.uid());
        for (String invalid : List.of("alice", "10001", "@", "@0", "@invalid",
                "alice@0", "alice@999999999999999")) {
            assertThrows(IllegalArgumentException.class,
                    () -> SayToCommand.parseRecipient(invalid), invalid);
        }
    }

    @Test
    void separatePicocliCommandsUseUnambiguousGrammarAndRetainPermissions() {
        var broadcast = new SayCommand().createCommandLine(null, null);
        var directed = new SayToCommand().createCommandLine(null, null);

        assertTrue(broadcast.getUsageMessage().contains("say <message...>"));
        assertFalse(broadcast.getUsageMessage().contains("[selector]"));
        assertTrue(directed.getUsageMessage()
                .contains("sayto <playerSelector> <message...>"));
        assertDoesNotThrow(() -> broadcast.parseArgs("hello", "world"));
        assertDoesNotThrow(() -> broadcast.parseArgs("hello", "@10001"));
        assertDoesNotThrow(() -> broadcast.parseArgs("hello", "alice@"));
        assertDoesNotThrow(() -> directed.parseArgs("@10001", "hello"));
        assertDoesNotThrow(() -> directed.parseArgs("alice@", "hello", "@10001"));
        assertThrows(CommandLine.ParameterException.class, broadcast::parseArgs);
        assertThrows(CommandLine.ParameterException.class, directed::parseArgs);
        assertThrows(CommandLine.ParameterException.class,
                () -> directed.parseArgs("@10001"));

        for (Class<?> type : List.of(SayCommand.class, SayToCommand.class)) {
            var annotation = type.getAnnotation(Command.class);
            assertFalse(annotation.inlineTarget());
            assertEquals(Command.TargetRequirement.NONE, annotation.targetRequirement());
            assertEquals("server.sendmessage", annotation.permission());
            assertTrue(annotation.permissionTargeted().isEmpty());
        }
    }

    @Test
    void registryExposesSeparateBroadcastAndDirectedRoutes() {
        var registry = new CommandMap(false);
        registry.registerCommand("say", new SayCommand());
        registry.registerCommand("sayto", new SayToCommand());
        assertNotNull(registry.getHandler("say"));
        assertNotNull(registry.getHandler("sayto"));
        assertNotSame(registry.getHandler("say"), registry.getHandler("sayto"));
        assertEquals("say", registry.getHandler("say").getLabel());
        assertEquals("sayto", registry.getHandler("sayto").getLabel());
    }
}
