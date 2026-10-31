package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class SupportCommandTest {
    @Test
    void oneCommandOwnsAllSupportButtons() {
        var map = new CommandMap(false);
        var handler = new SupportCommand();
        map.registerCommand("support", handler);

        assertSame(handler, map.getHandler("support"));
        assertSame(handler, map.getHandler("assist"));
        for (String removed : List.of("pierro", "ronova")) {
            assertNull(map.getHandler(removed));
            assertFalse(map.getCommandLine().getSubcommands().containsKey(removed));
        }

        var annotation = SupportCommand.class.getAnnotation(Command.class);
        assertEquals("player.support", annotation.permission());
        assertEquals("player.support.others", annotation.permissionTargeted());
    }

    @Test
    void pierroUsesTheSameTeamAbilityAsTheOldCommand() {
        String pierroAbility = "Level_Ronova_SupportSkillHandler";
        assertEquals(List.of(pierroAbility), SupportCommand.selectAbilities("pierro"));
        assertEquals(List.of(pierroAbility), SupportCommand.selectAbilities("PIERRO"));
        assertTrue(SupportCommand.selectAbilities("all").contains(pierroAbility));
        assertEquals(3, SupportCommand.selectAbilities("all").size());
        assertEquals(1, SupportCommand.selectAbilities("valeriy").size());
        assertEquals(1, SupportCommand.selectAbilities("blast").size());
        assertEquals(List.of(), SupportCommand.selectAbilities("unknown"));
    }

    @Test
    void supportParsesAllNamesAndOptionalSwitch() {
        for (String name : List.of("pierro", "valeriy", "blast", "all")) {
            for (String[] tail : new String[][] {{}, {"on"}, {"off"}}) {
                String[] args = new String[tail.length + 1];
                args[0] = name;
                System.arraycopy(tail, 0, args, 1, tail.length);
                assertDoesNotThrow(() -> new SupportCommand()
                        .createCommandLine(null, null).parseArgs(args));
            }
        }
        assertThrows(CommandLine.ParameterException.class,
                () -> new SupportCommand().createCommandLine(null, null).parseArgs());
        assertThrows(CommandLine.ParameterException.class,
                () -> new SupportCommand().createCommandLine(null, null)
                        .parseArgs("pierro", "on", "extra"));
    }
}
