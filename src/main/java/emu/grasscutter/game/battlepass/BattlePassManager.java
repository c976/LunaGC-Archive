package emu.grasscutter.game.battlepass;

import dev.morphia.annotations.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption;
import emu.grasscutter.net.proto.BattlePassScheduleOuterClass.BattlePassSchedule;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import lombok.Getter;
import org.bson.types.ObjectId;

@Entity(value = "battlepass", useDiscriminator = false)
public class BattlePassManager extends BasePlayerDataManager {
    @Id @Getter private ObjectId id;

    @Indexed private int ownerUid;
    @Getter private int point;
    @Getter private int cyclePoints; // Weekly maximum cap
    @Getter private int level;

    @Getter private boolean viewed;
    private boolean paid;

    private Map<Integer, BattlePassMission> missions;
    private Map<Integer, BattlePassReward> takenRewards;

    @Deprecated // Morphia only
    public BattlePassManager() {}

    public BattlePassManager(Player player) {
        super(player);
        this.ownerUid = player.getUid();
    }

    public void setPlayer(Player player) {
        this.player = player;
        this.ownerUid = player.getUid();
    }

    public void updateViewed() {
        this.viewed = true;
    }

    public boolean setLevel(int level) {
        if (level >= 0 && level <= GameConstants.BATTLE_PASS_MAX_LEVEL) {
            this.level = level;
            this.point = 0;
            this.player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(this.player));
            return true;
        }
        return false;
    }

    public void addPoints(int points) {
        this.addPointsDirectly(points, false);

        this.player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
        this.save();
    }

    public void addPointsDirectly(int points, boolean isWeekly) {
        int amount = points;

        if (isWeekly) {
            amount = Math.min(amount, GameConstants.BATTLE_PASS_POINT_PER_WEEK - this.cyclePoints);
        }

        if (amount <= 0) {
            return;
        }

        this.point += amount;
        this.cyclePoints += amount;

        if (this.point >= GameConstants.BATTLE_PASS_POINT_PER_LEVEL
                && this.getLevel() < GameConstants.BATTLE_PASS_MAX_LEVEL) {
            int levelups = Math.floorDiv(this.point, GameConstants.BATTLE_PASS_POINT_PER_LEVEL);

            // Make sure player cant go above max BP level
            levelups = Math.min(levelups, GameConstants.BATTLE_PASS_MAX_LEVEL - levelups);

            // Set new points after level up
            this.point = this.point - (levelups * GameConstants.BATTLE_PASS_POINT_PER_LEVEL);
            this.level += levelups;
        }
    }

    public Map<Integer, BattlePassMission> getMissions() {
        if (this.missions == null) this.missions = new HashMap<>();
        return this.missions;
    }

    // Will return a new empty mission if the mission id is not found
    public BattlePassMission loadMissionById(int id) {
        return getMissions().computeIfAbsent(id, i -> new BattlePassMission(i));
    }

    public boolean hasMission(int id) {
        return getMissions().containsKey(id);
    }

    public boolean isPaid() {
        return this.paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public Map<Integer, BattlePassReward> getTakenRewards() {
        if (this.takenRewards == null) this.takenRewards = new HashMap<>();
        return this.takenRewards;
    }

    // Mission triggers
    public void triggerMission(WatcherTriggerType triggerType) {
        getPlayer().getServer().getBattlePassSystem().triggerMission(getPlayer(), triggerType);
    }

    public void triggerMission(WatcherTriggerType triggerType, int param, int progress) {
        getPlayer()
                .getServer()
                .getBattlePassSystem()
                .triggerMission(getPlayer(), triggerType, param, progress);
    }

    // Handlers
    public void takeMissionPoint(List<Integer> missionIdList) {
        // Obvious exploit check
        if (missionIdList.size() > GameData.getBattlePassMissionDataMap().size()) {
            return;
        }

        List<BattlePassMission> updatedMissions = new ArrayList<>(missionIdList.size());

        for (int id : missionIdList) {
            // Skip if we dont have this mission
            if (!this.hasMission(id)) {
                continue;
            }

            BattlePassMission mission = this.loadMissionById(id);

            if (mission.getData() == null) {
                this.getMissions().remove(mission.getId());
                continue;
            }

            // Take reward
            if (mission.getStatus() == BattlePassMissionStatus.MISSION_STATUS_FINISHED) {
                this.addPointsDirectly(mission.getData().getAddPoint(), mission.getData().isCycleRefresh());
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_POINT_TAKEN);

                updatedMissions.add(mission);
            }
        }

        if (!updatedMissions.isEmpty()) {
            // Save to db
            this.save();

            // Packet
            getPlayer().sendPacket(new PacketBattlePassMissionUpdateNotify(updatedMissions));
            getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        }
    }

    private void takeRewardsFromSelectChest(
            ItemData rewardItemData, int index, ItemParamData entry, List<GameItem> rewardItems) {
        // Sanity checks.
        if (rewardItemData.getItemUse().size() < 1) {
            return;
        }

        // Get possible item choices.
        String[] choices = rewardItemData.getItemUse().get(0).getUseParam()[0].split(",");
        if (choices.length < index) {
            return;
        }

        // Get data for the selected item.
        // This depends on the type of chest.
        int chosenId = Integer.parseInt(choices[index - 1]);

        // For ITEM_USE_ADD_SELECT_ITEM chests, we can directly add the item specified in the chest's
        // data.
        if (rewardItemData.getItemUse().get(0).getUseOp() == ItemUseOp.ITEM_USE_ADD_SELECT_ITEM) {
            GameItem rewardItem =
                    new GameItem(GameData.getItemDataMap().get(chosenId), entry.getItemCount());
            rewardItems.add(rewardItem);
        }
        // For ITEM_USE_GRANT_SELECT_REWARD chests, we have to again look up reward data.
        else if (rewardItemData.getItemUse().get(0).getUseOp()
                == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            RewardData selectedReward = GameData.getRewardDataMap().get(chosenId);

            for (var r : selectedReward.getRewardItemList()) {
                GameItem rewardItem =
                        new GameItem(GameData.getItemDataMap().get(r.getItemId()), r.getItemCount());
                rewardItems.add(rewardItem);
            }
        } else {
            Grasscutter.getLogger().error("Invalid chest type for BP reward.");
        }
    }

    public void takeReward(List<BattlePassRewardTakeOption> takeOptionList) {
        List<BattlePassRewardTakeOption> rewardList = new ArrayList<>();

        for (BattlePassRewardTakeOption option : takeOptionList) {
            // Duplicate check
            if (option.getTag().getRewardId() == 0
                    || getTakenRewards().containsKey(option.getTag().getRewardId())) {
                continue;
            }

            // Level check
            if (option.getTag().getLevel() > this.getLevel()) {
                continue;
            }

            BattlePassRewardData rewardData =
                    GameData.getBattlePassRewardDataMap()
                            .get(GameConstants.BATTLE_PASS_CURRENT_INDEX * 100 + option.getTag().getLevel());

            // Sanity check with excel data
            if (rewardData.getFreeRewardIdList().contains(option.getTag().getRewardId())) {
                rewardList.add(option);
            } else if (this.isPaid()
                    && rewardData.getPaidRewardIdList().contains(option.getTag().getRewardId())) {
                rewardList.add(option);
            } else {
                Grasscutter.getLogger().info("Not in rewards list: {}", option.getTag().getRewardId());
            }
        }

        // Get rewards
        List<GameItem> rewardItems = null;

        if (!rewardList.isEmpty()) {
            rewardItems = new ArrayList<>();

            for (var option : rewardList) {
                var tag = option.getTag();
                int index = option.getOptionIdx();

                // Make sure we have reward data.
                RewardData reward = GameData.getRewardDataMap().get(tag.getRewardId());
                if (reward == null) {
                    continue;
                }

                // Add reward items.
                for (var entry : reward.getRewardItemList()) {
                    ItemData rewardItemData = GameData.getItemDataMap().get(entry.getItemId());

                    // Some rewards are chests where the user can select the item they want.
                    if (rewardItemData.getMaterialType() == MaterialType.MATERIAL_SELECTABLE_CHEST) {
                        this.takeRewardsFromSelectChest(rewardItemData, index, entry, rewardItems);
                    }
                    // All other rewards directly give us the right item.
                    else {
                        GameItem rewardItem = new GameItem(rewardItemData, entry.getItemCount());
                        rewardItems.add(rewardItem);
                    }
                }

                // Construct the reward and set as taken.
                BattlePassReward bpReward =
                        new BattlePassReward(
                                tag.getLevel(),
                                tag.getRewardId(),
                                tag.getUnlockStatus() == BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID);
                this.getTakenRewards().put(bpReward.getRewardId(), bpReward);
            }

            // Save to db
            this.save();

            // Add items and send battle pass schedule packet
            getPlayer().getInventory().addItems(rewardItems);
            getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        }

        getPlayer().sendPacket(new PacketTakeBattlePassRewardRsp(takeOptionList, rewardItems));
    }

    /** A validated purchase quote, shared by GM and client-level purchases. */
    public record LevelPurchase(int levels, int cost) {}

    public static LevelPurchase quoteLevelPurchase(int currentLevel, int requestedLevels) {
        if (requestedLevels <= 0 || currentLevel < 0
                || currentLevel >= GameConstants.BATTLE_PASS_MAX_LEVEL) {
            return new LevelPurchase(0, 0);
        }
        int levels = Math.min(requestedLevels, GameConstants.BATTLE_PASS_MAX_LEVEL - currentLevel);
        return new LevelPurchase(levels, levels * GameConstants.BATTLE_PASS_LEVEL_PRICE);
    }

    /**
     * Purchase levels through the same path for both the BP UI and GM command.
     * A rejected or cancelled primogem debit must not advance the BP level.
     */
    public synchronized int buyLevels(int requestedLevels) {
        LevelPurchase quote = quoteLevelPurchase(this.level, requestedLevels);
        if (quote.levels() == 0 || this.getPlayer() == null) return 0;

        Player player = this.getPlayer();
        int balance = player.getPrimogems();
        if (balance < quote.cost() || !player.setPrimogems(balance - quote.cost())) {
            return 0;
        }

        // Preserve the current fractional BP points when buying whole levels.
        this.level += quote.levels();
        player.save();
        this.save();
        player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
        player.sendPacket(new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
        return quote.levels();
    }

    public void resetDailyMissions() {
        var resetMissions = new ArrayList<BattlePassMission>();

        for (var mission : this.missions.values()) {
            if (mission.getData().getRefreshType() == null
                    || mission.getData().getRefreshType()
                            == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_DAILY) {
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_UNFINISHED);
                mission.setProgress(0);

                resetMissions.add(mission);
            }
        }

        this.getPlayer().sendPacket(new PacketBattlePassMissionUpdateNotify(resetMissions));
        this.getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(this.getPlayer()));
    }

    public void resetWeeklyMissions() {
        var resetMissions = resetWeeklyProgress();
        this.save();

        this.getPlayer().sendPacket(new PacketBattlePassMissionUpdateNotify(resetMissions));
        this.getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(this.getPlayer()));
    }

    List<BattlePassMission> resetWeeklyProgress() {
        this.cyclePoints = 0;
        var resetMissions = new ArrayList<BattlePassMission>();

        for (var mission : this.missions.values()) {
            if (mission.getData().getRefreshType()
                    == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE) {
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_UNFINISHED);
                mission.setProgress(0);

                resetMissions.add(mission);
            }
        }

        return resetMissions;
    }

    //
    public BattlePassSchedule getScheduleProto() {
        // LunaGC/0be043a path: schedule 6700 + reward plans. The old inline 2700 proto had no
        // reward-plan options, so the client showed an empty track and a ~3000-day timer.
        return SafeBattlePassSchedule.build(this);
    }

    public void save() {
        DatabaseHelper.saveBattlePass(this);
    }
}
