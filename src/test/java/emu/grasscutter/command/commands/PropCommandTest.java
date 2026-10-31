package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class PropCommandTest {
    private static CommandLine command() {
        return new SetPropCommand().createCommandLine(null, null);
    }

    @Test
    void minecraftStylePropIsTheSoleEntrypointWithExistingPermissions() {
        var map = new CommandMap(false);
        var handler = new SetPropCommand();
        map.registerCommand("prop", handler);

        assertSame(handler, map.getHandler("prop"));
        assertNull(map.getHandler("setprop"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("prop"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("setprop"));

        var annotation = SetPropCommand.class.getAnnotation(Command.class);
        assertEquals("prop", annotation.label());
        assertArrayEquals(new String[0], annotation.aliases());
        assertEquals("player.setprop", annotation.permission());
        assertEquals("player.setprop.others", annotation.permissionTargeted());
    }

    @Test
    void missingValueQueriesAndSuppliedValueSets() {
        var cli = command();
        assertEquals("prop", cli.getCommandName());
        assertFalse(cli.getSubcommands().containsKey("set"));
        assertTrue(cli.getUsageMessage().contains("property"));
        assertTrue(cli.getUsageMessage().contains("value"));

        for (String[] args : new String[][] {
                {"worldlevel"}, {"godmode"}, {"bplevel"},
                {"player_resin"}, {"player_level"}, {"dive"}, {"abyss"}
        }) {
            assertDoesNotThrow(() -> command().parseArgs(args), String.join(" ", args));
        }

        for (String[] args : new String[][] {
                {"worldlevel", "8"}, {"godmode", "on"},
                {"godmode", "toggle"},
                {"bp", "50"}, {"dive", "off"}, {"player_level", "7"},
                {"player_level", "8"}, {"player_level", "12"}, {"player_resin", "160"}
        }) {
            assertDoesNotThrow(() -> command().parseArgs(args), String.join(" ", args));
        }
    }

    @Test
    void removedNestedSetAndInvalidArityAreRejected() {
        for (String[] args : new String[][] {
                {}, {"set", "worldlevel", "8"},
                {"worldlevel", "8", "extra"},
                {"setprop", "godmode", "on"}
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command().parseArgs(args), String.join(" ", args));
        }
    }

    @Test
    void actionOnlyPropertiesCannotBeParsedAsPlayerProperties() {
        for (String name : new String[] {
                "setopenstate", "unsetopenstate", "unlockmap", "so", "uo", "um"
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command().parseArgs(name), name);
            assertThrows(CommandLine.ParameterException.class,
                    () -> command().parseArgs(name, "1"), name);
        }
    }
}
