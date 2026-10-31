package emu.grasscutter.game.achievement;

import static emu.grasscutter.net.proto.AchievementOuterClass.Achievement.Status.Status_FINISHED;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.achievement.AchievementData;
import emu.grasscutter.data.excels.achievement.AchievementGoalData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.InventoryAddResult;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeAchievementGoalRewardRspOuterClass.TakeAchievementGoalRewardRsp;
import emu.grasscutter.net.proto.TakeAchievementRewardRspOuterClass.TakeAchievementRewardRsp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
class AchievementRewardDeliveryTest {
    private static final Gson GSON = new Gson();
    private static final int GOAL_ID = 777771;
    private static final int ACHIEVEMENT_ID = 777772;
    private static final int REWARD_ID = 890002;

    @Test
    void goalClaimCanRetryAfterInventoryRefusal() throws Exception {
        withRewardData(() -> {
            var player = new RecordingPlayer();
            var claims = new ArrayList<Integer>();
            var record = new RecordingAchievements(player, claims);
            record.getAchievementList().put(ACHIEVEMENT_ID,
                    new Achievement(Status_FINISHED, ACHIEVEMENT_ID, 1, 1, 42));

            record.takeGoalReward(List.of(GOAL_ID));
            assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, goalResponse(player).getRetcode());
            assertTrue(claims.isEmpty());
            assertEquals(0, record.saves);

            player.inventory.accept = true;
            player.packets.clear();
            record.takeGoalReward(List.of(GOAL_ID));
            assertEquals(0, goalResponse(player).getRetcode());
            assertEquals(List.of(GOAL_ID), claims);
            assertEquals(1, record.saves);
            assertEquals(2, player.inventory.calls);
        });
    }

    @Test
    void singleAchievementClaimCanRetryAfterInventoryRefusal() throws Exception {
        withRewardData(() -> {
            var player = new RecordingPlayer();
            var record = new RecordingAchievements(player, new ArrayList<>());
            var achievement = new Achievement(Status_FINISHED, ACHIEVEMENT_ID, 1, 1, 42);
            record.getAchievementList().put(ACHIEVEMENT_ID, achievement);

            record.takeReward(List.of(ACHIEVEMENT_ID));
            assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, rewardResponse(player).getRetcode());
            assertTrue(record.isRewardLeft(ACHIEVEMENT_ID));
            assertEquals(0, record.saves);

            player.inventory.accept = true;
            player.packets.clear();
            record.takeReward(List.of(ACHIEVEMENT_ID));
            assertEquals(0, rewardResponse(player).getRetcode());
            assertTrue(record.isRewardTaken(ACHIEVEMENT_ID));
            assertEquals(1, record.saves);
            assertEquals(2, player.inventory.calls);
        });
    }

    private static TakeAchievementGoalRewardRsp goalResponse(RecordingPlayer player) throws Exception {
        return TakeAchievementGoalRewardRsp.parseFrom(player.packets.getLast().getData());
    }

    private static TakeAchievementRewardRsp rewardResponse(RecordingPlayer player) throws Exception {
        return TakeAchievementRewardRsp.parseFrom(player.packets.getLast().getData());
    }

    private static void withRewardData(ThrowingRunnable test) throws Exception {
        var goal = GSON.fromJson("{\"id\":" + GOAL_ID + ",\"finishRewardId\":" + REWARD_ID + "}", AchievementGoalData.class);
        var achievement = GSON.fromJson("{\"id\":" + ACHIEVEMENT_ID + ",\"goalId\":" + GOAL_ID
                + ",\"finishRewardId\":" + REWARD_ID + "}", AchievementData.class);
        var reward = GSON.fromJson("{\"rewardId\":" + REWARD_ID
                + ",\"rewardItemList\":[{\"id\":201,\"count\":5}]}", RewardData.class);
        var item = GSON.fromJson("{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class);
        var oldGoal = GameData.getAchievementGoalDataMap().put(GOAL_ID, goal);
        var oldAchievement = GameData.getAchievementDataMap().put(ACHIEVEMENT_ID, achievement);
        var oldReward = GameData.getRewardDataMap().put(REWARD_ID, reward);
        var oldItem = GameData.getItemDataMap().put(201, item);
        try {
            test.run();
        } finally {
            restore(GameData.getAchievementGoalDataMap(), GOAL_ID, oldGoal);
            restore(GameData.getAchievementDataMap(), ACHIEVEMENT_ID, oldAchievement);
            restore(GameData.getRewardDataMap(), REWARD_ID, oldReward);
            restore(GameData.getItemDataMap(), 201, oldItem);
        }
    }

    private static <T> void restore(it.unimi.dsi.fastutil.ints.Int2ObjectMap<T> map, int id, T old) {
        if (old == null) map.remove(id);
        else map.put(id, old);
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private static final class RecordingAchievements extends Achievements {
        int saves;
        RecordingAchievements(Player player, List<Integer> claims) {
            super(null, 1, player, new HashMap<>(), 0, claims);
        }
        @Override public void save() { saves++; }
    }

    private static final class RecordingPlayer extends Player {
        final List<BasePacket> packets = new ArrayList<>();
        final RecordingInventory inventory = new RecordingInventory(this);
        @Override public Inventory getInventory() { return inventory; }
        @Override public void sendPacket(BasePacket packet) { packets.add(packet); }
    }

    private static final class RecordingInventory extends Inventory {
        boolean accept;
        int calls;
        RecordingInventory(Player player) { super(player); }
        @Override public InventoryAddResult addItems(Collection<GameItem> items, ActionReason reason) {
            calls++;
            var entries = new ArrayList<InventoryAddResult.Entry>();
            for (var item : items) {
                entries.add(new InventoryAddResult.Entry(item.getItemId(), item.getCount(),
                        accept ? item.getCount() : 0,
                        accept ? InventoryAddResult.Status.ADDED : InventoryAddResult.Status.CAPACITY_FULL));
            }
            return new InventoryAddResult(entries);
        }
    }
}
