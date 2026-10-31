package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "player", targetRequirement = Command.TargetRequirement.NONE, inlineTarget = false)
public final class PlayerCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("list", new ListPlayers(sender));
        var info = new CommandLine(new InfoPlayer(sender));
        info.setExpandAtFiles(false);
        info.registerConverter(
                CommandMap.TargetSelector.class,
                value -> {
                    try {
                        return CommandMap.parseExplicitTargetSelector(value);
                    } catch (IllegalArgumentException invalid) {
                        throw new CommandLine.TypeConversionException(invalid.getMessage());
                    }
                });
        commandLine.addSubcommand("info", info);
        return commandLine;
    }

    @CommandLine.Command(name = "player")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            PlayerCommand.this.sendUsageMessage(sender);
        }
    }

    static String formatPlayer(String nickname, int uid, boolean styled) {
        return styled
                ? nickname + " <color=green>(" + uid + ")</color>"
                : nickname + " (" + uid + ")";
    }


    @CommandLine.Command(
            name = "info",
            description = "Inspect a player by @UID, username@, or username@UID.")
    private static final class InfoPlayer implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<playerSelector>")
        private CommandMap.TargetSelector selector;

        private InfoPlayer(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Player player;
            try {
                player = CommandMap.findPlayer(selector);
            } catch (IllegalArgumentException mismatch) {
                CommandOutput.sendMessage(sender, mismatch.getMessage());
                return;
            }
            if (player == null) {
                CommandOutput.sendMessage(sender, "Player not found.");
                return;
            }

            // Account names and exact location are administrative details.
            if (sender != null && sender.getUid() != player.getUid()
                    && (sender.getAccount() == null
                            || !sender.getAccount().hasPermission("server.playerinfo"))) {
                CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
                return;
            }

            var account = player.getAccount();
            boolean online = player.isOnline()
                    && player.getSession() != null
                    && player.getSession().isActive();
            var details = new StringBuilder()
                    .append("UID: ").append(player.getUid())
                    .append("\nNickname: ").append(player.getNickname())
                    .append("\nAccount: ").append(account == null ? "(unknown)" : account.getUsername())
                    .append("\nAdventure Rank: ").append(player.getLevel())
                    .append("\nWorld Level: ").append(player.getWorldLevel())
                    .append("\nOnline: ").append(online ? "yes" : "no");
            if (online) {
                details.append("\nScene: ").append(player.getSceneId());
                var position = player.getPosition();
                if (position != null) {
                    details.append("\nPosition: ")
                            .append(position.getX()).append(", ")
                            .append(position.getY()).append(", ")
                            .append(position.getZ());
                }
            }
            CommandOutput.sendMessage(sender, details.toString());
        }
    }

    @CommandLine.Command(name = "list")
    private static final class ListPlayers implements Runnable {
        private final Player sender;

        private ListPlayers(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Map<Integer, Player> playersMap = Grasscutter.getGameServer().getPlayers();

            CommandOutput.sendMessage(sender, translate(sender, "commands.player.success", playersMap.size()));
            if (playersMap.isEmpty()) return;

            String players =
                    playersMap.values().stream()
                            .map(player -> formatPlayer(player.getNickname(), player.getUid(), sender != null))
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("");
            CommandOutput.sendMessage(sender, players);
        }
    }
}
