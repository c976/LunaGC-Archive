package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;

/** Validation for 7.1 domain restarts after a successful reward claim. */
public final class DungeonRestartGuard {
    private DungeonRestartGuard() {}

    public static boolean mayRestart(Player player, byte[] payload) {
        if (player == null || payload == null || player.getScene() == null) return false;

        var scene = player.getScene();
        if (scene.getSceneType() != SceneType.SCENE_DUNGEON
                || !scene.getPlayers().contains(player)) return false;

        var manager = scene.getDungeonManager();
        if (manager == null || manager.getDungeonData() == null) return false;
        var dungeonData = manager.getDungeonData();
        synchronized (manager) {
            return isEligible(dungeonData.getId(), dungeonData.getSceneId(), scene.getId(),
                    dungeonData.getSubType(), manager.isTowerDungeon(),
                    manager.isFinishedSuccessfully(), manager.hasClaimedDomainReward(player.getUid()),
                    scene.getPlayers().size(), payload.length);
        }
    }

    static boolean isEligible(int dungeonId, int expectedScene, int currentScene,
            DungeonSubType subType, boolean tower, boolean finished, boolean claimed,
            int participants, int payloadLength) {
        return dungeonId > 0
                && expectedScene > 0
                && expectedScene == currentScene
                && (subType == DungeonSubType.DUNGEON_SUB_TALENT
                    || subType == DungeonSubType.DUNGEON_SUB_WEAPON
                    || subType == DungeonSubType.DUNGEON_SUB_RELIQUARY)
                && !tower && finished && claimed
                && participants == 1 && payloadLength == 0;
    }
}
