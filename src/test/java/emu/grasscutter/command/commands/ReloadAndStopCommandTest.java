package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class ReloadAndStopCommandTest {
    @Test
    void reloadModulesAreNamedAndKeepLegacyOrder() {
        assertEquals(List.of("config", "language", "gacha", "shop", "combine", "rewards"),
                ReloadCommand.supportedModules());
        var command = new ReloadCommand().createCommandLine(null, null);
        assertEquals(Set.of("all", "config", "language", "gacha", "shop", "combine", "rewards"),
                command.getSubcommands().keySet());
        assertEquals("server.reload", ReloadCommand.class.getAnnotation(Command.class).permission());
        assertDoesNotThrow(() -> command.parseArgs());
        for (String name : List.of("all", "config", "language", "gacha", "shop", "combine", "rewards")) {
            var cli = new ReloadCommand().createCommandLine(null, null);
            assertDoesNotThrow(() -> cli.parseArgs(name), name);
            assertTrue(cli.getSubcommands().get(name).getUsageMessage().contains("reload"));
        }
        assertThrows(CommandLine.ParameterException.class, () -> command.parseArgs("scripts"));
        assertThrows(CommandLine.ParameterException.class, () -> command.parseArgs("quest"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.parseArgs("config", "extra"));
    }

    @Test
    void stopKeepsItsPermissionAndUsesNormalExitStatus() {
        assertEquals(0, StopCommand.NORMAL_EXIT_CODE);
        var meta = StopCommand.class.getAnnotation(Command.class);
        assertEquals("server.stop", meta.permission());
        assertArrayEquals(new String[] {"shutdown"}, meta.aliases());
        var command = new StopCommand().createCommandLine(null, null);
        assertDoesNotThrow(() -> command.parseArgs());
        assertThrows(CommandLine.ParameterException.class,
                () -> command.parseArgs("unexpected"));
    }
}
