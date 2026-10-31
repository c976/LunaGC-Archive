package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.CompoundData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.CompoundQueueDataOuterClass.CompoundQueueData;
import emu.grasscutter.net.proto.GetCompoundDataReqOuterClass.GetCompoundDataReq;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.PlayerCompoundMaterialReqOuterClass.PlayerCompoundMaterialReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import java.util.*;

public class CookingCompoundManager extends BasePlayerManager {
    private static Set<Integer> defaultUnlockedCompounds = new HashSet<>();
    private static Map<Integer, Set<Integer>> compoundGroups = new HashMap<>();
    // TODO:bind it to player
    private static Set<Integer> unlocked = new HashSet<>();

    /**
     * Last pushed finished-count fingerprint; used to notify client when a batch
     * completes.
     */
    private transient int lastNotifiedOutputFingerprint = Integer.MIN_VALUE;

    public CookingCompoundManager(Player player) {
        super(player);
    }

    public static synchronized void initialize() {
        defaultUnlockedCompounds = new HashSet<>();
        compoundGroups = new HashMap<>();
        GameData.getCompoundDataMap()
                .forEach(
                        (id, compound) -> {
                            if (compound.isDefaultUnlocked()) {
                                defaultUnlockedCompounds.add(id);
                            }
                            compoundGroups.computeIfAbsent(compound.getGroupId(), gid -> new HashSet<>()).add(id);
                        });
        // TODO:Because we haven't implemented fishing feature,unlock all compounds
        // related to
        // fish.Besides,it should be bound to player rather than manager.
        unlocked = new HashSet<>(defaultUnlockedCompounds);
        if (compoundGroups.containsKey(3)) { // Avoid NPE from Resources error
            unlocked.addAll(compoundGroups.get(3));
        }
    }

    /**
     * Rebuild unlock tables if GameServer ran initialize() before Excel data was
     * loaded.
     */
    private static synchronized void ensureInitialized() {
        if (!unlocked.isEmpty() || GameData.getCompoundDataMap().isEmpty()) {
            return;
        }
        initialize();
    }

    private synchronized List<CompoundQueueData> getCompoundQueueData() {
        List<CompoundQueueData> compoundQueueData = new ArrayList<>(player.getActiveCookCompounds().size());
        int currentTime = Utils.getCurrentSeconds();
        for (var item : player.getActiveCookCompounds().values()) {
            var data = CompoundQueueData.newBuilder()
                    .setCompoundId(item.getCompoundId())
                    // Keep finished and remaining counts separate; output time is the next completion.
                    .setWaitCount(item.getOutputCount(currentTime))
                    .setOutputCount(item.getWaitCount(currentTime))
                    .setOutputTime(item.getOutputTime(currentTime))
                    .build();
            compoundQueueData.add(data);
        }
        return compoundQueueData;
    }

    private int outputFingerprint(int currentTime) {
        int fp = 0;
        for (var item : player.getActiveCookCompounds().values()) {
            fp = fp * 31 + item.getCompoundId();
            fp = fp * 31 + item.getOutputCount(currentTime);
            fp = fp * 31 + item.getTotalCount();
        }
        fp = fp * 31 + player.getActiveCookCompounds().size();
        return fp;
    }

    private void markQueueNotified(int currentTime) {
        this.lastNotifiedOutputFingerprint = outputFingerprint(currentTime);
    }

    private void notifyQueue(boolean force) {
        ensureInitialized();
        int now = Utils.getCurrentSeconds();
        int fp = outputFingerprint(now);
        if (!force && fp == this.lastNotifiedOutputFingerprint) {
            return;
        }
        this.lastNotifiedOutputFingerprint = fp;
        player.sendPacket(new PacketCompoundDataNotify(unlocked, getCompoundQueueData()));
    }

    /**
     * Mirror forging: push queue updates when a cooking batch becomes ready while
     * the UI is open.
     */
    public synchronized void onTick() {
        if (player.getActiveCookCompounds().isEmpty()) {
            if (this.lastNotifiedOutputFingerprint != 0) {
                this.lastNotifiedOutputFingerprint = 0;
            }
            return;
        }
        notifyQueue(false);
    }

    public synchronized void handleGetCompoundDataReq(GetCompoundDataReq req) {
        ensureInitialized();
        player.sendPacket(new PacketGetCompoundDataRsp(unlocked, getCompoundQueueData()));
        markQueueNotified(Utils.getCurrentSeconds());
    }

