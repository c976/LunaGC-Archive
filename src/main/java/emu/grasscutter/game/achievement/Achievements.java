package emu.grasscutter.game.achievement;

import com.github.davidmoten.guavamini.Lists;
import dev.morphia.annotations.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.achievement.AchievementData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryAddResult;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.AchievementOuterClass.Achievement.Status;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.event.player.PlayerCompleteAchievementEvent;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import lombok.*;
import org.bson.types.ObjectId;

@Entity("achievements")
@Data
@Builder(builderMethodName = "of")
public class Achievements {
    private static final IntSupplier currentTimeSecs =
            () -> (int) (System.currentTimeMillis() / 1000L);
    private static final Achievement INVALID = new Achievement(Status.Status_INVALID, -1, 0, 0, 0);
    // Keep individual update notifications comfortably smaller than a full achievement snapshot.
    static final int UPDATE_PACKET_BATCH_SIZE = 128;
    @Id private ObjectId id;
    private int uid;
    @Transient private Player player;
    private Map<Integer, Achievement> achievementList;
    @Getter private int finishedAchievementNum;
    private List<Integer> takenGoalRewardIdList;

    @PostLoad
    void restoreLegacyRewardClaims() {
        if (this.achievementList == null) return;
        for (Achievement achievement : this.achievementList.values()) {
            if (achievement != null && achievement.getStatus() == Status.Status_REWARD_TAKEN) {
                // Legacy BSON documents had no rewardClaimed field. Persist it on next save.
                achievement.setStatus(Status.Status_REWARD_TAKEN);
            }
        }
    }

    public static Achievements getByPlayer(Player player) {
        var achievements =
                player.getAchievements() == null
                        ? DatabaseHelper.getAchievementData(player.getUid())
                        : player.getAchievements();
        if (achievements == null) {
            achievements = create(player.getUid());
        }
        return achievements;
    }

    public static Achievements create(int uid) {
        var newAchievement =
                Achievements.of()
                        .uid(uid)
                        .achievementList(init())
                        .finishedAchievementNum(0)
                        .takenGoalRewardIdList(Lists.newArrayList())
                        .build();
        newAchievement.save();
        return newAchievement;
    }

    private static Map<Integer, Achievement> init() {
        Map<Integer, Achievement> map = new HashMap<>();
        GameData.getAchievementDataMap().values().stream()
                .filter(AchievementData::isUsed)
                .forEach(
                        a -> {
                            map.put(
                                    a.getId(),
                                    new Achievement(Status.Status_UNFINISHED, a.getId(), a.getProgress(), 0, 0));
                        });
        return map;
    }

    public synchronized AchievementControlReturns grant(int achievementId) {
        var a = this.getAchievement(achievementId);

        if (a == null || this.isFinished(achievementId)) {
            return a == null
                    ? AchievementControlReturns.achievementNotFound()
                    : AchievementControlReturns.alreadyAchieved();
        }

        return this.progress(achievementId, a.getTotalProgress());
    }

    public synchronized AchievementControlReturns revoke(int achievementId) {
        var a = this.getAchievement(achievementId);

        if (a == null || !this.isFinished(achievementId)) {
            return a == null
                    ? AchievementControlReturns.achievementNotFound()
                    : AchievementControlReturns.notYetAchieved();
        }

        return this.progress(achievementId, 0);
    }

    public synchronized AchievementControlReturns progress(int achievementId, int progress) {
        var a = this.getAchievement(achievementId);
        if (a == null) {
            return AchievementControlReturns.achievementNotFound();
        }

        a.setCurProgress(progress);
        return AchievementControlReturns.success(this.notifyOtherAchievements(a));
    }

    /**
     * Apply bulk changes without persisting or notifying once per achievement.
     * Grant-all completes each valid ID independently, while ordinary single-ID commands
     * retain their linked-stage propagation. Each completion still fires its event.
     */
    public synchronized int grantAll() {
        var batch = new UpdateBatch();
        var stages =
                GameData.getAchievementDataMap().values().stream()
                        .filter(AchievementData::isUsed)
                        .map(data -> this.getAchievement(data.getId()))
                        .filter(Objects::nonNull)
                        .toList();

        // Bypass linked-stage progress propagation here: each ID has its own required
        // progress and completion state, regardless of its group's representative.
        grantUnfinishedStages(stages, this::update, batch);
        return this.finishBulkUpdate(batch);
    }

