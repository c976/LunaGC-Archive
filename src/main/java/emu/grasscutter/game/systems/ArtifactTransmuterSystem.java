package emu.grasscutter.game.systems;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.ReliquaryAffixData;
import emu.grasscutter.data.excels.reliquary.ReliquaryMainPropData;
import emu.grasscutter.data.excels.reliquary.ReliquarySetData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.systems.ReliquaryDustSystem;
import emu.grasscutter.server.packet.send.PacketReliquaryOfferDataNotify;
import emu.grasscutter.server.packet.send.PacketReliquaryOfferDefineRsp;
import emu.grasscutter.server.packet.send.PacketReliquaryOfferExtractRsp;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.ProtoWire;
import emu.grasscutter.utils.objects.WeightedList;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Artifact Transmuter (item 220109).
 * Protocol reconstructed from official CN 7.0 capture (packets.jsonl).
 */
public final class ArtifactTransmuterSystem {
    public static final int SCHEDULE_ID = 700;
    public static final int GADGET_ITEM_ID = 220109;
    public static final int ELIXIR_ITEM_ID = 105005;
    public static final int MAX_PROGRESS = 100;
    /** Progress contributed per Sanctifying Essence (105003), matching capture. */
    public static final int PROGRESS_PER_ESSENCE = 2;
    /** Private-server cycle: 3 days, up to 10 Sanctifying Unction per cycle. */
    public static final int CYCLE_DURATION_SEC = 3 * 24 * 3600;
    public static final int MAX_ELIXIR_PER_CYCLE = 10;

    private static final Map<Integer, PlayerState> STATES = new ConcurrentHashMap<>();
    /** Uids that already received login Offer this JVM session (cleared on logout path). */
    private static final Set<Integer> LOGIN_OFFER_SENT = ConcurrentHashMap.newKeySet();

    private ArtifactTransmuterSystem() {}

    /** Send Offer once per login; safe to call from EnterSceneDone / delayed StoreNotify. */
    public static void sendLoginNotifyOnce(Player player) {
        if (player == null) {
            return;
        }
        try {
            emu.grasscutter.game.player.PushTipsSuppressHelper.suppressAll(player);
            emu.grasscutter.game.player.PushTipsSuppressHelper.scheduleLoginSuppress(player);
        } catch (Throwable ignored) {
        }
        if (!LOGIN_OFFER_SENT.add(player.getUid())) {
            return;
        }
        sendLoginNotify(player);
    }

    public static void clearLoginOfferFlag(int uid) {
        LOGIN_OFFER_SENT.remove(uid);
    }

    /** Refresh cycle window; reset extract count when the previous cycle ended. */
    public static void ensureCycle(PlayerState st) {
        int now = (int) (System.currentTimeMillis() / 1000L);
        if (st.cycleEndUnix <= now) {
            // Align to a clean rolling 3-day window from "now".
            st.cycleEndUnix = now + CYCLE_DURATION_SEC;
            st.extractedThisCycle = 0;
        }
        // Guard against stale / corrupt end times that blow up the countdown UI.
        long remainSec = (st.cycleEndUnix & 0xFFFFFFFFL) - (now & 0xFFFFFFFFL);
        if (remainSec <= 0 || remainSec > CYCLE_DURATION_SEC + 3600L) {
            st.cycleEndUnix = now + CYCLE_DURATION_SEC;
            if (remainSec <= 0) {
                st.extractedThisCycle = 0;
            }
        }
    }

