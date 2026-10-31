package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "permission",
        permission = "permission",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class PermissionCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("add", new Add(sender, targetPlayer));
        commandLine.addSubcommand("remove", new Remove(sender, targetPlayer));
        commandLine.addSubcommand("clear", new Clear(sender, targetPlayer));
        commandLine.addSubcommand("list", new ListPermissions(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "permission")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            PermissionCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract static class PermissionAction implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private PermissionAction(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected Account account() {
            if (!Grasscutter.getPermissionHandler().EnablePermissionCommand()) {
                CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
                return null;
            }
            Account account = targetPlayer.getAccount();
            if (account == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.permission.account_error"));
            }
            return account;
        }
    }

    @CommandLine.Command(name = "add")
    private static final class Add extends PermissionAction {
        @Parameters(index = "0", paramLabel = "<permission>")
        private String permission;

        private Add(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Account account = account();
            if (account == null) return;
            if (account.addPermission(permission)) {
                DatabaseHelper.saveAccountFields(account, "permissions");
                CommandOutput.sendMessage(sender, translate(sender, "commands.permission.add"));
            } else {
                CommandOutput.sendMessage(sender, translate(sender, "commands.permission.has_error"));
            }
        }
    }

    @CommandLine.Command(name = "remove")
    private static final class Remove extends PermissionAction {
        @Parameters(index = "0", paramLabel = "<permission>")
        private String permission;

        private Remove(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Account account = account();
            if (account == null) return;
            if (account.removePermission(permission)) {
                DatabaseHelper.saveAccountFields(account, "permissions");
                CommandOutput.sendMessage(sender, translate(sender, "commands.permission.remove"));
            } else {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.permission.not_have_error"));
            }
        }
    }

    @CommandLine.Command(name = "clear")
    private static final class Clear extends PermissionAction {
        private Clear(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Account account = account();
            if (account == null) return;
            account.clearPermission();
            DatabaseHelper.saveAccountFields(account, "permissions");
            CommandOutput.sendMessage(sender, translate(sender, "commands.permission.remove"));
        }
    }

    @CommandLine.Command(name = "list")
    private static final class ListPermissions extends PermissionAction {
        private ListPermissions(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Account account = account();
            if (account == null) return;
            CommandOutput.sendMessage(sender, String.join("\n", account.getPermissions()));
        }
    }
}
