package emu.grasscutter.game.dungeons;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult.DungeonEndReason;
import emu.grasscutter.game.dungeons.dungeon_results.TowerResult;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.tower.TowerRewards;
import emu.grasscutter.server.packet.send.*;
import java.util.ArrayList;
import java.util.List;

public class TowerDungeonSettleListener implements DungeonSettleListener {

    @Override
    public void onDungeonSettle(DungeonManager dungeonManager, DungeonEndReason endReason) {
        var scene = dungeonManager.getScene();

        var dungeonData = dungeonManager.getDungeonData();
        var players = scene.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        var player = players.get(0);
        var towerManager = player.getTowerManager();

        // Upper-half done → mid-half team swap still pending. Settling here resets
        // abyssTempTeamIndex to 0 / advances the chamber, so the timed swap is skipped and
        // the player keeps the upper half into the lower half, or jumps to the next chamber.
        if (towerManager != null && towerManager.isMidHalfCutscenePending()) {
            emu.grasscutter.Grasscutter.getLogger()
                    .info(
                            "Tower settle skipped uid={} - mid-half team swap pending",
                            player.getUid());
            return;
        }

        // Mid-half (upper done, lower pending): stage is set to 1. Do not settle the chamber yet.
        if (scene.getLoadedGroups().stream()
                .anyMatch(
                        g -> {
                            var variables = scene.getScriptManager().getVariables(g.id);
                            return variables != null
                                    && variables.containsKey("stage")
                                    && variables.get("stage") == 1;
                        })) {
            return;
        }
        var floorId = towerManager.getCurrentFloorId();
        var levelId = towerManager.getCurrentLevelId();
        var stars = towerManager.getCurLevelStars();
        var hasNextLevel = towerManager.getCurrentLevel() < 3;
        var canJump = towerManager.hasNextFloor();
        var nextFloorId = canJump ? towerManager.getNextFloorId() : 0;
        var challenge = scene.getChallenge();
        var finishedTime = challenge != null ? challenge.getFinishedTime() : 0;
        var dungeonStats =
                new DungeonEndStats(scene.getKilledMonsterCount(), finishedTime, 0, endReason);

        List<GameItem> rewards = List.of();
        if (endReason == DungeonEndReason.COMPLETED) {
            rewards = grantFirstPassRewards(towerManager, levelId);
            towerManager.notifyCurLevelRecordChangeWhenDone(stars);
            var floorRecord = towerManager.getRecordMap().get(floorId);
            scene.broadcastPacket(
                    new PacketTowerFloorRecordChangeNotify(
                            floorId,
                            floorRecord != null ? floorRecord.getStarCount() : stars,
                            towerManager.canEnterScheduleFloor(),
                            floorRecord));
        }

        var result =
                endReason == DungeonEndReason.COMPLETED
                        ? new TowerResult(
                                dungeonData,
                                dungeonStats,
                                challenge,
                                stars,
                                hasNextLevel,
                                canJump,
                                nextFloorId,
                                rewards)
                        : new BaseDungeonResult(dungeonData, dungeonStats);

        scene.broadcastPacket(new PacketDungeonSettleNotify(result));
    }

    /** First-clear chamber loot from the rotation reward group, with a chamber-row fallback. */
    private static List<GameItem> grantFirstPassRewards(
            emu.grasscutter.game.tower.TowerManager towerManager, int levelId) {
        var record = towerManager.getRecordMap().get(towerManager.getCurrentFloorId());
        int prevStars = record != null ? record.getLevelStars(levelId) : 0;
        if (prevStars > 0) {
            emu.grasscutter.Grasscutter.getLogger()
                    .info(
                            "Tower firstPass skip uid={} floor={} levelId={} prevStars={}",
                            towerManager.getPlayer().getUid(),
                            towerManager.getCurrentFloorId(),
                            levelId,
                            prevStars);
            return List.of();
        }

        var levelData = GameData.getTowerLevelDataMap().get(levelId);
        if (levelData == null) {
            levelData = towerManager.getCurrentTowerLevelDataMap();
        }
        var schedule =
                towerManager.getPlayer().getServer().getTowerSystem().getCurrentTowerScheduleData();
        var floorData = GameData.getTowerFloorDataMap().get(towerManager.getCurrentFloorId());
        int rewardId = TowerRewards.chamberRewardId(schedule, floorData, levelData);
        if (rewardId <= 0) {
            emu.grasscutter.Grasscutter.getLogger()
                    .warn(
                            "Tower firstPass missing reward uid={} levelId={}",
                            towerManager.getPlayer().getUid(),
                            levelId);
            return List.of();
        }
        var reward = GameData.getRewardDataMap().get(rewardId);
        if (reward == null || reward.getRewardItemList() == null) {
            return List.of();
        }

        List<GameItem> items = new ArrayList<>();
        for (var param : reward.getRewardItemList()) {
            if (param == null || param.getId() <= 0 || param.getCount() <= 0) continue;
            items.add(new GameItem(param.getId(), param.getCount()));
        }
        if (!items.isEmpty()) {
            towerManager
                    .getPlayer()
                    .getInventory()
                    .addItems(items, ActionReason.TowerFirstPassReward);
            emu.grasscutter.Grasscutter.getLogger()
                    .info(
                            "Tower firstPass grant uid={} floor={} levelId={} rewardId={} items={}",
                            towerManager.getPlayer().getUid(),
                            towerManager.getCurrentFloorId(),
                            levelId,
                            rewardId,
                            items.size());
        }
        return items;
    }
}
