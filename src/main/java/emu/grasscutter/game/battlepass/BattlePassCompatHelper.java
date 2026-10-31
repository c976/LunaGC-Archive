/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.GameData
 *  emu.grasscutter.data.common.ItemParamData
 *  emu.grasscutter.data.common.ItemUseData
 *  emu.grasscutter.data.excels.BattlePassRewardData
 *  emu.grasscutter.data.excels.ItemData
 *  emu.grasscutter.data.excels.RewardData
 *  emu.grasscutter.game.battlepass.BattlePassManager
 *  emu.grasscutter.game.battlepass.BattlePassReward
 *  emu.grasscutter.game.inventory.GameItem
 *  emu.grasscutter.game.inventory.MaterialType
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.props.ItemUseOp
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.BattlePassCycleOuterClass$BattlePassCycle
 *  emu.grasscutter.net.proto.BattlePassProductOuterClass$BattlePassProduct
 *  emu.grasscutter.net.proto.BattlePassRewardPlanOption$_BattlePassRewardPlanOption
 *  emu.grasscutter.net.proto.BattlePassRewardTagOuterClass$BattlePassRewardTag
 *  emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass$BattlePassRewardTakeOption
 *  emu.grasscutter.net.proto.BattlePassScheduleOuterClass$BattlePassSchedule
 *  emu.grasscutter.net.proto.BattlePassScheduleOuterClass$BattlePassSchedule$Builder
 *  emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass$BattlePassUnlockStatus
 *  emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketTakeBattlePassRewardRsp
 */
