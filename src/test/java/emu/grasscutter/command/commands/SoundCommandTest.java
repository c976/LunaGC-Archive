package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.CommandMap;
import emu.grasscutter.data.SoundRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class SoundCommandTest {
    private static CommandLine cli() {
        return new SoundCommand().createCommandLine(null, null);
    }

    @Test
    void listAndCompletionUseObservedNamesOnly() {
        SoundRegistry.observe("test_audio_cue_A");
        SoundRegistry.observe("test_audio_cue_B");
        SoundRegistry.observe("test_audio_cue_A");
        assertEquals(List.of("test_audio_cue_A", "test_audio_cue_B"),
                SoundRegistry.names("test_audio_cue"));
        assertEquals(List.of("test_audio_cue_A", "test_audio_cue_B"),
                SoundRegistry.names("TEST_AUDIO_CUE"));

        assertTrue(cli().getSubcommands().containsKey("list"));
        assertDoesNotThrow(() -> cli().parseArgs("list"));
        assertDoesNotThrow(() -> cli().parseArgs("list", "cue", "--page", "2"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cli().parseArgs("list", "cue", "extra"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cli().parseArgs("list", "--page", "not-a-number"));
    }

    @Test
    void acceptsOnlyNoCoordinatesOrAllThree() {
        for (String[] args : new String[][] {
                {"test_audio_cue_A"},
                {"test_audio_cue_A", "1", "2", "3"},
                {"test_audio_cue_A", "-1.5", "0", "6.25"}
        }) {
            assertDoesNotThrow(() -> cli().parseArgs(args), String.join(" ", args));
        }
        for (String[] args : new String[][] {
                {"test_audio_cue_A", "1"},
                {"test_audio_cue_A", "1", "2"},
                {"test_audio_cue_A", "1", "2", "x"},
                {"test_audio_cue_A", "1", "2", "3", "4"},
                {"test_audio_cue_A", "NaN", "2", "3"}
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> cli().parseArgs(args), String.join(" ", args));
        }
    }

    @Test
    void commandStillSupportsAudioAlias() {
        var map = new CommandMap(false);
        var cmd = new SoundCommand();
        map.registerCommand("sound", cmd);
        assertSame(cmd, map.getHandler("audio"));
    }
}
