package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "coop",
        permission = "server.coop",
        targetRequirement = Command.TargetRequirement.NONE,
        inlineTarget = false)
public final class CoopCommand implements CommandHandler {
    record Participants(CommandMap.TargetSelector guest, CommandMap.TargetSelector host) {}

    /** With one selector, the current command target is the guest and the argument is the host. */
    static Participants splitSelectors(List<CommandMap.TargetSelector> selectors) {
        if (selectors.size() == 1) return new Participants(null, selectors.getFirst());
        if (selectors.size() == 2) return new Participants(selectors.get(0), selectors.get(1));
        throw new IllegalArgumentException("Specify [guestSelector] <hostSelector>.");
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.setExpandAtFiles(false);
        commandLine.registerConverter(
                CommandMap.TargetSelector.class,
                value -> {
                    try {
                        return CommandMap.parseExplicitTargetSelector(value);
                    } catch (IllegalArgumentException invalid) {
                        throw new CommandLine.TypeConversionException(invalid.getMessage());
                    }
                });
        return commandLine;
    }

    @CommandLine.Command(
            name = "coop",
            description = "guestSelector and hostSelector: @UID, username@, or username@UID. "
                    + "Omitting guestSelector uses the current command target.",
            customSynopsis = "coop [guestSelector] <hostSelector>")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player defaultGuest;

        @Parameters(index = "0..1", arity = "1..2", paramLabel = "<playerSelector>")
        private List<CommandMap.TargetSelector> selectors = List.of();

        private Args(Player sender, Player defaultGuest) {
            this.sender = sender;
            this.defaultGuest = defaultGuest;
        }

        @Override
        public void run() {
            var selected = splitSelectors(selectors);
            final Player guest;
            final Player host;
            try {
                guest = selected.guest() == null
                        ? onlinePlayer(defaultGuest)
                        : onlinePlayer(CommandMap.findPlayer(selected.guest()));
                host = onlinePlayer(CommandMap.findPlayer(selected.host()));
            } catch (IllegalArgumentException mismatch) {
                CommandOutput.sendMessage(sender, mismatch.getMessage());
                return;
            }

            if (guest == null) {
                CommandOutput.sendMessage(sender, "Select an online guest player before using coop.");
                return;
            }
            if (host == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_offline_error");
                return;
            }
            // The dispatcher checks the base permission. Check the actual guest, not the
            // remembered default target, before moving another player's session.
            if (!Grasscutter.getPermissionHandler()
                    .checkPermission(sender, guest, "server.coop", "server.coop.others")) return;

            if (host == guest) {
                CommandOutput.sendMessage(sender, "A player cannot join their own world.");
                return;
            }
            if (guest.isInMultiplayer()) {
                guest.getServer().getMultiplayerSystem().leaveCoop(guest);
            }
            host.getServer().getMultiplayerSystem().applyEnterMp(guest, host.getUid());
            guest.getServer().getMultiplayerSystem().applyEnterMpReply(host, guest.getUid(), true);
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.coop.success", guest.getNickname(), host.getNickname());
        }

        private static Player onlinePlayer(Player player) {
            if (player == null) return null;
            var online = Grasscutter.getGameServer().getPlayerByUid(player.getUid());
            return online != null
                            && online.isOnline()
                            && online.getSession() != null
                            && online.getSession().isActive()
                    ? online
                    : null;
        }
    }
}
