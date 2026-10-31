package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.SceneGroupInstance;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "group",
        aliases = {"gr"},
        permission = "player.group",
        permissionTargeted = "player.group.others")
public final class GroupCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var root = new CommandLine(new Root(sender));
        root.addSubcommand("refresh", new Refresh(sender, targetPlayer));
        return root;
    }

    @CommandLine.Command(name = "group")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            GroupCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "refresh")
    private static final class Refresh implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<groupId>")
        private int groupId;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[suiteId]")
        private Integer suiteId;

        private Refresh(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (targetPlayer.getScene() == null) {
                CommandOutput.sendMessage(sender, "Target player has no active scene.");
                return;
            }
            SceneGroupInstance groupInstance =
                    targetPlayer.getScene().getScriptManager().getGroupInstanceById(groupId);
            if (groupInstance == null) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.group.group_not_found", groupId));
                return;
            }

            if (suiteId == null) {
                targetPlayer.getScene().getScriptManager().refreshGroup(groupInstance);
            } else {
                targetPlayer.getScene().getScriptManager().refreshGroup(groupInstance, suiteId, false);
            }
            CommandOutput.sendMessage(sender, translate(sender, "commands.group.refreshed", groupId));
        }
    }
}
