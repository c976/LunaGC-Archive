package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GlobalWatcherConfigData;
import emu.grasscutter.data.excels.codex.PushTipsConfigData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketCodexDataUpdateNotify;
import emu.grasscutter.server.packet.send.PacketGetPushTipsRewardRsp;
import emu.grasscutter.server.packet.send.PacketPushTipsChangeNotify;
import emu.grasscutter.server.packet.send.PacketPushTipsReadFinishRsp;
import emu.grasscutter.server.packet.send.PacketWatcherChangeNotify;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Push tips (the tutorial codex).
 *
 * <p>A tip is recorded only when its watcher fires: a quest finishes, a monster is met for the first
 * time, or the client reports a client-side trigger. A fresh account therefore starts with none.
 * Each tip is {@link #STATE_START unread} until the client reports it read, then {@link
 * #STATE_FINISH finished}; the reward (if any) can be claimed once the tip is finished.
 *
 * <p>All game access goes through {@link Catalog}, the packet sink and the reward hook so the rules
 * can be exercised without a server; {@link #of(Player)} wires the real ones.
 */
public final class PushTipsManager {
    /** Unread (PUSH_TIPS_STATE_START). */
    public static final int STATE_START = 1;

    /** Read (PUSH_TIPS_STATE_FINISH), value taken off a 7.0 capture. */
    public static final int STATE_FINISH = 3;

    /** CodexType CODEX_PUSHTIPS. */
    public static final int CODEX_TYPE_PUSHTIPS = 6;

    /** Static data the rules need. */
    public interface Catalog {
        PushTipsConfigData tip(int tipId);

        Collection<PushTipsConfigData> tips();

        GlobalWatcherConfigData watcher(int watcherId);

        /** Id of the tip in the codex list, or 0 when it has no codex entry. */
        int codexId(int tipId);
    }

    /** Hands out the items of a reward id. Returns false if nothing was delivered. */
    public interface RewardGranter {
        boolean grant(int rewardId);
    }

    private final int uid;
    private final Supplier<PlayerCodex> codexSource;
    private final Catalog catalog;
    private final Consumer<BasePacket> out;
    private final Runnable save;
    private final RewardGranter rewards;

    public PushTipsManager(
            int uid,
            Supplier<PlayerCodex> codexSource,
            Catalog catalog,
            Consumer<BasePacket> out,
            Runnable save,
            RewardGranter rewards) {
        this.uid = uid;
        this.codexSource = codexSource;
        this.catalog = catalog;
        this.out = out;
        this.save = save;
        this.rewards = rewards;
    }

    public static PushTipsManager of(Player player) {
        return new PushTipsManager(
                player.getUid(),
                player::getCodex,
                GAME_DATA,
                player::sendPacket,
                player::save,
                rewardId -> grantReward(player, rewardId));
    }

    private PlayerCodex codex() {
        return this.codexSource.get();
    }

    // ---------------------------------------------------------------- queries

    public boolean isUnlocked(int tipId) {
        return this.codex().getPushTipsState().containsKey(tipId);
    }

    /** Snapshot of tipId -> state for PushTipsAllDataNotify, ordered by tip id. */
    public TreeMap<Integer, Integer> states() {
        synchronized (this.codex()) {
            return new TreeMap<>(this.codex().getPushTipsState());
        }
    }

    /** Codex ids (type 6) of the recorded tips that have a codex entry, ordered. */
    public List<Integer> unlockedCodexIds() {
        var ids = new TreeMap<Integer, Boolean>();
        synchronized (this.codex()) {
            for (int tipId : this.codex().getPushTipsState().keySet()) {
                int codexId = this.recordedCodexId(this.catalog.tip(tipId));
                if (codexId > 0) ids.put(codexId, true);
            }
        }
        return new ArrayList<>(ids.keySet());
    }

    public boolean isCodexViewed(int codexId) {
        return this.codex().getViewedPushTipsCodex().contains(codexId);
    }

    /**
     * Watcher ids the client should keep watching: those of every tip not recorded yet. Watchers
     * flagged as disused are left out.
     */
    public Set<Integer> pendingWatcherIds() {
        var ids = new LinkedHashSet<Integer>();
        var state = this.codex().getPushTipsState();
        for (var tip : this.catalog.tips()) {
            if (tip.getWatcherId() <= 0 || state.containsKey(tip.getPushTipsId())) continue;
            var watcher = this.catalog.watcher(tip.getWatcherId());
            if (watcher == null || !watcher.isUsed()) continue;
            ids.add(tip.getWatcherId());
        }
        return ids;
    }

    // ---------------------------------------------------------------- unlocking

    /**
     * Records a tip as unread and tells the client. Returns false for an unknown tip or one that is
     * already recorded (nothing is sent then).
     */
    public boolean unlock(int tipId, String source) {
        var tip = this.catalog.tip(tipId);
        if (tip == null) return false;

        int codexId;
        synchronized (this.codex()) {
            var state = this.codex().getPushTipsState();
            if (state.containsKey(tipId)) return false;
            state.put(tipId, STATE_START);
            codexId = this.recordedCodexId(tip);
        }
        this.save.run();

        this.out.accept(new PacketPushTipsChangeNotify(tipId, STATE_START));
        if (codexId > 0) this.out.accept(new PacketCodexDataUpdateNotify(CODEX_TYPE_PUSHTIPS, codexId));
        this.stopWatching(tip);

        Grasscutter.getLogger()
                .info(
                        "[PushTips] uid={} unlocked tip {} (codex {}) by {}",
                        this.uid,
                        tipId,
                        codexId,
                        source);
        return true;
    }

    /** The client-side watcher {@code watcherId} fired: unlock every tip bound to it. */
    public int onWatcherEvent(int watcherId) {
        var watcher = this.catalog.watcher(watcherId);
        if (watcher == null || !watcher.isUsed()) return 0;
        return this.unlockMatching("watcher " + watcherId, tip -> tip.getWatcherId() == watcherId);
    }

    /**
     * WatcherEventTypeNotify：客户端按触发类型上报，解锁观察器类型和参数都对得上的提示。
     * 推断：提示的观察器进度基本都是 1，这里不累计进度，单次上报够数才解锁。
     */
    public int onTypeEvent(WatcherTriggerType type, List<Integer> params, int addProgress) {
        if (type == null) return 0;
        var state = this.codex().getPushTipsState();
        int unlocked = 0;
        for (var tip : new ArrayList<>(this.catalog.tips())) {
            if (tip.getWatcherId() <= 0 || state.containsKey(tip.getPushTipsId())) continue;
            var watcher = this.catalog.watcher(tip.getWatcherId());
            if (watcher == null || !watcher.isUsed() || watcher.getTriggerConfig() == null) continue;
            if (watcher.getTriggerConfig().getTriggerType() != type) continue;
            if (!emu.grasscutter.game.achievement.AchievementWatchers.paramsMatch(
                    watcher.getTriggerConfig().getParamList(), params)) continue;
            if (Math.max(1, addProgress) < watcher.getProgress()) continue;
            if (this.unlock(tip.getPushTipsId(), "type " + type + " via watcher " + tip.getWatcherId())) {
                unlocked++;
            }
        }
        return unlocked;
    }

    /** A sub quest finished: unlock the tips whose TRIGGER_FINISH_QUEST_OR lists it. */
    public int onQuestFinished(int subQuestId) {
        return this.unlockByTrigger(
                WatcherTriggerType.TRIGGER_FINISH_QUEST_OR, subQuestId, "quest " + subQuestId);
    }

    /** A monster was met for the first time: unlock the tips whose TRIGGER_NEW_MONSTER lists it. */
    public int onNewMonster(int monsterId) {
        return this.unlockByTrigger(
                WatcherTriggerType.TRIGGER_NEW_MONSTER, monsterId, "monster " + monsterId);
    }

    private int unlockByTrigger(WatcherTriggerType type, int id, String source) {
        var state = this.codex().getPushTipsState();
        int unlocked = 0;
        // Copy: unlock() may touch the same collections while we walk the catalog.
        for (var tip : new ArrayList<>(this.catalog.tips())) {
            if (tip.getWatcherId() <= 0 || state.containsKey(tip.getPushTipsId())) continue;
            var watcher = this.catalog.watcher(tip.getWatcherId());
            if (watcher == null || !watcher.isUsed() || watcher.getTriggerConfig() == null) continue;
            if (watcher.getTriggerConfig().getTriggerType() != type) continue;
            if (!paramListContains(watcher.getTriggerConfig().getParamList(), id)) continue;
            if (this.unlock(tip.getPushTipsId(), source + " via watcher " + tip.getWatcherId())) {
                unlocked++;
            }
        }
        return unlocked;
    }

    /**
     * Records, as already read, the tips whose server-side trigger the player met before tips were
     * tracked: a listed sub quest is finished or a listed monster is in the animal codex. These are
     * what an official account at the same progress has; client-side triggers cannot be replayed.
     * Sends nothing: the login PushTipsAllDataNotify that follows carries them.
     */
    public int backfill(IntPredicate questFinished, IntPredicate monsterSeen) {
        int recorded = 0;
        synchronized (this.codex()) {
            var state = this.codex().getPushTipsState();
            for (var tip : this.catalog.tips()) {
                if (tip.getWatcherId() <= 0 || state.containsKey(tip.getPushTipsId())) continue;
                var watcher = this.catalog.watcher(tip.getWatcherId());
                if (watcher == null || !watcher.isUsed() || watcher.getTriggerConfig() == null) continue;
                var params = watcher.getTriggerConfig().getParamList();
                // Trigger types this server has no enum value for load as null.
                var type = watcher.getTriggerConfig().getTriggerType();
                IntPredicate met =
                        type == WatcherTriggerType.TRIGGER_FINISH_QUEST_OR
                                ? questFinished
                                : type == WatcherTriggerType.TRIGGER_NEW_MONSTER ? monsterSeen : null;
                if (met == null || paramIds(params).stream().noneMatch(met::test)) continue;
                state.put(tip.getPushTipsId(), STATE_FINISH);
                recorded++;
            }
        }
        if (recorded > 0) {
            this.save.run();
            Grasscutter.getLogger()
                    .info("[PushTips] uid={} backfilled {} tip(s) from finished quests and seen monsters",
                            this.uid, recorded);
        }
        return recorded;
    }

    private int unlockMatching(String source, java.util.function.Predicate<PushTipsConfigData> filter) {
        int unlocked = 0;
        for (var tip : new ArrayList<>(this.catalog.tips())) {
            if (filter.test(tip) && this.unlock(tip.getPushTipsId(), source)) unlocked++;
        }
        return unlocked;
    }

    /** Drops the tip's watcher from the client once no other unrecorded tip still needs it. */
    private void stopWatching(PushTipsConfigData tip) {
        int watcherId = tip.getWatcherId();
        if (watcherId <= 0) return;
        var state = this.codex().getPushTipsState();
        for (var other : this.catalog.tips()) {
            if (other.getWatcherId() == watcherId && !state.containsKey(other.getPushTipsId())) return;
        }
        this.out.accept(new PacketWatcherChangeNotify(List.of(), List.of(watcherId)));
    }

    // ---------------------------------------------------------------- client requests

    /** PushTipsReadFinishReq. */
    public void readFinish(int tipId) {
        boolean known;
        synchronized (this.codex()) {
            var state = this.codex().getPushTipsState();
            known = state.containsKey(tipId);
            if (known) state.put(tipId, STATE_FINISH);
        }
        if (!known) {
            Grasscutter.getLogger()
                    .info("[PushTips] uid={} read of tip {} which is not unlocked", this.uid, tipId);
            this.out.accept(
                    new PacketPushTipsReadFinishRsp(Retcode.RET_PUSH_TIPS_NOT_FOUND_VALUE, tipId));
            return;
        }
        this.save.run();
        Grasscutter.getLogger().info("[PushTips] uid={} finished reading tip {}", this.uid, tipId);
        this.out.accept(new PacketPushTipsReadFinishRsp(Retcode.RET_SUCC_VALUE, tipId));
    }

    /** GetPushTipsRewardReq: pays every finished, unclaimed tip with a reward. */
    public void claimRewards(List<Integer> tipIds) {
        var claimed = new ArrayList<Integer>();
        synchronized (this.codex()) {
            var codex = this.codex();
            for (int tipId : new LinkedHashSet<>(tipIds)) {
                var tip = this.catalog.tip(tipId);
                if (tip == null || tip.getRewardId() <= 0) continue;
                if (codex.getPushTipsState().getOrDefault(tipId, 0) != STATE_FINISH) continue;
                if (codex.getClaimedPushTipsReward().contains(tipId)) continue;
                // A bag that cannot take the reward leaves the tip claimable for a retry.
                if (!this.rewards.grant(tip.getRewardId())) continue;
                codex.getClaimedPushTipsReward().add(tipId);
                claimed.add(tipId);
                Grasscutter.getLogger()
                        .info(
                                "[PushTips] uid={} claimed reward {} of tip {}",
                                this.uid,
                                tip.getRewardId(),
                                tipId);
            }
        }
        if (claimed.isEmpty()) {
            Grasscutter.getLogger()
                    .info("[PushTips] uid={} nothing to claim for tips {}", this.uid, tipIds);
            this.out.accept(
                    new PacketGetPushTipsRewardRsp(Retcode.RET_PUSH_TIPS_NOT_FOUND_VALUE, List.of()));
            return;
        }
        this.save.run();
        this.out.accept(new PacketGetPushTipsRewardRsp(Retcode.RET_SUCC_VALUE, claimed));
    }

    /** SetCodexPushtipsReadReq: the player opened a codex entry. */
    public void setCodexViewed(int codexId) {
        boolean added;
        synchronized (this.codex()) {
            added = this.codex().getViewedPushTipsCodex().add(codexId);
        }
        if (added) this.save.run();
    }

    // ---------------------------------------------------------------- helpers

    private int recordedCodexId(PushTipsConfigData tip) {
        if (tip == null || !tip.isRecordedInCodex()) return 0;
        return this.catalog.codexId(tip.getPushTipsId());
    }

    /** The ids in {@code paramList[0]}, a comma separated list. */
    static List<Integer> paramIds(String[] paramList) {
        var ids = new ArrayList<Integer>();
        if (paramList == null || paramList.length == 0 || paramList[0] == null) return ids;
        for (var part : paramList[0].split(",")) {
            try {
                ids.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // Not an id; skip it.
            }
        }
        return ids;
    }

    /** {@code paramList[0]} is a comma separated id list; true if it holds {@code id}. */
    static boolean paramListContains(String[] paramList, int id) {
        if (paramList == null || paramList.length == 0 || paramList[0] == null) return false;
        for (var part : paramList[0].split(",")) {
            var text = part.trim();
            if (text.isEmpty()) continue;
            try {
                if (Integer.parseInt(text) == id) return true;
            } catch (NumberFormatException ignored) {
                // Not an id; skip it.
            }
        }
        return false;
    }

    private static boolean grantReward(Player player, int rewardId) {
        var reward = GameData.getRewardDataMap().get(rewardId);
        if (reward == null) {
            Grasscutter.getLogger().warn("[PushTips] reward {} does not exist", rewardId);
            return false;
        }
        var items = new ArrayList<GameItem>();
        for (var param : reward.getAllRewardItems()) {
            if (param.getId() > 0 && param.getCount() > 0) {
                items.add(new GameItem(param.getId(), param.getCount()));
            }
        }
        return player.getInventory().addItems(items, ActionReason.PushTipsReward).allAccepted();
    }

    /** Catalog backed by the loaded excel tables. */
    private static final Catalog GAME_DATA =
            new Catalog() {
                @Override
                public PushTipsConfigData tip(int tipId) {
                    return GameData.getPushTipsConfigDataMap().get(tipId);
                }

                @Override
                public Collection<PushTipsConfigData> tips() {
                    return GameData.getPushTipsConfigDataMap().values();
                }

                @Override
                public GlobalWatcherConfigData watcher(int watcherId) {
                    return GameData.getGlobalWatcherConfigDataMap().get(watcherId);
                }

                @Override
                public int codexId(int tipId) {
                    var entry = GameData.getPushTipsCodexExcelConfigDataMap().get(tipId);
                    return entry == null ? 0 : entry.getCodexId();
                }
            };
}
