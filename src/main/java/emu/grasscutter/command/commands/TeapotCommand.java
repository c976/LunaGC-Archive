package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketHomeBasicInfoNotify;
import emu.grasscutter.server.packet.send.PacketPlayerHomeCompInfoNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "teapot",
        permission = "player.teapot",
        permissionTargeted = "player.teapot.others")
public final class TeapotCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("setlevel", new SetLevel(sender, targetPlayer));
        commandLine.addSubcommand("unlockmodule", new UnlockModule(sender, targetPlayer));
        commandLine.addSubcommand("lockmodule", new LockModule(sender, targetPlayer));
        commandLine.addSubcommand("giveallfurniture", new GiveFurniture(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "teapot")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            TeapotCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract static class PlayerCommand implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private PlayerCommand(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }
    }

    @CommandLine.Command(name = "setlevel")
    private static final class SetLevel extends PlayerCommand {
        @Parameters(index = "0", paramLabel = "<level>")
        private int level;

        private SetLevel(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (level < 1 || level > 10) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.teapot.level_range_error"));
                return;
            }
            targetPlayer.getHome().setLevel(level);
            targetPlayer.getHome().save();
            targetPlayer.sendPacket(new PacketHomeBasicInfoNotify(targetPlayer, false));
            CommandOutput.sendMessage(sender, translate(sender, "commands.teapot.level_success", level));
        }
    }

    @CommandLine.Command(name = "unlockmodule")
    private static final class UnlockModule extends PlayerCommand {
        @Parameters(index = "0", paramLabel = "<1-4>")
        private int module;

        private UnlockModule(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (!validModule(sender, module)) return;
            if (targetPlayer.getRealmList() != null && targetPlayer.getRealmList().contains(module)) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.teapot.unlock_module_contain_error"));
                return;
            }

            targetPlayer.addRealmList(module);
            targetPlayer.save();
            targetPlayer.sendPacket(new PacketPlayerHomeCompInfoNotify(targetPlayer));
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.teapot.unlock_module_success", module));
        }
    }

    @CommandLine.Command(name = "lockmodule")
    private static final class LockModule extends PlayerCommand {
        @Parameters(index = "0", paramLabel = "<1-4>")
        private int module;

        private LockModule(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (!validModule(sender, module)) return;
            if (module == targetPlayer.getCurrentRealmId()) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.teapot.lock_module_in_scene_error"));
                return;
            }
            if (targetPlayer.getRealmList() == null || !targetPlayer.getRealmList().contains(module)) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.teapot.lock_module_contain_error"));
                return;
            }

            int outdoorSceneId = module + 2000;
            targetPlayer.getHome().getSceneMap().remove(outdoorSceneId);
            targetPlayer.getHome().getMainHouseMap().remove(outdoorSceneId);
            targetPlayer.getHome().save();
            targetPlayer.getRealmList().remove(module);
            targetPlayer.save();
            targetPlayer.sendPacket(new PacketPlayerHomeCompInfoNotify(targetPlayer));
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.teapot.lock_module_success", module));
        }
    }

    @CommandLine.Command(name = "giveallfurniture")
    private static final class GiveFurniture extends PlayerCommand {
        @Parameters(index = "0", paramLabel = "<count>")
        private int count;

        private GiveFurniture(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (count <= 0) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.teapot.invalid_param"));
                return;
            }
            for (ItemData item : GameData.getItemDataMap().values()) {
                if (item.getFurnType() == null || item.getFurnType().isEmpty()) continue;
                targetPlayer.getInventory().addItem(item.getId(), count);
            }
            CommandOutput.sendMessage(sender, translate(sender, "commands.teapot.give_furniture_success"));
        }
    }

    private static boolean validModule(Player sender, int module) {
        if (module > 4) {
            CommandOutput.sendMessage(sender, translate(sender, "commands.teapot.module_sumeru_error"));
            return false;
        }
        if (module < 1) {
            CommandOutput.sendMessage(sender, translate(sender, "commands.teapot.module_range_error"));
            return false;
        }
        return true;
    }
}
