package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

@Command(
        label = "position",
        aliases = {"pos"})
public final class PositionCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "position")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var pos = targetPlayer.getPosition();
            var rot = targetPlayer.getRotation();
            CommandOutput.sendTranslatedMessage(
                    sender,
                    "commands.position.success",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    rot.getX(),
                    rot.getY(),
                    rot.getZ(),
                    targetPlayer.getSceneId());
        }
    }
}
