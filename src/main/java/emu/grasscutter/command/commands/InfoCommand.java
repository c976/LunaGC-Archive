package emu.grasscutter.command.commands;

import emu.grasscutter.BuildConfig;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.tools.Tools;
import picocli.CommandLine;

@Command(
        label = "info",
        targetRequirement = Command.TargetRequirement.NONE)
public final class InfoCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "info")
    private static final class Args implements Runnable {
        private final Player sender;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            var build = "%s (%s)".formatted(BuildConfig.VERSION, BuildConfig.GIT_HASH);
            var playerCount = Grasscutter.getGameServer().getPlayers().size();
            var resourceInfo = Tools.resourcesInfo();

            var config = Grasscutter.getConfig();
            var gameOptions = config.server.game;
            var questingEnabled = gameOptions.gameOptions.questing.enabled;
            var scriptsEnabled = gameOptions.enableScriptInBigWorld;
            var fastRequire = config.server.fastRequire;

            if (sender == null
                    || sender.getAccount().hasPermission("grasscutter.command.troubleshoot")
                    || playerCount == 1) {
                CommandOutput.sendMessage(
                        sender,
                        """
                        Server Information
                        Revision: %s
                        Player Count: %d
                        Questing Enabled: %s
                        Scripts Enabled: %s
                        Using Fast Require: %s
                        Operating System: %s
                        Resource Information: %s"""
                                .formatted(
                                        build,
                                        playerCount,
                                        questingEnabled,
                                        scriptsEnabled,
                                        fastRequire,
                                        System.getProperty("os.name"),
                                        resourceInfo));
            } else {
                CommandOutput.sendMessage(
                        sender,
                        "Server revision: " + build + "\nOnline players: " + playerCount);
            }
        }
    }
}
