package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class EnterDungeonCommandTest {
    @Test
    void parsesDungeonIdsAndRequiresOneArgument() {
        var command = new EnterDungeonCommand();
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("1001"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs());
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("not-a-number"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("1001", "1002"));
    }

    @Test
    void validatesIdsAndRetainsPermissionContracts() {
        assertFalse(EnterDungeonCommand.validDungeonId(0));
        assertFalse(EnterDungeonCommand.validDungeonId(-1));
        assertTrue(EnterDungeonCommand.validDungeonId(1));
        var command = EnterDungeonCommand.class.getAnnotation(Command.class);
        assertEquals(Command.TargetRequirement.ONLINE, command.targetRequirement());
        assertEquals("player.enterdungeon", command.permission());
        assertEquals("player.enterdungeon.others", command.permissionTargeted());
        assertEquals("dungeon", command.label());
        assertArrayEquals(new String[] {}, command.aliases());
    }
}