    /**
     * Set each unfinished stage to its own threshold, then run the normal transition
     * handler (including its per-achievement completion event). No sibling stage is
     * reset or overwritten by a lower threshold from another ID.
     */
    static void grantUnfinishedStages(
            Iterable<Achievement> stages,
            Function<Achievement, Boolean> update,
            UpdateBatch batch) {
        for (Achievement achievement : stages) {
            if (achievement == null || achievement.getStatus() != Status.Status_UNFINISHED) {
                continue;
            }
            achievement.setCurProgress(achievement.getTotalProgress());
            batch.record(achievement, update.apply(achievement));
        }
    }

    public synchronized int revokeAll() {
        var batch = new UpdateBatch();
        // Each stage has its own ID and persisted state. Revoke those states directly
        // rather than checking the final stage (isParent) for each group.
        for (Achievement achievement : this.achievementList.values()) {
            if (achievement == null || this.isInvalid(achievement.getId())) continue;
            if (resetCompletedAchievement(achievement)) {
                batch.record(achievement, true);
            }
        }
        return this.finishBulkUpdate(batch);
    }

    /** Clear an achieved ID without touching its durable reward-claim history. */
    static boolean resetCompletedAchievement(Achievement achievement) {
        if (achievement == null) return false;
        var status = achievement.getStatus();
        if (status != Status.Status_FINISHED && status != Status.Status_REWARD_TAKEN) {
            return false;
        }
        achievement.setCurProgress(0);
        achievement.setStatus(Status.Status_UNFINISHED);
        achievement.setFinishTimestampSec(0);
        return true;
    }

    private int finishBulkUpdate(UpdateBatch batch) {
        if (batch.isEmpty()) return 0;
        this.computeFinishedAchievementNum();
        this.save();
        batch.flush(this::sendUpdatePacket);
        return batch.changedCount();
    }

    private int notifyOtherAchievements(Achievement achievement) {
        var batch = new UpdateBatch();
        this.updateRelatedAchievements(achievement, batch);
        this.computeFinishedAchievementNum();
        this.save();
        batch.flush(this::sendUpdatePacket);
        return batch.changedCount();
    }

    /** Shared by single-target and bulk operations so events and stage propagation match. */
    private void updateRelatedAchievements(Achievement achievement, UpdateBatch batch) {
        batch.record(achievement, this.update(achievement));
        for (int linkedId :
                GameData.getAchievementDataMap()
                        .get(achievement.getId())
                        .getExcludedGroupAchievementIdList()) {
            var linked = this.getAchievement(linkedId);
            if (linked == null) continue;
            linked.setCurProgress(achievement.getCurProgress());
            batch.record(linked, this.update(linked));
        }
    }

    /** Collect unique affected IDs, count transitions, and emit bounded update packets. */
    static final class UpdateBatch {
        private final Map<Integer, Achievement> updated = new LinkedHashMap<>();
        private int changedCount;

        void record(Achievement achievement, boolean statusChanged) {
            updated.put(achievement.getId(), achievement);
            if (statusChanged) changedCount++;
        }

        boolean isEmpty() {
            return updated.isEmpty();
        }

        int changedCount() {
            return changedCount;
        }

        void flush(Consumer<List<Achievement>> send) {
            if (updated.isEmpty()) return;
            var chunk = new ArrayList<Achievement>(UPDATE_PACKET_BATCH_SIZE);
            for (Achievement achievement : updated.values()) {
                chunk.add(achievement);
                if (chunk.size() == UPDATE_PACKET_BATCH_SIZE) {
                    send.accept(chunk);
                    chunk = new ArrayList<>(UPDATE_PACKET_BATCH_SIZE);
                }
            }
            if (!chunk.isEmpty()) send.accept(chunk);
        }
    }

