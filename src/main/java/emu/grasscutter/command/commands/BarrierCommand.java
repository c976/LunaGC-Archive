package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketOpenStateChangeNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "barrier",
        aliases = {"br"},
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others",
        targetRequirement = Command.TargetRequirement.NONE)
public final class BarrierCommand implements CommandHandler {
    private enum State {
        ON,
        OFF
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.registerConverter(
                State.class,
                value ->
                        switch (value.toLowerCase()) {
                            case "on", "1" -> State.ON;
                            case "off", "0" -> State.OFF;
                            default -> throw new CommandLine.TypeConversionException(
                                    "Expected on/off or 1/0");
                        });
        return commandLine;
    }

    // The starter-region boundary uses OpenState 47 (LIMIT_REGION_FRESHMEAT), not 48.
    static PacketOpenStateChangeNotify barrierStatePacket(boolean enabled) {
        return new PacketOpenStateChangeNotify(47, enabled ? 0 : 1);
    }

    @CommandLine.Command(name = "barrier")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<on|off>")
        private State state;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (targetPlayer == null || !targetPlayer.isOnline()
                    || targetPlayer.getSession() == null || !targetPlayer.getSession().isActive()) {
                CommandOutput.sendMessage(sender, "Barrier control requires an online player.");
                return;
            }
            boolean enabled = state == State.ON;
            targetPlayer.sendPacket(barrierStatePacket(enabled));
            CommandOutput.sendMessage(sender, enabled ? "Restored barrier" : "Removed barrier");
        }
    }
}
