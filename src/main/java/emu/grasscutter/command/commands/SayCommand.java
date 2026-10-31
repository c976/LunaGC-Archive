package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.command.Command.TargetRequirement;
import emu.grasscutter.game.player.Player;
import java.util.Arrays;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Server-wide messages. Player-directed messages use the separate sayto command. */
@Command(
        label = "say",
        permission = "server.sendmessage",
        targetRequirement = TargetRequirement.NONE,
        inlineTarget = false)
public final class SayCommand implements CommandHandler {
    static String joinMessage(List<String> words) {
        if (words == null || words.isEmpty()) {
            throw new IllegalArgumentException("Specify a message.");
        }
        String message = String.join(" ", words);
        if (message.isBlank()) {
            throw new IllegalArgumentException("Specify a non-empty message.");
        }
        return message;
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var cli = new CommandLine(new Args(sender));
        // Literal @UID and similar text belong to the message, not an argument file or target.
        cli.setExpandAtFiles(false);
        return cli;
    }

    @CommandLine.Command(
            name = "say",
            customSynopsis = "say <message...>",
            description = "Broadcast a server message to every online player.")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<message...>")
        private String[] words;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            final String message;
            try {
                message = joinMessage(Arrays.asList(words));
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }

            for (Player player : Grasscutter.getGameServer().getPlayers().values()) {
                CommandOutput.sendMessage(player, message);
            }
            CommandOutput.sendTranslatedMessage(sender, "commands.say.success");
        }
    }
}
