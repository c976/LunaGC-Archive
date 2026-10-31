package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandHelpers;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "teleport",
        aliases = {"tp"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class TeleportCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        // Do not resolve an implicit console target here. Picocli builds a completion tree during
        // server bootstrap, before GameServer exists. Resolve only when a command actually runs.
        var commandLine = new CommandLine(new Root(sender, targetPlayer));
        commandLine.addSubcommand("pos", new PositionTeleport(sender, targetPlayer));
        commandLine.addSubcommand("scene", new SceneTeleport(sender, targetPlayer));
        commandLine.addSubcommand("all", new TeleportAll(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "teleport")
    private final class Root implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        /**
         * {@code <pointId> [sceneId]}, or {@code <x> <y> <z> [sceneId]} as before the picocli migration:
         * three or more arguments are coordinates, the same as {@code teleport pos}.
         */
        @Parameters(arity = "0..4", paramLabel = "[pointId [sceneId] | x y z [sceneId]]")
        private java.util.List<String> args = new java.util.ArrayList<>();

        private Root(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (args.isEmpty()) {
                TeleportCommand.this.sendUsageMessage(sender);
                return;
            }
            if (args.size() >= 3) {
                Integer scene = args.size() == 4 ? parseIntOrNull(args.get(3)) : null;
                if (args.size() == 4 && scene == null) {
                    TeleportCommand.this.sendUsageMessage(sender);
                    return;
                }
                new PositionTeleport(sender, targetPlayer, args.get(0), args.get(1), args.get(2), scene)
                        .run();
                return;
            }

            Integer pointId = parseIntOrNull(args.get(0));
            Integer sceneId = args.size() == 2 ? parseIntOrNull(args.get(1)) : null;
            if (pointId == null || (args.size() == 2 && sceneId == null)) {
                TeleportCommand.this.sendUsageMessage(sender);
                return;
            }

            Player target = resolveTarget(sender, targetPlayer);
            if (target == null) return;
            teleportToScenePoint(sender, target, pointId, sceneId);
        }
    }

    private static Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void teleportToScenePoint(
            Player sender, Player targetPlayer, int pointId, Integer requestedSceneId) {
        if (!hasPermission(sender, targetPlayer, "player.teleport", "player.teleport.others")) return;

        int sceneId = requestedSceneId == null ? targetPlayer.getSceneId() : requestedSceneId;
        var entry = GameData.getScenePointEntryById(sceneId, pointId);
        if (entry == null || entry.getPointData() == null) {
            CommandOutput.sendMessage(
                    sender, "Scene point " + sceneId + ":" + pointId + " does not exist.");
            return;
        }

        var point = entry.getPointData();
        Position sourcePos = point.getTranPos() != null ? point.getTranPos() : point.getPos();
        if (sourcePos == null) {
            CommandOutput.sendMessage(
                    sender, "Scene point " + sceneId + ":" + pointId + " has no position.");
            return;
        }

        // GM point teleport is deliberately independent from map unlock state. This allows testing
        // and administration at locked waypoints/statues without mutating their unlock sets.
        Position destination = new Position(sourcePos);
        Position pointRot = point.getTranRot() != null ? point.getTranRot() : point.getRot();
        if (pointRot != null) {
            targetPlayer.getRotation().set(pointRot);
        }

        boolean transferred =
                targetPlayer
                        .getWorld()
                        .transferPlayerToScene(
                                targetPlayer, sceneId, TeleportType.COMMAND, destination);
        if (!transferred) {
            CommandOutput.sendMessage(
                    sender, "Failed to teleport to scene point " + sceneId + ":" + pointId + ".");
            return;
        }

        CommandOutput.sendMessage(
                sender,
                "Teleported "
                        + targetPlayer.getNickname()
                        + " to scene point "
                        + sceneId
                        + ":"
                        + pointId
                        + ".");
    }

    @CommandLine.Command(name = "pos")
    private static final class PositionTeleport implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<x>")
        private String x;

        @Parameters(index = "1", paramLabel = "<y>")
        private String y;

        @Parameters(index = "2", paramLabel = "<z>")
        private String z;

        @Parameters(index = "3", arity = "0..1", paramLabel = "[sceneId]")
        private Integer sceneId;

        private PositionTeleport(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        private PositionTeleport(
                Player sender, Player targetPlayer, String x, String y, String z, Integer sceneId) {
            this(sender, targetPlayer);
            this.x = x;
            this.y = y;
            this.z = z;
            this.sceneId = sceneId;
        }

        @Override
        public void run() {
            Player target = resolveTarget(sender, targetPlayer);
            if (target == null) return;
            if (!hasPermission(sender, target, "player.teleport", "player.teleport.others")) return;

            Position basePosition = new Position(target.getPosition());
            Position rotation = new Position(target.getRotation());
            Position destination;
            try {
                destination = CommandHelpers.parsePosition(x, y, z, basePosition, rotation);
            } catch (NumberFormatException ignored) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.teleport.invalid_position"));
                return;
            }

            int destinationScene = sceneId == null ? target.getSceneId() : sceneId;
            boolean transferred =
                    target.getWorld()
                            .transferPlayerToScene(
                                    target,
                                    destinationScene,
                                    TeleportType.COMMAND,
                                    destination);
            if (!transferred) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.teleport.exists_error"));
                return;
            }

            CommandOutput.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.teleport.success",
                            target.getNickname(),
                            destination.getX(),
                            destination.getY(),
                            destination.getZ(),
                            destinationScene));
        }
    }

    @CommandLine.Command(name = "scene")
    private static final class SceneTeleport implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<sceneId>")
        private int sceneId;

        private SceneTeleport(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Player target = resolveTarget(sender, targetPlayer);
            if (target == null) return;
            if (!hasPermission(sender, target, "player.teleport", "player.teleport.others")) return;

            var scene = target.getWorld().getSceneById(sceneId);
            if (scene == null) {
                CommandOutput.sendMessage(sender, "Scene " + sceneId + " does not exist.");
                return;
            }

            var config = scene.getScriptManager().getConfig();
            if (config == null || config.born_pos == null) {
                CommandOutput.sendMessage(
                        sender,
                        "Scene " + sceneId + " has no spawn position; use teleport pos with coordinates.");
                return;
            }

            Position destination = config.born_pos;
            if (config.born_rot != null) target.getRotation().set(config.born_rot);
            boolean transferred =
                    target.getWorld()
                            .transferPlayerToScene(
                                    target, sceneId, TeleportType.COMMAND, destination);
            if (!transferred) {
                CommandOutput.sendMessage(sender, "Failed to enter scene " + sceneId + ".");
                return;
            }

            var pos = target.getPosition();
            var rot = target.getRotation();
            CommandOutput.sendTranslatedMessage(
                    sender,
                    "commands.position.success",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    rot.getX(),
                    rot.getY(),
                    rot.getZ(),
                    target.getSceneId());
        }
    }

    @CommandLine.Command(name = "all")
    private static final class TeleportAll implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private TeleportAll(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Player target = resolveTarget(sender, targetPlayer);
            if (target == null) return;
            if (!hasPermission(sender, target, "player.tpall", "player.tpall.others")) return;
            if (!target.getWorld().isMultiplayer()) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.teleportAll.error"));
                return;
            }

            for (Player player : target.getWorld().getPlayers()) {
                if (player.equals(target)) continue;
                player.getWorld()
                        .transferPlayerToScene(
                                player,
                                target.getSceneId(),
                                TeleportType.COMMAND,
                                target.getPosition());
            }

            CommandOutput.sendMessage(sender, translate(sender, "commands.teleportAll.success"));
        }
    }

    private static Player resolveTarget(Player sender, Player targetPlayer) {
        if (targetPlayer != null) return targetPlayer;
        if (sender != null) return sender;

        var server = Grasscutter.getGameServer();
        if (server == null) {
            CommandOutput.sendMessage(null, "Game server is not ready.");
            return null;
        }

        var onlinePlayers = server.getPlayers().values().stream().filter(Player::isOnline).toList();
        if (onlinePlayers.size() == 1) return onlinePlayers.get(0);

        if (onlinePlayers.isEmpty()) {
            CommandOutput.sendMessage(null, "No online player is available for teleport.");
        } else {
            CommandOutput.sendMessage(
                    null,
                    "Multiple players are online; add @UID to the command or use target @UID first.");
        }
        return null;
    }

    private static boolean hasPermission(
            Player sender, Player targetPlayer, String permission, String permissionTargeted) {
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = targetPlayer != sender ? permissionTargeted : permission;
        if (account != null && account.hasPermission(required)) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
