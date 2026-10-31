package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.GlobalWatcherConfigData;
import emu.grasscutter.data.excels.codex.PushTipsConfigData;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PushTipsReadFinishRspOuterClass.PushTipsReadFinishRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PushTipsManagerTest {
    private static final Gson GSON = new Gson();

    /** In-memory tables standing in for the excel files. */
    private static final class FakeCatalog implements PushTipsManager.Catalog {
        final Map<Integer, PushTipsConfigData> tips = new HashMap<>();
        final Map<Integer, GlobalWatcherConfigData> watchers = new HashMap<>();
        final Map<Integer, Integer> codexIds = new HashMap<>();

        void addTip(String json, int codexId) {
            var tip = GSON.fromJson(json, PushTipsConfigData.class);
            tips.put(tip.getPushTipsId(), tip);
            codexIds.put(tip.getPushTipsId(), codexId);
        }

        void addWatcher(String json) {
            var watcher = GSON.fromJson(json, GlobalWatcherConfigData.class);
            watchers.put(watcher.getId(), watcher);
        }

        @Override
        public PushTipsConfigData tip(int tipId) {
            return tips.get(tipId);
        }

        @Override
        public Collection<PushTipsConfigData> tips() {
            return tips.values();
        }

        @Override
        public GlobalWatcherConfigData watcher(int watcherId) {
            return watchers.get(watcherId);
        }

        @Override
        public int codexId(int tipId) {
            return codexIds.getOrDefault(tipId, 0);
        }
    }

    private FakeCatalog catalog;
    private PlayerCodex codex;
    private List<BasePacket> sent;
    private int saves;
    private List<Integer> granted;
    private boolean bagFull;
    private PushTipsManager manager;

    @BeforeEach
    void setUp() {
        catalog = new FakeCatalog();
        // 1144: quest tip with a codex entry and a reward. Quests 7306126 or 7306127.
        catalog.addWatcher(
                "{\"id\":10026,\"triggerConfig\":{\"triggerType\":\"TRIGGER_FINISH_QUEST_OR\","
                        + "\"paramList\":[\"7306126,7306127\",\"\"]},\"progress\":1}");
        catalog.addTip(
                "{\"pushTipsId\":1144,\"codexType\":\"CODEX_ADVENTURE\",\"watcherId\":10026,\"rewardId\":300001}",
                60100010);
        // 2048: monster tip.
        catalog.addWatcher(
                "{\"id\":2048,\"triggerConfig\":{\"triggerType\":\"TRIGGER_NEW_MONSTER\","
                        + "\"paramList\":[\"23010201\"]},\"progress\":1}");
        catalog.addTip(
                "{\"pushTipsId\":2048,\"codexType\":\"CODEX_ENEMY\",\"watcherId\":2048,\"rewardId\":300001}",
                60200001);
        // 3001: pop-up only (CODEX_UNRECORDED), finishes with quest 7306127 as well.
        catalog.addWatcher(
                "{\"id\":3001,\"triggerConfig\":{\"triggerType\":\"TRIGGER_FINISH_QUEST_OR\","
                        + "\"paramList\":[\"7306127\"]},\"progress\":1}");
        catalog.addTip(
                "{\"pushTipsId\":3001,\"codexType\":\"CODEX_UNRECORDED\",\"watcherId\":3001}", 60999999);
        // 4001: disused watcher.
        catalog.addWatcher(
                "{\"id\":4001,\"isDisuse\":true,\"triggerConfig\":{\"triggerType\":\"TRIGGER_FINISH_QUEST_OR\","
                        + "\"paramList\":[\"7306126\"]},\"progress\":1}");
        catalog.addTip(
                "{\"pushTipsId\":4001,\"codexType\":\"CODEX_ADVENTURE\",\"watcherId\":4001}", 60100011);
        // 5001: client side trigger, no reward.
        catalog.addWatcher(
                "{\"id\":5001,\"triggerConfig\":{\"triggerType\":\"TRIGGER_GENERAL_GADGET_OR\","
                        + "\"paramList\":[\"x\"]},\"progress\":1}");
        catalog.addTip(
                "{\"pushTipsId\":5001,\"codexType\":\"CODEX_SYSTEM\",\"watcherId\":5001}", 60100012);

        codex = new PlayerCodex();
        sent = new ArrayList<>();
        saves = 0;
        granted = new ArrayList<>();
        bagFull = false;
        manager =
                new PushTipsManager(
                        100000001,
                        () -> codex,
                        catalog,
                        sent::add,
                        () -> saves++,
                        rewardId -> {
                            if (bagFull) return false;
                            granted.add(rewardId);
                            return true;
                        });
    }

    private long count(int opcode) {
        return sent.stream().filter(p -> p.getOpcode() == opcode).count();
    }

    @Test
    void freshAccountHasNoTips() {
        assertTrue(codex.getPushTipsState().isEmpty());
        assertTrue(manager.states().isEmpty());
        assertTrue(manager.unlockedCodexIds().isEmpty());
        assertTrue(sent.isEmpty());
    }

    @Test
    void questFinishUnlocksOnlyMatchingTips() {
        assertEquals(1, manager.onQuestFinished(7306126));

        assertEquals(Map.of(1144, PushTipsManager.STATE_START), manager.states());
        assertEquals(List.of(60100010), manager.unlockedCodexIds());
        // An unrelated quest unlocks nothing.
        assertEquals(0, manager.onQuestFinished(1));
        assertEquals(1, manager.states().size());
    }

    @Test
    void disusedWatcherNeverUnlocks() {
        manager.onQuestFinished(7306126);
        assertFalse(manager.isUnlocked(4001));
        assertEquals(0, manager.onWatcherEvent(4001));
        assertFalse(manager.pendingWatcherIds().contains(4001));
    }

    @Test
    void unlockingTwiceRecordsAndSendsOnce() {
        assertTrue(manager.unlock(1144, "test"));
        int packets = sent.size();
        int savedBefore = saves;

        assertFalse(manager.unlock(1144, "test"));
        assertEquals(0, manager.onQuestFinished(7306126));

        assertEquals(1, manager.states().size());
        assertEquals(packets, sent.size());
        assertEquals(savedBefore, saves);
    }

    @Test
    void unlockSendsChangeCodexUpdateAndDropsWatcher() {
        manager.unlock(1144, "test");

        assertEquals(1, count(PacketOpcodes.PushTipsChangeNotify));
        assertEquals(1, count(PacketOpcodes.CodexDataUpdateNotify));
        assertEquals(1, count(PacketOpcodes.WatcherChangeNotify));
    }

    @Test
    void unrecordedTipIsNotListedInTheCodex() {
        // 7306127 matches 1144 (codex) and 3001 (pop-up only).
        assertEquals(2, manager.onQuestFinished(7306127));

        assertEquals(2, manager.states().size());
        assertEquals(List.of(60100010), manager.unlockedCodexIds());
        // Two tips changed, but only the codex tip announces a codex update.
        assertEquals(2, count(PacketOpcodes.PushTipsChangeNotify));
        assertEquals(1, count(PacketOpcodes.CodexDataUpdateNotify));
    }

    @Test
    void backfillRecordsMetServerTriggersAsReadAndSendsNothing() {
        // 7306127 finished and monster 23010201 seen before tips were tracked.
        int recorded = manager.backfill(quest -> quest == 7306127, monster -> monster == 23010201);

        assertEquals(3, recorded);
        assertEquals(
                Map.of(1144, PushTipsManager.STATE_FINISH,
                        3001, PushTipsManager.STATE_FINISH,
                        2048, PushTipsManager.STATE_FINISH),
                manager.states());
        assertFalse(manager.isUnlocked(4001), "disused watcher stays locked");
        assertFalse(manager.isUnlocked(5001), "client-side triggers are not replayed");
        assertTrue(sent.isEmpty(), "the login AllData notify carries them");
        // Only 4001 (disused) and 5001 (client side) remain; neither backfills.
        assertEquals(0, manager.backfill(quest -> true, monster -> true));
    }

    @Test
    void backfillOfAFreshAccountRecordsNothing() {
        assertEquals(0, manager.backfill(quest -> false, monster -> false));
        assertTrue(manager.states().isEmpty());
    }

    @Test
    void newMonsterUnlocksMonsterTip() {
        assertEquals(0, manager.onNewMonster(1));
        assertEquals(1, manager.onNewMonster(23010201));
        assertEquals(PushTipsManager.STATE_START, manager.states().get(2048));
    }

    @Test
    void clientWatcherEventUnlocks() {
        assertTrue(manager.pendingWatcherIds().contains(5001));
        assertEquals(1, manager.onWatcherEvent(5001));
        assertEquals(PushTipsManager.STATE_START, manager.states().get(5001));
        assertFalse(manager.pendingWatcherIds().contains(5001));
        assertEquals(0, manager.onWatcherEvent(99999));
    }

    @Test
    void typeEventUnlocksWindCrystalTutorial() {
        // 官方 8001 / 提示 1037：靠近风神瞳时客户端按 TRIGGER_WIND_CRYSTAL 上报，不带观察器号。
        catalog.addWatcher(
                "{\"id\":8001,\"triggerConfig\":{\"triggerType\":\"TRIGGER_WIND_CRYSTAL\","
                        + "\"paramList\":[\"\",\"\",\"\",\"\",\"\"]},\"progress\":1}");
        catalog.addTip(
                "{\"pushTipsId\":1037,\"codexType\":\"CODEX_ADVENTURE\",\"watcherId\":8001}", 60100013);
        assertTrue(manager.pendingWatcherIds().contains(8001));

        assertEquals(0, manager.onTypeEvent(WatcherTriggerType.TRIGGER_NEW_MONSTER, List.of(), 1));
        assertEquals(1, manager.onTypeEvent(WatcherTriggerType.TRIGGER_WIND_CRYSTAL, List.of(), 1));

        assertEquals(PushTipsManager.STATE_START, manager.states().get(1037));
        assertFalse(manager.pendingWatcherIds().contains(8001), "观察器撤掉后客户端不再每秒上报");
        assertEquals(1, count(PacketOpcodes.WatcherChangeNotify));
        assertEquals(0, manager.onTypeEvent(WatcherTriggerType.TRIGGER_WIND_CRYSTAL, List.of(), 1));
    }

    @Test
    void readFinishMovesUnreadToFinished() throws Exception {
        manager.unlock(1144, "test");
        sent.clear();

        manager.readFinish(1144);

        assertEquals(PushTipsManager.STATE_FINISH, manager.states().get(1144));
        assertEquals(1, sent.size());
        assertEquals(PacketOpcodes.PushTipsReadFinishRsp, sent.get(0).getOpcode());
        var rsp = PushTipsReadFinishRsp.parseFrom(sent.get(0).getData());
        assertEquals(Retcode.RET_SUCC_VALUE, rsp.getRetcode());
        assertEquals(1144, rsp.getPushTipsId());
    }

    @Test
    void readFinishOfLockedTipFails() throws Exception {
        manager.readFinish(1144);

        assertTrue(manager.states().isEmpty());
        var rsp = PushTipsReadFinishRsp.parseFrom(sent.get(0).getData());
        assertEquals(Retcode.RET_PUSH_TIPS_NOT_FOUND_VALUE, rsp.getRetcode());
    }

    @Test
    void rewardNeedsFinishedTipAndIsPaidOnce() {
        manager.unlock(1144, "test");

        // Unread: not payable.
        manager.claimRewards(List.of(1144));
        assertTrue(granted.isEmpty());

        manager.readFinish(1144);
        manager.claimRewards(List.of(1144, 1144));
        assertEquals(List.of(300001), granted);

        manager.claimRewards(List.of(1144));
        assertEquals(List.of(300001), granted);
        assertEquals(3, count(PacketOpcodes.GetPushTipsRewardRsp));
    }

    @Test
    void rewardIsKeptWhenTheBagIsFull() {
        manager.unlock(1144, "test");
        manager.readFinish(1144);

        bagFull = true;
        manager.claimRewards(List.of(1144));
        assertTrue(granted.isEmpty());

        bagFull = false;
        manager.claimRewards(List.of(1144));
        assertEquals(List.of(300001), granted);
    }

    @Test
    void tipWithoutRewardIsNotPaid() {
        manager.unlock(5001, "test");
        manager.readFinish(5001);
        manager.claimRewards(List.of(5001));
        assertTrue(granted.isEmpty());
    }

    @Test
    void codexViewedIsTracked() {
        assertFalse(manager.isCodexViewed(60100010));
        manager.setCodexViewed(60100010);
        assertTrue(manager.isCodexViewed(60100010));
    }

    @Test
    void paramListParsing() {
        assertTrue(PushTipsManager.paramListContains(new String[] {"1, 2,3", ""}, 2));
        assertFalse(PushTipsManager.paramListContains(new String[] {"1,2,3"}, 4));
        assertFalse(PushTipsManager.paramListContains(new String[] {"12"}, 1));
        assertFalse(PushTipsManager.paramListContains(new String[] {"abc,,"}, 0));
        assertFalse(PushTipsManager.paramListContains(null, 1));
    }

    @Test
    void olderDocumentsWithoutPushTipsFieldsLoadAsEmpty() throws Exception {
        // A stored document without these fields must still give empty collections, even if the
        // constructor defaults were lost.
        var older = new PlayerCodex();
        for (var name : List.of("pushTipsState", "claimedPushTipsReward", "viewedPushTipsCodex")) {
            Field field = PlayerCodex.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(older, null);
        }
        assertNotNull(older.getPushTipsState());
        assertTrue(older.getPushTipsState().isEmpty());
        assertNotNull(older.getClaimedPushTipsReward());
        assertNotNull(older.getViewedPushTipsCodex());
        assertNotNull(new PlayerCodex().getPushTipsState());
    }
}