package emu.grasscutter.game.battlepass;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.common.ItemUseData;
import emu.grasscutter.data.excels.BattlePassRewardData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BattlePassReward;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.BattlePassCycleOuterClass;
import emu.grasscutter.net.proto.BattlePassProductOuterClass;
import emu.grasscutter.net.proto.BattlePassRewardPlanOption;
import emu.grasscutter.net.proto.BattlePassRewardTagOuterClass;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass;
import emu.grasscutter.net.proto.BattlePassScheduleOuterClass;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketTakeBattlePassRewardRsp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class BattlePassCompatHelper {
    public static final int SCHEDULE_ID = 6700;
    public static final int DEFAULT_PLAN = 1;
    public static final int BEYOND_SCHEDULE_ID = 6700;
    public static final int PLAN_SEGMENT_COUNT = 5;
    public static final int BEYOND_LEVEL = 30;
    public static final boolean DISABLE_UI = false;
    public static final boolean DISPLAY_TEST = false;
    public static final int DISPLAY_TEST_ROUND = 2;
    public static final boolean RESET_TAKEN_FOR_UI_CLAIM = false;
    public static final boolean AUTO_CLAIM_ON_LOGIN = false;
    private static final int[] BEYOND_ALL_DATA_PROBES = new int[]{28050, 28051, 28052, 28053, 28054, 28055, 28056, 28057, 28059, 28060, 28061, 28062, 28063, 28064, 28065, 28066, 28067, 28068, 28069, 28070, 28071, 28072, 28073, 28074, 28075, 28076, 28077, 28078, 28079, 28080, 28510, 28511, 28512, 28513, 28514, 28515, 28516, 28517, 28518, 28519, 28520, 28522, 28523, 28524, 28525, 28526, 28527, 28528, 28529, 28530, 28531, 28532, 28600, 28601, 28602, 28603, 28604, 28605, 28610, 28611, 28612, 28613, 28614, 28615, 27980, 27981, 27982, 27983, 27984, 27985, 27986, 27987, 27988, 27989, 27990, 6260, 6270, 6280, 6288, 6289, 6290, 6292, 6294, 6295, 6296, 6297, 6298, 6299, 6300, 7090, 7100, 7105, 7108, 7109, 7111, 7112, 7113, 7114, 7115, 7116, 7117, 7118, 7119, 7120, 25690, 25691, 25692, 25693, 25694, 25695, 25696, 25697, 25699, 25700, 25701, 25702};
    private static final int[] BEYOND_CUR_UPDATE_PROBES = new int[]{28081, 28082, 28083, 28084, 28085, 28533, 28534, 28535, 28536, 28537, 6301, 7121, 7122, 7123, 25703, 25704};
    private static final ConcurrentHashMap<Integer, Integer> SELECTED_PLAN = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Boolean> CLAIMED_THIS_SESSION = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Boolean> PROBED_THIS_SESSION = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Boolean> CLEARED_TAKEN_ONCE = new ConcurrentHashMap();
    private static final int[] BP_STACK_TRIPLE_ITEMS = new int[]{202, 104001, 104002, 104003, 104013};

    private BattlePassCompatHelper() {
    }

    public static int getSelectedPlan(Player player) {
        return player == null ? 1 : SELECTED_PLAN.getOrDefault(player.getUid(), 1);
    }

    public static void setSelectedPlan(Player player, int n) {
        if (player != null) {
            SELECTED_PLAN.put(player.getUid(), n > 0 ? n : 1);
        }
    }

    public static int rewardDataKey(int n) {
        return 100 + n;
    }

    private static int scaledCount(int n, int n2) {
        if (n2 <= 0) {
            return n2;
        }
        for (int n3 : BP_STACK_TRIPLE_ITEMS) {
            if (n3 != n) continue;
            return n2 * 3;
        }
        return n2;
    }

    public static boolean isRewardAllowed(BattlePassManager battlePassManager, int n, int n2) {
        if (n2 > 0 && n > 0) {
            for (int i = 1; i <= 4; ++i) {
                BattlePassRewardData battlePassRewardData = (BattlePassRewardData)GameData.getBattlePassRewardDataMap().get(i * 100 + n);
                if (battlePassRewardData == null) continue;
                if (battlePassRewardData.getFreeRewardIdList() != null && battlePassRewardData.getFreeRewardIdList().contains(n2)) {
                    return true;
                }
                if (!battlePassManager.isPaid() || battlePassRewardData.getPaidRewardIdList() == null || !battlePassRewardData.getPaidRewardIdList().contains(n2)) continue;
                return true;
            }
            return GameData.getRewardDataMap().containsKey(n2);
        }
        return false;
    }

    public static void takeReward(BattlePassManager battlePassManager, List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> list) {
        if (battlePassManager != null && battlePassManager.getPlayer() != null && list != null) {
            ArrayList<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> arrayList = new ArrayList<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption>();
            for (BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption object : list) {
                if (object == null || object.getTag() == null) continue;
                int battlePassRewardTakeOption = object.getTag().getRewardId();
                int battlePassRewardTag = object.getTag().getLevel();
                if (battlePassRewardTakeOption != 0 && battlePassRewardTag <= battlePassManager.getLevel()) {
                    if (battlePassManager.getTakenRewards().containsKey(battlePassRewardTakeOption)) {
                        Grasscutter.getLogger().info("BattlePass already taken uid={} rewardId={}", (Object)battlePassManager.getPlayer().getUid(), (Object)battlePassRewardTakeOption);
                        continue;
                    }
                    if (!BattlePassCompatHelper.isRewardAllowed(battlePassManager, battlePassRewardTag, battlePassRewardTakeOption)) {
                        Grasscutter.getLogger().info("Not in rewards list: {}", (Object)battlePassRewardTakeOption);
                        continue;
                    }
                    arrayList.add(object);
                    continue;
                }
                Grasscutter.getLogger().info("BattlePass claim skip uid={} rewardId={} level={} playerLv={}", new Object[]{battlePassManager.getPlayer().getUid(), battlePassRewardTakeOption, battlePassRewardTag, battlePassManager.getLevel()});
            }
            if (arrayList.isEmpty()) {
                Grasscutter.getLogger().info("BattlePass claim empty uid={} requested={}", (Object)battlePassManager.getPlayer().getUid(), (Object)list.size());
            }
            Object object = null;
            if (!arrayList.isEmpty()) {
                object = new ArrayList();
                for (BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption battlePassRewardTakeOption : arrayList) {
                    BattlePassRewardTagOuterClass.BattlePassRewardTag battlePassRewardTag = battlePassRewardTakeOption.getTag();
                    int n = battlePassRewardTakeOption.getOptionIdx();
                    RewardData rewardData = (RewardData)GameData.getRewardDataMap().get(battlePassRewardTag.getRewardId());
                    if (rewardData == null) continue;
                    for (ItemParamData itemParamData : rewardData.getRewardItemList()) {
                        ItemData itemData;
                        if (itemParamData == null || itemParamData.getItemId() <= 0 || (itemData = (ItemData)GameData.getItemDataMap().get(itemParamData.getItemId())) == null) continue;
                        if (itemData.getMaterialType() == MaterialType.MATERIAL_SELECTABLE_CHEST) {
                            BattlePassCompatHelper.takeRewardsFromSelectChest(itemData, n, itemParamData, (List<GameItem>)object);
                            continue;
                        }
                        int n2 = BattlePassCompatHelper.scaledCount(itemParamData.getItemId(), itemParamData.getItemCount());
                        ((ArrayList)object).add(new GameItem(itemData, n2));
                    }
                    BattlePassReward battlePassReward = new BattlePassReward(battlePassRewardTag.getLevel(), battlePassRewardTag.getRewardId(), battlePassRewardTag.getUnlockStatus() == BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID);
                    battlePassManager.getTakenRewards().put(battlePassReward.getRewardId(), battlePassReward);
                }
                battlePassManager.save();
                battlePassManager.getPlayer().getInventory().addItems((Collection)object);
                battlePassManager.getPlayer().sendPacket((BasePacket)new PacketBattlePassCurScheduleUpdateNotify(battlePassManager.getPlayer()));
                Grasscutter.getLogger().info("BattlePass claim ok uid={} granted={} items={}", new Object[]{battlePassManager.getPlayer().getUid(), arrayList.size(), ((ArrayList)object).size()});
            }
            battlePassManager.getPlayer().sendPacket((BasePacket)new PacketTakeBattlePassRewardRsp(list, (List)object));
        }
        Object var14_16 = null;
        try {
            if (battlePassManager != null && battlePassManager.getPlayer() != null) {
                battlePassManager.getPlayer().sendPacket((BasePacket)new PacketBeyondBattlePassCurScheduleUpdateNotify(battlePassManager.getPlayer()));
            }
        }
        catch (Throwable throwable) {}
    }

    private static void takeRewardsFromSelectChest(ItemData itemData, int n, ItemParamData itemParamData, List<GameItem> list) {
        String[] stringArray;
        ItemUseData itemUseData;
        if (itemData.getItemUse() != null && itemData.getItemUse().size() >= 1 && (itemUseData = (ItemUseData)itemData.getItemUse().get(0)).getUseParam() != null && itemUseData.getUseParam().length >= 1 && (stringArray = itemUseData.getUseParam()[0].split(",")).length >= n && n >= 1) {
            int n2 = Integer.parseInt(stringArray[n - 1].trim());
            if (itemUseData.getUseOp() == ItemUseOp.ITEM_USE_ADD_SELECT_ITEM) {
                ItemData itemData2 = (ItemData)GameData.getItemDataMap().get(n2);
                if (itemData2 != null) {
                    list.add(new GameItem(itemData2, BattlePassCompatHelper.scaledCount(n2, itemParamData.getItemCount())));
                }
            } else if (itemUseData.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
                RewardData rewardData = (RewardData)GameData.getRewardDataMap().get(n2);
                if (rewardData == null) {
                    return;
                }
                for (ItemParamData itemParamData2 : rewardData.getRewardItemList()) {
                    ItemData itemData3;
                    if (itemParamData2 == null || itemParamData2.getItemId() <= 0 || (itemData3 = (ItemData)GameData.getItemDataMap().get(itemParamData2.getItemId())) == null) continue;
                    list.add(new GameItem(itemData3, BattlePassCompatHelper.scaledCount(itemParamData2.getItemId(), itemParamData2.getItemCount())));
                }
            } else {
                Grasscutter.getLogger().error("Invalid chest type for BP reward.");
            }
        }
    }

    public static void clearSession(int n) {
        CLAIMED_THIS_SESSION.remove(n);
        PROBED_THIS_SESSION.remove(n);
    }

    public static void clearPlayerState(int uid) {
        clearSession(uid);
        SELECTED_PLAN.remove(uid);
        CLEARED_TAKEN_ONCE.remove(uid);
    }

    public static void clearBeyondProbe(int n) {
        PROBED_THIS_SESSION.remove(n);
    }

    public static int beginTime() {
        return 1785528000;
    }

    public static int endTime() {
        return 1795982399;
    }

    public static BattlePassScheduleOuterClass.BattlePassSchedule buildSchedule(BattlePassManager battlePassManager) {
        int n = BattlePassCompatHelper.beginTime();
        int n2 = n - 604800;
        int n3 = BattlePassCompatHelper.endTime();
        int n4 = battlePassManager != null ? battlePassManager.getLevel() : 0;
        int n5 = battlePassManager != null ? battlePassManager.getPoint() : 0;
        int n6 = battlePassManager != null ? battlePassManager.getCyclePoints() : 0;
        boolean bl = battlePassManager != null && battlePassManager.isPaid();
        int n7 = battlePassManager != null && battlePassManager.getPlayer() != null ? BattlePassCompatHelper.getSelectedPlan(battlePassManager.getPlayer()) : 1;
        LocalDate localDate = LocalDate.now();
        LocalDate localDate2 = localDate.getDayOfWeek() == DayOfWeek.SUNDAY ? localDate : LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        LocalDateTime localDateTime = LocalDateTime.of(localDate2.getYear(), localDate2.getMonthValue(), localDate2.getDayOfMonth(), 23, 59, 59);
        int n8 = (int)localDateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        BattlePassProductOuterClass.BattlePassProduct battlePassProduct = BattlePassProductOuterClass.BattlePassProduct.newBuilder().setNormalProductId("201").setExtraProductId("202").setUpgradeProductId("203").build();
        BattlePassScheduleOuterClass.BattlePassSchedule.Builder builder = BattlePassScheduleOuterClass.BattlePassSchedule.newBuilder().setScheduleId(6700).setLevel(n4).setPoint(n5).setCurCyclePoints(n6).setBeginTime(n2).setEndTime(n3).setIsViewed(true).setPaidPlatformFlags(bl ? 3 : 0).setProductInfo(battlePassProduct).setUnlockStatus(bl ? BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID : BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_FREE).setCurCycle(BattlePassCycleOuterClass.BattlePassCycle.newBuilder().setBeginTime(n2).setEndTime(n8).setCycleIdx(1).build());
        for (int i = 1; i <= 5; ++i) {
            builder.addRewardPlanOptionList(BattlePassRewardPlanOption._BattlePassRewardPlanOption.newBuilder().setBattlePassPlan(n7).setFBHFDJJIDBD(i).setENGHPDCKACD(false).build());
        }
        if (battlePassManager != null && battlePassManager.getTakenRewards() != null) {
            for (BattlePassReward battlePassReward : battlePassManager.getTakenRewards().values()) {
                if (battlePassReward == null || !bl && battlePassReward.isPaid()) continue;
                builder.addRewardTakenList(battlePassReward.toProto());
            }
        }
        return builder.build();
    }

    public static boolean unlockPaidFromItem(Player player) {
        if (player != null && player.getBattlePassManager() != null) {
            BattlePassManager battlePassManager = player.getBattlePassManager();
            if (!BattlePassCompatHelper.setPaidFlag(battlePassManager, true)) {
                return false;
            }
            battlePassManager.save();
            player.sendPacket((BasePacket)new PacketBattlePassCurScheduleUpdateNotify(player));
            Grasscutter.getLogger().info("BattlePass unlocked paid via item uid={}", (Object)player.getUid());
            return true;
        }
        return false;
    }

    public static boolean setPaidFlag(BattlePassManager battlePassManager, boolean bl) {
        if (battlePassManager == null) return false;
        battlePassManager.setPaid(bl);
        return true;
    }

    public static void prepareClaimState(BattlePassManager battlePassManager) {
    }

    public static void ensureClaimReady(BattlePassManager battlePassManager) {
        if (battlePassManager == null || battlePassManager.getPlayer() == null) {
            // empty if block
        }
    }

    public static void claimAllAvailableOnce(BattlePassManager battlePassManager) {
        int n;
        if (battlePassManager != null && battlePassManager.getPlayer() != null && !Boolean.TRUE.equals(CLAIMED_THIS_SESSION.putIfAbsent(n = battlePassManager.getPlayer().getUid(), Boolean.TRUE))) {
            BattlePassCompatHelper.ensureClaimReady(battlePassManager);
            ArrayList<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> arrayList = new ArrayList<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption>();
            for (int i = 1; i <= battlePassManager.getLevel() && i <= 50; ++i) {
                BattlePassRewardData battlePassRewardData = (BattlePassRewardData)GameData.getBattlePassRewardDataMap().get(BattlePassCompatHelper.rewardDataKey(i));
                if (battlePassRewardData == null) continue;
                BattlePassCompatHelper.addOptions(arrayList, battlePassManager, i, battlePassRewardData.getFreeRewardIdList(), false);
                BattlePassCompatHelper.addOptions(arrayList, battlePassManager, i, battlePassRewardData.getPaidRewardIdList(), true);
            }
            if (arrayList.isEmpty()) {
                Grasscutter.getLogger().info("BattlePass auto-claim uid={} nothing left", (Object)n);
            } else {
                Grasscutter.getLogger().info("BattlePass auto-claim uid={} options={}", (Object)n, (Object)arrayList.size());
                try {
                    battlePassManager.takeReward(arrayList);
                }
                catch (Exception exception) {
                    CLAIMED_THIS_SESSION.remove(n);
                    Grasscutter.getLogger().error("BattlePass auto-claim failed uid=" + n, (Throwable)exception);
                }
            }
        }
    }

    private static void addOptions(List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> list, BattlePassManager battlePassManager, int n, List<Integer> list2, boolean bl) {
        if (list2 != null) {
            for (int n2 : list2) {
                if (n2 <= 0 || battlePassManager.getTakenRewards().containsKey(n2)) continue;
                list.add(BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption.newBuilder().setOptionIdx(1).setTag(BattlePassRewardTagOuterClass.BattlePassRewardTag.newBuilder().setLevel(n).setRewardId(n2).setUnlockStatus(bl ? BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID : BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus.BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_FREE).build()).build());
            }
        }
    }

    public static void probeBeyondNoSchedule(Player player) {
    }

    public static void logBuilt(Player player, String string) {
        Grasscutter.getLogger().info("BattlePassCompat {} uid={} uiDisabled={} displayTest={} r{} schedule={}", new Object[]{string, player != null ? player.getUid() : 0, false, false, 2, 6700});
    }

    public static void logMissions(Player player, int n) {
        Grasscutter.getLogger().info("BattlePass missions seeded uid={} count={}", (Object)(player != null ? player.getUid() : 0), (Object)n);
    }
}

