package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class DPSCommandTest {
    @Test
    void parseWithoutOnlineTargetDoesNotRequireGameplayState() {
        var command = new DPSCommand();
        assertEquals(Command.TargetRequirement.NONE,
                DPSCommand.class.getAnnotation(Command.class).targetRequirement());
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("start"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("start", "60", "2"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("stop"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("start", "abc"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("stop", "extra"));
    }
}