    /** Persist after extract / define so cycle UI survives restarts. */
    public static void saveState(Player player) {
        if (player == null) {
            return;
        }
        PlayerState st = STATES.get(player.getUid());
        if (st == null) {
            return;
        }
        try {
            Map<String, Object> root = loadAllStates();
            Map<String, Object> entry = new HashMap<>();
            entry.put("progress", st.progress);
            entry.put("extractedThisCycle", st.extractedThisCycle);
            entry.put("cycleEndUnix", st.cycleEndUnix);
            entry.put("definedSuites", st.definedSuites);
            root.put(Integer.toString(player.getUid()), entry);
            writeAllStates(root);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("ArtifactTransmuter saveState failed: {}", t.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadAllStates() {
        Map<String, Object> root = new HashMap<>();
        try {
            var path = FileUtils.getDataPath("artifact_transmuter_state.json");
            if (!Files.exists(path)) {
                return root;
            }
            String raw = Files.readString(path).trim();
            if (raw.isEmpty()) {
                return root;
            }
            // Tiny hand-rolled JSON object reader for flat uid -> state maps.
            // Prefer Gson if available on classpath.
            try {
                return new com.google.gson.Gson().fromJson(raw, Map.class);
            } catch (Throwable ignored) {
                return root;
            }
        } catch (Throwable t) {
            return root;
        }
    }

    private static void writeAllStates(Map<String, Object> root) throws Exception {
        var path = FileUtils.getDataPath("artifact_transmuter_state.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    @SuppressWarnings("unchecked")
    public static PlayerState state(Player player) {
        PlayerState st = STATES.computeIfAbsent(player.getUid(), uid -> {
            PlayerState loaded = new PlayerState();
            try {
                Map<String, Object> root = loadAllStates();
                Object entryObj = root.get(Integer.toString(uid));
                if (entryObj instanceof Map<?, ?> entry) {
                    Object p = entry.get("progress");
                    Object e = entry.get("extractedThisCycle");
                    Object c = entry.get("cycleEndUnix");
                    if (p instanceof Number n) loaded.progress = n.intValue();
                    if (e instanceof Number n) loaded.extractedThisCycle = n.intValue();
                    if (c instanceof Number n) loaded.cycleEndUnix = n.intValue();
                    Object suites = entry.get("definedSuites");
                    if (suites instanceof Map<?, ?> sm) {
                        for (Map.Entry<?, ?> se : sm.entrySet()) {
                            try {
                                loaded.definedSuites.put(
                                        Integer.parseInt(String.valueOf(se.getKey())),
                                        ((Number) se.getValue()).intValue());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("ArtifactTransmuter loadState failed: {}", t.toString());
            }
            return loaded;
        });
        ensureCycle(st);
        return st;
    }

    public static int cycleEndTime(PlayerState st) {
        ensureCycle(st);
        return st.cycleEndUnix;
    }

    public static int remainingElixir(PlayerState st) {
        ensureCycle(st);
        return Math.max(0, MAX_ELIXIR_PER_CYCLE - st.extractedThisCycle);
    }

    public static void ensureGadget(Player player) {
        Inventory inv = player.getInventory();
        if (inv.getItemCountById(GADGET_ITEM_ID) <= 0) {
            inv.addItem(new GameItem(GADGET_ITEM_ID, 1), ActionReason.PlayerUpgradeReward);
        }
    }

    /**
     * Login: ensure gadget + suppress tips + full Offer sync.
     *
     * <p>Previously Offer was deferred until UseItem/QuickUse. On current clients the define UI
     * often opens without those packets, leaving schedule unset, which shows TxtItemName / Set Name
     * placeholders. Sync Offer here with tip 7013 suppressed (same as {@link #sendDataNotify}).
     */
    public static void sendLoginNotify(Player player) {
        ensureGadget(player);
        emu.grasscutter.game.player.PushTipsSuppressHelper.suppressAll(player);
        repairInventoryDefinedTemplates(player);
        // Full Offer sync on login - required for the transmuter set list and main/sub pickers.
        sendDataNotify(player);
        Grasscutter.getLogger().debug(
                "ArtifactTransmuter login Offer synced uid={} schedule={}",
                player.getUid(),
                SCHEDULE_ID);
    }

    public static void sendDataNotify(Player player) {
        ensureGadget(player);
        PlayerState st = state(player);
        // Same idea as MusicGameBook: clear tip BEFORE feature data, then again after.
        emu.grasscutter.game.player.PushTipsSuppressHelper.suppressRelicDefine(player);
        emu.grasscutter.game.player.PushTipsSuppressHelper.suppressAll(player);
        player.sendPacket(new PacketReliquaryOfferDataNotify(st));
        emu.grasscutter.game.player.PushTipsSuppressHelper.suppressRelicDefine(player);
        ReliquaryDustSystem.sendDataNotify(player);
        Grasscutter.getLogger().debug(
                "ArtifactTransmuter open Offer uid={} schedule={} remainElixir={}/{} progress={} cycleEnd={}",
                player.getUid(),
                SCHEDULE_ID,
                remainingElixir(st),
                MAX_ELIXIR_PER_CYCLE,
                st.progress,
                st.cycleEndUnix);
    }

    /** One-shot bag repair for defined relics on wrong ones-digit templates. */
    public static void repairInventoryDefinedTemplates(Player player) {
        if (player == null || player.getInventory() == null) {
            return;
        }
        java.util.List<GameItem> changed = new ArrayList<>();
        for (GameItem item : player.getInventory()) {
            try {
                if (repairDefinedReliquaryTemplate(item)) {
                    item.save();
                    changed.add(item);
                }
            } catch (Throwable t) {
                Grasscutter.getLogger()
                        .warn(
                                "ArtifactTransmuter repair skip guid={}: {}",
                                item != null ? item.getGuid() : 0,
                                t.toString());
            }
        }
        if (!changed.isEmpty()) {
            player.sendPacket(new emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify(changed));
            Grasscutter.getLogger()
                    .debug(
                            "ArtifactTransmuter repaired-defined-templates uid={} count={}",
                            player.getUid(),
                            changed.size());
        }
    }

    public static void handleExtract(Player player, byte[] payload) {
        ensureGadget(player);
        PlayerState st = state(player);
        if (remainingElixir(st) <= 0) {
            player.sendPacket(new PacketReliquaryOfferDataNotify(st));
            player.sendPacket(new PacketReliquaryOfferExtractRsp());
            Grasscutter.getLogger().debug("ArtifactTransmuter extract blocked uid={} cycle full", player.getUid());
            return;
        }
        Map<Integer, List<Object>> fields = ProtoWire.parse(payload);

        // Consume submitted materials / guids.
        int gained = 0;
        Inventory inv = player.getInventory();

        // Length-delimited entries may be ItemParam {item_id=1,count=2} or artifact guids.
        for (List<Object> values : fields.values()) {
            for (Object v : values) {
                if (!(v instanceof byte[] bytes)) {
                    continue;
                }
                Map<Integer, List<Object>> sub = ProtoWire.parse(bytes);
                List<Integer> nums = ProtoWire.asUint32List(sub.get(1));
                List<Integer> counts = ProtoWire.asUint32List(sub.get(2));
                if (!nums.isEmpty() && !counts.isEmpty()) {
                    int itemId = nums.get(0);
                    int count = Math.max(1, counts.get(0));
                    if (itemId == 105003 || itemId == 105002 || itemId == 105004) {
                        int have = inv.getItemCountById(itemId);
                        int use = Math.min(have, count);
                        if (use > 0) {
                            inv.removeItemById(itemId, use);
                            gained += use * (itemId == 105003 ? PROGRESS_PER_ESSENCE : 1);
                        }
                    }
                }
            }
        }

        // Also accept raw uint64-looking guids encoded as varints in any field (artifact extract).
        for (Map.Entry<Integer, List<Object>> e : fields.entrySet()) {
            for (Object v : e.getValue()) {
                if (!(v instanceof Long guidLong)) {
                    continue;
                }
                long guid = guidLong;
                if (guid < 100000) {
                    continue; // likely schedule id / small ints
                }
                GameItem item = inv.getItemByGuid(guid);
                if (item == null || item.getItemData() == null) {
                    continue;
                }
                if (item.getItemData().getItemType() != ItemType.ITEM_RELIQUARY) {
                    continue;
                }
                if (item.getItemData().getRankLevel() < 5 || item.getLevel() < 4) {
                    continue;
                }
                int add = Math.max(2, item.getLevel()); // generous private-server curve
                if (inv.removeItem(item)) {
                    gained += add;
                }
            }
        }

        if (gained <= 0) {
            // Fallback for private server: grant progress even if parse missed items.
            gained = PROGRESS_PER_ESSENCE;
        }

        st.progress += gained;
        int elixirs = 0;
        while (st.progress >= MAX_PROGRESS) {
            st.progress -= MAX_PROGRESS;
            elixirs++;
        }
        int remainCap = remainingElixir(st);
        if (elixirs > remainCap) {
            // Refund overflow progress into the bar; do not exceed cycle cap.
            int overflow = elixirs - remainCap;
            st.progress += overflow * MAX_PROGRESS;
            elixirs = remainCap;
        }
        if (elixirs > 0) {
            inv.addItem(new GameItem(ELIXIR_ITEM_ID, elixirs), ActionReason.PlayerUpgradeReward);
            st.extractedThisCycle += elixirs;
        }

        player.sendPacket(new PacketReliquaryOfferDataNotify(st));
        player.sendPacket(new PacketReliquaryOfferExtractRsp());
        saveState(player);
        Grasscutter.getLogger().debug(
                "ArtifactTransmuter extract uid={} gainedProgress={} elixir={} remainProgress={} remainElixir={} cycleEnd={}",
                player.getUid(), gained, elixirs, st.progress, remainingElixir(st), st.cycleEndUnix);
    }

    public static void handleDefine(Player player, byte[] payload) {
        ensureGadget(player);
        PlayerState st = state(player);
        Map<Integer, List<Object>> fields = ProtoWire.parse(payload);

        // 7.1 client: groups 3, equipType 8, versionIdx 9, schedule 10, mainIndex 1 (the 7.0 capture's
        // 5/9/11/13/14 carried over by the structural match). Schedule may be omitted.
        List<Integer> groups = new ArrayList<>();
        int equipType = 1;
        int versionIdx = 1;
        int mainIndex = 1;

        Map<Integer, List<Integer>> decoded = new HashMap<>();
        for (Map.Entry<Integer, List<Object>> e : fields.entrySet()) {
            decoded.put(e.getKey(), ProtoWire.asUint32List(e.getValue()));
        }

        // Prefer explicit live field map when present.
        if (decoded.containsKey(3) || decoded.containsKey(8) || decoded.containsKey(9) || decoded.containsKey(1)) {
            if (decoded.containsKey(3)) {
                groups.addAll(filterGroupIds(decoded.get(3)));
            }
            if (decoded.containsKey(8) && !decoded.get(8).isEmpty()) {
                equipType = decoded.get(8).get(0);
            }
            if (decoded.containsKey(9) && !decoded.get(9).isEmpty()) {
                versionIdx = decoded.get(9).get(0);
            }
            if (decoded.containsKey(1) && !decoded.get(1).isEmpty()) {
                mainIndex = decoded.get(1).get(0);
            }
        } else {
            // Older capture layout: 1=groups 2=equip 3=version 4=schedule 5=main
            if (decoded.containsKey(1)) {
                groups.addAll(filterGroupIds(decoded.get(1)));
            }
            if (decoded.containsKey(2) && !decoded.get(2).isEmpty()) {
                equipType = decoded.get(2).get(0);
            }
            if (decoded.containsKey(3) && !decoded.get(3).isEmpty()) {
                versionIdx = decoded.get(3).get(0);
            }
            if (decoded.containsKey(5) && !decoded.get(5).isEmpty()) {
                mainIndex = decoded.get(5).get(0);
            }
        }

        // Fallback: any multi-value field that looks like affix group ids (crit=20, critdmg=22, etc.).
        if (groups.isEmpty()) {
            for (List<Integer> vals : decoded.values()) {
                List<Integer> g = filterGroupIds(vals);
                if (g.size() >= 2 && g.size() <= 4) {
                    groups = g;
                    break;
                }
            }
        }

        // Fallback singles only when version field missing.
        if (!decoded.containsKey(9) && versionIdx <= 1) {
            for (List<Integer> vals : decoded.values()) {
                if (vals.size() == 1) {
                    int v = vals.get(0);
                    if (v >= 2 && v <= offerSetOrder().size() && v != equipType && v != SCHEDULE_ID) {
                        versionIdx = v;
                        break;
                    }
                }
            }
        }

        Grasscutter.getLogger().debug(
                "ArtifactTransmuter define parse uid={} versionIdx={} equipType={} mainIndex={} groups={} decoded={}",
                player.getUid(), versionIdx, equipType, mainIndex, groups, decoded);

        ReliquarySetData setData = resolveSetByVersionIndex(versionIdx);
        if (setData == null) {
            Grasscutter.getLogger().warn("ArtifactTransmuter define: unknown versionIdx={}", versionIdx);
            player.sendPacket(new PacketReliquaryOfferDefineRsp(versionIdx));
            return;
        }

        GameItem relic = createDefinedReliquary(setData, equipType, mainIndex, groups);
        if (relic == null) {
            Grasscutter.getLogger().warn("ArtifactTransmuter define: failed to create relic set={}", setData.getId());
            player.sendPacket(new PacketReliquaryOfferDefineRsp(versionIdx));
            return;
        }

        // Cost: flower/plume=1, sands=2, circlet=3, goblet=4.
        int cost = switch (equipType) {
            case 3 -> 2; // sands
            case 5 -> 3; // circlet
            case 4 -> 4; // goblet
            default -> 1; // flower / plume
        };
        Inventory inv = player.getInventory();
        if (inv.getItemCountById(ELIXIR_ITEM_ID) < cost) {
            inv.addItem(new GameItem(ELIXIR_ITEM_ID, cost), ActionReason.PlayerUpgradeReward);
        }
        inv.removeItemById(ELIXIR_ITEM_ID, cost);

        inv.addItem(relic, ActionReason.PlayerUpgradeReward);
        int setId = setData.getId();
        st.definedSuites.merge(setId, 1, Integer::sum);

        player.sendPacket(new PacketReliquaryOfferDataNotify(st));
        player.sendPacket(new PacketReliquaryOfferDefineRsp(versionIdx));
        saveState(player);
        Grasscutter.getLogger().debug(
                "ArtifactTransmuter define uid={} set={} equipType={} mainFp={} subFps={} item={} mainPropId={} append={}",
                player.getUid(),
                setId,
                equipType,
                mainIndex,
                groups,
                relic.getItemId(),
                relic.getMainPropId(),
                relic.getAppendPropIdList());
    }

    /** Affix group ids include crit=20 / critdmg=22 (not only 1..12). */
    private static List<Integer> filterGroupIds(List<Integer> vals) {
        List<Integer> out = new ArrayList<>();
        if (vals == null) {
            return out;
        }
        for (int v : vals) {
            if (v >= 2 && v <= 81 && v != SCHEDULE_ID) {
                out.add(v);
            }
        }
        // Single scalar that is also a valid group id is OK only when multi later; keep all candidates.
        return out;
    }

    private static volatile List<Integer> OFFER_SET_ORDER;

    /**
     * Official UI indexes offer sets as DFGNDLNPOIJ==5 (5★ filter pool), sorted by bagSortValue.
     * Capture: HBLBDKIBOCG=46 gives setId 15048.
     */
    private static List<Integer> offerSetOrder() {
        List<Integer> cached = OFFER_SET_ORDER;
        if (cached != null) {
            return cached;
        }
        List<Integer> ordered = new ArrayList<>();
        try {
            var path = FileUtils.getResourcePath("ExcelBinOutput/ReliquarySetExcelConfigData.json");
            if (Files.exists(path)) {
                String json = Files.readString(path);
                Pattern obj = Pattern.compile("\\{([^{}]{0,800})\\}");
                Matcher m = obj.matcher(json);
                record Row(int setId, int bag, int dfg, boolean disabled) {}
                List<Row> rows = new ArrayList<>();
                while (m.find()) {
                    String body = m.group(1);
                    Matcher sid = Pattern.compile("\"setId\"\\s*:\\s*(\\d+)").matcher(body);
                    Matcher bag = Pattern.compile("\"bagSortValue\"\\s*:\\s*(\\d+)").matcher(body);
                    Matcher dfg = Pattern.compile("\"DFGNDLNPOIJ\"\\s*:\\s*(\\d+)").matcher(body);
                    Matcher dis = Pattern.compile("\"disableFilter\"\\s*:\\s*(true|false|\\d+)").matcher(body);
                    if (!sid.find() || !bag.find()) {
                        continue;
                    }
                    int dfgVal = dfg.find() ? Integer.parseInt(dfg.group(1)) : 0;
                    boolean disabled = false;
                    if (dis.find()) {
                        String v = dis.group(1);
                        disabled = "true".equals(v) || (!"false".equals(v) && Integer.parseInt(v) != 0);
                    }
                    rows.add(new Row(
                            Integer.parseInt(sid.group(1)),
                            Integer.parseInt(bag.group(1)),
                            dfgVal,
                            disabled));
                }
                rows.stream()
                        .filter(r -> !r.disabled && r.bag > 0 && r.dfg == 5)
                        .sorted(Comparator.comparingInt(Row::bag))
                        .map(Row::setId)
                        .forEach(ordered::add);
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("ArtifactTransmuter: failed loading set order: {}", t.toString());
        }
        if (ordered.isEmpty()) {
            ordered = GameData.getItemDataMap().values().stream()
                    .filter(d -> d != null && d.getItemType() == ItemType.ITEM_RELIQUARY)
                    .filter(d -> d.getRankLevel() == 5 && d.getSetId() > 0)
                    .map(ItemData::getSetId)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }
        OFFER_SET_ORDER = ordered;
        Grasscutter.getLogger().debug("ArtifactTransmuter offer set pool size={}", ordered.size());
        return ordered;
    }

    private static ReliquarySetData resolveSetByVersionIndex(int versionIdx) {
        List<Integer> order = offerSetOrder();
        if (versionIdx > 0 && versionIdx <= order.size()) {
            ReliquarySetData byBag = GameData.getReliquarySetDataMap().get((int) order.get(versionIdx - 1));
            if (byBag != null) {
                return byBag;
            }
        }
        // Allow direct setId pass-through.
        return GameData.getReliquarySetDataMap().get(versionIdx);
    }

    private static GameItem createDefinedReliquary(
            ReliquarySetData setData, int equipType, int mainFightPropId, List<Integer> subFightPropIds) {
        int setId = setData.getId();
        // Old sets such as Gladiator and Pale Flame ship appendPropNum 0..4 templates for the same slot.
        // First-match used to grab appendPropNum=0/1/2 itemIds, after which the client hides reshaping even at +20.
        // Prefer classic 501 + appendPropNum>=3 (ideally 4), same as newer sets' only templates.
        ItemData piece = pickDefinedPiece(setId, equipType);
        if (piece == null) {
            return null;
        }

        GameItem item = new GameItem(piece);
        item.setLevel(1);
        item.setLocked(true);

        // Client ReliquaryOfferDefineReq main/sub fields are FightProperty ids
        // (see ReliquaryWear* / ReliquarySetScheme* excel), NOT main-depot list indices
        // and NOT affix groupId (EM is fightProp 28 but affix groupId 24).
        FightProperty wantMain = FightProperty.getPropById(mainFightPropId);
        int depotId = piece.getMainPropDepotId();
        ReliquaryMainPropData main = findMainProp(depotId, wantMain);
        if (main == null) {
            main = findMainProp(classicMainDepotForEquip(equipType), wantMain);
        }
        if (main != null) {
            item.setMainPropId(main.getId());
        } else {
            Grasscutter.getLogger().warn(
                    "ArtifactTransmuter define: no main prop fightProp={} depot={} equipType={}",
                    mainFightPropId, depotId, equipType);
        }

        // Build append props using the STANDARD 5-star depot 501.
        // Newer set depots 961-965 in resources are "compressed" (one inflated value per group),
        // which makes +0 relics show multi-roll stats (e.g. CRIT 9.7%).
        List<Integer> append = new ArrayList<>();
        Set<FightProperty> used = new HashSet<>();
        ReliquaryMainPropData mainData = GameData.getReliquaryMainPropDataMap().get(item.getMainPropId());
        if (mainData != null && mainData.getFightProp() != null) {
            used.add(mainData.getFightProp());
        }
        int appendDepot = normalizeAppendDepot(piece.getAppendPropDepotId());
        // Official define: exactly 2 chosen substats + fill to 3 or 4 single rolls.
        List<Integer> chosen = new ArrayList<>();
        List<Integer> chosenAffixIds = new ArrayList<>();
        for (int fightPropId : subFightPropIds) {
            if (chosen.size() >= 2) {
                break;
            }
            ReliquaryAffixData affix = pickAffixByFightProp(appendDepot, fightPropId, used);
            if (affix != null) {
                append.add(affix.getId());
                used.add(affix.getFightProp());
                chosen.add(fightPropId);
                chosenAffixIds.add(affix.getId());
            } else {
                Grasscutter.getLogger().warn(
                        "ArtifactTransmuter define: no affix for fightProp={} depot={}",
                        fightPropId, appendDepot);
            }
        }
        int target = Math.min(4, Math.max(3, effectiveAppendPropNum(piece)));
        while (append.size() < target) {
            ReliquaryAffixData affix = pickRandomAffix(appendDepot, used);
            if (affix == null) {
                break;
            }
            append.add(affix.getId());
            used.add(affix.getFightProp());
        }
        item.getAppendPropIdList().clear();
        item.getAppendPropIdList().addAll(append);
        // Purple define icon plus at least 2 shared upgrade hits on the chosen lines, per the official tutorial.
        item.markAsDefinedReliquary(chosenAffixIds);
        Grasscutter.getLogger().debug(
                "ArtifactTransmuter define stats item={} mainPropId={} mainFp={} chosenSubs={} append={} definite={} excelApn={} effApn={}",
                item.getItemId(),
                item.getMainPropId(),
                mainData != null ? mainData.getFightProp() : wantMain,
                chosen,
                append,
                chosenAffixIds,
                piece.getAppendPropNum(),
                effectiveAppendPropNum(piece));
        return item;
    }

    /**
     * Remap legacy defined relics stamped onto appendPropNum 0/1/2 templates, which have no reshape
     * tab) onto the preferred appendPropNum&gt;=3 classic piece for the same set/slot.
     *
     * @return true if itemId changed
     */
    public static boolean repairDefinedReliquaryTemplate(GameItem item) {
        if (item == null || item.getItemType() != ItemType.ITEM_RELIQUARY) {
            return false;
        }
        if (item.getPurchasedAppendPropIdList() == null || item.getPurchasedAppendPropIdList().isEmpty()) {
            return false;
        }
        ItemData cur = item.getItemData();
        if (cur == null) {
            return false;
        }
        // Remote ReliquaryExcel often stamps appendPropNum=4 on every template; use effective count.
        if (effectiveAppendPropNum(cur) >= 3) {
            return false;
        }
        int equipType = cur.getEquipType() == null ? 0 : cur.getEquipType().getValue();
        ItemData better = pickDefinedPiece(cur.getSetId(), equipType);
        if (better == null || better.getId() == item.getItemId()) {
            return false;
        }
        if (effectiveAppendPropNum(better) < 3) {
            return false;
        }
        int oldId = item.getItemId();
        item.setItemId(better.getId());
        item.setItemData(better);
        Grasscutter.getLogger()
                .debug(
                        "ArtifactTransmuter repair-defined-template guid={} {} -> {} (effApn {} -> {}, excel {} -> {})",
                        item.getGuid(),
                        oldId,
                        better.getId(),
                        effectiveAppendPropNum(cur),
                        effectiveAppendPropNum(better),
                        cur.getAppendPropNum(),
                        better.getAppendPropNum());
        return true;
    }

    /**
     * Prefer classic 5-star templates with effective appendPropNum &gt;= 3 so the reshape UI stays available
     * after +20. Public for remapping already-crafted defined relics that used 0/1/2 templates.
     */
    public static ItemData pickDefinedPiece(int setId, int equipType) {
        ItemData best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ItemData data : GameData.getItemDataMap().values()) {
            if (data == null || data.getItemType() != ItemType.ITEM_RELIQUARY) {
                continue;
            }
            if (data.getSetId() != setId || data.getRankLevel() != 5) {
                continue;
            }
            int et = data.getEquipType() == null ? 0 : data.getEquipType().getValue();
            if (et != equipType) {
                continue;
            }
            int score = scoreDefinedPiece(data);
            if (score > bestScore || (score == bestScore && (best == null || data.getId() < best.getId()))) {
                best = data;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * Classic open-world 5-star families encode the initial affix count in the ones digit (71520..71524).
     * Some server resource dumps wrongly flatten excel {@code appendPropNum} to 4 for every row -
     * client still uses the real digit, so trust id%10 for classic 501+main depot pieces.
     */
    static int effectiveAppendPropNum(ItemData data) {
        if (data == null) {
            return 0;
        }
        int digit = Math.floorMod(data.getId(), 10);
        if (digit <= 4
                && data.getAppendPropDepotId() == 501
                && isClassicMainDepot(data.getMainPropDepotId())) {
            return digit;
        }
        return data.getAppendPropNum();
    }

    private static int scoreDefinedPiece(ItemData data) {
        int appendDepot = data.getAppendPropDepotId();
        int mainDepot = data.getMainPropDepotId();
        int score = 0;
        if (appendDepot == 501) {
            score += 100;
        } else if (appendDepot > 0 && appendDepot < 961) {
            score += 50;
        } else {
            // Compressed 961-965: soft-lock risk + wrong roll display.
            score -= 100;
        }
        if (isClassicMainDepot(mainDepot)) {
            score += 80;
        }
        int apn = effectiveAppendPropNum(data);
        if (apn >= 4) {
            score += 40;
        } else if (apn >= 3) {
            score += 30;
        } else {
            // The client ReliquaryDust gate uses the real template index; 0/1/2 hide reshaping.
            score -= 50;
        }
        return score;
    }

    private static boolean isClassicMainDepot(int depotId) {
        return depotId == 1000 || depotId == 2000 || depotId == 3000 || depotId == 4000 || depotId == 5000;
    }

    /** Classic 5-star main-prop depots by EquipType value. */
    private static int classicMainDepotForEquip(int equipType) {
        return switch (equipType) {
            case 1 -> 4000; // flower
            case 2 -> 2000; // plume
            case 3 -> 1000; // sands
            case 4 -> 5000; // goblet
            case 5 -> 3000; // circlet
            default -> 3000;
        };
    }

    private static ReliquaryMainPropData findMainProp(int depotId, FightProperty want) {
        if (want == null || want == FightProperty.FIGHT_PROP_NONE || depotId <= 0) {
            return null;
        }
        return GameData.getReliquaryMainPropDataMap().values().stream()
                .filter(m -> m.getPropDepotId() == depotId)
                .filter(m -> m.getWeight() > 0)
                .filter(m -> m.getFightProp() == want)
                .min(Comparator.comparingInt(ReliquaryMainPropData::getId))
                .orElse(null);
    }

    /** Map compressed 961-965 depots to classic tiered depot 501. */
    private static int normalizeAppendDepot(int depotId) {
        if (depotId >= 961 && depotId <= 965) {
            return 501;
        }
        return depotId > 0 ? depotId : 501;
    }

    /**
     * Pick a +0 affix by FightProperty id from the client define UI.
     * Matches ReliquaryWearAppendPropExcel (2/3/5/6/8/9/20/22/28/23); EM is 28 here
     * while ReliquaryAffixExcel groupId for EM is 24.
     * Tier (e.g. 2.7%/3.1%/3.5%/3.9% CRIT) is weighted-random like normal relic rolls.
     */
    private static ReliquaryAffixData pickAffixByFightProp(int depotId, int fightPropId, Set<FightProperty> used) {
        FightProperty prop = FightProperty.getPropById(fightPropId);
        if (prop == FightProperty.FIGHT_PROP_NONE || used.contains(prop)) {
            return null;
        }
        List<ReliquaryAffixData> cands = GameData.getReliquaryAffixDataMap().values().stream()
                .filter(a -> a.getDepotId() == depotId)
                .filter(a -> a.getFightProp() == prop)
                .filter(a -> a.getWeight() > 0)
                .collect(Collectors.toList());
        if (cands.isEmpty() && depotId != 501) {
            cands = GameData.getReliquaryAffixDataMap().values().stream()
                    .filter(a -> a.getDepotId() == 501)
                    .filter(a -> a.getFightProp() == prop)
                    .filter(a -> a.getWeight() > 0)
                    .collect(Collectors.toList());
        }
        return weightedPick(cands);
    }

    private static ReliquaryAffixData pickRandomAffix(int depotId, Set<FightProperty> used) {
        List<ReliquaryAffixData> cands = GameData.getReliquaryAffixDataMap().values().stream()
                .filter(a -> a.getDepotId() == depotId)
                .filter(a -> a.getWeight() > 0)
                .filter(a -> !used.contains(a.getFightProp()))
                .collect(Collectors.toList());
        if (cands.isEmpty() && depotId != 501) {
            return pickRandomAffix(501, used);
        }
        // Same as GameItem.addNewAppendProp: weight across all remaining affix rows (prop + tier).
        return weightedPick(cands);
    }

    private static ReliquaryAffixData weightedPick(List<ReliquaryAffixData> cands) {
        if (cands == null || cands.isEmpty()) {
            return null;
        }
        WeightedList<ReliquaryAffixData> randomList = new WeightedList<>();
        for (ReliquaryAffixData affix : cands) {
            randomList.add(affix.getWeight(), affix);
        }
        if (randomList.size() == 0) {
            return null;
        }
        return randomList.next();
    }

    public static final class PlayerState {
        public int progress = 0;
        public int extractedThisCycle = 0;
        /** Unix seconds when the current 3-day cycle ends. */
        public int cycleEndUnix = 0;
        public final Map<Integer, Integer> definedSuites = new HashMap<>();
    }
}
