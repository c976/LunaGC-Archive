package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.SceneTagData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketPlayerWorldSceneInfoListNotify;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "setSceneTag",
        aliases = {"tag"},
        permission = "player.setscenetag",
        permissionTargeted = "player.setscenetag.others")
public final class SetSceneTagCommand implements CommandHandler {
    private enum Scope {
        ALL
    }

    private final Int2ObjectMap<SceneTagData> sceneTagData = GameData.getSceneTagDataMap();

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("add", new SetTag(sender, targetPlayer, true));
        commandLine.addSubcommand("remove", new SetTag(sender, targetPlayer, false));
        commandLine.addSubcommand("unlock", new UnlockAll(targetPlayer));
        commandLine.addSubcommand("reset", new Reset(targetPlayer));
        commandLine.addSubcommand("list", new ListTags(sender, targetPlayer));
        CommandHandler.allowCaseInsensitiveEnums(commandLine);
        return commandLine;
    }

    @CommandLine.Command(name = "setSceneTag")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            SetSceneTagCommand.this.sendUsageMessage(sender);
        }
    }

    private final class SetTag implements Runnable {
        private final Player sender;
        private final Player targetPlayer;
        private final boolean enabled;

        @Parameters(index = "0", paramLabel = "<sceneTagId>")
        private int sceneTagId;

        private SetTag(Player sender, Player targetPlayer, boolean enabled) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
            this.enabled = enabled;
        }

        @Override
        public void run() {
            SceneTagData data = sceneTagData.get(sceneTagId);
            if (data == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.generic.invalid.id");
                return;
            }

            if (enabled) {
                targetPlayer.getProgressManager().addSceneTag(data.getSceneId(), sceneTagId);
            } else {
                targetPlayer.getProgressManager().delSceneTag(data.getSceneId(), sceneTagId);
            }
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.generic.set_to", sceneTagId, enabled ? "add" : "remove");
        }
    }

    @CommandLine.Command(name = "unlock")
    private final class UnlockAll implements Runnable {
        @Parameters(index = "0", paramLabel = "<all>")
        private Scope scope;

        private final Player targetPlayer;

        private UnlockAll(Player targetPlayer) {
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var allData = sceneTagData.values();
            allData.forEach(
                    sceneTag -> {
                        targetPlayer
                                .getSceneTags()
                                .computeIfAbsent(sceneTag.getSceneId(), k -> new HashSet<>());
                        targetPlayer.getSceneTags().get(sceneTag.getSceneId()).add(sceneTag.getId());
                    });

            allData.stream()
                    .filter(SceneTagData::isDefaultValid)
                    .filter(sceneTag -> sceneTag.getSceneId() == 3)
                    .forEach(
                            sceneTag ->
                                    targetPlayer
                                            .getSceneTags()
                                            .get(sceneTag.getSceneId())
                                            .remove(sceneTag.getId()));

            setSceneTags(targetPlayer);
            CommandOutput.sendMessage(targetPlayer, "All scene tags unlocked.");
        }
    }

    @CommandLine.Command(name = "reset")
    private final class Reset implements Runnable {
        private final Player targetPlayer;

        private Reset(Player targetPlayer) {
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            targetPlayer.getSceneTags().clear();
            sceneTagData.values().stream()
                    .filter(SceneTagData::isDefaultValid)
                    .forEach(
                            sceneTag -> {
                                targetPlayer
                                        .getSceneTags()
                                        .computeIfAbsent(sceneTag.getSceneId(), k -> new HashSet<>());
                                targetPlayer.getSceneTags().get(sceneTag.getSceneId()).add(sceneTag.getId());
                            });
            setSceneTags(targetPlayer);
            CommandOutput.sendMessage(targetPlayer, "Scene tags reset to defaults.");
        }
    }

    @CommandLine.Command(name = "list")
    private final class ListTags implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private ListTags(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int sceneId = targetPlayer.getSceneId();
            Set<Integer> active = targetPlayer.getSceneTags().getOrDefault(sceneId, Set.of());
            List<SceneTagData> sceneTags =
                    sceneTagData.values().stream()
                            .filter(tag -> tag.getSceneId() == sceneId)
                            .sorted(Comparator.comparingInt(SceneTagData::getId))
                            .toList();

            if (sceneTags.isEmpty()) {
                CommandOutput.sendMessage(sender, "No scene tag data for scene " + sceneId + ".");
                return;
            }

            StringBuilder message =
                    new StringBuilder("Scene ")
                            .append(sceneId)
                            .append(" tags (")
                            .append(active.size())
                            .append(" active):\n");

            var activeList = sceneTags.stream().filter(tag -> active.contains(tag.getId())).toList();
            var inactiveList = sceneTags.stream().filter(tag -> !active.contains(tag.getId())).toList();

            if (!activeList.isEmpty()) {
                message.append("  [ACTIVE]\n");
                activeList.forEach(
                        tag ->
                                message.append("    ")
                                        .append(tag.getId())
                                        .append("  ")
                                        .append(tag.getSceneTagName())
                                        .append('\n'));
            }
            if (!inactiveList.isEmpty()) {
                message.append("  [INACTIVE - use /tag add <id> to enable]\n");
                inactiveList.forEach(
                        tag ->
                                message.append("    ")
                                        .append(tag.getId())
                                        .append("  ")
                                        .append(tag.getSceneTagName())
                                        .append('\n'));
            }

            CommandOutput.sendMessage(sender, message.toString());
        }
    }

    private void setSceneTags(Player targetPlayer) {
        targetPlayer.sendPacket(new PacketPlayerWorldSceneInfoListNotify(targetPlayer));
        targetPlayer.save();
    }
}
