package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.CommandMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class AvatarStatCommandTest {
    private static CommandLine command() {
        return new AvatarCommand().createCommandLine(null, null);
    }

    @Test
    void onlyCanonicalStatRoutesAreRegistered() {
        var map = new CommandMap(false);
        map.registerCommand("avatar", new AvatarCommand());
        for (String removed : List.of("setStats", "stats", "stat")) {
            assertNull(map.getHandler(removed), removed);
            assertFalse(map.getCommandLine().getSubcommands().containsKey(removed), removed);
        }

        var stat = command().getSubcommands().get("stat");
        assertNotNull(stat);
        for (String name : List.of("set", "lock", "unlock")) {
            assertTrue(stat.getSubcommands().containsKey(name), name);
        }
        for (String removed : List.of("stats", "freeze", "unfreeze")) {
            assertFalse(stat.getSubcommands().containsKey(removed), removed);
        }
    }

    @Test
    void acceptsStatSetLockUnlockAndOwnedCharacterSelection() {
        for (String[] args : new String[][] {
                {"stat", "set", "atk", "3000"},
                {"stat", "set", "cr", "100%", "--avatar", "10000002"},
                {"stat", "set", "cd", "0.5"},
                {"stat", "lock", "atk"},
                {"stat", "lock", "atk", "3000"},
                {"stat", "lock", "er", "250%", "--avatar", "10000002"},
                {"stat", "unlock", "atk"},
                {"stat", "unlock", "atk", "--avatar", "10000002"}
        }) {
            assertDoesNotThrow(() -> command().parseArgs(args), String.join(" ", args));
        }
    }

    @Test
    void rejectsUnrecognizedStatsOrLegacySyntaxAndBulkOptions() {
        for (String[] args : new String[][] {
                {"stat", "atk", "3000"},
                {"stat", "set", "atk"},
                {"stat", "set", "invalid_stat", "3000"},
                {"stat", "set", "atk", "3000", "--all"},
                {"stat", "set", "atk", "3000", "--avatar"},
                {"stat", "set", "atk", "3000", "--avatar", "not-an-id"},
                {"stat", "lock"},
                {"stat", "unlock"},
                {"stat", "unlock", "atk", "123"},
                {"stat", "freeze", "atk"},
                {"stats", "set", "atk", "3000"}
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command().parseArgs(args), String.join(" ", args));
        }
    }

    @Test
    void keepsPercentSemanticsAndOriginalOperationPermission() {
        assertEquals(1.0f, AvatarStatCommand.parsePercent("100%"));
        assertEquals(2.5f, AvatarStatCommand.parsePercent("250%"));
        assertEquals(3f, AvatarStatCommand.parsePercent("3"));
        assertThrows(NumberFormatException.class, () -> AvatarStatCommand.parsePercent("not-a-number"));

        assertEquals("player.setstats",
                AvatarStatCommand.operationPermission());
    }
}
