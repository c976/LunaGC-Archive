package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "unban", targetRequirement = Command.TargetRequirement.NONE, inlineTarget = false)
public final class UnbanCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender));
        commandLine.setExpandAtFiles(false);
        return commandLine;
    }

    @CommandLine.Command(
            name = "unban",
            description = "playerSelector: @UID (UID), username@ (account), username@UID (both match).",
            customSynopsis = "unban <playerSelector|IPv4>")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<playerSelector|IPv4>")
        private String selector;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            final BanCommand.BanTarget target;
            try {
                target = BanCommand.parseTarget(selector, "unban");
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }

            if (target.type() == BanCommand.TargetType.IPV4) {
                unbanIp(target.value());
                return;
            }
            unbanPlayer(target.value());
        }

        private void unbanPlayer(String selector) {
            final Account account;
            try {
                account = CommandMap.findAccount(CommandMap.parseExplicitTargetSelector(selector));
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }
            if (account == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.unban.failure");
                return;
            }

            boolean someoneElse = sender != null
                    && !sender.getAccount().getId().equals(account.getId());
            if (!BanCommand.hasPermission(
                    sender, "server.ban", someoneElse ? "server.ban.others" : null)) return;

            DatabaseHelper.unbanAccount(account);
            CommandOutput.sendTranslatedMessage(sender, "commands.unban.success");
        }

        private void unbanIp(String ip) {
            if (!BanCommand.hasPermission(sender, "server.banip", null)) return;
            if (!DatabaseHelper.removeBannedIp(ip)) {
                CommandOutput.sendMessage(sender, "No ban recorded for " + ip + ".");
                return;
            }

            int unbanned = DatabaseHelper.unbanAccountsBannedByIp(ip);
            CommandOutput.sendMessage(
                    sender,
                    unbanned > 0
                            ? "Unbanned IP " + ip + ", along with " + unbanned + " account(s)."
                            : "Unbanned IP " + ip + ".");
        }
    }
}
