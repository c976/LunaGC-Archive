package emu.grasscutter.game.systems;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.excels.reliquary.ReliquaryAffixData;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.packet.send.PacketReliquaryDustCompanionRsp;
import emu.grasscutter.server.packet.send.PacketReliquaryDustConfirmRsp;
import emu.grasscutter.server.packet.send.PacketReliquaryDustRsp;
import emu.grasscutter.server.packet.send.PacketReliquaryDustSelectRsp;
import emu.grasscutter.server.packet.send.PacketReliquaryOfferCompanionNotify;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;
import emu.grasscutter.utils.ProtoWire;
import emu.grasscutter.utils.objects.WeightedList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Artifact reshaping / ReliquaryDust (item 105006, Sanctifying Essence).
 *
 * <p>Protocol (7.0 sniffer obfuscated names):
 *
 * <ul>
 *   <li>C2S 7273 JGGGGCPPBPM - real reshape select (guid + chosen affix ids)
 *   <li>S2C 7281 / 25355 - select rsp + candidate notify (FKBHBPBNOJJ)
 *   <li>C2S 2210 / 21945 - adopt/reject candidate
 *   <li>C2S 21870 DODGOEFKAMF - NOT dust ({@code avatar_id_list}); must not reshape
 *   <li>C2S 26587 COEPHMIKICA - NOT dust ({@code avatar_id}); ack-only companion
 * </ul>
 *
 * <p>Misbinding 21870 as reshape caused ghost pending via {@code findLikelyTarget} and sticky
 * the "previous artifact reshape has not been chosen yet" dialog.
 *
 * Const: CONST_VALUE_RELIQUARY_DUST_PARAM_1/2
 */
public final class ReliquaryDustSystem {
    public static final int DUST_ITEM_ID = 105006;
    // These were 7.0 CmdIds, none of which 7.1 names. In 7.1 21870 is DungeonPlayerDieReq (its
    // handler and the dust one both claimed it), 21898 GetAllActivatedBargainDataRsp and 2210
    // _BeyondHallRoomCardChangeNotify, so the requests sit on placeholders that never match until
    // the 7.1 ids are known. The 7.0 numbers are kept in the comments.
    public static final int OPCODE_DUST_REQ = -1101; // 7.0: 21870
    public static final int OPCODE_DUST_RSP = 0; // 7.0: 21898, GetAllActivatedBargainDataRsp in 7.1
    public static final int OPCODE_DUST_COMPANION_REQ = -1102; // 7.0: 26587
    public static final int OPCODE_DUST_COMPANION_RSP = 26599;
    public static final int OPCODE_DUST_SELECT_REQ = -1103; // 7.0: 7273
    public static final int OPCODE_DUST_CONFIRM_REQ_A = -1104; // 7.0: 2210
    public static final int OPCODE_DUST_CONFIRM_REQ_B = -1105; // 7.0: 21945

    /**
     * SelectRsp opcode probe (local debugging only). Keep false on production - wrong opcodes and
     * dust refunds must not ship to the live jar.
     */
    private static final boolean ENABLE_RSP_PROBE = false;

    /**
     * One-at-a-time SelectRsp opcode probe list. 7281 with a valid header still does not open the
     * compare page; rotate nearby retcode-only S2C candidates when ENABLE_RSP_PROBE is true.
     */
    private static final int[][] SELECT_RSP_PROBES = {
        // opcode, retcodeField
        {7307, 13},
        {7237, 8},
        {7192, 1},
        {7162, 12},
        {7155, 11},
        {7128, 2},
        {7111, 7},
        {7101, 10},
        {7080, 2},
        {7056, 5},
        {7061, 4},
        {7478, 2},
        {7605, 15},
        {7653, 8},
        {6856, 7},
        {6851, 7},
        {6788, 10},
        {6828, 8},
        {6979, 13},
        {7616, 7},
        {7757, 1},
        {7795, 11},
        {7801, 8},
        {7813, 13},
        {8036, 7}, // +guid field 4
        {6541, 15}, // +guid field 14
        {4711, 7}, // confirm-shaped; +guid field 8
        {7281, 9}, // previous guess
    };

