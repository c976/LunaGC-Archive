package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class DebugCommandTest {
    @Test
    void debugCommandsParseWithSelectedOrMissingPlayer() {
        var command = new DebugCommand();
        assertEquals(Command.TargetRequirement.NONE,
                DebugCommand.class.getAnnotation(Command.class).targetRequirement());
        var cli = command.createCommandLine(null, null);
        assertDoesNotThrow(() -> cli.parseArgs("abilities", "123", "--config"));
        assertDoesNotThrow(() -> cli.parseArgs("entity"));
        assertDoesNotThrow(() -> cli.parseArgs("setvar", "123", "Damage", "1.5", "--ability", "2"));
        assertDoesNotThrow(() -> cli.parseArgs("dynamicmap"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs("setvar", "123"));
    }
}