    private boolean update(Achievement a) {
        if (a.getStatus() == Status.Status_UNFINISHED && a.getCurProgress() >= a.getTotalProgress()) {
            a.setStatus(a.statusOnCompletion());
            a.setFinishTimestampSec(currentTimeSecs.getAsInt());

            // Call PlayerCompleteAchievementEvent.
            new PlayerCompleteAchievementEvent(this.player, a).call();

            return true;
        } else if (this.isFinished(a.getId()) && a.getCurProgress() < a.getTotalProgress()) {
            a.setStatus(Status.Status_UNFINISHED);
            a.setFinishTimestampSec(0);
            return true;
        }

        return false;
    }

    private void computeFinishedAchievementNum() {
        this.finishedAchievementNum =
                GameData.getAchievementDataMap().values().stream()
                        .filter(a -> this.isFinished(a.getId()))
                        .mapToInt(value -> 1)
                        .sum();
    }

    private void sendUpdatePacket(List<Achievement> achievement) {
        if (this.isPacketSendable()) {
            this.player.sendPacket(new PacketAchievementUpdateNotify(achievement));
        }
    }

    @Nullable public Achievement getAchievement(int achievementId) {
        if (this.isInvalid(achievementId)) {
            return null;
        }

        return this.getAchievementList()
                .computeIfAbsent(
                        achievementId,
                        id -> {
                            return new Achievement(
                                    Status.Status_UNFINISHED,
                                    id,
                                    GameData.getAchievementDataMap().get(id.intValue()).getProgress(),
                                    0,
                                    0);
                        });
    }

    public boolean isInvalid(int achievementId) {
        var data = GameData.getAchievementDataMap().get(achievementId);
        return data == null || data.isDisuse();
    }

    public Status getStatus(int achievementId) {
        return this.getAchievementList().getOrDefault(achievementId, INVALID).getStatus();
    }

    public boolean isFinished(int achievementId) {
        var status = this.getStatus(achievementId);
        return status == Status.Status_FINISHED || status == Status.Status_REWARD_TAKEN;
    }

    public synchronized void takeReward(List<Integer> ids) {
        // Validate the entire batch before mutating anything. A duplicate ID or an unfinished,
        // revoked, or already-claimed achievement cannot be redeemed.
        if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
            this.player.sendPacket(new PacketTakeAchievementRewardRsp());
            return;
        }

        List<GameItem> rewards = Lists.newArrayList();
        for (int i : ids) {
            var target = GameData.getAchievementDataMap().get(i);
            if (target == null || !target.isUsed() || !this.isRewardLeft(i)) {
                this.player.sendPacket(new PacketTakeAchievementRewardRsp());
                return;
            }

            var data = GameData.getRewardDataMap().get(target.getFinishRewardId());
            if (data == null) {
                Grasscutter.getLogger().warn("null returned while getting reward data!");
                this.player.sendPacket(new PacketTakeAchievementRewardRsp());
                return;
            }

            for (var itemParamData : data.getRewardItemList()) {
                var itemData = GameData.getItemDataMap().get(itemParamData.getId());
                if (itemData == null) {
                    Grasscutter.getLogger().warn("itemData == null!");
                    this.player.sendPacket(new PacketTakeAchievementRewardRsp(Retcode.RET_FAIL_VALUE));
                    return;
                }
                rewards.add(new GameItem(itemData, itemParamData.getCount()));
            }
        }

        if (rewards.isEmpty()) {
            this.player.sendPacket(new PacketTakeAchievementRewardRsp(Retcode.RET_FAIL_VALUE));
            return;
        }

        var added = this.player.getInventory().addItems(rewards, ActionReason.AchievementReward);
        if (!added.allAccepted()) {
            this.player.sendPacket(new PacketTakeAchievementRewardRsp(rewardGrantRetcode(added)));
            return;
        }

        var claimedAchievements = new ArrayList<Achievement>(ids.size());
        for (int i : ids) {
            var achievement = this.getAchievement(i);
            achievement.setStatus(Status.Status_REWARD_TAKEN);
            claimedAchievements.add(achievement);
        }
        this.sendUpdatePacket(claimedAchievements);
        this.save();

