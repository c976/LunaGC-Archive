package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.trialavatar.TrialAvatarActivityHandler;
import emu.grasscutter.game.activity.trialavatar.TrialAvatarPlayerData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActivityType;
import emu.grasscutter.server.packet.send.PacketActivityInfoNotify;
import emu.grasscutter.utils.JsonUtils;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "trial",
        permission = "player.trialavataractivity",
        permissionTargeted = "player.trialavataractivity.others")
public final class TrialAvatarActivityCommand implements CommandHandler {
    private record Selection(Integer index) {
        private boolean all() {
            return index == null;
        }
    }

    private record Context(
            PlayerActivityData playerData,
            TrialAvatarActivityHandler handler,
            TrialAvatarPlayerData detail) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("change", new Change(sender, targetPlayer));
        commandLine.addSubcommand("toggledungeon", new ToggleDungeon(sender, targetPlayer));
        commandLine.addSubcommand("togglereward", new ToggleReward(sender, targetPlayer));
        CommandHandler.registerConverterTree(
                commandLine,
                Selection.class,
                value -> {
                    if (value.equalsIgnoreCase("all")) {
                        return new Selection(null);
                    }
                    try {
                        return new Selection(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException("Expected an index or 'all'");
                    }
                });
        return commandLine;
    }

    @CommandLine.Command(name = "trial")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            TrialAvatarActivityCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract class ActivityCommand implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private ActivityCommand(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected Context context() {
            var playerDataOption =
                    targetPlayer
                            .getActivityManager()
                            .getPlayerActivityDataByActivityType(
                                    ActivityType.NEW_ACTIVITY_TRIAL_AVATAR);
            if (playerDataOption.isEmpty()) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.trial.not_found"));
                return null;
            }

            var playerData = playerDataOption.get();
            var handler = (TrialAvatarActivityHandler) playerData.getActivityHandler();
            var detail = JsonUtils.decode(playerData.getDetail(), TrialAvatarPlayerData.class);
            if (handler == null || detail == null) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.trial.not_found"));
                return null;
            }
            return new Context(playerData, handler, detail);
        }

        protected void saveAndNotify(Context context) {
            context.playerData().setDetail(context.detail());
            context.playerData().save();
            targetPlayer.sendPacket(
                    new PacketActivityInfoNotify(
                            context.handler()
                                    .toProto(
                                            context.playerData(),
                                            targetPlayer
                                                    .getActivityManager()
                                                    .getConditionExecutor())));
        }
    }

    private final class Change extends ActivityCommand {
        @Parameters(index = "0", paramLabel = "<scheduleId>")
        private int scheduleId;

        private Change(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Context context = context();
            if (context == null) {
                return;
            }
            if (TrialAvatarPlayerData.getAvatarIdList(scheduleId).isEmpty()) {
                CommandOutput.sendMessage(
                        sender,
                        translate(
                                sender,
                                "commands.trial.schedule_not_found",
                                scheduleId));
                return;
            }

            context.playerData().setDetail(TrialAvatarPlayerData.create(scheduleId));
            context.playerData().save();
            CommandOutput.sendMessage(
                    sender,
                    translate(sender, "commands.trial.success_schedule", scheduleId));
            targetPlayer.sendPacket(
                    new PacketActivityInfoNotify(
                            context.handler()
                                    .toProto(
                                            context.playerData(),
                                            targetPlayer
                                                    .getActivityManager()
                                                    .getConditionExecutor())));
        }
    }

    private final class ToggleDungeon extends ActivityCommand {
        @Parameters(index = "0", paramLabel = "<index|all>")
        private Selection selection;

        private ToggleDungeon(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Context context = context();
            if (context == null) {
                return;
            }
            if (selection.all()) {
                context.detail().getRewardInfoList().forEach(
                        reward -> reward.setPassedDungeon(!reward.isPassedDungeon()));
                saveAndNotify(context);
                CommandOutput.sendMessage(
                        sender,
                        translate(sender, "commands.trial.success_dungeon_all"));
                return;
            }

            int offset = selection.index() - 1;
            if (offset < 0 || offset >= context.detail().getRewardInfoList().size()) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.trial.invalid_param"));
                return;
            }
            var reward = context.detail().getRewardInfoList().get(offset);
            reward.setPassedDungeon(!reward.isPassedDungeon());
            saveAndNotify(context);
            CommandOutput.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.trial.success_dungeon",
                            selection.index()));
        }
    }

    private final class ToggleReward extends ActivityCommand {
        @Parameters(index = "0", paramLabel = "<index|all>")
        private Selection selection;

        private ToggleReward(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Context context = context();
            if (context == null) {
                return;
            }
            if (selection.all()) {
                context.detail().getRewardInfoList().forEach(
                        reward -> reward.setReceivedReward(!reward.isReceivedReward()));
                saveAndNotify(context);
                CommandOutput.sendMessage(
                        sender,
                        translate(sender, "commands.trial.success_reward_all"));
                return;
            }

            int offset = selection.index() - 1;
            if (offset < 0 || offset >= context.detail().getRewardInfoList().size()) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.trial.invalid_param"));
                return;
            }
            var reward = context.detail().getRewardInfoList().get(offset);
            reward.setReceivedReward(!reward.isReceivedReward());
            saveAndNotify(context);
            CommandOutput.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.trial.success_reward",
                            selection.index()));
        }
    }
}
