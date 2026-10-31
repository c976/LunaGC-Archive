package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.lang.Language;
import java.util.*;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "quest",
        aliases = {"q"},
        permission = "player.quest",
        permissionTargeted = "player.quest.others")
public final class QuestCommand implements CommandHandler {
    private record QuestId(int value) {}

    private record ForceFinishTarget(Integer questId) {
        private boolean all() {
            return questId == null;
        }
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.setExpandAtFiles(false);

        commandLine.addSubcommand("forcefinish", new ForceFinish(sender, targetPlayer));
        commandLine.addSubcommand("add", new Add(sender, targetPlayer));
        commandLine.addSubcommand("finish", new Finish(sender, targetPlayer));
        commandLine.addSubcommand("running", new Running(sender, targetPlayer));
        commandLine.addSubcommand("talking", new Talking(sender, targetPlayer));
        commandLine.addSubcommand("dungeons", new Dungeons(sender, targetPlayer));
        commandLine.addSubcommand("debug", new Debug(sender, targetPlayer));
        commandLine.addSubcommand("triggers", new Triggers(sender, targetPlayer));
        commandLine.addSubcommand("grouptriggers", new GroupTriggers(sender, targetPlayer));
        commandLine.addSubcommand("list", new ListQuests(sender, targetPlayer));
        CommandHandler.registerConverterTree(commandLine,
                QuestId.class,
                value -> {
                    try {
                        return new QuestId(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException(
                                translate(sender, "commands.quest.invalid_id"));
                    }
                });
        CommandHandler.registerConverterTree(commandLine,
                ForceFinishTarget.class,
                value -> {
                    if ("all".equalsIgnoreCase(value)) return new ForceFinishTarget(null);
                    try {
                        return new ForceFinishTarget(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException(
                                translate(sender, "commands.quest.invalid_id"));
                    }
                });

        return commandLine;
    }

    @picocli.CommandLine.Command(name = "quest")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            QuestCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract class QuestIdCommand implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<questId>")
        protected QuestId questId;

        private QuestIdCommand(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }
    }

    @picocli.CommandLine.Command(name = "forcefinish")
    private final class ForceFinish implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<questId|all>")
        private ForceFinishTarget selection;

        private ForceFinish(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var ids =
                    selection.all()
                            ? emu.grasscutter.game.quest.ForcedQuests.allMainQuests()
                            : List.of(selection.questId());
            int added = emu.grasscutter.game.quest.ForcedQuests.apply(targetPlayer, ids);
            CommandOutput.sendMessage(
                    sender,
                    "Force-finished " + ids.size() + " main quest(s), " + added + " newly."
                            + " This is saved and re-sent on every login.");
        }
    }

