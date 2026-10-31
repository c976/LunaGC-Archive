package emu.grasscutter.game.player;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.Utils;
import java.util.Set;

/** Nearest unlocked teleport waypoint for open-world revive. */
public final class RespawnPositionHelper {
    private RespawnPositionHelper() {}

    public static Position find(Player player) {
        if (player == null) {
            return GameConstants.START_POSITION.clone();
        }
        Position deathPos = player.getPosition();
        int sceneId = player.getSceneId();
        Set<Integer> unlocked;
        try {
            unlocked = player.getUnlockedScenePoints(sceneId);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("getUnlockedScenePoints failed sceneId={}", sceneId, t);
            return deathPos.clone();
        }
        if (unlocked == null || unlocked.isEmpty()) {
            Grasscutter.getLogger()
                    .info(
                            "No unlocked scene points for uid={} sceneId={}, stay put",
                            player.getUid(),
                            sceneId);
            return deathPos.clone();
        }

        ScenePointEntry best = null;
        double bestDist = Double.MAX_VALUE;
        for (Integer pointId : unlocked) {
            if (pointId == null || player.isScenePointForceLocked(sceneId, pointId)) continue;
            ScenePointEntry entry;
            try {
                entry = GameData.getScenePointEntryById(sceneId, pointId);
            } catch (Throwable ignored) {
                continue;
            }
            if (entry == null || entry.getPointData() == null) continue;
            PointData pd = entry.getPointData();
            if (!isTeleportWaypoint(pd.getType())) continue;
            Position pos = pd.getTranPos();
            if (pos == null) pos = pd.getPos();
            if (pos == null) continue;
            double dist = Utils.getDist(pos, deathPos);
            if (dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }
        if (best == null) {
            Grasscutter.getLogger()
                    .info(
                            "No matching teleport waypoint for uid={} sceneId={} unlocked={}, stay put",
                            player.getUid(),
                            sceneId,
                            unlocked.size());
            return deathPos.clone();
        }
        PointData pd = best.getPointData();
        Position out = pd.getTranPos();
        if (out == null) out = pd.getPos();
        return out.clone();
    }

    private static boolean isTeleportWaypoint(String type) {
        if (type == null) return false;
        // 7.0 scene points use TransPointNormal; older data may use SceneTransPoint.
        return "SceneTransPoint".equals(type)
                || "TransPointNormal".equals(type)
                || "TransPoint".equals(type)
                || type.contains("TransPoint");
    }
}
