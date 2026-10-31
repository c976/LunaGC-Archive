package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.dailytask.DailyTaskManager;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "dailytask",
        aliases = {"dt"},
        permission = "player.dailytask",
        permissionTargeted = "player.dailytask.others",
        targetRequirement = Command.TargetRequirement.NONE)
public final class DailyTaskCommand implements CommandHandler {
    private record CityArg(int id) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("list", new ListTasks(sender, targetPlayer));
        commandLine.addSubcommand("load", new Load(sender, targetPlayer));
        commandLine.addSubcommand("reset", new Reset(sender, targetPlayer));
        commandLine.addSubcommand("city", new City(sender, targetPlayer));
        commandLine.addSubcommand("finish", new Finish(sender, targetPlayer));
        commandLine.addSubcommand("support", new Support(sender));
        commandLine.addSubcommand("bonus", new Bonus(sender, targetPlayer));
        CommandHandler.registerConverterTree(commandLine,
                CityArg.class,
                value -> {
                    Integer cityId = parseCityId(value);
                    if (cityId == null) {
                        throw new CommandLine.TypeConversionException(
                                "Expected random, a region name, or a city ID");
                    }
                    return new CityArg(cityId);
                });
        return commandLine;
    }

    @CommandLine.Command(name = "dailytask")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            DailyTaskCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract static class DailyCommand implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private DailyCommand(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected boolean loadManager() {
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                CommandOutput.sendMessage(sender, "This operation requires an online target player.");
                return false;
            }
            targetPlayer.loadDailyTaskManager();
            return true;
        }
    }

    @CommandLine.Command(name = "list")
    private static final class ListTasks extends DailyCommand {
        private ListTasks(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (!loadManager()) return;
            var manager = targetPlayer.getDailyTaskManager();
            CommandOutput.sendMessage(
                    sender,
                    "Daily commissions: date=%08d, filter=%s (%d), activeRegion=%s (%d), finished=%d/4, scoreReward=%d, bonusTaken=%s"
                            .formatted(
                                    manager.getLastGenerationDate(),
                                    getCityName(manager.getCityId()),
                                    manager.getCityId(),
                                    getCityName(manager.getActiveCityId()),
                                    manager.getActiveCityId(),
                                    manager.getFinishedCount(),
                                    manager.getScoreRewardId(),
                                    manager.isScoreRewardTaken()));

            if (manager.getDailyTasks().isEmpty()) {
                CommandOutput.sendMessage(sender, "No daily commissions are currently active.");
                return;
            }

            for (var task : manager.getDailyTasks()) {
                var data = GameData.getDailyTaskDataMap().get(task.getTaskId());
                String groups =
                        data == null || data.getNewGroupVec() == null
                                ? "[]"
                                : data.getNewGroupVec().toString();
                CommandOutput.sendMessage(
                        sender,
                        "Task %d: progress=%d/%d, finished=%s, reward=%d, groups=%s"
                                .formatted(
                                        task.getTaskId(),
                                        task.getProgress(),
                                        task.getFinishProgress(),
                                        task.isFinished(),
                                        task.getRewardId(),
                                        groups));
            }
        }
    }

    @CommandLine.Command(name = "load")
    private static final class Load extends DailyCommand {
        private Load(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (!loadManager()) return;
            if (targetPlayer.getScene() == null) {
                CommandOutput.sendMessage(sender, "The target player has no active scene.");
                return;
            }
            var manager = targetPlayer.getDailyTaskManager();
            int ready = manager.loadActiveGroups(targetPlayer.getScene());
            CommandOutput.sendMessage(
                    sender,
                    "Ready daily commission groups: %d/%d in scene %d."
                            .formatted(
                                    ready,
                                    manager.getActiveGroupIds().size(),
                                    targetPlayer.getSceneId()));
        }
    }

    @CommandLine.Command(name = "reset")
    private static final class Reset extends DailyCommand {
        private Reset(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (!loadManager()) return;
            var manager = targetPlayer.getDailyTaskManager();
            int count = manager.resetDailyTasks();
            CommandOutput.sendMessage(
                    sender,
                    "Generated %d daily commissions for city %d."
                            .formatted(count, manager.getCityId()));
        }
    }

    private static final class City extends DailyCommand {
        @Parameters(index = "0", paramLabel = "<random|cityId|region>")
        private CityArg city;

        private City(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (!loadManager()) return;
            var manager = targetPlayer.getDailyTaskManager();
            if (!manager.setCityIdAndReset(city.id())) {
                CommandOutput.sendMessage(
                        sender,
                        "Region %s (%d) does not contain at least four supported combat commissions."
                                .formatted(getCityName(city.id()), city.id()));
                CommandOutput.sendMessage(
                        sender, "Supported city IDs: " + manager.getSupportedCityIds());
                return;
            }
            CommandOutput.sendMessage(
                    sender,
                    "Daily commission filter: %s (%d). Today's active region: %s (%d)."
                            .formatted(
                                    getCityName(manager.getCityId()),
                                    manager.getCityId(),
                                    getCityName(manager.getActiveCityId()),
                                    manager.getActiveCityId()));
        }
    }

    private static final class Finish extends DailyCommand {
        @Parameters(index = "0", paramLabel = "<taskId>")
        private int taskId;

        private Finish(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (!loadManager()) return;
            var manager = targetPlayer.getDailyTaskManager();
            if (!manager.finishDailyTask(taskId)) {
                CommandOutput.sendMessage(
                        sender,
                        "Daily task %d was not active or was already finished.".formatted(taskId));
                return;
            }
            CommandOutput.sendMessage(sender, "Daily task %d completed.".formatted(taskId));
        }
    }

    @CommandLine.Command(name = "support")
    private static final class Support implements Runnable {
        private final Player sender;

        private Support(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            var cityIds =
                    GameData.getDailyTaskDataMap().values().stream()
                            .map(data -> data.getCityId())
                            .distinct()
                            .sorted()
                            .toList();
            CommandOutput.sendMessage(sender, "Daily commission Lua resource coverage:");
            for (int cityId : cityIds) {
                long defined = DailyTaskManager.getDefinedCombatTaskCount(cityId);
                long resourceBacked = DailyTaskManager.getResourceBackedTaskCount(cityId);
                CommandOutput.sendMessage(
                        sender,
                        "%s (%d): %d/%d combat commissions have usable encounter resources."
                                .formatted(
                                        getCityName(cityId), cityId, resourceBacked, defined));
            }
        }
    }

    @CommandLine.Command(name = "bonus")
    private static final class Bonus extends DailyCommand {
        private Bonus(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (!loadManager()) return;
            if (!targetPlayer.getDailyTaskManager().claimScoreReward()) {
                CommandOutput.sendMessage(
                        sender,
                        "The four-commission bonus cannot be claimed yet, was already claimed, or its reward data was unavailable.");
                return;
            }
            CommandOutput.sendMessage(sender, "Daily commission completion bonus claimed.");
        }
    }

    private static String getCityName(int cityId) {
        return switch (cityId) {
            case 0 -> "Random";
            case 1 -> "Mondstadt";
            case 2 -> "Liyue";
            case 3 -> "Inazuma";
            case 4 -> "Sumeru";
            case 5 -> "Fontaine";
            case 6 -> "Natlan";
            case 7 -> "Nod-Krai";
            default -> "City " + cityId;
        };
    }

    private static Integer parseCityId(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "random" -> 0;
            case "mondstadt" -> 1;
            case "liyue" -> 2;
            case "inazuma" -> 3;
            case "sumeru" -> 4;
            case "fontaine" -> 5;
            case "natlan" -> 6;
            case "nodkrai", "nod-krai", "nod_krai" -> 7;
            default -> {
                try {
                    yield Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    yield null;
                }
            }
        };
    }
}
