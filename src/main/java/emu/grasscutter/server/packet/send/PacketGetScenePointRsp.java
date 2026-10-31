package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetScenePointRspOuterClass.GetScenePointRsp;
import java.util.LinkedHashSet;

/**
 * Per-scene unlock+unhide+areas. Only points in the player's unlocked set are unlocked; everything
 * else stays locked (so map fog / waypoints follow statue & manual unlocks).
 */
public class PacketGetScenePointRsp extends BasePacket {

    public PacketGetScenePointRsp(Player player, int sceneId) {
        super(PacketOpcodes.GetScenePointRsp);

        // belong_uid names whose points these are; left at 0 the client matched it to nobody and
        // drew no waypoint or statue at all, unlocked or not.
        GetScenePointRsp.Builder p =
                GetScenePointRsp.newBuilder().setSceneId(sceneId).setBelongUid(player.getUid());

        LinkedHashSet<Integer> pointIds = new LinkedHashSet<>();
        var perScene = GameData.getScenePointsPerScene();
        if (perScene != null) {
            var scenePoints = perScene.get(sceneId);
            if (scenePoints != null) {
                pointIds.addAll(scenePoints);
            }
        }
        if (pointIds.isEmpty()) {
            var unlocked = player.getUnlockedScenePoints(sceneId);
            if (unlocked != null && !unlocked.isEmpty()) {
                pointIds.addAll(unlocked);
            }
        }
        if (pointIds.isEmpty()) {
            var all = GameData.getScenePointIdList();
            if (all != null) {
                pointIds.addAll(all);
            }
        }

        var unlockedSet = player.getUnlockedScenePoints(sceneId);
        boolean has7 = false;
        int unlockedCount = 0;
        int lockedCount = 0;
        for (int pointId : pointIds) {
            boolean locked =
                    player.isScenePointForceLocked(sceneId, pointId)
                            || !unlockedSet.contains(pointId);

            // A locked point is simply left out of the unlocked list, which the client draws as a
            // locked waypoint. Putting it on hide_point_list as well took it off the map entirely.
            if (locked) {
                lockedCount++;
            } else {
                p.addUnlockedPointList(pointId);
                unlockedCount++;
            }
            if (pointId == 7) has7 = unlockedSet.contains(7);
        }

        // Scene-point and scene-area snapshots must agree about visible map regions.
        // Fog-off mode does not unlock points or persist additional area flags.
        p.addAllUnlockAreaList(SceneAreaVisibility.forPlayer(player, sceneId));

        Grasscutter.getLogger()
                .debug(
                        "GetScenePointRsp sceneId={} total={} unlocked={} locked={} hasStatue7={} uid={}",
                        sceneId,
                        pointIds.size(),
                        unlockedCount,
                        lockedCount,
                        has7,
                        player.getUid());

        this.setData(p);
    }
}
