package emu.grasscutter.command;

import emu.grasscutter.game.player.Player;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;

/**
 * Runs a command written against the pre-picocli {@link CommandHandler}, which only implements
 * {@code execute(sender, targetPlayer, args)}.
 *
 * <p>Plugins built before the picocli migration were compiled against that interface and do not
 * implement {@link CommandHandler#createCommandLine}; registering one failed with an
 * AbstractMethodError and took the whole plugin down. This wraps the old entry point in a command
 * line that hands every argument through verbatim, as the old dispatcher did.
 */
final class LegacyCommandLine {
    private LegacyCommandLine() {}

    static CommandLine create(CommandHandler handler, Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(handler, sender, targetPlayer));
        // The old dispatcher passed every token through: no options, no @file expansion.
        commandLine.setUnmatchedArgumentsAllowed(true);
        commandLine.setUnmatchedOptionsArePositionalParams(true);
        commandLine.setExpandAtFiles(false);
        try {
            commandLine.getCommandSpec().name(handler.getLabel());
        } catch (RuntimeException ignored) {
            // A handler without a @Command label keeps picocli's placeholder name.
        }
        return commandLine;
    }

    @CommandLine.Command(name = "command")
    static final class Root implements Runnable {
        private final CommandHandler handler;
        private final Player sender;
        private final Player targetPlayer;

        @CommandLine.Parameters(arity = "0..*")
        List<String> args = new ArrayList<>();

        @CommandLine.Unmatched List<String> unmatched = new ArrayList<>();

        Root(CommandHandler handler, Player sender, Player targetPlayer) {
            this.handler = handler;
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var all = new ArrayList<String>(this.args);
            all.addAll(this.unmatched);
            this.handler.execute(this.sender, this.targetPlayer, all);
        }
    }
}
