package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.SpecialtyMaterialTrackHelper;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "trackmat",
        aliases = {"trackmaterial"},
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class TrackMatCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var root = new CommandLine(new Track(sender, targetPlayer));
        root.addSubcommand("clear", new Clear(sender, targetPlayer));
        return root;
    }

    @CommandLine.Command(name = "trackmat")
    private static final class Track implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<itemId|materialName>")
        private String[] material;

        private Track(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            String query = String.join(" ", material);
            int itemId = SpecialtyMaterialTrackHelper.resolveItemId(query);
            if (itemId <= 0) {
                CommandOutput.sendMessage(sender, "Unrecognised material: " + query);
                return;
            }

            int count = SpecialtyMaterialTrackHelper.track(targetPlayer, itemId);
            if (count <= 0) {
                CommandOutput.sendMessage(
                        sender, "No specialty point data found for itemId=" + itemId + ".");
                return;
            }

            CommandOutput.sendMessage(
                    sender,
                    "Marked material "
                            + itemId
                            + ", "
                            + count
                            + " gather points, on the world map. Use /trackmat clear to remove them.");
        }
    }

    @CommandLine.Command(name = "clear")
    private static final class Clear implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Clear(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int count = SpecialtyMaterialTrackHelper.clear(targetPlayer);
            CommandOutput.sendMessage(sender, "Cleared " + count + " material map markers.");
        }
    }
}
