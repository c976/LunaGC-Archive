package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketBattlePassAllDataNotify;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassAllDataNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "battlepass",
        aliases = {"bp"},
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class BattlePassCommand implements CommandHandler {
    private record PaidFlag(boolean value) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("buy", new Buy(sender, targetPlayer));
        commandLine.addSubcommand("paid", new Paid(sender, targetPlayer));
        // Converters only reach the subcommands that exist when they are registered.
        CommandHandler.registerConverterTree(commandLine, PaidFlag.class, BattlePassCommand::parsePaidFlag);
        return commandLine;
    }

    private static PaidFlag parsePaidFlag(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "1", "yes", "paid" -> new PaidFlag(true);
            case "false", "off", "0", "no", "free" -> new PaidFlag(false);
            default -> throw new CommandLine.TypeConversionException(
                    "Expected true|false|on|off|1|0");
        };
    }

    @CommandLine.Command(name = "battlepass")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            BattlePassCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract static class PlayerCommand implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private PlayerCommand(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected Player player() {
            Player player = targetPlayer != null ? targetPlayer : sender;
            if (player == null) CommandOutput.sendMessage(sender, "No player.");
            return player;
        }

        protected BattlePassManager battlePass(Player player) {
            BattlePassManager battlePass = player.getBattlePassManager();
            if (battlePass == null) CommandOutput.sendMessage(sender, "No battle pass manager.");
            return battlePass;
        }
    }

    @CommandLine.Command(name = "buy")
    private static final class Buy extends PlayerCommand {
        @Parameters(index = "0", paramLabel = "<levels>")
        private int levels;

        private Buy(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Player player = player();
            if (player == null) return;
            if (levels <= 0) {
                CommandOutput.sendMessage(sender, "levels must be > 0");
                return;
            }

            BattlePassManager battlePass = battlePass(player);
            if (battlePass == null) return;

            var quote = BattlePassManager.quoteLevelPurchase(battlePass.getLevel(), levels);
            if (quote.levels() == 0) {
                CommandOutput.sendMessage(sender, "Already at max BP level.");
                return;
            }
            if (player.getPrimogems() < quote.cost()) {
                CommandOutput.sendMessage(
                        sender, "Need " + quote.cost() + " primogems, have " + player.getPrimogems());
                return;
            }

            int bought = battlePass.buyLevels(levels);
            if (bought == 0) {
                CommandOutput.sendMessage(sender, "BP level purchase failed; primogems were not charged.");
                return;
            }
            CommandOutput.sendMessage(
                    sender,
                    "Bought "
                            + bought
                            + " BP levels for "
                            + quote.cost()
                            + " primogems. Now level "
                            + battlePass.getLevel());
        }
    }

    @CommandLine.Command(name = "paid")
    private static final class Paid extends PlayerCommand {
        @Parameters(index = "0", arity = "0..1", paramLabel = "[true|false]")
        private PaidFlag paid;

        private Paid(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Player player = player();
            if (player == null) return;
            BattlePassManager battlePass = battlePass(player);
            if (battlePass == null) return;

            if (paid == null) {
                CommandOutput.sendMessage(sender, "Pearl BP paid=" + battlePass.isPaid());
                return;
            }

            battlePass.setPaid(paid.value());
            battlePass.save();
            player.sendPacket(new PacketBattlePassAllDataNotify(player));
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketBeyondBattlePassAllDataNotify(player));
            player.sendPacket(new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
            CommandOutput.sendMessage(
                    sender,
                    "Pearl BP paid set to "
                            + paid.value()
                            + " (isPaid="
                            + battlePass.isPaid()
                            + ")");
        }
    }
}
