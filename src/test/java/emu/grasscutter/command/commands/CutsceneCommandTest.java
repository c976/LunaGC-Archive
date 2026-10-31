package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class CutsceneCommandTest {
    @Test
    void listParsesWithoutPlayerAndNoOnlineTargetRequirement() {
        var command = new CutsceneCommand();
        assertEquals(Command.TargetRequirement.NONE,
                CutsceneCommand.class.getAnnotation(Command.class).targetRequirement());
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("list"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null)
                .parseArgs("list", "some", "path"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs());
    }

    @Test
    void unknownIdsRequireExplicitForceAndPositiveId() {
        assertTrue(CutsceneCommand.mayPlayId(100, true, false));
        assertFalse(CutsceneCommand.mayPlayId(100, false, false));
        assertTrue(CutsceneCommand.mayPlayId(100, false, true));
        assertFalse(CutsceneCommand.mayPlayId(0, true, true));
        assertFalse(CutsceneCommand.mayPlayId(-1, true, true));
        assertDoesNotThrow(() -> new CutsceneCommand()
                .createCommandLine(null, null).parseArgs("100", "--force"));
        assertDoesNotThrow(() -> new CutsceneCommand()
                .createCommandLine(null, null).parseArgs("100"));
    }
}
