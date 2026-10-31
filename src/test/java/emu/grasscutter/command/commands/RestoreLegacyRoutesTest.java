package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class RestoreLegacyRoutesTest {
    @Test
    void standaloneHealAndHAreRemovedButHpRetainsBulkOption() {
        var map = new CommandMap(false);
        map.registerCommand("restore", new RestoreCommand());
        assertNull(map.getHandler("heal"));
        assertNull(map.getHandler("h"));

        var handler = new RestoreCommand();
        assertDoesNotThrow(() -> handler.createCommandLine(null, null).parseArgs("hp"));
        assertDoesNotThrow(() -> handler.createCommandLine(null, null).parseArgs("hp", "--all"));
        assertThrows(CommandLine.ParameterException.class,
                () -> handler.createCommandLine(null, null).parseArgs("hp", "all"));
        assertThrows(CommandLine.ParameterException.class,
                () -> handler.createCommandLine(null, null).parseArgs("hp", "--all", "--bad"));
    }

    @Test
    void energyAndCombinedRestorationRetainTheSupportedRoutes() {
        var handler = new RestoreCommand();
        for (String[] args : new String[][] {{"energy"}, {"all"}}) {
            assertDoesNotThrow(() -> handler.createCommandLine(null, null).parseArgs(args));
        }
        assertThrows(CommandLine.ParameterException.class,
                () -> handler.createCommandLine(null, null).parseArgs("energy", "--all"));
        assertThrows(CommandLine.ParameterException.class,
                () -> handler.createCommandLine(null, null).parseArgs("all", "--all"));
    }
}
