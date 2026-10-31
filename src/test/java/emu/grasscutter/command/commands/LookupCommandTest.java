package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class LookupCommandTest {
    @Test
    void searchAliasesAndPageSyntax() {
        var map = new CommandMap(false);
        var cmd = new LookupCommand();
        map.registerCommand("lookup", cmd);
        assertSame(cmd, map.getHandler("find"));
        assertSame(cmd, map.getHandler("search"));
        for (String[] args : new String[][] {
                {"slime"}, {"hydro", "slime"}, {"slime", "--page", "2"},
                {"--page", "3", "hydro", "slime"}
        }) {
            assertDoesNotThrow(() -> cmd.createCommandLine(null, null).parseArgs(args));
        }
        assertTrue(cmd.createCommandLine(null, null).getUsageMessage().contains("--page"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cmd.createCommandLine(null, null).parseArgs("--page", "not-number", "slime"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cmd.createCommandLine(null, null).parseArgs("--page", "2"));
    }
}
