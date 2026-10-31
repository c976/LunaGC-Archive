package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

@Command(
        label = "resetShopLimit",
        aliases = {"resetshop"},
        permission = "server.resetshop",
        permissionTargeted = "server.resetshop.others")
public final class ResetShopLimitCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "resetShopLimit")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            targetPlayer.getShopLimit().forEach(x -> x.setNextRefreshTime(0));
            targetPlayer.save();
            CommandOutput.sendMessage(sender, translate(sender, "commands.resetShopLimit.success"));
        }
    }
}