    public synchronized void handlePlayerCompoundMaterialReq(PlayerCompoundMaterialReq req) {
        ensureInitialized();
        int id = req.getCompoundId(), count = req.getCount();
        CompoundData compound = GameData.getCompoundDataMap().get(id);
        var activeCompounds = player.getActiveCookCompounds();

        // check whether the compound is available
        // TODO:add other compounds,see my pr for detail
        if (!unlocked.contains(id)) {
            player.sendPacket(new PacketPlayerCompoundMaterialRsp(Retcode.RET_FAIL_VALUE));
            return;
        }
        // check whether the queue is full
        if (activeCompounds.containsKey(id)
                && activeCompounds.get(id).getTotalCount() + count > compound.getQueueSize()) {
            player.sendPacket(new PacketPlayerCompoundMaterialRsp(Retcode.RET_COMPOUND_QUEUE_FULL_VALUE));
            return;
        }
        // try to consume raw materials
        if (!player.getInventory().payItems(compound.getInputVec(), count)) {
            player.sendPacket(
                    new PacketPlayerCompoundMaterialRsp(Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE));
            return;
        }
        ActiveCookCompoundData c;
        int currentTime = Utils.getCurrentSeconds();
        if (activeCompounds.containsKey(id)) {
            c = activeCompounds.get(id);
            c.addCompound(count, currentTime);
        } else {
            c = new ActiveCookCompoundData(id, compound.getCostTime(), count, currentTime);
            activeCompounds.put(id, c);
        }
        var data = CompoundQueueData.newBuilder()
                .setCompoundId(id)
                .setWaitCount(c.getOutputCount(currentTime))
                .setOutputCount(c.getWaitCount(currentTime))
                .setOutputTime(c.getOutputTime(currentTime))
                .build();
        player.sendPacket(new PacketPlayerCompoundMaterialRsp(data));
        markQueueNotified(currentTime);
    }

    public synchronized void handleTakeCompoundOutputReq(TakeCompoundOutputReq req) {
        ensureInitialized();
        var activeCompounds = player.getActiveCookCompounds();
        int now = Utils.getCurrentSeconds();
        boolean success = false;
        Map<Integer, GameItem> allRewards = new HashMap<>();

        Collection<Integer> targets;
        if (req.getCompoundId() != 0) {
            targets = List.of(req.getCompoundId());
        } else if (req.getCompoundGroupId() != 0) {
            Set<Integer> group = compoundGroups.get(req.getCompoundGroupId());
            if (group == null) {
                // Fall back to all active queues instead of hard-failing on unknown group.
                targets = new ArrayList<>(activeCompounds.keySet());
            } else {
                targets = group;
            }
        } else {
            // claim-all / empty ids: take every active queue
            targets = new ArrayList<>(activeCompounds.keySet());
        }
        Grasscutter.getLogger()
                .info(
                        "[CompoundTake] uid={} compoundId={} groupId={} claimAll={} targets={} active={}",
                        player.getUid(),
                        req.getCompoundId(),
                        req.getCompoundGroupId(),
                        req.getIsClaimAll(),
                        targets,
                        activeCompounds.keySet());

        for (int id : targets) {
            if (!activeCompounds.containsKey(id))
                continue;
            int quantity = activeCompounds.get(id).takeCompound(now);
            if (activeCompounds.get(id).getTotalCount() == 0)
                activeCompounds.remove(id);
            if (quantity == 0)
                continue;
            var compound = GameData.getCompoundDataMap().get(id);
            if (compound == null || compound.getOutputVec() == null)
                continue;
            for (var i : compound.getOutputVec()) {
                if (i.getId() == 0)
                    continue;
                if (allRewards.containsKey(i.getId())) {
                    GameItem item = allRewards.get(i.getId());
                    item.setCount(item.getCount() + i.getCount() * quantity);
                } else {
                    allRewards.put(i.getId(), new GameItem(i.getId(), i.getCount() * quantity));
                }
            }
            success = true;
        }

        if (success) {
            player.getInventory().addItems(allRewards.values(), ActionReason.Compound);
            player.sendPacket(
                    new PackageTakeCompoundOutputRsp(
                            allRewards.values().stream()
                                    .map(
                                            i -> ItemParam.newBuilder()
                                                    .setItemId(i.getItemId())
                                                    .setCount(i.getCount())
                                                    .build())
                                    .toList(),
                            Retcode.RET_SUCC_VALUE));
        } else {
            player.sendPacket(
                    new PackageTakeCompoundOutputRsp(
                            Collections.emptyList(), Retcode.RET_COMPOUND_NOT_FINISH_VALUE));
        }
        // Client keeps a local queue copy; always push the latest after claim.
        notifyQueue(true);
    }

    public void onPlayerLogin() {
        ensureInitialized();
        notifyQueue(true);
    }
}
