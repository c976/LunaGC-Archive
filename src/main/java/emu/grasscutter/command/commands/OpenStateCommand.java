package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerProgressManager;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Query or override an open state, preferably by its descriptive name. */
@Command(
        label = "openstate",
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class OpenStateCommand implements CommandHandler {
    private record StateArg(int id) {}

    private record StateValue(int value) {}

    public static final class BooleanCandidates implements Iterable<String> {
        @Override
        public java.util.Iterator<String> iterator() {
            return List.of("true", "false").iterator();
        }
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var cli = new CommandLine(new Args(sender, targetPlayer));
        cli.registerConverter(StateArg.class, text -> new StateArg(OpenStateNames.parse(text)));
        cli.registerConverter(
                StateValue.class,
                text -> new StateValue(switch (text.toLowerCase(Locale.ROOT)) {
                    case "true", "1" -> 1;
                    case "false", "0" -> 0;
                    default -> throw new CommandLine.TypeConversionException(
                            "Open state value must be true or false (also accepts 1 or 0).");
                }));
        cli.addSubcommand("list", new ListStates(sender));
        return cli;
    }

    @CommandLine.Command(
            name = "openstate",
            description = "Query or set an open state by name (numeric ID also accepted).")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(
                index = "0",
                arity = "0..1",
                paramLabel = "<name|id>",
                completionCandidates = OpenStateNames.Candidates.class)
        private StateArg state;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[true|false]",
                completionCandidates = BooleanCandidates.class)
        private StateValue value;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (state == null) {
                OpenStateCommand.this.sendUsageMessage(sender);
                return;
            }
            int id = state.id();
            String display = OpenStateNames.display(id);
            if (!GameData.getOpenStateDataMap().containsKey(id)) {
                CommandOutput.sendMessage(sender, "Open state " + display
                        + " is not available in the loaded resources.");
                return;
            }
            if (value == null) {
                // Same saved-value/default precedence as the login snapshot.
                int current = targetPlayer.getOpenStates().containsKey(id)
                        ? targetPlayer.getProgressManager().getOpenState(id)
                        : PlayerProgressManager.DEFAULT_OPEN_STATES.contains(id) ? 1 : 0;
                CommandOutput.sendMessage(sender, "Open state " + display
                        + " = " + (current != 0));
                return;
            }

            targetPlayer.getProgressManager().forceSetOpenState(id, value.value());
            targetPlayer.save();
            CommandOutput.sendMessage(sender, "Open state " + display
                    + " = " + (value.value() != 0));
        }
    }

    @CommandLine.Command(name = "list", description = "Find available open state names and IDs.")
    private static final class ListStates implements Runnable {
        private static final int PAGE_SIZE = 20;

        private final Player sender;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[filter]")
        private String filter;

        @Option(names = "--page", defaultValue = "1", description = "1-based result page")
        private int page;

        private ListStates(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (page < 1) {
                CommandOutput.sendMessage(sender, "Page must be at least 1.");
                return;
            }
            var matches = OpenStateNames.entries(GameData.getOpenStateDataMap().keySet(), filter);
            if (matches.isEmpty()) {
                CommandOutput.sendMessage(sender, "No open states matched"
                        + (filter == null ? "." : ": " + filter));
                return;
            }
            int pages = 1 + (matches.size() - 1) / PAGE_SIZE;
            if (page > pages) {
                CommandOutput.sendMessage(sender, "Page out of range (1-" + pages + ").");
                return;
            }
            StringBuilder message = new StringBuilder(
                    "Open states (" + page + "/" + pages + ", " + matches.size() + " matches):");
            int start = (page - 1) * PAGE_SIZE;
            for (var entry : matches.subList(start, Math.min(start + PAGE_SIZE, matches.size()))) {
                message.append("\n  ").append(OpenStateNames.display(entry.id()));
            }
            if (page < pages) {
                message.append("\nNext: openstate list");
                if (filter != null) message.append(" ").append(filter);
                message.append(" --page ").append(page + 1);
            }
            CommandOutput.sendMessage(sender, message.toString());
        }
    }
}