    private static final java.util.concurrent.atomic.AtomicInteger PROBE_IDX =
            new java.util.concurrent.atomic.AtomicInteger(loadProbeIndex());
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Long> RECV_LOG_UNTIL =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static int loadProbeIndex() {
        try {
            java.nio.file.Path p = java.nio.file.Path.of("_dust_rsp_probe_idx.txt");
            if (java.nio.file.Files.isRegularFile(p)) {
                String s = java.nio.file.Files.readString(p).trim();
                if (!s.isEmpty()) {
                    return Math.max(0, Integer.parseInt(s));
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static void saveProbeIndex(int idx) {
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("_dust_rsp_probe_idx.txt"), Integer.toString(idx));
        } catch (Exception ignored) {
        }
    }

    /** True for a few seconds after a reshape - used to log every client opcode. */
    public static boolean shouldLogAllRecv(Player player) {
        if (player == null) {
            return false;
        }
        Long until = RECV_LOG_UNTIL.get(player.getUid());
        return until != null && System.currentTimeMillis() < until;
    }

    public static void noteRecvOpcode(Player player, int opcode) {
        if (!shouldLogAllRecv(player)) {
            return;
        }
        if (opcode == PacketOpcodes.PingReq || opcode == PacketOpcodes.UnionCmdNotify) {
            return;
        }
        Grasscutter.getLogger()
                .debug("ReliquaryDust post-reshape RECV uid={} opcode={}", player.getUid(), opcode);
    }

    /** Min guaranteed upgrades for chosen lines by effect tier (1=normal,2=high,3=prophecy). */
    private static final int[] GUARANTEE_BY_TIER = {0, 2, 3, 4};

    private static final Map<Integer, PlayerDustState> STATE = new ConcurrentHashMap<>();

    private ReliquaryDustSystem() {}

    public static final class PlayerDustState {
        public int progress; // transmuter progress, unbounded, cycles mod 6
        public int highTierTriggered; // for prophecy every 3rd high-tier
        public long focusGuid;
        public List<Integer> chosenKeys = new ArrayList<>(); // slot idx or fightProp id
        public PendingCandidate pending;
    }

    public static final class PendingCandidate {
        public long guid;
        public List<Integer> oldAppend = new ArrayList<>();
        public List<Integer> newAppend = new ArrayList<>();
        /** Client-chosen affix ids, for the "selected appended attributes" UI. */
        public List<Integer> chosenAffixIds = new ArrayList<>();
        public List<Integer> chosenFightPropIds = new ArrayList<>();
        public int cost;
        public boolean dustConsumed;
        /** True once new result was written to the item (should stay false until confirm). */
        public boolean applied;
    }

    public static PlayerDustState state(Player player) {
        return STATE.computeIfAbsent(player.getUid(), u -> new PlayerDustState());
    }

    public static void sendDataNotify(Player player) {
        PlayerDustState st = state(player);
        if (st.pending != null) {
            player.sendPacket(new PacketReliquaryOfferCompanionNotify(st));
            return;
        }
        // MergeFrom-safe clear: guid=0 sentinel (empty body does NOT clear sticky candidate).
        player.sendPacket(new PacketReliquaryOfferCompanionNotify(st, true));
    }

    /** Wipe client unfinished-reshape flag via guid=0 overwrite (MergeFrom-safe). */
    public static void sendClearCandidateNotify(Player player) {
        player.sendPacket(new PacketReliquaryOfferCompanionNotify(state(player), true));
    }

    /**
     * After enhance / relic inventory actions: abandon leftover server pending (keep old props) and
     * force-clear sticky client dialog. Re-sending a real candidate here was making the popup worse.
     */
    public static void onReliquaryInventoryTouch(Player player) {
        if (player == null) {
            return;
        }
        PlayerDustState st = state(player);
        if (st.pending != null) {
            abandonPendingKeepOld(player, st);
            Grasscutter.getLogger()
                    .debug(
                            "ReliquaryDust abandon-pending uid={} (inventory touch)",
                            player.getUid());
        }
        sendClearCandidateNotify(player);
        Grasscutter.getLogger()
                .debug("ReliquaryDust force-clear uid={}", player.getUid());
    }

    /** Restore old append if needed and drop server pending without adopting new. */
    private static void abandonPendingKeepOld(Player player, PlayerDustState st) {
        PendingCandidate pending = st.pending;
        if (pending == null) {
            return;
        }
        GameItem relic = player.getInventory().getItemByGuid(pending.guid);
        if (relic != null && relic.getAppendPropIdList() != null && pending.oldAppend != null) {
            if (!relic.getAppendPropIdList().equals(pending.oldAppend)) {
                relic.getAppendPropIdList().clear();
                relic.getAppendPropIdList().addAll(pending.oldAppend);
                relic.save();
                player.sendPacket(new PacketStoreItemChangeNotify(relic));
            }
        }
        st.pending = null;
        st.focusGuid = 0L;
    }

    /**
     * Push candidate notify (25355), then SelectRsp with echoed request PacketHead.
     *
     * <p>Production uses 7281 / retcode field 9. Optional local probe rotates nearby retcode-only
     * S2C opcodes when {@link #ENABLE_RSP_PROBE} is true.
     */
    private static void sendReshapeSuccessPackets(
            Player player, int selectRet, byte[] requestHeader, boolean advanceProbe) {
        PlayerDustState st = state(player);
        sendDataNotify(player);

        final byte[] hdr =
                requestHeader != null && requestHeader.length > 0
                        ? java.util.Arrays.copyOf(requestHeader, requestHeader.length)
                        : null;

        if (selectRet != 0) {
            player.sendPacket(new PacketReliquaryDustSelectRsp(selectRet, hdr));
            Grasscutter.getLogger()
                    .debug(
                            "ReliquaryDust send-fail uid={} ret={} hdrLen={}",
                            player.getUid(),
                            selectRet,
                            hdr != null ? hdr.length : 0);
            return;
        }

        syncDustItem(player);
        sendDataNotify(player);

        int opcode = PacketReliquaryDustSelectRsp.OPCODE;
        int retField = 9;
        long guid = st.pending != null ? st.pending.guid : 0L;

        if (ENABLE_RSP_PROBE) {
            int idx;
            if (advanceProbe) {
                idx = PROBE_IDX.getAndIncrement();
                saveProbeIndex(idx + 1);
                if (st.pending != null && st.pending.cost > 0 && st.pending.dustConsumed) {
                    player.getInventory().addItem(DUST_ITEM_ID, st.pending.cost);
                    st.pending.dustConsumed = false;
                    syncDustItem(player);
                    Grasscutter.getLogger()
                            .debug(
                                    "ReliquaryDust probe-refund uid={} amount={}",
                                    player.getUid(),
                                    st.pending.cost);
                }
            } else {
                idx = Math.max(0, PROBE_IDX.get() - 1);
            }
            int[] probe = SELECT_RSP_PROBES[idx % SELECT_RSP_PROBES.length];
            opcode = probe[0];
            retField = probe[1];
            RECV_LOG_UNTIL.put(player.getUid(), System.currentTimeMillis() + 8_000L);
            Grasscutter.getLogger()
                    .debug(
                            "ReliquaryDust RspProbe uid={} idx={}/{} opcode={} retField={} guid={} hdrLen={} advance={}",
                            player.getUid(),
                            idx % SELECT_RSP_PROBES.length,
                            SELECT_RSP_PROBES.length,
                            opcode,
                            retField,
                            guid,
                            hdr != null ? hdr.length : 0,
                            advanceProbe);
        }

        player.sendPacket(new PacketReliquaryDustSelectRsp(opcode, retField, 0, hdr, guid));
        sendDataNotify(player);
        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust send-success uid={} opcode={} pending={} progress={} hdrLen={}",
                        player.getUid(),
                        opcode,
                        guid,
                        st.progress,
                        hdr != null ? hdr.length : 0);
    }

    /** C2S 7273 - primary ReliquaryDustReq (guid + chosen append affix ids). */
    public static void handleSelect(Player player, byte[] payload) {
        handleSelect(player, payload, 0, null);
    }

    public static void handleSelect(Player player, byte[] payload, int clientSequence) {
        handleSelect(player, payload, clientSequence, null);
    }

    public static void handleSelect(
            Player player, byte[] payload, int clientSequence, byte[] requestHeader) {
        PlayerDustState st = state(player);
        Map<Integer, List<Object>> fields = ProtoWire.parse(payload);
        long guid = firstUint64(fields, 1);
        List<Integer> chosen = ProtoWire.asUint32List(fields.get(9));
        if (guid != 0L) {
            st.focusGuid = guid;
        }
        if (!chosen.isEmpty()) {
            st.chosenKeys = new ArrayList<>(chosen);
        }

        // Already have a candidate: re-push so client can open comparison (exit-dialog path).
        if (st.pending != null && (guid == 0L || st.pending.guid == guid)) {
            ensurePendingShowsDiff(player, st.pending);
            Grasscutter.getLogger()
                    .debug(
                            "ReliquaryDust select exist-candidate uid={} guid={} pending={}",
                            player.getUid(),
                            st.focusGuid,
                            st.pending.guid);
            // Already have a candidate: re-push so client can open comparison (exit-dialog path).
            // Keep retcode=0 - 14320 can toast as an error and block the working exit-to-compare jump.
            sendReshapeSuccessPackets(player, 0, requestHeader, false);
            return;
        }

        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust select/reshape uid={} guid={} chosen={} seq={} hdrLen={} hex={}",
                        player.getUid(),
                        st.focusGuid,
                        st.chosenKeys,
                        clientSequence,
                        requestHeader != null ? requestHeader.length : 0,
                        toHex(payload));

        int ret = doReshape(player, st.focusGuid, st.chosenKeys);
        sendReshapeSuccessPackets(player, ret, requestHeader, true);
    }

