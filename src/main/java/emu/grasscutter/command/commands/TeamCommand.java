package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.NameIndex;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketChangeMpTeamAvatarRsp;
import java.util.*;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "team",
        permission = "player.team",
        permissionTargeted = "player.team.others")
public final class TeamCommand implements CommandHandler {
    private static final int BASE_AVATARID = 10000000;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.setExpandAtFiles(false);
        commandLine.setUnmatchedOptionsArePositionalParams(true);
        commandLine.addSubcommand("add", new Add(sender, targetPlayer));
        commandLine.addSubcommand("remove", new Remove(sender, targetPlayer));
        commandLine.addSubcommand("set", new Set(sender, targetPlayer));
        commandLine.addSubcommand("switch", new Switch(sender, targetPlayer));
        return commandLine;
    }

    @picocli.CommandLine.Command(name = "team")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            TeamCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract class TeamMutation implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private TeamMutation(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected abstract boolean mutate();

        @Override
        public final void run() {
            if (!mutate()) return;
            targetPlayer
                    .getTeamManager()
                    .updateTeamEntities(
                            new PacketChangeMpTeamAvatarRsp(
                                    targetPlayer,
                                    targetPlayer.getTeamManager().getCurrentTeamInfo()));
        }
    }

    @picocli.CommandLine.Command(name = "add")
    private final class Add extends TeamMutation {
        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<avatarId,...>")
        private List<String> tokens;

        private Add(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        protected boolean mutate() {
            var args = new ArrayList<>(tokens);
            var typed = args.get(0);
            if (!typed.contains(",") && !isNumber(typed)) {
                var rest = new ArrayList<>(args.subList(1, args.size()));
                var before = rest.size();
                var named = NameIndex.resolve(typed, rest);

                if (GameData.getAvatarDataMap().containsKey(named)) {
                    int consumed = before - rest.size();
                    for (int i = 0; i < consumed; i++) args.remove(1);
                    args.set(0, String.valueOf(named));
                }
            }

            int index = -1;
            if (args.size() > 1) {
                try {
                    index = Integer.parseInt(args.get(1)) - 1;
                    if (index < 0) index = 0;
                } catch (Exception e) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.team.invalid_index");
                    return false;
                }
            }

            var avatarIds = args.get(0).split(",");
            var currentTeamAvatars =
                    targetPlayer.getTeamManager().getCurrentTeamInfo().getAvatars();

            if (currentTeamAvatars.size() + avatarIds.length
                    > GAME_OPTIONS.avatarLimits.singlePlayerTeam) {
                CommandOutput.sendTranslatedMessage(
                        sender,
                        "commands.team.add_too_much",
                        GAME_OPTIONS.avatarLimits.singlePlayerTeam);
                return false;
            }

            for (var avatarId : avatarIds) {
                int id;
                if (isNumber(avatarId)) {
                    id = Integer.parseInt(avatarId);
                } else {
                    id = NameIndex.resolve(avatarId, new ArrayList<>());
                    if (!GameData.getAvatarDataMap().containsKey(id)) {
                        CommandOutput.sendTranslatedMessage(
                                sender, "commands.team.failed_to_add_avatar", avatarId);
                        continue;
                    }
                }

                if (!addAvatar(sender, targetPlayer, id, index))
                    CommandOutput.sendTranslatedMessage(
                            sender, "commands.team.failed_to_add_avatar", avatarId);
                if (index > 0) ++index;
            }
            return true;
        }
    }

    @picocli.CommandLine.Command(name = "remove")
    private final class Remove extends TeamMutation {
        @Parameters(index = "0", paramLabel = "<index|first|last|index-index,...>")
        private String selection;

        private Remove(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        protected boolean mutate() {
            var currentTeamAvatars =
                    targetPlayer.getTeamManager().getCurrentTeamInfo().getAvatars();
            var avatarCount = currentTeamAvatars.size();

            var metaIndexList = selection.split(",");
            var indexes = new HashSet<Integer>();
            var ignoreList = new ArrayList<Integer>();
            for (var metaIndex : metaIndexList) {
                var subIndexes = transformToIndexes(metaIndex, avatarCount);
                if (subIndexes == null) {
                    CommandOutput.sendTranslatedMessage(
                            sender, "commands.team.failed_to_parse_index", metaIndex);
                    continue;
                }

                for (var avatarIndex : subIndexes) {
                    try {
                        indexes.add(currentTeamAvatars.get(avatarIndex - 1));
                    } catch (Exception e) {
                        ignoreList.add(avatarIndex);
                    }
                }
            }

            if (indexes.size() >= avatarCount) {
                CommandOutput.sendTranslatedMessage(sender, "commands.team.remove_too_much");
                return false;
            }

            if (!ignoreList.isEmpty()) {
                CommandOutput.sendTranslatedMessage(sender, "commands.team.ignore_index", ignoreList);
            }

            currentTeamAvatars.removeAll(indexes);
            return true;
        }
    }

    @picocli.CommandLine.Command(name = "set")
    private final class Set extends TeamMutation {
        @Parameters(index = "0", paramLabel = "<index>")
        private String indexText;

        @Parameters(index = "1", paramLabel = "<avatarId>")
        private String avatarIdText;

        private Set(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        protected boolean mutate() {
            var currentTeamAvatars =
                    targetPlayer.getTeamManager().getCurrentTeamInfo().getAvatars();

            int index;
            try {
                index = Integer.parseInt(indexText) - 1;
                if (index < 0) index = 0;
            } catch (Exception e) {
                CommandOutput.sendTranslatedMessage(
                        sender, "commands.team.failed_to_parse_index", indexText);
                return false;
            }

            if (index + 1 > currentTeamAvatars.size()) {
                CommandOutput.sendTranslatedMessage(sender, "commands.team.index_out_of_range");
                return false;
            }

            int avatarId;
            try {
                avatarId = Integer.parseInt(avatarIdText);
            } catch (Exception e) {
                CommandOutput.sendTranslatedMessage(
                        sender, "commands.team.failed_parse_avatar_id", avatarIdText);
                return false;
            }
            if (avatarId < BASE_AVATARID) avatarId += BASE_AVATARID;

            if (currentTeamAvatars.contains(avatarId)) {
                CommandOutput.sendTranslatedMessage(
                        sender, "commands.team.avatar_already_in_team", avatarId);
                return false;
            }

            if (!targetPlayer.getAvatars().hasAvatar(avatarId)) {
                CommandOutput.sendTranslatedMessage(sender, "commands.team.avatar_not_found", avatarId);
                return false;
            }

            currentTeamAvatars.set(index, avatarId);
            return true;
        }
    }

    @picocli.CommandLine.Command(name = "switch")
    private final class Switch implements Runnable {
        @Parameters(index = "0", paramLabel = "<index>")
        private int index;

        private final Player sender;
        private final Player targetPlayer;

        private Switch(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var teamManager = targetPlayer.getTeamManager();
            if (teamManager == null) return;
            var team = teamManager.getTeams().get(index);
            if (team == null || team.getAvatars().isEmpty()) {
                CommandOutput.sendTranslatedMessage(sender, "commands.team.index_out_of_range");
                return;
            }
            // Switches the active party preset; the manager pushes the change to the client itself.
            teamManager.setCurrentTeam(index);
        }
    }

    private static boolean isNumber(String text) {
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean addAvatar(Player sender, Player targetPlayer, int avatarId, int index) {
        if (avatarId < BASE_AVATARID) avatarId += BASE_AVATARID;
        var currentTeamAvatars = targetPlayer.getTeamManager().getCurrentTeamInfo().getAvatars();
        if (currentTeamAvatars.contains(avatarId)) {
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.team.avatar_already_in_team", avatarId);
            return false;
        }
        if (!targetPlayer.getAvatars().hasAvatar(avatarId)) {
            CommandOutput.sendTranslatedMessage(sender, "commands.team.avatar_not_found", avatarId);
            return false;
        }
        if (index < 0) currentTeamAvatars.add(avatarId);
        else currentTeamAvatars.add(index, avatarId);
        return true;
    }

    private List<Integer> transformToIndexes(String metaIndexes, int listLength) {
        if (metaIndexes.equals("first")) {
            return List.of(1);
        } else if (metaIndexes.equals("last")) {
            return List.of(listLength);
        }

        if (metaIndexes.contains("-")) {
            var range = metaIndexes.split("-");
            if (range.length < 2) return null;

            int min, max;
            try {
                min =
                        switch (range[0]) {
                            case "first" -> 1;
                            case "last" -> listLength;
                            default -> Integer.parseInt(range[0]);
                        };

                max =
                        switch (range[1]) {
                            case "first" -> 1;
                            case "last" -> listLength;
                            default -> Integer.parseInt(range[1]);
                        };
            } catch (Exception e) {
                return null;
            }

            if (min > max) {
                min ^= max;
                max ^= min;
                min ^= max;
            }

            var indexes = new ArrayList<Integer>();
            for (int i = min; i <= max; ++i) indexes.add(i);
            return indexes;
        }

        try {
            return List.of(Integer.parseInt(metaIndexes));
        } catch (Exception e) {
            return null;
        }
    }
}
