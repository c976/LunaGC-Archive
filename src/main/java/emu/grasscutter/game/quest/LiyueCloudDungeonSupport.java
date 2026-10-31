package emu.grasscutter.game.quest;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.binout.routes.RoutePoint;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.InTimeTrigger;
import emu.grasscutter.game.dungeons.challenge.trigger.TriggerGroupTriggerTrigger;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.packet.send.PacketChallengeDataNotify;
import java.util.List;

/** Mechanics overrides only for Cloud Retainer's plot dungeon 1103, scene 1052. */
public final class LiyueCloudDungeonSupport {
    private LiyueCloudDungeonSupport() {}

    public static boolean isTargetScene(Scene scene) {
        var dungeon = scene != null ? scene.getDungeonManager() : null;
        return scene != null && scene.getId() == 1052 && dungeon != null
                && dungeon.getDungeonData() != null && dungeon.getDungeonData().getId() == 1103;
    }

    public static boolean isTimerGroup(Scene scene, int group) {
        return LiyueTeapotDungeonSupport.isTimerGroup(scene, group) || isFireLinkGroup(scene, group)
                || (isTargetScene(scene) && group >= 201052002 && group <= 201052017);
    }

    public static boolean isFireLinkGroup(Scene scene, int group) {
        if (scene == null || scene.getId() != 1052 || group != 201052013) return false;
        var dungeon = scene.getDungeonManager();
        // A login into the saved scene has no DungeonManager yet. This exact native group still
        // needs ordered links and its fractional timer; an identified different dungeon stays out.
        return dungeon == null || (dungeon.getDungeonData() != null
                && dungeon.getDungeonData().getId() == 1103);
    }

    public static int ticksForSeconds(double seconds) {
        return Math.max(1, (int) Math.ceil(Math.max(0, seconds) * 1000
                / Math.max(1, Configuration.GAME_INFO.tickRateMs)));
    }

    public static boolean isPlatform(EntityGadget gadget) {
        return isTargetScene(gadget.getScene()) && gadget.getGroupId() == 201052008
                && gadget.getConfigId() >= 8011 && gadget.getConfigId() <= 8013;
    }

    public static double pointSeconds(RoutePoint point, double distance) {
        return Math.max(0, point.getWaitTime())
                + (distance > 0 && point.getTargetVelocity() > 0 ? distance / point.getTargetVelocity() : 0);
    }

    public static boolean isFireChallenge(Scene scene, SceneGroup group, int localId, int dataId) {
        return isTargetScene(scene) && group != null && group.id == 201052009
                && ((localId == 100 && dataId == 50) || (localId == 101 && dataId == 53)
                    || (localId == 102 && dataId == 54));
    }

    public static WorldChallenge createFireChallenge(Scene scene, SceneGroup group,
            int localId, int dataId, int seconds, int tag, int count) {
        var timer = new InTimeTrigger() {
            @Override public void onBegin(WorldChallenge challenge) {
                // The native templates use %1% for time and #2#/%2% for the fire-tablet count.
                scene.broadcastPacket(new PacketChallengeDataNotify(challenge, 1,
                        challenge.getStartedAt() + challenge.getTimeLimit()));
            }
        };
        return new WorldChallenge(scene, group, dataId, localId, List.of(seconds, count), seconds,
                count, List.of(timer, new TriggerGroupTriggerTrigger(String.valueOf(tag)))) {
            @Override public synchronized void done() { super.done(); }
            @Override public synchronized void fail() { super.fail(); }
        };
    }

    public static int stopFireChallenge(Scene scene, int localId, int success) {
        if (!isTargetScene(scene) || localId < 100 || localId > 102 || (success != 0 && success != 1))
            return 1;
        var challenge = scene.getChallenge();
        // Stale/duplicate callbacks cannot end a different round or emit its completion twice.
        if (challenge == null || challenge.getChallengeIndex() != localId) return 0;
        if (!isFireChallenge(scene, challenge.getGroup(), localId, challenge.getChallengeId())) return 1;
        if (success == 1) challenge.done(); else challenge.fail();
        return 0;
    }
}
