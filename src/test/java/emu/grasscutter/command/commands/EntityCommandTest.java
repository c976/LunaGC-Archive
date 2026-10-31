package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class EntityCommandTest {
    @Test
    void parsesReadAndWriteModes() {
        var cmd = new EntityCommand();
        assertDoesNotThrow(() -> cmd.createCommandLine(null, null).parseArgs("101"));
        assertDoesNotThrow(() -> cmd.createCommandLine(null, null)
                .parseArgs("101", "--entity-id", "--hp", "0", "--max-hp", "100", "--atk", "5", "--def", "3"));
        assertDoesNotThrow(() -> cmd.createCommandLine(null, null)
                .parseArgs("101", "--state", "2", "--ai", "1"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cmd.createCommandLine(null, null).parseArgs());
        assertThrows(CommandLine.ParameterException.class,
                () -> cmd.createCommandLine(null, null).parseArgs("101", "--hp", "bad"));
    }

    @Test
    void validatesExplicitValuesIncludingZeroHp() {
        assertTrue(EntityCommand.validStats(null, 0, 0, 0));
        assertTrue(EntityCommand.validStats(100, 100, 1, 1));
        assertFalse(EntityCommand.validStats(0, 1, null, null));
        assertFalse(EntityCommand.validStats(null, -1, null, null));
        assertFalse(EntityCommand.validStats(null, null, -2, null));
        assertEquals("server.entity", EntityCommand.class.getAnnotation(Command.class).permission());
    }
}