        this.player.sendPacket(
                new PacketTakeAchievementRewardRsp(
                        ids, rewards.stream().map(GameItem::toItemParam).toList()));
    }

    public synchronized void takeGoalReward(List<Integer> ids) {
        // A previously claimed goal (or the same goal twice in one request) is not payable.
        if (ids.isEmpty()
                || new HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(this.takenGoalRewardIdList::contains)) {
            this.player.sendPacket(new PacketTakeAchievementGoalRewardRsp());
            return;
        }

        List<GameItem> rewards = Lists.newArrayList();
        for (int i : ids) {
            var goalData = GameData.getAchievementGoalDataMap().get(i);
            if (goalData == null
                    || goalData.getFinishRewardId() <= 0
                    || !isGoalComplete(i, GameData.getAchievementDataMap().values(), this::isFinished)) {
                Grasscutter.getLogger().warn("null returned while getting goal reward data!");
                this.player.sendPacket(new PacketTakeAchievementGoalRewardRsp());
                return;
            }

            var data = GameData.getRewardDataMap().get(goalData.getFinishRewardId());
            if (data == null) {
                Grasscutter.getLogger().warn("null returned while getting reward data!");
                this.player.sendPacket(new PacketTakeAchievementGoalRewardRsp());
                return;
            }

            for (var itemParamData : data.getRewardItemList()) {
                var itemData = GameData.getItemDataMap().get(itemParamData.getId());
                if (itemData == null) {
                    Grasscutter.getLogger().warn("itemData == null!");
                    this.player.sendPacket(new PacketTakeAchievementGoalRewardRsp());
                    return;
                }
                rewards.add(new GameItem(itemData, itemParamData.getCount()));
            }
        }

        if (rewards.isEmpty()) {
            this.player.sendPacket(new PacketTakeAchievementGoalRewardRsp(Retcode.RET_FAIL_VALUE));
            return;
        }

        var added = this.player.getInventory().addItems(rewards, ActionReason.AchievementGoalReward);
        if (!added.allAccepted()) {
            this.player.sendPacket(new PacketTakeAchievementGoalRewardRsp(rewardGrantRetcode(added)));
            return;
        }

        this.takenGoalRewardIdList.addAll(ids);
        this.save();
        this.player.sendPacket(
                new PacketTakeAchievementGoalRewardRsp(
                        ids, rewards.stream().map(GameItem::toItemParam).toList()));
    }

    private static int rewardGrantRetcode(InventoryAddResult result) {
        return result.entries().stream()
                        .anyMatch(entry -> entry.status() == InventoryAddResult.Status.CAPACITY_FULL
                                || entry.status() == InventoryAddResult.Status.STACK_LIMIT)
                ? Retcode.RET_ITEM_EXCEED_LIMIT_VALUE
                : Retcode.RET_FAIL_VALUE;
    }

    static boolean isGoalComplete(
            int goalId, Collection<AchievementData> achievementData, IntPredicate isFinished) {
        boolean found = false;
        for (var achievement : achievementData) {
            if (achievement.getGoalId() != goalId || !achievement.isUsed()) continue;
            found = true;
            if (!isFinished.test(achievement.getId())) return false;
        }
        return found;
    }

    public boolean isRewardTaken(int achievementId) {
        var achievement = this.achievementList.get(achievementId);
        return achievement != null && achievement.hasClaimedReward();
    }

    public boolean isRewardLeft(int achievementId) {
        var achievement = this.achievementList.get(achievementId);
        return achievement != null
                && achievement.getStatus() == Status.Status_FINISHED
                && !achievement.hasClaimedReward();
    }

    private boolean isPacketSendable() {
        return this.player != null;
    }

    public void save() {
        DatabaseHelper.saveAchievementData(this);
    }

    public void onLogin(Player player) {
        if (this.player == null) {
            this.player = player;
        }

        this.registerNewAchievementsIfExist();
        this.player.sendPacket(new PacketAchievementAllDataNotify(this.player));
        AchievementWatchers.sendAll(this.player);
    }

    private void registerNewAchievementsIfExist() {
        GameData.getAchievementDataMap().values().stream()
                .filter(AchievementData::isUsed)
                .filter(a -> !this.achievementList.containsKey(a.getId()))
                .forEach(
                        a -> {
                            Grasscutter.getLogger().trace("Registering a new achievement (id: {})", a.getId());
                            this.achievementList.put(
                                    a.getId(),
                                    new Achievement(Status.Status_UNFINISHED, a.getId(), a.getProgress(), 0, 0));
                        });
        this.save();
    }
}
