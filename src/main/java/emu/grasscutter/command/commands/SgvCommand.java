package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.utils.Utils;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "sgv", aliases = {"serverglobalvalue"})
public final class SgvCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "sgv")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<name>")
        private String name;

        @Parameters(index = "1", paramLabel = "<value>")
        private float value;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            targetPlayer.sendPacket(
                    new PacketServerGlobalValueChangeNotify(
                            targetPlayer.getTeamManager().getEntity().getId(), name, value));
            CommandOutput.sendMessage(sender, String.valueOf(Utils.abilityHash(name)));
            CommandOutput.sendMessage(sender, "Changed Server Global Value for " + name);
            if (name.equalsIgnoreCase("SGV_PlayerTeam_Phlogiston")) {
                targetPlayer.setPhlogistonValue(value);
            }
        }
    }
}