    /**
     * C2S 21870 DODGOEFKAMF - {@code repeated uint32 avatar_id_list = 3}. Not ReliquaryDust.
     *
     * <p>Previously this path called {@code doReshape} with guid=0, reaching {@code findLikelyTarget} and
     * creating a ghost candidate (25355), which sticky-loops the unfinished-selection dialog. Ack only.
     */
    public static void handleDustReq(Player player, byte[] payload) {
        Map<Integer, List<Object>> fields = ProtoWire.parse(payload);
        List<Integer> avatarIds = ProtoWire.asUint32List(fields.get(3));
        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust ignore-non-dust-21870 uid={} avatarIds={} hex={}",
                        player.getUid(),
                        avatarIds,
                        toHex(payload));
        // Still ack so the real avatar UI flow does not stall on missing rsp.
        player.sendPacket(new PacketReliquaryDustRsp(0));
    }

    /** C2S 26587 - companion ping on confirm; always ack. */
    public static void handleCompanionReq(Player player, byte[] payload) {
        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust companion uid={} hex={}",
                        player.getUid(),
                        toHex(payload));
        player.sendPacket(new emu.grasscutter.server.packet.send.PacketReliquaryDustCompanionRsp(0));
    }

    /** C2S 2210 / 21945 - adopt (true) or keep original (false). */
    public static void handleConfirm(Player player, byte[] payload, int opcode) {
        PlayerDustState st = state(player);
        Map<Integer, List<Object>> fields = ProtoWire.parse(payload);
        long guid = firstUint64(fields, 2);
        if (guid == 0L) {
            guid = firstUint64(fields, 1);
        }
        if (guid == 0L && st.pending != null) {
            guid = st.pending.guid;
        }
        boolean adopt =
                firstBool(fields, 15) || firstBool(fields, 6) || firstBool(fields, 13);
        // Some builds send enum/uint: 1 = adopt, 0 = keep
        int choice = firstUint32(fields, 15);
        if (choice == 0) {
            choice = firstUint32(fields, 6);
        }
        if (choice == 0) {
            choice = firstUint32(fields, 1);
        }
        if (choice == 1) {
            adopt = true;
        } else if (choice == 2) {
            // occasionally 2 = adopt
            adopt = true;
        }

        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust confirm uid={} op={} guid={} adopt={} choice={} fields={} hex={}",
                        player.getUid(),
                        opcode,
                        guid,
                        adopt,
                        choice,
                        summarize(fields),
                        toHex(payload));

        int ret = applyOrDiscard(player, guid, adopt);
        player.sendPacket(new PacketReliquaryDustConfirmRsp(guid, ret));
        // Always wipe server pending after confirm attempt.
        st.pending = null;
        st.focusGuid = 0L;
        // Empty 25355 only here, never on login - clears the client unfinished-reshape state.
        sendClearCandidateNotify(player);
        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust confirm-done uid={} ret={} pendingCleared=true",
                        player.getUid(),
                        ret);
    }

    /** Keep inventory on OLD while candidate is pending so left/right panels can differ. */
    private static void ensurePendingShowsDiff(Player player, PendingCandidate pending) {
        if (pending == null) {
            return;
        }
        GameItem relic = player.getInventory().getItemByGuid(pending.guid);
        if (relic == null || relic.getAppendPropIdList() == null) {
            return;
        }
        if (relic.getAppendPropIdList().equals(pending.oldAppend)) {
            pending.applied = false;
            return;
        }
        relic.getAppendPropIdList().clear();
        relic.getAppendPropIdList().addAll(pending.oldAppend);
        relic.save();
        player.sendPacket(new PacketStoreItemChangeNotify(relic));
        pending.applied = false;
        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust restored old append for compare uid={} guid={}",
                        player.getUid(),
                        pending.guid);
    }

    private static int doReshape(Player player, long guid, List<Integer> chosenKeys) {
        // Never invent a target: guid=0 + findLikelyTarget created ghost pending / sticky popup.
        if (guid == 0L) {
            Grasscutter.getLogger().warn("ReliquaryDust: no target guid uid={}", player.getUid());
            return 1;
        }

        GameItem relic = player.getInventory().getItemByGuid(guid);
        if (relic == null || relic.getItemType() != ItemType.ITEM_RELIQUARY) {
            return 1;
        }
        if (relic.getItemData() == null || relic.getItemData().getRankLevel() < 5) {
            return 14322; // RET_RELIQUARY_DUST_RANK_LEVEL_INVALID
        }
        if (relic.getLevel() < relic.getItemData().getMaxLevel()) {
            return 14321; // RET_RELIQUARY_DUST_LEVEL_INVALID
        }
        List<Integer> append = relic.getAppendPropIdList();
        if (append == null || append.size() < 4) {
            return 14324; // RET_RELIQUARY_DUST_INIT_APPEND_PROP_NUM_INVALID
        }

        PlayerDustState st = state(player);
        if (st.pending != null && st.pending.guid == guid) {
            return 14320; // RET_RELIQUARY_DUST_EXIST_CANDIDATE_APPEND_PROP
        }

        // Auto-choose from the definite/purchased set when the client sent none.
        List<Integer> chosen = new ArrayList<>(chosenKeys == null ? List.of() : chosenKeys);
        if (chosen.size() < 2
                && relic.getDefiniteAppendPropIdList() != null
                && relic.getDefiniteAppendPropIdList().size() >= 2) {
            chosen = new ArrayList<>(relic.getDefiniteAppendPropIdList().subList(0, 2));
        }
        if (chosen.size() < 2
                && relic.getPurchasedAppendPropIdList() != null
                && relic.getPurchasedAppendPropIdList().size() >= 2) {
            chosen = new ArrayList<>(relic.getPurchasedAppendPropIdList().subList(0, 2));
        }
        if (chosen.size() < 2) {
            // Default: first two unique fight props on the relic.
            chosen = defaultChosenSlots(append);
        }
        if (chosen.size() < 2) {
            return 14323; // RET_RELIQUARY_DUST_CHOOSE_APPEND_PROP_INVALID
        }

        int cost = dustCost(relic);
        int have = player.getInventory().getItemCountById(DUST_ITEM_ID);
        if (have < cost) {
            Grasscutter.getLogger()
                    .warn("ReliquaryDust: not enough dust uid={} have={} need={}", player.getUid(), have, cost);
            return -1;
        }

        int tier = nextEffectTier(st, cost); // 1/2/3
        int guarantee = GUARANTEE_BY_TIER[tier];
        List<Integer> newAppend = reshuffle(append, chosen, guarantee);
        if (newAppend == null || newAppend.size() < 4) {
            return 1;
        }

        // Consume dust now (wiki: not refunded either way). Force material remove + notify
        // so reshape UI refreshes count immediately (payItem path can look deferred until exit).
        int before = player.getInventory().getItemCountById(DUST_ITEM_ID);
        if (!player.getInventory().removeItemById(DUST_ITEM_ID, cost)) {
            Grasscutter.getLogger()
                    .warn(
                            "ReliquaryDust: remove dust failed uid={} have={} need={}",
                            player.getUid(),
                            before,
                            cost);
            return -1;
        }
        syncDustItem(player);
        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust consumed uid={} cost={} before={} after={}",
                        player.getUid(),
                        cost,
                        before,
                        player.getInventory().getItemCountById(DUST_ITEM_ID));

        st.progress += cost;
        if (tier >= 2) {
            st.highTierTriggered++;
        }

        PendingCandidate pending = new PendingCandidate();
        pending.guid = guid;
        pending.oldAppend = new ArrayList<>(append);
        pending.newAppend = newAppend;
        pending.chosenAffixIds = new ArrayList<>(chosen);
        pending.chosenFightPropIds = resolveChosenFightProps(append, chosen);
        pending.cost = cost;
        pending.dustConsumed = true;
        pending.applied = false;
        st.pending = pending;
        st.focusGuid = guid;
        st.chosenKeys = chosen;

        // Keep inventory on OLD until player confirms. Writing NEW here made both compare
        // panels identical (left+right both read the item).
        ensurePendingShowsDiff(player, pending);

        Grasscutter.getLogger()
                .debug(
                        "ReliquaryDust reshape uid={} guid={} tier={} guarantee={} cost={} progress={} old={} new={}",
                        player.getUid(),
                        guid,
                        tier,
                        guarantee,
                        cost,
                        st.progress,
                        pending.oldAppend,
                        pending.newAppend);
        return 0;
    }

    /** Push the current Sanctifying Essence stack, or make sure the client saw the delete, so the UI count
     * updates in place. */
    private static void syncDustItem(Player player) {
        GameItem dust = player.getInventory().getItemById(DUST_ITEM_ID);
        if (dust != null) {
            player.sendPacket(new PacketStoreItemChangeNotify(dust));
        }
    }

    private static int applyOrDiscard(Player player, long guid, boolean adopt) {
        PlayerDustState st = state(player);
        PendingCandidate pending = st.pending;
        if (pending == null) {
            return 0;
        }
        // Prefer the sole server pending when client guid mismatches (truncated / wrong field).
        if (guid != 0L && pending.guid != guid) {
            Grasscutter.getLogger()
                    .warn(
                            "ReliquaryDust confirm guid mismatch uid={} req={} pending={} - applying pending",
                            player.getUid(),
                            guid,
                            pending.guid);
        }
        GameItem relic = player.getInventory().getItemByGuid(pending.guid);
        if (relic == null) {
            st.pending = null;
            return 1;
        }
        if (adopt) {
            relic.getAppendPropIdList().clear();
            relic.getAppendPropIdList().addAll(pending.newAppend);
            pending.applied = true;
        } else {
            relic.getAppendPropIdList().clear();
            relic.getAppendPropIdList().addAll(pending.oldAppend);
            pending.applied = false;
        }
        relic.save();
        player.sendPacket(new PacketStoreItemChangeNotify(relic));
        st.pending = null;
        st.focusGuid = 0L;
        return 0;
    }

    private static int firstUint32(Map<Integer, List<Object>> fields, int field) {
        List<Object> vals = fields.get(field);
        if (vals == null || vals.isEmpty()) {
            return 0;
        }
        Object v = vals.get(0);
        if (v instanceof Long l) {
            return l.intValue();
        }
        if (v instanceof Integer i) {
            return i;
        }
        return 0;
    }

    /** Effect tier for this reshape based on progress thresholds 6/12/18. */
    private static int nextEffectTier(PlayerDustState st, int cost) {
        int before = st.progress;
        int after = st.progress + Math.max(1, cost);
        boolean crossed = false;
        int crossedAt = 0;
        for (int p = before + 1; p <= after; p++) {
            if (p % 6 == 0) {
                crossed = true;
                crossedAt = p;
            }
        }
        if (!crossed) {
            return 1;
        }
        int highIndex = crossedAt / 6;
        if (highIndex % 3 == 0) {
            return 3; // oracle tier
        }
        return 2; // high tier
    }

    private static int dustCost(GameItem relic) {
        EquipType type = relic.getItemData().getEquipType();
        // PARAM_1[3]: 1,1;2,1;3,2;4,2;5,2
        return switch (type) {
            case EQUIP_BRACER, EQUIP_NECKLACE -> 1;
            case EQUIP_SHOES, EQUIP_RING, EQUIP_DRESS -> 2;
            default -> 1;
        };
    }

    private static List<Integer> defaultChosenSlots(List<Integer> append) {
        List<Integer> out = new ArrayList<>();
        Map<FightProperty, Integer> firstSlot = new LinkedHashMap<>();
        for (int i = 0; i < append.size(); i++) {
            ReliquaryAffixData aff = GameData.getReliquaryAffixDataMap().get(append.get(i));
            if (aff == null) {
                continue;
            }
            firstSlot.putIfAbsent(aff.getFightProp(), i);
            if (firstSlot.size() >= 2 && out.size() < 2) {
                // fill below
            }
        }
        for (Integer slot : firstSlot.values()) {
            out.add(slot);
            if (out.size() >= 2) {
                break;
            }
        }
        return out;
    }

    private static List<Integer> resolveChosenFightProps(List<Integer> append, List<Integer> chosen) {
        List<Integer> props = new ArrayList<>();
        List<FightProperty> uniqueOrder = uniqueFightProps(append);
        for (int key : chosen) {
            FightProperty fp = null;
            if (key >= 0 && key < uniqueOrder.size()) {
                fp = uniqueOrder.get(key);
            } else {
                ReliquaryAffixData aff = GameData.getReliquaryAffixDataMap().get(key);
                if (aff != null) {
                    fp = aff.getFightProp();
                } else {
                    fp = FightProperty.getPropById(key);
                }
            }
            if (fp != null && fp != FightProperty.FIGHT_PROP_NONE) {
                props.add(fp.getId());
            }
        }
        return props;
    }

    private static List<FightProperty> uniqueFightProps(List<Integer> append) {
        List<FightProperty> order = new ArrayList<>();
        for (int id : append) {
            ReliquaryAffixData aff = GameData.getReliquaryAffixDataMap().get(id);
            if (aff == null) {
                continue;
            }
            if (!order.contains(aff.getFightProp())) {
                order.add(aff.getFightProp());
            }
        }
        return order;
    }

    /**
     * Rebuild append list: keep 4 base lines (one each type), redistribute upgrade rolls with
     * guarantee on chosen types.
     */
    private static List<Integer> reshuffle(
            List<Integer> oldAppend, List<Integer> chosenKeys, int guaranteeTotal) {
        List<FightProperty> types = uniqueFightProps(oldAppend);
        if (types.size() < 4) {
            return null;
        }
        // Keep only first 4 unique types in appearance order.
        types = new ArrayList<>(types.subList(0, 4));

        int totalRolls = oldAppend.size(); // base4 + upgrades
        int upgrades = Math.max(0, totalRolls - 4);

        List<FightProperty> chosenTypes = new ArrayList<>();
        for (int key : chosenKeys) {
            FightProperty fp = null;
            if (key >= 0 && key < types.size()) {
                fp = types.get(key);
            } else {
                ReliquaryAffixData aff = GameData.getReliquaryAffixDataMap().get(key);
                if (aff != null) {
                    fp = aff.getFightProp();
                } else {
                    fp = FightProperty.getPropById(key);
                }
            }
            if (fp != null && types.contains(fp) && !chosenTypes.contains(fp)) {
                chosenTypes.add(fp);
            }
        }
        while (chosenTypes.size() < 2 && chosenTypes.size() < types.size()) {
            for (FightProperty fp : types) {
                if (!chosenTypes.contains(fp)) {
                    chosenTypes.add(fp);
                    break;
                }
            }
        }

        int[] counts = new int[4]; // upgrades per type index in `types`
        int remaining = upgrades;
        int g = Math.min(guaranteeTotal, remaining);
        // Split guarantee across chosen types
        if (!chosenTypes.isEmpty() && g > 0) {
            int per = g / chosenTypes.size();
            int extra = g % chosenTypes.size();
            for (int i = 0; i < chosenTypes.size(); i++) {
                int idx = types.indexOf(chosenTypes.get(i));
                int add = per + (i < extra ? 1 : 0);
                counts[idx] += add;
                remaining -= add;
            }
        }
        // Randomly distribute the rest
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        while (remaining > 0) {
            counts[rng.nextInt(4)]++;
            remaining--;
        }

        // Depot from first affix
        ReliquaryAffixData sample = GameData.getReliquaryAffixDataMap().get(oldAppend.get(0));
        int depotId = sample != null ? sample.getDepotId() : 501;
        List<ReliquaryAffixData> pool = GameDepot.getRelicAffixList(depotId);
        if (pool == null || pool.isEmpty()) {
            pool = GameDepot.getRelicAffixList(501);
        }

        List<Integer> result = new ArrayList<>();
        // Base line for each type (one roll)
        for (FightProperty fp : types) {
            result.add(rollAffixId(pool, fp, false));
        }
        // Upgrades
        for (int t = 0; t < 4; t++) {
            for (int u = 0; u < counts[t]; u++) {
                result.add(rollAffixId(pool, types.get(t), true));
            }
        }
        return result;
    }

    private static int rollAffixId(List<ReliquaryAffixData> pool, FightProperty fp, boolean upgrade) {
        WeightedList<ReliquaryAffixData> wl = new WeightedList<>();
        for (ReliquaryAffixData aff : pool) {
            if (aff.getFightProp() == fp) {
                int w = upgrade ? Math.max(1, aff.getUpgradeWeight()) : Math.max(1, aff.getWeight());
                wl.add(w, aff);
            }
        }
        if (wl.size() == 0) {
            // fallback any
            for (ReliquaryAffixData aff : pool) {
                if (aff.getFightProp() == fp) {
                    return aff.getId();
                }
            }
            return pool.get(0).getId();
        }
        return wl.next().getId();
    }

    private static long firstUint64(Map<Integer, List<Object>> fields, int field) {
        List<Object> vals = fields.get(field);
        if (vals == null || vals.isEmpty()) {
            return 0L;
        }
        Object v = vals.get(0);
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Integer i) {
            return i & 0xFFFFFFFFL;
        }
        if (v instanceof byte[] bytes) {
            ProtoWire.Reader r = new ProtoWire.Reader(bytes);
            if (r.hasRemaining()) {
                return r.readVarint();
            }
        }
        return 0L;
    }

    private static boolean firstBool(Map<Integer, List<Object>> fields, int field) {
        List<Object> vals = fields.get(field);
        if (vals == null || vals.isEmpty()) {
            return false;
        }
        Object v = vals.get(0);
        if (v instanceof Long l) {
            return l != 0L;
        }
        if (v instanceof Integer i) {
            return i != 0;
        }
        return false;
    }

    private static String toHex(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(payload.length * 2);
        for (byte b : payload) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String summarize(Map<Integer, List<Object>> fields) {
        StringBuilder sb = new StringBuilder("{");
        for (var e : fields.entrySet()) {
            sb.append(e.getKey()).append("=").append(ProtoWire.asUint32List(e.getValue())).append(';');
        }
        sb.append('}');
        return sb.toString();
    }
}
