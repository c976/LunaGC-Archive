package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "help", targetRequirement = Command.TargetRequirement.NONE)
public final class HelpCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "help")
    private final class Args implements Runnable {
        private final Player player;

        @Parameters(index = "0..*", arity = "0..*", paramLabel = "[command [subcommand...]]")
        private List<String> commandPath = List.of();

        private Args(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            Account account = player == null ? null : player.getAccount();
            var commandMap = CommandMap.getInstance();

            if (commandPath.isEmpty()) {
                CommandOutput.sendTranslatedMessage(player, "commands.help.available_commands");
                commandMap.getHandlers().forEach((label, handler) -> {
                    if (isVisible(player, account, handler)) {
                        CommandOutput.sendMessage(player, summarize(player, handler));
                    }
                });
                return;
            }

            CommandHandler handler = commandMap.getHandler(commandPath.getFirst());
            if (handler == null) {
                CommandOutput.sendTranslatedMessage(player, "commands.generic.command_exist_error");
                CommandOutput.sendMessage(player, "Command: " + commandPath.getFirst());
                return;
            }
            if (!isVisible(player, account, handler)) {
                CommandOutput.sendTranslatedMessage(player, "commands.generic.permission_error");
                return;
            }

            CommandLine root = handler.createCommandLine(player, null);
            CommandLine selected = findSubcommand(root, commandPath.subList(1, commandPath.size()));
            if (selected == null) {
                CommandOutput.sendMessage(player, "Unknown subcommand: " + commandPath.getLast());
                CommandOutput.sendMessage(player, root.getUsageMessage().stripTrailing());
                return;
            }
            CommandOutput.sendMessage(player, describe(player, handler, selected));
        }
    }

    static CommandLine findSubcommand(CommandLine root, List<String> names) {
        CommandLine current = root;
        for (String name : names) {
            current = current.getSubcommands().get(name.toLowerCase(Locale.ROOT));
            if (current == null) return null;
        }
        return current;
    }

    private static boolean isVisible(Player player, Account account, CommandHandler handler) {
        String permission = handler.getClass().getAnnotation(Command.class).permission();
        return player == null || account != null && account.hasPermission(permission);
    }

    private static String summarize(Player player, CommandHandler handler) {
        Command metadata = handler.getClass().getAnnotation(Command.class);
        String summary = handler.getLabel() + " - " + handler.getDescriptionString(player);
        return metadata.aliases().length == 0
                ? summary
                : summary + " (" + translate(player, "commands.help.aliases")
                        + String.join(", ", metadata.aliases()) + ")";
    }

    private static String describe(Player player, CommandHandler handler, CommandLine commandLine) {
        Command metadata = handler.getClass().getAnnotation(Command.class);
        StringBuilder builder = new StringBuilder(handler.getLabel())
                .append(" - ")
                .append(handler.getDescriptionString(player))
                .append("\n\t")
                .append(commandLine.getUsageMessage().stripTrailing());

        if (metadata.aliases().length > 0) {
            builder.append("\n\t").append(translate(player, "commands.help.aliases"))
                    .append(String.join(", ", metadata.aliases()));
        }

        builder.append("\n\t").append(translate(player, "commands.help.tip_need_permission"));
        if (metadata.permission().isEmpty()) {
            builder.append(translate(player, "commands.help.tip_need_no_permission"));
        } else {
            builder.append(metadata.permission());
        }
        if (!metadata.permissionTargeted().isEmpty()) {
            builder.append(' ').append(translate(
                    player, "commands.help.tip_permission_targeted", metadata.permissionTargeted()));
        }
        return builder.toString();
    }
}
