package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.SoundRegistry;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.packet.send.PacketScenePlayerSoundNotify;
import java.util.List;
import java.util.Stack;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        label = "sound",
        aliases = {"audio"},
        permission = "player.sound",
        permissionTargeted = "player.sound.others")
public final class SoundCommand implements CommandHandler {
    private static final int PAGE_SIZE = 20;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var cli = new CommandLine(new Play(sender, targetPlayer));
        cli.addSubcommand("list", new ListSounds(sender));
        return cli;
    }

    /**
     * Picocli treats the optional coordinates as one logical three-value argument.
     * Incomplete coordinates fail during parsing, not after command execution.
     */
    public static final class CoordinatesConsumer implements CommandLine.IParameterConsumer {
        @Override
        public void consumeParameters(Stack<String> arguments, ArgSpec spec, CommandSpec command) {
            if (arguments.size() < 3) {
                throw new CommandLine.ParameterException(command.commandLine(),
                        "Provide all three coordinates x y z, or omit all of them.");
            }
            float[] values = new float[3];
            for (int i = 0; i < 3; i++) {
                String next = arguments.pop();
                try {
                    values[i] = Float.parseFloat(next);
                } catch (NumberFormatException exception) {
                    throw new CommandLine.ParameterException(command.commandLine(),
                            "Invalid coordinate: " + next, exception);
                }
                if (!Float.isFinite(values[i])) {
                    throw new CommandLine.ParameterException(command.commandLine(),
                            "Coordinates must be finite numbers.");
                }
            }
            spec.setValue(values);
        }
    }

    @CommandLine.Command(name = "sound", description = "Play a sound or list observed sound names.")
    private static final class Play implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "<sound>",
                completionCandidates = SoundRegistry.Candidates.class)
        private String name;

        @Parameters(index = "1..3", arity = "0..3", paramLabel = "[x y z]",
                parameterConsumer = CoordinatesConsumer.class)
        private float[] coordinates = new float[0];

        private Play(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (name == null) {
                CommandOutput.sendMessage(sender, "Usage: sound <sound> [x y z] or sound list [filter] [--page N]");
                return;
            }
            Position playPosition = coordinates.length == 3
                    ? new Position(coordinates[0], coordinates[1], coordinates[2])
                    : targetPlayer.getPosition();
            targetPlayer.getScene().broadcastPacket(
                    new PacketScenePlayerSoundNotify(playPosition, name, 1));
            CommandOutput.sendMessage(sender, "Sent sound: " + name);
        }
    }

    @CommandLine.Command(name = "list", description = "List observed 7.1 sound event names (not exhaustive).")
    private static final class ListSounds implements Runnable {
        private final Player sender;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[filter]")
        private String filter;

        @Option(names = "--page", defaultValue = "1", description = "1-based result page")
        private int page;

        private ListSounds(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (page < 1) {
                CommandOutput.sendMessage(sender, "Page must be at least 1.");
                return;
            }

            List<String> matches = SoundRegistry.names(filter);
            if (matches.isEmpty()) {
                CommandOutput.sendMessage(sender, "No observed sound names match this search. "
                        + "Names are learned when scene scripts or sound commands play them.");
                return;
            }
            int pages = 1 + (matches.size() - 1) / PAGE_SIZE;
            if (page > pages) {
                CommandOutput.sendMessage(sender, "Page out of range (1-" + pages + ").");
                return;
            }

            CommandOutput.sendMessage(sender, "Observed sounds ("
                    + page + "/" + pages + ", " + matches.size()
                    + " matches; not a complete catalog):");
            int start = (page - 1) * PAGE_SIZE;
            for (String name : matches.subList(start, Math.min(start + PAGE_SIZE, matches.size()))) {
                CommandOutput.sendMessage(sender, "  " + name);
            }
            if (page < pages) {
                CommandOutput.sendMessage(sender, "Next: sound list "
                        + (filter == null ? "" : filter + " ")
                        + "--page " + (page + 1));
            }
        }
    }
}
