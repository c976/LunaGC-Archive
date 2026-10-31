package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "kick",
        permission = "server.kick",
        targetRequirement = Command.TargetRequirement.NONE,
        inlineTarget = false)
public final class KickCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender));
        commandLine.setExpandAtFiles(false);
        return commandLine;
    }

    @CommandLine.Command(
            name = "kick",
            description = "playerSelector: @UID (UID), username@ (account), username@UID (both match).",
            customSynopsis = "kick <playerSelector>")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<playerSelector>")
        private String selector;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            final Player target;
            try {
                target = CommandMap.findPlayer(CommandMap.parseExplicitTargetSelector(selector));
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }

            if (target == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_exist_error");
                return;
            }
            if (!target.isOnline() || target.getSession() == null || !target.getSession().isActive()) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.need_target_online");
                return;
            }

            if (sender != null) {
                CommandOutput.sendTranslatedMessage(
                        sender,
                        "commands.kick.player_kick_player",
                        sender.getUid(),
                        sender.getAccount().getUsername(),
                        target.getUid(),
                        target.getAccount().getUsername());
            } else {
                CommandOutput.sendTranslatedMessage(
                        null,
                        "commands.kick.server_kick_player",
                        target.getUid(),
                        target.getAccount().getUsername());
            }
            target.getSession().close();
        }
    }
}
