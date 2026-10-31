package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import emu.grasscutter.game.player.Player;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Commands from plugins built before the picocli migration only implement execute(). */
class LegacyCommandHandlerTest {

    @Command(label = "legacytest")
    static final class LegacyCommand implements CommandHandler {
        final List<List<String>> calls = new ArrayList<>();

        @Override
        public void execute(Player sender, Player targetPlayer, List<String> args) {
            calls.add(args);
        }
    }

    @Test
    void aLegacyCommandBuildsACommandLineForRegistration() {
        var command = new LegacyCommand();
        // CommandMap.registerCommand builds this; it threw AbstractMethodError for old plugins.
        assertNotNull(command.createCompletionCommandLine());
        assertEquals("legacytest", command.createCompletionCommandLine().getCommandName());
    }

    @Test
    void everyArgumentReachesExecuteVerbatim() {
        var command = new LegacyCommand();
        int exit = command.createCommandLine(null, null).execute("set", "-3", "--flag", "@file", "x10");
        assertEquals(0, exit);
        assertEquals(List.of(List.of("set", "-3", "--flag", "@file", "x10")), command.calls);
    }

    @Test
    void noArgumentsIsAnEmptyList() {
        var command = new LegacyCommand();
        command.createCommandLine(null, null).execute();
        assertEquals(List.of(List.of()), command.calls);
    }

    @Test
    void theOldHelperFieldsAndMethodsAreBack() {
        assertEquals(5, CommandHelpers.matchIntOrNeg(CommandHelpers.refineRegex, "r5"));
        assertEquals(-1, CommandHelpers.matchIntOrNeg(CommandHelpers.refineRegex, "hr5"));
        assertEquals(90, CommandHelpers.matchIntOrNeg(CommandHelpers.lvlRegex, "lv90"));
    }
}
