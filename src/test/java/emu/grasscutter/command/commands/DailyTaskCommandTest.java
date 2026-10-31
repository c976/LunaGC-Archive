package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class DailyTaskCommandTest {
    @Test
    void resourceCoverageCanBeParsedWithoutASelectedPlayer() {
        var command = new DailyTaskCommand();
        assertEquals(
                Command.TargetRequirement.NONE,
                DailyTaskCommand.class.getAnnotation(Command.class).targetRequirement());
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("support"));
    }

    @Test
    void statefulSubcommandsKeepTheirExistingArguments() {
        var command = new DailyTaskCommand();
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("list"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("load"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("reset"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("city", "mondstadt"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("city", "2026"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("finish", "10001"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("bonus"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("finish"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("city"));
    }
}
