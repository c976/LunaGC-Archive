package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.BannedIp;
import emu.grasscutter.game.player.Player;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "ban", targetRequirement = Command.TargetRequirement.NONE, inlineTarget = false)
public final class BanCommand implements CommandHandler {
    private static final int DEFAULT_BAN_END = 2051190000;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var cli = new CommandLine(new BanTargetCommand(sender));
        cli.setExpandAtFiles(false);
        return cli;
    }

    enum TargetType { PLAYER, IPV4 }
    record BanTarget(TargetType type, String value) {}
    record BanArguments(BanTarget target, int endTime, String reason) {}

    static BanTarget parseTarget(String value) {
        return parseTarget(value, "ban");
    }

    static BanTarget parseTarget(String value, String commandName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Specify <playerSelector|IPv4>. See help " + commandName + ".");
        }
        if (value.contains("@")) {
            CommandMap.parseExplicitTargetSelector(value);
            return new BanTarget(TargetType.PLAYER, value);
        }
        // Only a complete IPv4 address may omit the explicit player-selector suffix.
        if (value.matches("[0-9]+(\\.[0-9]+){3}")) {
            for (String octet : value.split("\\.")) {
                if (octet.length() > 3
                        || (octet.length() > 1 && octet.startsWith("0"))
                        || Integer.parseInt(octet) > 255) {
                    throw new IllegalArgumentException("Invalid IPv4 address: " + value);
                }
            }
            return new BanTarget(TargetType.IPV4, value);
        }
        throw new IllegalArgumentException(
                "Invalid ban target: " + value + ". Use <playerSelector|IPv4>. See help " + commandName + ".");
    }

    static BanArguments parseBanArguments(String target, List<String> trailing) {
        var parsed = parseTarget(target);
        int endTime = DEFAULT_BAN_END;
        int offset = 0;
        if (!trailing.isEmpty() && trailing.getFirst().matches("-?[0-9]+")) {
            try {
                endTime = Integer.parseInt(trailing.getFirst());
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid ban end time: " + trailing.getFirst());
            }
            offset = 1;
        }
        if (parsed.type() == TargetType.IPV4) {
            // Preserve the previous indefinite IP-ban default when endTime is omitted.
            if (offset == 0) {
                endTime = 0;
            } else if (endTime < 0
                    || (endTime != 0 && endTime <= System.currentTimeMillis() / 1000)) {
                throw new IllegalArgumentException(
                        "IP ban endTime must be 0 (permanent) or a future Unix timestamp.");
            }
        }
        String reason = trailing.size() == offset
                ? "Reason not specified."
                : String.join(" ", trailing.subList(offset, trailing.size()));
        return new BanArguments(parsed, endTime, reason);
    }

    @CommandLine.Command(name = "ban",
            description = "playerSelector: @UID (UID), username@ (account), username@UID (both match).",
            customSynopsis = "ban <playerSelector|IPv4> [endTime] [reason...]")
    private static final class BanTargetCommand implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<playerSelector|IPv4>")
        private String target;

        @Parameters(index = "1..*", arity = "0..*", paramLabel = "[endTime] [reason...]")
        private List<String> trailing = List.of();

        private BanTargetCommand(Player sender) { this.sender = sender; }

        @Override
        public void run() {
            final BanArguments args;
            try {
                args = parseBanArguments(target, trailing);
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }

            if (args.target().type() == TargetType.IPV4) {
                if (!hasPermission(sender, "server.banip", null)) return;
                new BannedIp(args.target().value(), args.reason(), args.endTime()).save();
                CommandOutput.sendMessage(
                        sender,
                        "Banned IP " + args.target().value()
                                + (args.endTime() == 0
                                        ? " permanently."
                                        : " until " + args.endTime() + ".")
                                + " Reason: " + args.reason());
                return;
            }

            final Account account;
            try {
                account = CommandMap.findAccount(
                        CommandMap.parseExplicitTargetSelector(args.target().value()));
            } catch (IllegalArgumentException mismatch) {
                CommandOutput.sendMessage(sender, mismatch.getMessage());
                return;
            }

            if (account == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_exist_error");
                return;
            }

            boolean someoneElse = sender != null
                    && !sender.getAccount().getId().equals(account.getId());
            if (!hasPermission(sender, "server.ban",
                    someoneElse ? "server.ban.others" : null)) return;

            int startTime = (int) (System.currentTimeMillis() / 1000);
            DatabaseHelper.updateAccount(
                    account,
                    banned -> {
                        banned.setBanReason(args.reason());
                        banned.setBanEndTime(args.endTime());
                        banned.setBanStartTime(startTime);
                        banned.setBannedByIp(null);
                        banned.setBanned(true);
                    },
                    DatabaseHelper.BAN_FIELDS);

            Player online = Grasscutter.getGameServer().getPlayerByAccountId(account.getId());
            if (online != null && online.getSession() != null) online.getSession().close();
            CommandOutput.sendTranslatedMessage(sender, "commands.ban.success");
        }
    }

    static boolean hasPermission(Player sender, String permission, String additional) {
        if (sender == null) return true;
        Account account = sender.getAccount();
        if (account != null && account.hasPermission(permission)
                && (additional == null || account.hasPermission(additional))) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
