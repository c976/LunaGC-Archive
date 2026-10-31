package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class AvatarCommandTest {
    private static CommandLine command() {
        return new AvatarCommand().createCommandLine(null, null);
    }

    @Test
    void rootGroupsExposeCanonicalRoutes() {
        var cli = command();
        for (String route : List.of("list", "constellation", "talent", "stat", "friendship", "extralevel", "max")) {
            assertTrue(cli.getSubcommands().containsKey(route), route);
        }
        assertEquals(Command.TargetRequirement.ONLINE,
                AvatarCommand.class.getAnnotation(Command.class).targetRequirement());
        for (String old : List.of("setFetterLevel", "getid", "extralevel", "constellation")) {
            assertFalse(cli.getSubcommands().get("talent").getSubcommands().containsKey(old));
        }
    }

    @Test
    void constellationAcceptsOnlyFlagBasedBulkSelection() {
        for (String[] args : new String[][] {
                {"constellation", "set", "0"},
                {"constellation", "set", "6", "--all"},
                {"constellation", "set", "5", "--avatar", "1001"},
                {"constellation", "reset"},
                {"constellation", "reset", "--all"},
                {"constellation", "reset", "--avatar", "1001"}
        }) {
            assertDoesNotThrow(() -> command().parseArgs(args), String.join(" ", args));
        }

        for (String[] args : new String[][] {
                {"constellation", "set"},
                {"constellation", "set", "6", "all"},
                {"constellation", "reset", "all"},
                {"constellation", "set", "6", "--all", "--avatar", "1001"},
                {"constellation", "reset", "--all", "--avatar", "1001"},
                {"constellation", "set", "invalid"}
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command().parseArgs(args), String.join(" ", args));
        }

        assertFalse(AvatarCommand.validConstellationLevel(-1));
        for (int level = 0; level <= 6; level++) {
            assertTrue(AvatarCommand.validConstellationLevel(level));
        }
        assertFalse(AvatarCommand.validConstellationLevel(7));
    }

    @Test
    void talentCommandsSupportSelectiveBulkOperationsAndHaveSlotAliases() {
        var subcommands = command().getSubcommands().get("talent").getSubcommands();
        for (String name : List.of("set", "normal", "skill", "burst", "all", "list", "n", "e", "q")) {
            assertTrue(subcommands.containsKey(name), name);
        }
        assertSame(subcommands.get("normal"), subcommands.get("n"));
        assertSame(subcommands.get("skill"), subcommands.get("e"));
        assertSame(subcommands.get("burst"), subcommands.get("q"));
        for (String[] args : new String[][] {
                {"talent", "set", "10001", "10", "--avatar", "1001"},
                {"talent", "normal", "10"},
                {"talent", "normal", "10", "--all"},
                {"talent", "skill", "10", "--avatar", "1001"},
                {"talent", "skill", "10", "--all"},
                {"talent", "burst", "10"},
                {"talent", "burst", "10", "--all"},
                {"talent", "n", "10"},
                {"talent", "e", "10"},
                {"talent", "q", "10"},
                {"talent", "all", "10", "--avatar", "1001"},
                {"talent", "all", "10", "--all"},
                {"talent", "list", "--avatar", "1001"}
        }) {
            assertDoesNotThrow(() -> command().parseArgs(args), String.join(" ", args));
        }
        for (String[] args : new String[][] {
                {"talent", "set", "10001", "10", "--all"},
                {"talent", "list", "--all"},
                {"talent", "normal", "10", "--all", "--avatar", "1001"},
                {"talent", "all", "10", "--all", "--avatar", "1001"}
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command().parseArgs(args), String.join(" ", args));
        }
        assertThrows(CommandLine.ParameterException.class,
                () -> command().parseArgs("talent", "getid"));
    }

    @Test
    void friendshipMaxAndExtraLevelShareAvatarSelectionRules() {
        for (String[] args : new String[][] {
                {"list"},
                {"friendship", "set", "10"},
                {"friendship", "set", "5", "--avatar", "1001"},
                {"friendship", "set", "5", "--all"},
                {"extralevel"},
                {"extralevel", "--avatar", "1001"},
                {"max"},
                {"max", "--all"},
                {"max", "--avatar", "1001"}
        }) {
            assertDoesNotThrow(() -> command().parseArgs(args), String.join(" ", args));
        }
        for (String[] args : new String[][] {
                {"friendship", "set", "5", "--avatar", "1001", "--all"},
                {"extralevel", "1001"},
                {"max", "all"},
                {"max", "--all", "--avatar", "1001"},
                {"max", "--avatar", "not-a-number"},
                {"list", "--all"}
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command().parseArgs(args), String.join(" ", args));
        }
    }
}
