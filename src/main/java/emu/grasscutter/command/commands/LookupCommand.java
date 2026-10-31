package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.NameIndex;
import emu.grasscutter.game.player.Player;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Find names and IDs in the loaded game data, with ranked paginated results. */
@Command(
        label = "lookup",
        aliases = {"find", "search"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class LookupCommand implements CommandHandler {
    private static final int PAGE_SIZE = 15;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "lookup", description = "Search item, avatar, monster and gadget names.")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<name...>")
        private String[] words;

        @Option(names = "--page", defaultValue = "1", description = "1-based result page")
        private int page;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (page < 1) {
                CommandOutput.sendMessage(sender, "Page must be at least 1.");
                return;
            }

            String query = String.join(" ", words);
            var found = NameIndex.searchPage(query, page, PAGE_SIZE);
            if (found.total() == 0) {
                CommandOutput.sendMessage(sender, "Nothing is called anything like \"" + query + "\".");
                return;
            }

            int pages = 1 + (found.total() - 1) / PAGE_SIZE;
            if (page > pages) {
                CommandOutput.sendMessage(sender, "Page out of range (1-" + pages + ").");
                return;
            }

            CommandOutput.sendMessage(sender,
                    found.total() + " match(es) for \"" + query + "\" (page "
                            + page + "/" + pages + "):");
            found.results().forEach(entry -> CommandOutput.sendMessage(sender, "  " + entry));
            if (page < pages) {
                CommandOutput.sendMessage(sender,
                        "Next: lookup " + query + " --page " + (page + 1));
            }
        }
    }
}
