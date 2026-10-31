package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class TrialCommandTest {
    @Test
    void trialReplacesOldNamesWithoutChangingPermissions() {
        var handler = new TrialAvatarActivityCommand();
        var registry = new CommandMap(false);
        registry.registerCommand("trial", handler);

        assertSame(handler, registry.getHandler("trial"));
        assertNull(registry.getHandler("trialavataractivity"));
        assertNull(registry.getHandler("taa"));

        var annotation = TrialAvatarActivityCommand.class.getAnnotation(Command.class);
        assertEquals("trial", annotation.label());
        assertEquals(0, annotation.aliases().length);
        assertEquals("player.trialavataractivity", annotation.permission());
        assertEquals("player.trialavataractivity.others", annotation.permissionTargeted());
    }

    @Test
    void trialSubcommandsUseWorkingConvertersAndShowCanonicalUsage() {
        var command = new TrialAvatarActivityCommand().createCommandLine(null, null);

        assertEquals("trial", command.getCommandName());
        assertTrue(command.getSubcommands().containsKey("change"));
        assertTrue(command.getSubcommands().containsKey("toggledungeon"));
        assertTrue(command.getSubcommands().containsKey("togglereward"));
        assertTrue(command.getUsageMessage().contains("trial"));

        assertDoesNotThrow(() -> command.parseArgs("change", "1234"));
        assertDoesNotThrow(() -> command.parseArgs("toggledungeon", "all"));
        assertDoesNotThrow(() -> command.parseArgs("toggledungeon", "2"));
        assertDoesNotThrow(() -> command.parseArgs("togglereward", "all"));
        assertDoesNotThrow(() -> command.parseArgs("togglereward", "1"));

        assertThrows(CommandLine.ParameterException.class,
                () -> command.parseArgs("toggledungeon", "invalid"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.parseArgs("togglereward", "invalid"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.parseArgs("change"));
    }
}
