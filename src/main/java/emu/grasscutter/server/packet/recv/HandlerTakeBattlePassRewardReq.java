package emu.grasscutter.server.packet.recv;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BattlePassRewardData;
import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BattlePassReward;
import emu.grasscutter.game.battlepass.BattlePassSelectChestHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BattlePassRewardTagOuterClass;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass;
import emu.grasscutter.net.proto.TakeBattlePassRewardReqOuterClass.TakeBattlePassRewardReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketTakeBattlePassRewardRsp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Opcodes(PacketOpcodes.TakeBattlePassRewardReq)
public class HandlerTakeBattlePassRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Player player = session.getPlayer();
        if (player == null || player.getBattlePassManager() == null) return;
        BattlePassManager manager = player.getBattlePassManager();
        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> options = new ArrayList<>();
        try {
            TakeBattlePassRewardReq req = TakeBattlePassRewardReq.parseFrom(payload);
            options.addAll(req.getTakeOptionListList());
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("TakeBP OuterClass parse: {}", throwable.toString());
        }
        if (options.isEmpty()) options.addAll(parseOptionsFromWire(payload));
        if (options.isEmpty()) options.addAll(buildAllClaimableNonSelectable(manager));

        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> normal = new ArrayList<>();
        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> selectable = new ArrayList<>();
        for (var option : options) {
            if (option == null || !option.hasTag() || option.getTag().getRewardId() <= 0) continue;
            if (BattlePassSelectChestHelper.isSelectableReward(option.getTag().getRewardId())) {
                if (option.getOptionIdx() > 0) selectable.add(option);
            } else normal.add(option);
        }
        if (!normal.isEmpty()) {
            BattlePassCompatHelper.takeReward(manager, normal);
            if (selectable.isEmpty()) return;
        }
        List<GameItem> granted = new ArrayList<>();
        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> claimed = new ArrayList<>();
        for (var option : selectable) {
            int rewardId = option.getTag().getRewardId();
            int level = option.getTag().getLevel();
            boolean paid = option.getTag().getUnlockStatus()
                    == BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID;
            manager.getTakenRewards().remove(rewardId);
            List<GameItem> items = BattlePassSelectChestHelper.grant(player, rewardId, option.getOptionIdx(), paid);
            if (items.isEmpty()) continue;
            manager.getTakenRewards().put(rewardId, new BattlePassReward(level, rewardId, paid));
            granted.addAll(items);
            claimed.add(option);
        }
        if (!claimed.isEmpty()) {
            manager.save();
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketTakeBattlePassRewardRsp(claimed, granted));
        } else if (normal.isEmpty()) {
            player.sendPacket(new PacketTakeBattlePassRewardRsp(options, granted));
        }
    }

    private static List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> parseOptionsFromWire(byte[] payload) {
        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> result = new ArrayList<>();
        try {
            UnknownFieldSet fields = UnknownFieldSet.parseFrom(payload);
            for (int fieldNumber : new int[] {TakeBattlePassRewardReq.TAKE_OPTION_LIST_FIELD_NUMBER, 10, 11, 8, 1, 2, 5, 7, 9, 12, 13, 14, 15}) {
                UnknownFieldSet.Field field = fields.getField(fieldNumber);
                if (field == null) continue;
                for (ByteString bytes : field.getLengthDelimitedList()) {
                    var option = parseOneOption(bytes.toByteArray());
                    if (option != null) result.add(option);
                }
                if (!result.isEmpty()) break;
            }
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("TakeBP wire parse failed: {}", throwable.toString());
        }
        return result;
    }

    private static BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption parseOneOption(byte[] payload) {
        try {
            UnknownFieldSet fields = UnknownFieldSet.parseFrom(payload);
            int optionIdx = firstVarint(fields, BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption.OPTION_IDX_FIELD_NUMBER);
            ByteString tagBytes = firstBytes(fields, BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption.TAG_FIELD_NUMBER);
            if (tagBytes == null) return null;
            int rewardId = 0, level = 1, unlock = 0;
            try {
                var tag = BattlePassRewardTagOuterClass.BattlePassRewardTag.parseFrom(tagBytes);
                rewardId = tag.getRewardId();
                level = Math.max(1, tag.getLevel());
                unlock = tag.getUnlockStatus().getNumber();
            } catch (Throwable ignored) {
                UnknownFieldSet tag = UnknownFieldSet.parseFrom(tagBytes);
                rewardId = firstVarint(tag, 7, 1);
                level = Math.max(1, firstVarint(tag, 15, 9));
                unlock = firstVarint(tag, 4, 13);
            }
            if (rewardId <= 0) return null;
            var status = unlock == BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID.getNumber()
                    ? BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID
                    : BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_FREE;
            var builder = BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption.newBuilder()
                    .setTag(BattlePassRewardTagOuterClass.BattlePassRewardTag.newBuilder().setRewardId(rewardId)
                            .setLevel(level).setUnlockStatus(status).build());
            if (optionIdx > 0) builder.setOptionIdx(optionIdx);
            return builder.build();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> buildAllClaimableNonSelectable(BattlePassManager manager) {
        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> result = new ArrayList<>();
        int maxLevel = Math.min(Math.max(manager.getLevel(), 0), 50);
        Map<Integer, BattlePassReward> taken = manager.getTakenRewards();
        for (int level = 1; level <= maxLevel; level++) {
            BattlePassRewardData data = GameData.getBattlePassRewardDataMap().get(BattlePassCompatHelper.rewardDataKey(level));
            if (data == null) {
                for (int plan = 1; plan <= 4 && data == null; plan++) data = GameData.getBattlePassRewardDataMap().get(plan * 100 + level);
            }
            if (data == null) continue;
            addOptions(result, taken, level, data.getFreeRewardIdList(), false);
            if (manager.isPaid()) addOptions(result, taken, level, data.getPaidRewardIdList(), true);
        }
        return result;
    }

    private static void addOptions(List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> result, Map<Integer, BattlePassReward> taken, int level, List<Integer> ids, boolean paid) {
        if (ids == null) return;
        for (Integer id : ids) {
            if (id == null || id <= 0 || taken.containsKey(id) || BattlePassSelectChestHelper.isSelectableReward(id)) continue;
            result.add(BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption.newBuilder().setOptionIdx(1)
                    .setTag(BattlePassRewardTagOuterClass.BattlePassRewardTag.newBuilder().setRewardId(id).setLevel(level)
                            .setUnlockStatus(paid ? BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID
                                    : BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_FREE).build()).build());
        }
    }

    private static int firstVarint(UnknownFieldSet fields, int... numbers) {
        for (int number : numbers) {
            UnknownFieldSet.Field field = fields.getField(number);
            if (field != null && !field.getVarintList().isEmpty()) return field.getVarintList().get(0).intValue();
        }
        return 0;
    }

    private static ByteString firstBytes(UnknownFieldSet fields, int... numbers) {
        for (int number : numbers) {
            UnknownFieldSet.Field field = fields.getField(number);
            if (field != null && !field.getLengthDelimitedList().isEmpty()) return field.getLengthDelimitedList().get(0);
        }
        return null;
    }
}
