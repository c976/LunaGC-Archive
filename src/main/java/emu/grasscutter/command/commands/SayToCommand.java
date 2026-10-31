package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.command.Command.TargetRequirement;
import emu.grasscutter.game.player.Player;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Address a single online player explicitly instead of interpreting message text as a selector. */
@Command(
        label = "sayto",
        permission = "server.sendmessage",
        targetRequirement = TargetRequirement.NONE,
        inlineTarget = false)
public final class SayToCommand implements CommandHandler {
    static CommandMap.TargetSelector parseRecipient(String selector) {
        return CommandMap.parseExplicitTargetSelector(selector);
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var cli = new CommandLine(new Args(sender));
        cli.setExpandAtFiles(false);
        return cli;
    }

    @CommandLine.Command(
            name = "sayto",
            customSynopsis = "sayto <playerSelector> <message...>",
            description = "Send a server message to one online player: @UID, username@ or username@UID.")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<playerSelector>")
        private String selector;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "<message...>")
        private List<String> words = List.of();

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            final CommandMap.TargetSelector recipient;
            final String message;
            try {
                recipient = parseRecipient(selector);
                message = SayCommand.joinMessage(words);
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }

            final Player selected;
            try {
                selected = CommandMap.findPlayer(recipient);
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }
            if (selected == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_exist_error");
                return;
            }

            Player online = Grasscutter.getGameServer().getPlayerByUid(selected.getUid());
            if (online == null || !online.isOnline()
                    || online.getSession() == null || !online.getSession().isActive()) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.need_target_online");
                return;
            }
            if (!Grasscutter.getPermissionHandler().checkPermission(
                    sender, online, "server.sendmessage", "server.sendmessage.others")) {
                return;
            }

            CommandOutput.sendMessage(online, message);
            CommandOutput.sendTranslatedMessage(sender, "commands.say.success");
        }
    }
}
