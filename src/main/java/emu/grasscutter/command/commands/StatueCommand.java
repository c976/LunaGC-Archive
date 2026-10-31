package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.data.common.PointData;
import java.util.Set;
import emu.grasscutter.server.packet.send.PacketGetSceneAreaRsp;
import emu.grasscutter.server.packet.send.PacketGetScenePointRsp;
import emu.grasscutter.server.packet.send.PacketLevelupCityRsp;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "statue",
        aliases = {"sots"},
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others")
public final class StatueCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("lock", new PointAction(sender, targetPlayer, PointMode.LOCK));
        commandLine.addSubcommand("unlock", new PointAction(sender, targetPlayer, PointMode.UNLOCK));
        commandLine.addSubcommand("status", new PointAction(sender, targetPlayer, PointMode.STATUS));
        commandLine.addSubcommand("level", new Level(sender, targetPlayer));
        commandLine.addSubcommand("city", new City(sender, targetPlayer));
        commandLine.addSubcommand("reset", new Reset(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "statue")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            StatueCommand.this.sendUsageMessage(sender);
        }
    }

    private enum PointMode {
        LOCK,
        UNLOCK,
        STATUS
    }

    private static final class PointAction implements Runnable {
        private final Player sender;
        private final Player targetPlayer;
        private final PointMode mode;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[pointId]")
        private Integer pointId;

        private PointAction(Player sender, Player targetPlayer, PointMode mode) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
            this.mode = mode;
        }

        @Override
        public void run() {
            int sceneId = targetPlayer.getSceneId();
            int point = pointId != null ? pointId : nearestStatue(targetPlayer, sceneId);
            if (point <= 0) {
                CommandOutput.sendMessage(
                        sender, "No statue found in this scene. Pass a point ID explicitly.");
                return;
            }

            switch (mode) {
                case LOCK -> lock(sender, targetPlayer, sceneId, point);
                case UNLOCK -> unlock(sender, targetPlayer, sceneId, point);
                case STATUS -> status(sender, targetPlayer, sceneId, point);
            }
        }
    }

    @CommandLine.Command(name = "level")
    private static final class Level implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<cityId>")
        private int cityId;

        @Parameters(index = "1", paramLabel = "<level>")
        private int level;

        private Level(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            setCityLevel(sender, targetPlayer, cityId, level);
        }
    }

    @CommandLine.Command(name = "reset")
    private static final class Reset implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", defaultValue = "8", paramLabel = "[cityId]")
        private int cityId;

        private Reset(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            setCityLevel(sender, targetPlayer, cityId, 1);
        }
    }

    @CommandLine.Command(name = "city")
    private static final class City implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[cityId]")
        private Integer cityId;

        private City(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var sots = targetPlayer.getSotsManager();
            if (sots == null) {
                CommandOutput.sendMessage(sender, "No SotS manager");
                return;
            }
            if (cityId == null) {
                var map = targetPlayer.getCityInfoData();
                if (map == null || map.isEmpty()) {
                    CommandOutput.sendMessage(sender, "cityInfo empty");
                    return;
                }
                StringBuilder message =
                        new StringBuilder("cityInfo uid=").append(targetPlayer.getUid());
                map.forEach(
                        (id, info) ->
                                message.append(" | city")
                                        .append(id)
                                        .append(" Lv.")
                                        .append(info.getLevel())
                                        .append(" crystal=")
                                        .append(info.getNumCrystal()));
                CommandOutput.sendMessage(sender, message.toString());
                return;
            }

            var info = sots.getCityInfo(cityId);
            CommandOutput.sendMessage(
                    sender,
                    "uid="
                            + targetPlayer.getUid()
                            + " city"
                            + cityId
                            + " Lv."
                            + info.getLevel()
                            + " crystal="
                            + info.getNumCrystal());
        }
    }

    private static void lock(Player sender, Player player, int sceneId, int pointId) {
        player.getUnlockedScenePoints(sceneId).remove(pointId);
        player.getForceLockedScenePoints(sceneId).add(pointId);
        player.save();
        player.sendPacket(PacketScenePointUnlockNotify.lock(sceneId, pointId));
        player.sendPacket(new PacketGetScenePointRsp(player, sceneId));
        CommandOutput.sendMessage(
                sender,
                "Locked scene "
                        + sceneId
                        + " statue/point "
                        + pointId
                        + " for uid "
                        + player.getUid()
                        + ".");
    }

    private static void unlock(Player sender, Player player, int sceneId, int pointId) {
        player.getForceLockedScenePoints(sceneId).remove(pointId);
        boolean unlocked = player.getProgressManager().unlockTransPoint(sceneId, pointId, true);
        if (!unlocked) {
            player.getUnlockedScenePoints(sceneId).add(pointId);
            player.sendPacket(new PacketScenePointUnlockNotify(sceneId, pointId));
            player.sendPacket(new PacketGetScenePointRsp(player, sceneId));
        }
        player.save();
        CommandOutput.sendMessage(
                sender,
                (unlocked ? "Unlocked" : "Already unlocked / refreshed")
                        + " scene "
                        + sceneId
                        + " point "
                        + pointId);
    }

    private static void status(Player sender, Player player, int sceneId, int pointId) {
        boolean forceLocked = player.isScenePointForceLocked(sceneId, pointId);
        boolean unlocked = player.getUnlockedScenePoints(sceneId).contains(pointId);
        var entry = GameData.getScenePointEntryById(sceneId, pointId);
        Integer spring =
                entry != null && entry.getPointData() != null
                        ? entry.getPointData().getMaxSpringVolume()
                        : null;
        CommandOutput.sendMessage(
                sender,
                "scene="
                        + sceneId
                        + " point="
                        + pointId
                        + " forceLocked="
                        + forceLocked
                        + " unlockedSet="
                        + unlocked
                        + " maxSpring="
                        + spring);
    }

    private static void setCityLevel(Player sender, Player player, int cityId, int level) {
        if (cityId < 1 || cityId > 8 || level < 1 || level > 10) {
            CommandOutput.sendMessage(sender, "cityId 1-8, level 1-10");
            return;
        }
        var sots = player.getSotsManager();
        if (sots == null) {
            CommandOutput.sendMessage(sender, "No SotS manager");
            return;
        }

        var info = sots.getCityInfo(cityId);
        int before = info.getLevel();
        info.setLevel(level);
        info.setNumCrystal(0);
        sots.addCityInfo(info);
        player.save();

        int sceneId = player.getSceneId();
        player.sendPacket(new PacketLevelupCityRsp(sceneId, info.getLevel(), cityId, 0, 0, 0));
        player.sendPacket(new PacketGetSceneAreaRsp(player, sceneId));
        CommandOutput.sendMessage(
                sender,
                "Set city"
                        + cityId
                        + " Lv."
                        + before
                        + " → Lv."
                        + level
                        + " (crystal=0) for uid "
                        + player.getUid()
                        + ".");
    }

    private static final Set<Integer> STATUE_GADGET_IDS =
            Set.of(70130009, 70130010, 70130011, 73176017);

    static boolean isStatuePoint(PointData data) {
        if (data == null) return false;
        if (data.getMaxSpringVolume() > 0) return true;
        if (STATUE_GADGET_IDS.contains(data.getGadgetId())) return true;
        String type = data.getType();
        return type != null && type.contains("KDEHKECBDBO");
    }

    private static int nearestStatue(Player player, int sceneId) {
        var pointIds = GameData.getScenePointsPerScene().get(sceneId);
        if (pointIds == null || pointIds.isEmpty()) return -1;

        var position = player.getPosition();
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int pointId : pointIds) {
            var entry = GameData.getScenePointEntryById(sceneId, pointId);
            if (entry == null || entry.getPointData() == null) continue;
            var data = entry.getPointData();
            if (!isStatuePoint(data) || data.getPos() == null) continue;
            var point = data.getPos();
            double dx = point.getX() - position.getX();
            double dy = point.getY() - position.getY();
            double dz = point.getZ() - position.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pointId;
            }
        }
        return best;
    }
}
