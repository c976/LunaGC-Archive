package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CutsceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketCutsceneBeginNotify;
import java.util.Comparator;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        label = "cutscene",
        aliases = {"c"},
        permission = "player.cutscene",
        permissionTargeted = "player.cutscene.others",
        targetRequirement = Command.TargetRequirement.NONE)
public final class CutsceneCommand implements CommandHandler {
    private static final int MAX_RESULTS = 30;

    static boolean mayPlayId(int id, boolean known, boolean force) {
        return id > 0 && (known || force);
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var root = new CommandLine(new Play(sender, targetPlayer));
        root.addSubcommand("list", new ListCutscenes(sender));
        return root;
    }

    @CommandLine.Command(name = "cutscene")
    private static final class Play implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[cutsceneId]")
        private Integer cutsceneId;

        @Option(names = "--force", description = "Send an ID missing from CutsceneExcelConfigData")
        private boolean force;

        private Play(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (cutsceneId == null) {
                new CutsceneCommand().sendUsageMessage(sender);
                return;
            }
            if (targetPlayer == null || !targetPlayer.isOnline() || targetPlayer.getSession() == null
                    || !targetPlayer.getSession().isActive()) {
                CommandOutput.sendMessage(sender, "Playing a cutscene requires an online player.");
                return;
            }
            if (GAME_OPTIONS.disableCutscenes) {
                CommandOutput.sendMessage(sender, "Cutscenes are disabled by game.disableCutscenes.");
                return;
            }

            var data = GameData.getCutsceneDataMap().get(cutsceneId);
            if (!mayPlayId(cutsceneId, data != null, force)) {
                CommandOutput.sendMessage(sender,
                        "Unknown cutscene ID " + cutsceneId
                                + ". Use --force to send an unlisted positive ID.");
                return;
            }

            var scene = targetPlayer.getScene();
            if ((scene != null && scene.getScriptManager().hasPendingCutscene(cutsceneId))
                    || targetPlayer.getTowerManager().isAwaitingCutsceneFinish(cutsceneId)) {
                CommandOutput.sendMessage(sender,
                        "Cutscene " + cutsceneId
                                + " is awaited by gameplay; manual playback was not started.");
                return;
            }

            var session = targetPlayer.getSession();
            if (!session.registerManualCutscene(cutsceneId)) {
                CommandOutput.sendMessage(sender,
                        "Cutscene " + cutsceneId + " is already pending as a manual playback.");
                return;
            }
            try {
                targetPlayer.sendPacket(new PacketCutsceneBeginNotify(cutsceneId));
            } catch (RuntimeException failed) {
                session.cancelManualCutscene(cutsceneId);
                throw failed;
            }
            CommandOutput.sendMessage(sender,
                    "Manual cutscene %d: %s".formatted(cutsceneId,
                            data == null ? "(unlisted ID)" : data.getPath()));
        }
    }

    @CommandLine.Command(name = "list")
    private static final class ListCutscenes implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "0..*", paramLabel = "[search]")
        private String[] searchWords = new String[0];

        private ListCutscenes(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            String search = String.join(" ", searchWords);
            String needle = search.toLowerCase(Locale.ROOT);
            var matches =
                    GameData.getCutsceneDataMap().values().stream()
                            .filter(data -> data.getPath() != null)
                            .filter(
                                    data ->
                                            needle.isEmpty()
                                                    || data.getPath()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains(needle))
                            .sorted(Comparator.comparingInt(CutsceneData::getId))
                            .toList();

            if (matches.isEmpty()) {
                CommandOutput.sendMessage(
                        sender, "No cutscene path contains '%s'.".formatted(search));
                return;
            }

            CommandOutput.sendMessage(
                    sender,
                    "%d cutscene(s)%s:"
                            .formatted(
                                    matches.size(),
                                    needle.isEmpty()
                                            ? ""
                                            : " matching '%s'".formatted(search)));
            matches.stream()
                    .limit(MAX_RESULTS)
                    .forEach(
                            data ->
                                    CommandOutput.sendMessage(
                                            sender,
                                            "  %d - %s".formatted(data.getId(), data.getPath())));
            if (matches.size() > MAX_RESULTS) {
                CommandOutput.sendMessage(
                        sender,
                        "  ...and %d more; narrow the search."
                                .formatted(matches.size() - MAX_RESULTS));
            }
        }
    }
}
