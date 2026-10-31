package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.achievement.AchievementControlReturns;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "achievement",
        aliases = {"am"},
        permission = "player.achievement",
        permissionTargeted = "player.achievement.others",
        targetRequirement = Command.TargetRequirement.PLAYER,
        threading = true)
public final class AchievementCommand implements CommandHandler {
    private record AchievementTarget(Integer achievementId) {
        boolean all() {
            return achievementId == null;
        }
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("grant", new Grant(sender, targetPlayer));
        commandLine.addSubcommand("revoke", new Revoke(sender, targetPlayer));
        commandLine.addSubcommand("progress", new Progress(sender, targetPlayer));
        CommandHandler.registerConverterTree(commandLine,
                AchievementTarget.class,
                value -> {
                    if ("all".equalsIgnoreCase(value)) return new AchievementTarget(null);
                    try {
                        return new AchievementTarget(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException(
                                "Expected a numeric achievement ID or 'all'");
                    }
                });
        return commandLine;
    }

    @CommandLine.Command(name = "achievement")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            AchievementCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract static class AchievementCommandBase implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private AchievementCommandBase(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected Achievements achievements() {
            return Achievements.getByPlayer(targetPlayer);
        }
    }

    @CommandLine.Command(name = "grant")
    private static final class Grant extends AchievementCommandBase {
        @Parameters(index = "0", paramLabel = "<achievementId|all>")
        private AchievementTarget selection;

        private Grant(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (selection.all()) {
                int changed = achievements().grantAll();
                sendSuccessMessage(sender, "grantall", changed, targetPlayer.getNickname());
                return;
            }

            var result = achievements().grant(selection.achievementId());
            switch (result.getRet()) {
                case SUCCESS -> sendSuccessMessage(sender, "grant", targetPlayer.getNickname());
                case ACHIEVEMENT_NOT_FOUND -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey());
                case ALREADY_ACHIEVED -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey(), targetPlayer.getNickname());
            }
        }
    }

    @CommandLine.Command(name = "revoke")
    private static final class Revoke extends AchievementCommandBase {
        @Parameters(index = "0", paramLabel = "<achievementId|all>")
        private AchievementTarget selection;

        private Revoke(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (selection.all()) {
                int changed = achievements().revokeAll();
                sendSuccessMessage(sender, "revokeall", changed, targetPlayer.getNickname());
                return;
            }

            var result = achievements().revoke(selection.achievementId());
            switch (result.getRet()) {
                case SUCCESS -> sendSuccessMessage(sender, "revoke", targetPlayer.getNickname());
                case ACHIEVEMENT_NOT_FOUND -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey());
                case NOT_YET_ACHIEVED -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey(), targetPlayer.getNickname());
            }
        }
    }

    private static final class Progress extends AchievementCommandBase {
        @Parameters(index = "0", paramLabel = "<achievementId>")
        private int achievementId;

        @Parameters(index = "1", paramLabel = "<progress>")
        private int progress;

        private Progress(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var result = achievements().progress(achievementId, progress);
            switch (result.getRet()) {
                case SUCCESS -> sendSuccessMessage(
                        sender, "progress", targetPlayer.getNickname(), achievementId, progress);
                case ACHIEVEMENT_NOT_FOUND -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey());
            }
        }
    }

    private static void sendSuccessMessage(Player sender, String command, Object... args) {
        CommandOutput.sendTranslatedMessage(
                sender, AchievementControlReturns.Return.SUCCESS.getKey() + command, args);
    }
}
