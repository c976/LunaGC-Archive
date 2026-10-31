package emu.grasscutter.game.dungeons.dungeon_results;

import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonEndStats;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.tower.TowerManager;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.TowerLevelEndNotifyOuterClass.TowerLevelEndNotify;
import java.util.Collections;
import java.util.List;

public class TowerResult extends BaseDungeonResult {
    WorldChallenge challenge;
    boolean canJump;
    boolean hasNextLevel;
    int nextFloorId;
    int currentStars;
    List<GameItem> rewardItems;

    public TowerResult(
            DungeonData dungeonData,
            DungeonEndStats dungeonStats,
            TowerManager towerManager,
            WorldChallenge challenge,
            int currentStars,
            boolean hasNextLevel,
            boolean canJump,
            int nextFloorId) {
        this(
                dungeonData,
                dungeonStats,
                challenge,
                currentStars,
                hasNextLevel,
                canJump,
                nextFloorId,
                Collections.emptyList());
    }

    public TowerResult(
            DungeonData dungeonData,
            DungeonEndStats dungeonStats,
            WorldChallenge challenge,
            int currentStars,
            boolean hasNextLevel,
            boolean canJump,
            int nextFloorId,
            List<GameItem> rewardItems) {
        super(dungeonData, dungeonStats);
        this.challenge = challenge;
        this.canJump = canJump;
        this.hasNextLevel = hasNextLevel;
        this.nextFloorId = hasNextLevel ? 0 : nextFloorId;
        this.currentStars = currentStars;
        this.rewardItems = rewardItems != null ? rewardItems : Collections.emptyList();
    }

    private static final int CONTINUE_STATE_CAN_NOT_CONTINUE = 0;
    private static final int CONTINUE_STATE_CAN_ENTER_NEXT_LEVEL = 1;
    private static final int CONTINUE_STATE_CAN_ENTER_NEXT_FLOOR = 2;

    @Override
    protected void onProto(DungeonSettleNotifyOuterClass.DungeonSettleNotify.Builder builder) {
        var continueStatus = CONTINUE_STATE_CAN_NOT_CONTINUE;
        boolean success =
                getDungeonStats().getDungeonResult() == DungeonEndReason.COMPLETED
                        || (challenge != null && challenge.isSuccess());
        if (success) {
            if (hasNextLevel) {
                continueStatus = CONTINUE_STATE_CAN_ENTER_NEXT_LEVEL;
            } else if (canJump) {
                continueStatus = CONTINUE_STATE_CAN_ENTER_NEXT_FLOOR;
            }
        }

        var towerLevelEndNotify =
                TowerLevelEndNotify.newBuilder()
                        .setIsSuccess(success)
                        .setContinueState(continueStatus);

        for (int i = 1; i <= currentStars; i++) {
            towerLevelEndNotify.addFinishedStarCondList(i);
        }

        for (var item : rewardItems) {
            if (item == null || item.getItemId() <= 0 || item.getCount() <= 0) continue;
            towerLevelEndNotify.addRewardItemList(
                    ItemParamOuterClass.ItemParam.newBuilder()
                            .setItemId(item.getItemId())
                            .setCount(item.getCount())
                            .build());
        }

        if (nextFloorId > 0 && canJump) {
            towerLevelEndNotify.setNextFloorId(nextFloorId);
        }
        builder.setTowerLevelEndNotify(towerLevelEndNotify.build());
    }
}