    @picocli.CommandLine.Command(name = "add")
    private final class Add extends QuestIdCommand {
        private Add(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            // Works with questing off too: the added quest line is shown and tracked on its own.
            var quest = targetPlayer.getQuestManager().addQuestByCommand(questId.value());
            if (quest != null) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.quest.added", questId.value()));
                return;
            }
            CommandOutput.sendMessage(sender, translate(sender, "commands.quest.not_found"));
        }
    }

    @picocli.CommandLine.Command(name = "finish")
    private final class Finish extends QuestIdCommand {
        private Finish(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var quest = targetPlayer.getQuestManager().getQuestById(questId.value());
            if (quest == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.quest.not_found"));
                return;
            }
            quest.finish();
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.quest.finished", questId.value()));
        }
    }

    @picocli.CommandLine.Command(name = "running")
    private final class Running extends QuestIdCommand {
        private Running(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var quest = targetPlayer.getQuestManager().getQuestById(questId.value());
            if (quest == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.quest.not_found"));
                return;
            }

            CommandOutput.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.quest.running",
                            questId.value(),
                            translate(
                                    sender,
                                    switch (quest.state) {
                                        case QUEST_STATE_NONE, NONE -> "commands.quest.state.none";
                                        case QUEST_STATE_UNSTARTED, UNSTARTED ->
                                                "commands.quest.state.unstarted";
                                        case QUEST_STATE_UNFINISHED, UNFINISHED ->
                                                "commands.quest.state.unfinished";
                                        case QUEST_STATE_FINISHED, FINISHED ->
                                                "commands.quest.state.finished";
                                        case QUEST_STATE_FAILED, FAILED -> "commands.quest.state.failed";
                                    }),
                            quest.getState().getValue()));
        }
    }

    @picocli.CommandLine.Command(name = "talking")
    private final class Talking extends QuestIdCommand {
        private Talking(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var mainQuest = targetPlayer.getQuestManager().getMainQuestByTalkId(questId.value());
            if (mainQuest == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.quest.not_found"));
                return;
            }

            var talk = mainQuest.getTalks().get(questId.value());
            CommandOutput.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.quest.talking",
                            questId.value(),
                            talk == null
                                    ? translate(sender, "commands.quest.state.not_exists")
                                    : translate(sender, "commands.quest.state.exists"),
                            mainQuest.getParentQuestId(),
                            mainQuest.getState().getValue()));
        }
    }

    @picocli.CommandLine.Command(name = "dungeons")
    private final class Dungeons implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Dungeons(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var dungeons = targetPlayer.getPlayerProgress().getCompletedDungeons();
            CommandOutput.sendMessage(
                    sender,
                    "Dungeons completed: "
                            + String.join(", ", dungeons.intStream().mapToObj(String::valueOf).toList()));
        }
    }

    @picocli.CommandLine.Command(name = "debug")
    private final class Debug extends QuestIdCommand {
        private Debug(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var loggedQuests = targetPlayer.getQuestManager().getLoggedQuests();
            var shouldAdd = !loggedQuests.contains(questId.value());
            if (shouldAdd) loggedQuests.add(questId.value());
            else loggedQuests.remove(questId.value());

            CommandOutput.sendMessage(
                    sender,
                    "Quest %s will %s."
                            .formatted(
                                    questId.value(),
                                    shouldAdd ? "now be logged" : "no longer be logged"));
        }
    }

    @picocli.CommandLine.Command(name = "triggers")
    private final class Triggers extends QuestIdCommand {
        private Triggers(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var quest = targetPlayer.getQuestManager().getQuestById(questId.value());
            if (quest == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.quest.not_found"));
                return;
            }
            CommandOutput.sendMessage(
                    sender,
                    "Triggers registered for %s: %s."
                            .formatted(
                                    questId.value(),
                                    String.join(", ", quest.getTriggers().keySet())));
        }
    }

    @picocli.CommandLine.Command(name = "grouptriggers")
    private final class GroupTriggers extends QuestIdCommand {
        private GroupTriggers(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (targetPlayer.getScene() == null) {
                CommandOutput.sendMessage(sender, "Target player has no active scene.");
                return;
            }
            var scriptManager = targetPlayer.getScene().getScriptManager();
            var group = scriptManager.getGroupById(questId.value());
            if (group == null) {
                CommandOutput.sendMessage(sender, "The group does not exist.");
                return;
            }

            CommandOutput.sendMessage(
                    sender,
                    group.triggers.entrySet().stream()
                            .map(entry -> "%s: %s".formatted(entry.getKey(), entry.getValue()))
                            .collect(Collectors.joining(", ")));
        }
    }

    // A quest may be running in the server while deliberately hidden from the client.
    // This is common for statue / waypoint unlock bookkeeping (e.g. main quest 303).
    static List<GameQuest> activeQuests(
            Collection<emu.grasscutter.game.quest.GameMainQuest> mainQuests,
            boolean includeHidden) {
        return mainQuests.stream()
                .flatMap(main -> main.getActiveQuests().stream())
                .filter(quest ->
                        includeHidden
                                || quest.getQuestData() == null
                                || !quest.getQuestData().isHidden())
                .sorted(Comparator.comparingInt(GameQuest::getSubQuestId))
                .toList();
    }

    /** Reject placeholder strings generated when the requested hash is absent from TextMap. */
    static String readableQuestText(String text) {
        if (text == null || text.isBlank() || text.startsWith("[N/A]")) return null;
        return text.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String questName(long textMapHash, String language) {
        if (textMapHash == 0) return "Unknown";
        // The Quest and TextMap dumps can differ by 512 hashes, as with item names.
        for (long candidate : new long[] {textMapHash, textMapHash + 512L}) {
            var text = Language.getTextMapKey(candidate);
            if (text == null) continue;
            var name = readableQuestText(text.getGC(language));
            if (name != null) return name;
        }
        return "Unknown";
    }

    @picocli.CommandLine.Command(name = "list")
    private final class ListQuests implements Runnable {
        private static final int PAGE_SIZE = 10;
        private final Player sender;
        private final Player targetPlayer;

        @picocli.CommandLine.Option(names = {"-a", "--all"}, description = "Include hidden internal quests")
        private boolean includeHidden;

        @picocli.CommandLine.Option(names = {"-p", "--page"}, defaultValue = "1", description = "Page number")
        private int page;

        @picocli.CommandLine.Option(names = {"--lang"}, description = "Quest text language, e.g. zh-CN")
        private String requestedLanguage;

        private ListQuests(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var mainQuests = targetPlayer.getQuestManager().getActiveMainQuests();
            var all = activeQuests(mainQuests, true);
            var visible = activeQuests(mainQuests, false);
            var selected = includeHidden ? all : visible;
            var hiddenCount = all.size() - visible.size();

            int pages = Math.max(1, (selected.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page < 1 || page > pages) {
                CommandOutput.sendMessage(sender, "Quest list page must be between 1 and " + pages + ".");
                return;
            }

            String language = requestedLanguage != null
                    ? requestedLanguage
                    : Utils.getLanguageCode(targetPlayer.getAccount().getLocale());
            if (!Language.TextStrings.MAP_GC_LANGUAGES.containsKey(language)) {
                CommandOutput.sendMessage(sender, "Unsupported quest text language: " + language);
                return;
            }
            String header =
                    "Quests: " + selected.size() + " active"
                            + (includeHidden ? " (including hidden)" : ", " + hiddenCount + " hidden")
                            + " - page " + page + "/" + pages;

            var entries =
                    selected.subList((page - 1) * PAGE_SIZE, Math.min(page * PAGE_SIZE, selected.size()))
                            .stream()
                            .map(quest -> {
                                QuestData data = quest.getQuestData();
                                var mainData = GameData.getMainQuestDataMap().get(quest.getMainQuestId());
                                String name = questName(data == null ? 0 : data.getDescTextMapHash(), language);
                                String mainName = questName(mainData == null ? 0 : mainData.getTitleTextMapHash(), language);
                                return "%d: %s (main %d: %s)%s"
                                        .formatted(
                                                quest.getSubQuestId(),
                                                name,
                                                quest.getMainQuestId(),
                                                mainName,
                                                data != null && data.isHidden() ? " [hidden]" : "");
                            })
                            .toList();
            CommandOutput.sendMessage(
                    sender,
                    header
                            + (entries.isEmpty() ? "\n(no active quests)" : "\n" + String.join("\n", entries))
                            + (!includeHidden && hiddenCount > 0
                                    ? "\nUse quest list --all to inspect hidden quests."
                                    : ""));
        }
    }
}
