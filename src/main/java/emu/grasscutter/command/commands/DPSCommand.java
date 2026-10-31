package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.dps.DPSMeter;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Controls the in-server DPS test. */
@Command(label = "dps", targetRequirement = Command.TargetRequirement.NONE)
public final class DPSCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var root = new CommandLine(new Root(sender));
        root.addSubcommand("start", new Start(sender, targetPlayer));
        root.addSubcommand("stop", new Stop(sender, targetPlayer));
        return root;
    }

    @CommandLine.Command(name = "dps")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            DPSCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "start")
    private static final class Start implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[seconds]")
        private Integer seconds;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[targetCount]")
        private Integer targetCount;

        private Start(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!onlineTarget(sender, targetPlayer)) return;
            int duration = seconds == null ? DPSMeter.DEFAULT_SECONDS : seconds;
            int count = targetCount == null ? 1 : targetCount;
            if (duration < DPSMeter.MIN_SECONDS || duration > DPSMeter.MAX_SECONDS
                    || count < 1 || count > DPSMeter.MAX_TARGETS) {
                CommandOutput.sendMessage(sender, "seconds must be 1..120 and targetCount must be 1..10.");
                return;
            }
            DPSMeter.start(targetPlayer, duration, count);
        }
    }

    @CommandLine.Command(name = "stop")
    private static final class Stop implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Stop(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!onlineTarget(sender, targetPlayer)) return;
            DPSMeter.stop(targetPlayer);
        }
    }
    private static boolean onlineTarget(Player sender, Player target) {
        if (target == null || !target.isOnline() || target.getScene() == null) {
            CommandOutput.sendMessage(sender, "DPS operations require an online player in a scene.");
            return false;
        }
        return true;
    }
}
