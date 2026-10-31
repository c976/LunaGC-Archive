package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class OpenStateCommandTest {
    private static CommandLine command() {
        return new OpenStateCommand().createCommandLine(null, null);
    }

    @Test
    void permissionsAreUnchanged() {
        var handler = new OpenStateCommand();
        var map = new CommandMap(false);
        map.registerCommand("openstate", handler);
        assertSame(handler, map.getHandler("openstate"));
        var metadata = OpenStateCommand.class.getAnnotation(Command.class);
        assertEquals("player.setprop", metadata.permission());
        assertEquals("player.setprop.others", metadata.permissionTargeted());
    }

    @Test
    void nameFirstQueryAndSetAcceptLegacyIdsAndBooleans() {
        assertEquals("openstate", command().getCommandName());
        assertTrue(command().getUsageMessage().contains("name|id"));
        assertTrue(command().getUsageMessage().contains("true|false"));
        for (String[] args : new String[][] {
                {}, {"gacha"}, {"GACHA"}, {"resin", "true"},
                {"multiplayer", "false"}, {"45"}, {"45", "1"},
                {"45", "0"}, {"7100", "true"}
        }) {
            assertDoesNotThrow(() -> command().parseArgs(args), String.join(" ", args));
        }
    }

    @Test
    void listIsDiscoverableAndSupportsSearchAndPaging() {
        assertTrue(command().getSubcommands().containsKey("list"));
        for (String[] args : new String[][] {
                {"list"}, {"list", "guide"}, {"list", "--page", "2"},
                {"list", "guide", "--page", "2"}
        }) {
            assertDoesNotThrow(() -> command().parseArgs(args), String.join(" ", args));
        }
        assertThrows(CommandLine.ParameterException.class,
                () -> command().parseArgs("list", "guide", "extra"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command().parseArgs("list", "--page", "NaN"));
    }

    @Test
    void registryQueriesAndCompletionsReflectAvailableResourceIds() {
        assertEquals(31, OpenStateNames.parse("gacha"));
        assertEquals(45, OpenStateNames.parse("resin"));
        assertEquals(2801, OpenStateNames.parse("adventure_manual_education"));
        assertEquals(7100, OpenStateNames.parse("7100"));
        assertEquals("gacha (31)", OpenStateNames.display(31));
        assertEquals("7100 (unknown)", OpenStateNames.display(7100));
        var ids = Set.of(31, 45, 24, 7100);
        assertEquals(List.of("gacha", "guide_map", "resin"),
                OpenStateNames.suggestions(ids));
        assertEquals(List.of(new OpenStateNames.Entry(24, "guide_map")),
                OpenStateNames.entries(ids, "GUIDE"));
        assertEquals(List.of(new OpenStateNames.Entry(7100, "7100")),
                OpenStateNames.entries(ids, "7100"));
        assertTrue(OpenStateNames.entries(ids, "missing").isEmpty());
    }

    @Test
    void invalidNamesAndValuesAreRejected() {
        for (String[] args : new String[][] {
                {"not_a_state"}, {"gacha", "on"}, {"gacha", "2"},
                {"set", "45", "1"}, {"unset", "45"}, {"45", "true", "extra"},
                {"999999999999999999999999999"}
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command().parseArgs(args), String.join(" ", args));
        }
    }
}
