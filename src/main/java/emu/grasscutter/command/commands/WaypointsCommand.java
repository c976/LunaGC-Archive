package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketSceneAreaUnlockNotify;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "waypoints",
        aliases = {"wp"},
        permission = "player.waypoints",
        permissionTargeted = "player.waypoints.others")
public final class WaypointsCommand implements CommandHandler {

    static boolean isWaypointType(String type) {
        return "SceneTransPoint".equals(type)
                || "TransPointNormal".equals(type)
                || "TransPoint".equals(type);
    }

    record UnlockSelection(List<Integer> points, Set<Integer> areas) {}

    /** A requested area must never unlock unrelated areas. No filter retains the broad legacy unlock. */
    static UnlockSelection selectWaypoints(Map<Integer, Integer> waypoints, List<Integer> requestedAreas) {
        Set<Integer> wanted = requestedAreas == null || requestedAreas.isEmpty()
                ? null
                : new HashSet<>(requestedAreas);
        var points = new ArrayList<Integer>();
        var areas = new HashSet<Integer>();
        for (var entry : waypoints.entrySet()) {
            if (wanted != null && !wanted.contains(entry.getValue())) continue;
            points.add(entry.getKey());
            if (entry.getValue() > 0) areas.add(entry.getValue());
        }
        if (wanted == null && !points.isEmpty()) {
            // Preserve no-argument behavior: open the entire scene alongside all its waypoints.
            for (int area = 1; area < 1000; area++) areas.add(area);
        }
        return new UnlockSelection(points, areas);
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Unlock(sender, targetPlayer));
        commandLine.addSubcommand("list", new ListAreas(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "waypoints")
    private final class Unlock implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0..*", arity = "0..*", paramLabel = "[areaId...]")
        private List<Integer> areaIds;

        private Unlock(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int sceneId = targetPlayer.getSceneId();
            var waypoints = collectWaypoints(sceneId);
            if (waypoints.isEmpty()) {
                CommandOutput.sendMessage(sender, "No waypoints are known for scene " + sceneId + ".");
                return;
            }

            var selected = selectWaypoints(waypoints, areaIds);
            var points = selected.points();
            var areas = selected.areas();

            if (points.isEmpty()) {
                CommandOutput.sendMessage(sender, "No waypoints matched those areas. Try /waypoints list");
                return;
            }

            var alreadyUnlocked = targetPlayer.getUnlockedScenePoints(sceneId);
            int fresh = (int) points.stream().filter(point -> !alreadyUnlocked.contains(point)).count();
            alreadyUnlocked.addAll(points);

            var unlockedAreas = targetPlayer.getUnlockedSceneAreas(sceneId);
            int newAreas = (int) areas.stream().filter(area -> !unlockedAreas.contains(area)).count();
            unlockedAreas.addAll(areas);
            targetPlayer.save();

            targetPlayer.sendPacket(
                    new PacketSceneAreaUnlockNotify(
                            sceneId, targetPlayer.getUnlockedSceneAreas(sceneId)));
            targetPlayer.sendPacket(new PacketScenePointUnlockNotify(sceneId, alreadyUnlocked));

            CommandOutput.sendMessage(
                    sender,
                    "Unlocked "
                            + points.size()
                            + " waypoint(s) in scene "
                            + sceneId
                            + " ("
                            + fresh
                            + " new), opened " + newAreas + " new area flag(s)"
                            + (areaIds == null || areaIds.isEmpty()
                                    ? " across the scene."
                                    : " in the selected areas.")
                            + " If a region is still blocked, inspect its scene tags with /tag list.");
        }
    }

    @CommandLine.Command(name = "list")
    private final class ListAreas implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private ListAreas(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int sceneId = targetPlayer.getSceneId();
            var waypoints = collectWaypoints(sceneId);
            if (waypoints.isEmpty()) {
                CommandOutput.sendMessage(sender, "No waypoints are known for scene " + sceneId + ".");
                return;
            }

            var perArea = new TreeMap<Integer, int[]>();
            var unlocked = targetPlayer.getUnlockedScenePoints(sceneId);
            for (var entry : waypoints.entrySet()) {
                var counts = perArea.computeIfAbsent(entry.getValue(), ignored -> new int[2]);
                counts[0]++;
                if (unlocked.contains(entry.getKey())) {
                    counts[1]++;
                }
            }

            var message = new StringBuilder("Waypoints in scene " + sceneId + " by area (unlocked/total):");
            perArea.forEach(
                    (area, counts) ->
                            message.append("\n  area ")
                                    .append(area)
                                    .append(": ")
                                    .append(counts[1])
                                    .append("/")
                                    .append(counts[0]));
            CommandOutput.sendMessage(sender, message.toString());
        }
    }

    private static TreeMap<Integer, Integer> collectWaypoints(int sceneId) {
        var waypoints = new TreeMap<Integer, Integer>();
        var pointIds = GameData.getScenePointsPerScene().get(sceneId);
        if (pointIds == null) {
            return waypoints;
        }

        for (var pointId : pointIds) {
            var entry = GameData.getScenePointEntryById(sceneId, pointId);
            if (entry == null || entry.getPointData() == null) {
                continue;
            }
            var data = entry.getPointData();
            if (data.isForbidSimpleUnlock() || !isWaypointType(data.getType())) {
                continue;
            }
            waypoints.put(pointId, data.getAreaId());
        }
        return waypoints;
    }
}
