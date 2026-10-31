package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.net.proto.PlayerOfferingDataNotifyOuterClass.PlayerOfferingDataNotify;
import emu.grasscutter.net.proto.PlayerOfferingDataOuterClass.PlayerOfferingData;
import emu.grasscutter.net.proto.PlayerOfferingRspOuterClass.PlayerOfferingRsp;
import emu.grasscutter.net.proto.TakeOfferingLevelRewardReqOuterClass.TakeOfferingLevelRewardReq;
import emu.grasscutter.net.proto.TakeOfferingLevelRewardRspOuterClass.TakeOfferingLevelRewardRsp;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.OfferingInfoOuterClass.OfferingInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.utils.ProtoWire;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for offering objects (Sacred Sakura, Frostbearing Tree, Tree of Dreams and the like):
 * gadget to offeringId mapping, the login notify and the interaction responses.
 *
 * <p>The client shows the F prompt based on SceneGadgetInfo.offering_info, and opens the offering page
 * based on PlayerOfferingDataNotify.
 */
public final class OfferingHelper {
    /** Sacred Sakura OfferingGadget. */
    public static final int GADGET_SACRED_SAKURA = 70290094;
    /** Sacred Sakura offeringId (OPEN_STATE_ORAIONOKAMI). */
    public static final int OFFERING_ORAIONOKAMI = 2;
    /** OPEN_STATE_ORAIONOKAMI */
    public static final int OPEN_STATE_ORAIONOKAMI = 2000;

    /** Known OfferingGadget to offeringId, covering the main open-world offerings. */
    private static final Map<Integer, Integer> GADGET_TO_OFFERING = new LinkedHashMap<>();

    static {
        GADGET_TO_OFFERING.put(70290032, 1); // Frostbearing Tree
        GADGET_TO_OFFERING.put(GADGET_SACRED_SAKURA, OFFERING_ORAIONOKAMI); // Sacred Sakura
        GADGET_TO_OFFERING.put(70290511, 5); // Tree of Dreams
        GADGET_TO_OFFERING.put(70290803, 5); // Sumeru Lake of Rebirth interaction, same tree family
        GADGET_TO_OFFERING.put(70330637, 7); // wishing pool
        GADGET_TO_OFFERING.put(70292163, 8); // saurian cub
        GADGET_TO_OFFERING.put(73002033, 9); // Natlan tribal offering
        GADGET_TO_OFFERING.put(73065005, 10);
        GADGET_TO_OFFERING.put(73065006, 11);
        GADGET_TO_OFFERING.put(73065007, 12);
        GADGET_TO_OFFERING.put(73074085, 13);
        GADGET_TO_OFFERING.put(73074086, 14);
        GADGET_TO_OFFERING.put(73074087, 15);
        GADGET_TO_OFFERING.put(73077192, 16); // firmament
    }

    /** In-process cache. The source of truth is the player questGlobalVariables / JSON on disk, so a
     * restart cannot swallow sigils without recording the level. */
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> LEVELS =
            new ConcurrentHashMap<>();

    /** Claimed level rewards: uid to offeringId to levels. */
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, java.util.Set<Integer>>>
            TAKEN = new ConcurrentHashMap<>();

    /** questGlobalVariables key: 910000 + offeringId. */
    private static final int QGV_OFFERING_BASE = 910000;

    private static final java.nio.file.Path LEVELS_FILE =
            java.nio.file.Path.of("data", "offering_levels.json");

    private OfferingHelper() {}

    private static int qgvKey(int offeringId) {
        return QGV_OFFERING_BASE + offeringId;
    }

    public static int resolveOfferingId(EntityGadget gadget) {
        if (gadget == null) {
            return 0;
        }
        Integer mapped = GADGET_TO_OFFERING.get(gadget.getGadgetId());
        if (mapped != null) {
            return mapped;
        }
        try {
            var data = gadget.getGadgetData();
            if (data != null && data.getJsonName() != null) {
                String j = data.getJsonName();
                if (j.contains("ThunderSeedOffer")) {
                    return OFFERING_ORAIONOKAMI;
                }
                if (j.contains("AncientBloodTree")) {
                    return 1;
                }
                if (j.contains("XMOfferingTree") || j.contains("RebornLake")) {
                    return 5;
                }
                if (j.contains("WishingPond")) {
                    return 7;
                }
                if (j.contains("Silong")) {
                    return 8;
                }
                if (j.contains("NatlanOffering")) {
                    return 9;
                }
                if (j.contains("NodkraiOffering") || j.contains("NodKraiOffering")) {
                    return 10;
                }
                if (j.contains("FirmamentOffering")) {
                    return 16;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static boolean isOfferingGadget(EntityGadget gadget) {
        return resolveOfferingId(gadget) > 0
                || (gadget != null
                        && gadget.getGadgetData() != null
                        && gadget.getGadgetData().getType()
                                == emu.grasscutter.game.props.EntityType.OfferingGadget);
    }

    public static int getLevel(Player player, int offeringId) {
        if (player == null || offeringId <= 0) {
            return 0;
        }
        ConcurrentHashMap<Integer, Integer> map =
                LEVELS.computeIfAbsent(player.getUid(), u -> loadPlayerLevels(player));
        return map.getOrDefault(offeringId, 0);
    }

    public static void setLevel(Player player, int offeringId, int level) {
        if (player == null || offeringId <= 0) {
            return;
        }
        int lv = Math.max(0, level);
        LEVELS.computeIfAbsent(player.getUid(), u -> new ConcurrentHashMap<>()).put(offeringId, lv);
        try {
            player.getQuestGlobalVariables().put(qgvKey(offeringId), lv);
            player.save();
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper qgv save: {}", t.toString());
        }
        savePlayerProgress(player.getUid());
    }

    private static java.util.Set<Integer> getTakenSet(Player player, int offeringId) {
        ensureProgressLoaded(player);
        return TAKEN.computeIfAbsent(player.getUid(), u -> new ConcurrentHashMap<>())
                .computeIfAbsent(offeringId, o -> java.util.concurrent.ConcurrentHashMap.newKeySet());
    }

    private static void markTaken(Player player, int offeringId, int level) {
        getTakenSet(player, offeringId).add(level);
        savePlayerProgress(player.getUid());
    }

    private static void ensureProgressLoaded(Player player) {
        if (player == null) {
            return;
        }
        LEVELS.computeIfAbsent(player.getUid(), u -> loadPlayerLevels(player));
        TAKEN.computeIfAbsent(player.getUid(), u -> loadPlayerTaken(player));
    }

    private static ConcurrentHashMap<Integer, Integer> loadPlayerLevels(Player player) {
        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        try {
            for (int oid : GADGET_TO_OFFERING.values()) {
                Integer v = player.getQuestGlobalVariables().get(qgvKey(oid));
                if (v != null && v > 0) {
                    map.put(oid, v);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            ensureLevelsFile();
            if (java.nio.file.Files.exists(LEVELS_FILE)) {
                String json = java.nio.file.Files.readString(LEVELS_FILE);
                var root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                String uidKey = String.valueOf(player.getUid());
                if (root.has(uidKey) && root.get(uidKey).isJsonObject()) {
                    var o = root.getAsJsonObject(uidKey);
                    for (var e : o.entrySet()) {
                        if (!e.getKey().matches("\\d+") || !e.getValue().isJsonPrimitive()) {
                            continue;
                        }
                        int oid = Integer.parseInt(e.getKey());
                        int lv = e.getValue().getAsInt();
                        map.merge(oid, lv, Math::max);
                    }
                }
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper load json: {}", t.toString());
        }
        try {
            for (var e : map.entrySet()) {
                player.getQuestGlobalVariables().put(qgvKey(e.getKey()), e.getValue());
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    private static ConcurrentHashMap<Integer, java.util.Set<Integer>> loadPlayerTaken(Player player) {
        ConcurrentHashMap<Integer, java.util.Set<Integer>> map = new ConcurrentHashMap<>();
        try {
            ensureLevelsFile();
            if (!java.nio.file.Files.exists(LEVELS_FILE)) {
                return map;
            }
            String json = java.nio.file.Files.readString(LEVELS_FILE);
            var root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            String uidKey = String.valueOf(player.getUid());
            if (!root.has(uidKey) || !root.get(uidKey).isJsonObject()) {
                return map;
            }
            var o = root.getAsJsonObject(uidKey);
            if (!o.has("taken") || !o.get("taken").isJsonObject()) {
                return map;
            }
            for (var e : o.getAsJsonObject("taken").entrySet()) {
                int oid = Integer.parseInt(e.getKey());
                var set = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
                if (e.getValue().isJsonArray()) {
                    for (var el : e.getValue().getAsJsonArray()) {
                        set.add(el.getAsInt());
                    }
                }
                map.put(oid, set);
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper load taken: {}", t.toString());
        }
        return map;
    }

    private static void savePlayerProgress(int uid) {
        try {
            ensureLevelsFile();
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            if (java.nio.file.Files.exists(LEVELS_FILE)) {
                String json = java.nio.file.Files.readString(LEVELS_FILE);
                if (!json.isBlank()) {
                    root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                }
            }
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            ConcurrentHashMap<Integer, Integer> map = LEVELS.get(uid);
            if (map != null) {
                for (var e : map.entrySet()) {
                    o.addProperty(String.valueOf(e.getKey()), e.getValue());
                }
            }
            ConcurrentHashMap<Integer, java.util.Set<Integer>> takenMap = TAKEN.get(uid);
            if (takenMap != null && !takenMap.isEmpty()) {
                com.google.gson.JsonObject takenObj = new com.google.gson.JsonObject();
                for (var e : takenMap.entrySet()) {
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    e.getValue().stream().sorted().forEach(arr::add);
                    takenObj.add(String.valueOf(e.getKey()), arr);
                }
                o.add("taken", takenObj);
            }
            root.add(String.valueOf(uid), o);
            java.nio.file.Files.writeString(
                    LEVELS_FILE, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingHelper save json failed: {}", t.toString());
        }
    }

    private static void ensureLevelsFile() throws java.io.IOException {
        java.nio.file.Path dir = LEVELS_FILE.getParent();
        if (dir != null && !java.nio.file.Files.exists(dir)) {
            java.nio.file.Files.createDirectories(dir);
        }
        if (!java.nio.file.Files.exists(LEVELS_FILE)) {
            java.nio.file.Files.writeString(LEVELS_FILE, "{}");
        }
    }

    public static OfferingInfo buildOfferingInfo(int offeringId) {
        return OfferingInfo.newBuilder().setOfferingId(offeringId).build();
    }

    /**
     * The offering packets are still assembled field by field (so retcode 0 is always on the wire),
     * but every field number comes from the generated 7.1 classes; they used to be pinned to a 7.0
     * capture, which a 7.1 client reads as different fields.
     */
    private static byte[] wireOfferingData(Player player, int offeringId, boolean isNewMaxLevel) {
        ensureProgressLoaded(player);
        int level = getLevel(player, offeringId);
        List<Integer> taken = new ArrayList<>(getTakenSet(player, offeringId));
        taken.sort(Integer::compareTo);
        return PlayerOfferingData.newBuilder()
                .setIsNewMaxLevel(isNewMaxLevel)
                .addAllTakenLevelRewardList(taken)
                .setIsFirstInteract(level <= 0)
                .setOfferingId(offeringId)
                .setLevel(level)
                .build()
                .toByteArray();
    }

    private static byte[] wireItemParam(int itemId, int count) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtoWire.writeUint32(out, 1, itemId);
        ProtoWire.writeUint32(out, 2, count);
        return out.toByteArray();
    }

    /** Sends unlocked offering progress on login; without it the client does not consider the offering
     * available and never shows the interaction UI. */
    public static void onPlayerLogin(Player player) {
        if (player == null || player.getSession() == null) {
            return;
        }
        try {
            if (player.getProgressManager().getOpenState(OPEN_STATE_ORAIONOKAMI) == 0) {
                player.getProgressManager().forceSetOpenState(OPEN_STATE_ORAIONOKAMI, 1);
            }
            LEVELS.put(player.getUid(), loadPlayerLevels(player));
            syncOfferingNotify(player);
            Grasscutter.getLogger()
                    .debug(
                            "OfferingHelper login notify uid={} sakuraLv={} notifyOp={}",
                            player.getUid(),
                            getLevel(player, OFFERING_ORAIONOKAMI),
                            PacketOpcodes.PlayerOfferingDataNotify);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingHelper onPlayerLogin failed: {}", t.toString());
        }
    }

    private static boolean isOfferingOpen(Player player, int offeringId) {
        int openStateId =
                switch (offeringId) {
                    case 1 -> 1406; // OPEN_STATE_SNOW_MOUNTAIN_ELDER_TREE
                    case OFFERING_ORAIONOKAMI -> OPEN_STATE_ORAIONOKAMI;
                    default -> 0;
                };
        if (openStateId == 0) {
            // Remaining offerings: open by default for exploration on a private server.
            return true;
        }
        return player.getProgressManager().getOpenState(openStateId) > 0;
    }

    /**
     * The player interacts with an offering: reply with GadgetInteractRsp plus PlayerOfferingRsp to open
     * the offering page.
     *
     * @return true when handled
     */
    public static boolean tryInteract(Player player, EntityGadget gadget) {
        if (player == null || gadget == null) {
            return false;
        }
        int offeringId = resolveOfferingId(gadget);
        if (offeringId <= 0 && !isOfferingGadget(gadget)) {
            return false;
        }
        if (offeringId <= 0) {
            offeringId = OFFERING_ORAIONOKAMI;
        }

        try {
            if (offeringId == OFFERING_ORAIONOKAMI
                    && player.getProgressManager().getOpenState(OPEN_STATE_ORAIONOKAMI) == 0) {
                player.getProgressManager().forceSetOpenState(OPEN_STATE_ORAIONOKAMI, 1);
            }
        } catch (Throwable ignored) {
        }

        try {
            player.sendPacket(
                    new emu.grasscutter.server.packet.send.PacketGadgetInteractRsp(
                            gadget,
                            emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType
                                    .InteractType_INTERACT_UI_INTERACT));
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper interact rsp: {}", t.toString());
        }

        sendOfferingRsp(player, offeringId, Retcode.RET_SUCC_VALUE, null, null);
        syncOfferingNotify(player);

        Grasscutter.getLogger()
                .debug(
                        "OfferingHelper interact uid={} gadget={} offeringId={} lv={}",
                        player.getUid(),
                        gadget.getGadgetId(),
                        offeringId,
                        getLevel(player, offeringId));
        return true;
    }

    /** Submitting: level up as many times as possible in one go, spending the available sigils, without
     * granting rewards. Rewards go through TakeOfferingLevelReward. */
    public static boolean tryLevelUp(Player player, int offeringId) {
        return tryLevelUp(player, offeringId, null);
    }

    public static boolean tryLevelUp(Player player, int offeringId, byte[] requestHeader) {
        if (player == null || offeringId <= 0) {
            return false;
        }
        ensureLevelConfigs();
        ensureProgressLoaded(player);
        int cur = getLevel(player, offeringId);
        int gained = 0;
        int lastRewardId = 0;

        while (true) {
            LevelRow next = LEVEL_ROWS.get(offeringId * 1000 + (cur + 1));
            if (next == null) {
                break;
            }
            if (next.consumeId > 0 && next.consumeCount > 0) {
                var inv = player.getInventory();
                if (inv.getItemCountById(next.consumeId) < next.consumeCount) {
                    break;
                }
                inv.removeItemById(next.consumeId, next.consumeCount);
            }
            cur = next.level;
            lastRewardId = next.rewardId;
            gained++;
        }

        if (gained == 0) {
            LevelRow next = LEVEL_ROWS.get(offeringId * 1000 + (getLevel(player, offeringId) + 1));
            if (next == null) {
                sendOfferingRsp(
                        player, offeringId, Retcode.RET_OFFERING_LEVEL_LIMIT_VALUE, null, requestHeader);
            } else {
                sendOfferingRsp(
                        player, offeringId, Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE, null, requestHeader);
            }
            syncOfferingNotify(player);
            return true;
        }

        setLevel(player, offeringId, cur);
        // Do not stuff rewards into the Rsp item_list, which would pop an acquisition toast on level up.
        // The reward page handles claiming.
        sendOfferingRsp(player, offeringId, Retcode.RET_SUCC_VALUE, null, requestHeader, true);
        syncOfferingNotify(player);

        Grasscutter.getLogger()
                .debug(
                        "OfferingHelper offerAll uid={} offeringId={} ->{} steps={} lastRewardId={} (rewards pending claim)",
                        player.getUid(),
                        offeringId,
                        cur,
                        gained,
                        lastRewardId);
        return true;
    }

    /**
     * Claims one tier from the reward screen. TakeOfferingLevelRewardReq: offering_id=1, level=10.
     */
    public static boolean tryTakeLevelReward(
            Player player, int offeringId, int takeLevel, byte[] requestHeader) {
        if (player == null || offeringId <= 0 || takeLevel <= 0) {
            return false;
        }
        ensureLevelConfigs();
        ensureProgressLoaded(player);
        int cur = getLevel(player, offeringId);
        if (takeLevel > cur) {
            sendTakeRewardRsp(
                    player,
                    offeringId,
                    takeLevel,
                    Retcode.RET_OFFERING_LEVEL_NOT_REACH_VALUE,
                    null,
                    requestHeader);
            return true;
        }
        if (getTakenSet(player, offeringId).contains(takeLevel)) {
            sendTakeRewardRsp(
                    player,
                    offeringId,
                    takeLevel,
                    Retcode.RET_OFFERING_LEVEL_HAS_TAKEN_VALUE,
                    null,
                    requestHeader);
            return true;
        }
        LevelRow row = LEVEL_ROWS.get(offeringId * 1000 + takeLevel);
        java.util.List<emu.grasscutter.data.common.ItemParamData> granted = new java.util.ArrayList<>();
        if (row != null && row.rewardId > 0) {
            var reward = emu.grasscutter.data.GameData.getRewardDataMap().get(row.rewardId);
            if (reward != null && reward.getRewardItemList() != null) {
                granted.addAll(reward.getRewardItemList());
                player.getInventory()
                        .addItemParamDatas(
                                reward.getRewardItemList(),
                                emu.grasscutter.game.props.ActionReason.OfferingItem);
            }
        }
        markTaken(player, offeringId, takeLevel);
        sendTakeRewardRsp(player, offeringId, takeLevel, Retcode.RET_SUCC_VALUE, granted, requestHeader);
        syncOfferingNotify(player);
        Grasscutter.getLogger()
                .debug(
                        "OfferingHelper takeReward uid={} offeringId={} level={} items={}",
                        player.getUid(),
                        offeringId,
                        takeLevel,
                        granted.size());
        return true;
    }

    private static void sendTakeRewardRsp(
            Player player,
            int offeringId,
            int takeLevel,
            int retcode,
            java.util.List<emu.grasscutter.data.common.ItemParamData> items,
            byte[] requestHeader) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ProtoWire.writeUint32(out, TakeOfferingLevelRewardRsp.OFFERING_ID_FIELD_NUMBER, offeringId);
            ProtoWire.writeUint32(out, TakeOfferingLevelRewardRsp.TAKE_LEVEL_FIELD_NUMBER, takeLevel);
            ProtoWire.writeUint32Force(out, TakeOfferingLevelRewardRsp.RETCODE_FIELD_NUMBER, retcode);
            if (items != null) {
                for (var it : items) {
                    if (it == null || it.getId() <= 0 || it.getCount() <= 0) {
                        continue;
                    }
                    ProtoWire.writeBytes(
                            out,
                            TakeOfferingLevelRewardRsp.ITEM_LIST_FIELD_NUMBER,
                            wireItemParam(it.getId(), it.getCount()));
                }
            }
            sendRaw(player, PacketOpcodes.TakeOfferingLevelRewardRsp, out.toByteArray(), requestHeader);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingHelper sendTakeRewardRsp: {}", t.toString());
        }
    }

    /**
     * Fallback for when no handler is registered: accept only the known PlayerOfferingReq opcode so other
     * packets are not intercepted by mistake.
     */
    public static boolean tryHandleUnknownOfferingReq(Player player, int opcode, byte[] payload) {
        return tryHandleUnknownOfferingReq(player, opcode, payload, null);
    }

    public static boolean tryHandleUnknownOfferingReq(
            Player player, int opcode, byte[] payload, byte[] requestHeader) {
        if (player == null) {
            return false;
        }
        if (opcode == PacketOpcodes.TakeOfferingLevelRewardReq) {
            int offeringId = OFFERING_ORAIONOKAMI;
            int takeLevel = 0;
            if (payload != null && payload.length > 0) {
                try {
                    com.google.protobuf.CodedInputStream in =
                            com.google.protobuf.CodedInputStream.newInstance(payload);
                    while (!in.isAtEnd()) {
                        int tag = in.readTag();
                        int field = tag >>> 3;
                        if ((tag & 7) != 0) {
                            in.skipField(tag);
                            continue;
                        }
                        int v = in.readUInt32();
                        if (field == TakeOfferingLevelRewardReq.OFFERING_ID_FIELD_NUMBER) {
                            offeringId = v;
                        } else if (field == TakeOfferingLevelRewardReq.LEVEL_FIELD_NUMBER) {
                            takeLevel = v;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            return tryTakeLevelReward(player, offeringId, takeLevel, requestHeader);
        }
        if (opcode != PacketOpcodes.PlayerOfferingReq) {
            return false;
        }
        int offeringId = OFFERING_ORAIONOKAMI;
        if (payload != null && payload.length > 0 && payload.length <= 64) {
            try {
                com.google.protobuf.CodedInputStream in =
                        com.google.protobuf.CodedInputStream.newInstance(payload);
                while (!in.isAtEnd()) {
                    int tag = in.readTag();
                    if ((tag & 7) == 0) {
                        int v = in.readUInt32();
                        if (v >= 1 && v <= 22 && isKnownOfferingId(v)) {
                            offeringId = v;
                            break;
                        }
                    } else {
                        in.skipField(tag);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        Grasscutter.getLogger()
                .debug(
                        "OfferingHelper handle opcode {} as PlayerOfferingReq offeringId={}",
                        opcode,
                        offeringId);
        return tryLevelUp(player, offeringId, requestHeader);
    }

    private static boolean isKnownOfferingId(int id) {
        return GADGET_TO_OFFERING.containsValue(id) || id == OFFERING_ORAIONOKAMI;
    }

    /** Full sync of offering progress. */
    public static void syncOfferingNotify(Player player) {
        if (player == null || player.getSession() == null) {
            return;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean hasSakura = false;
            for (int offeringId : GADGET_TO_OFFERING.values().stream().distinct().sorted().toList()) {
                if (!isOfferingOpen(player, offeringId)) {
                    continue;
                }
                if (offeringId == OFFERING_ORAIONOKAMI) {
                    hasSakura = true;
                }
                ProtoWire.writeBytes(
                        out,
                        PlayerOfferingDataNotify.OFFERING_DATA_LIST_FIELD_NUMBER,
                        wireOfferingData(player, offeringId, false));
            }
            if (!hasSakura) {
                ProtoWire.writeBytes(
                        out,
                        PlayerOfferingDataNotify.OFFERING_DATA_LIST_FIELD_NUMBER,
                        wireOfferingData(player, OFFERING_ORAIONOKAMI, false));
            }
            sendRaw(player, PacketOpcodes.PlayerOfferingDataNotify, out.toByteArray(), null);
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper syncOfferingNotify: {}", t.toString());
        }
    }

    private static void sendRaw(Player player, int opcode, byte[] payload, byte[] requestHeader) {
        BasePacket pkt = new BasePacket(opcode);
        if (requestHeader != null && requestHeader.length > 0) {
            pkt.setHeader(requestHeader);
        }
        pkt.setData(payload);
        player.sendPacket(pkt);
    }

    private static void sendOfferingRsp(
            Player player,
            int offeringId,
            int retcode,
            java.util.List<emu.grasscutter.data.common.ItemParamData> items,
            byte[] requestHeader) {
        sendOfferingRsp(player, offeringId, retcode, items, requestHeader, false);
    }

    private static void sendOfferingRsp(
            Player player,
            int offeringId,
            int retcode,
            java.util.List<emu.grasscutter.data.common.ItemParamData> items,
            byte[] requestHeader,
            boolean isNewMaxLevel) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (items != null) {
                for (var it : items) {
                    if (it == null || it.getId() <= 0 || it.getCount() <= 0) {
                        continue;
                    }
                    ProtoWire.writeBytes(
                            out,
                            PlayerOfferingRsp.ITEM_LIST_FIELD_NUMBER,
                            wireItemParam(it.getId(), it.getCount()));
                }
            }
            ProtoWire.writeBytes(
                    out,
                    PlayerOfferingRsp.OFFERING_DATA_FIELD_NUMBER,
                    wireOfferingData(player, offeringId, isNewMaxLevel));
            // retcode is always written out, including 0
            ProtoWire.writeUint32Force(out, PlayerOfferingRsp.RETCODE_FIELD_NUMBER, retcode);
            sendRaw(player, PacketOpcodes.PlayerOfferingRsp, out.toByteArray(), requestHeader);
            Grasscutter.getLogger()
                    .debug(
                            "OfferingHelper Rsp uid={} offeringId={} level={} ret={} items={} op={}",
                            player.getUid(),
                            offeringId,
                            getLevel(player, offeringId),
                            retcode,
                            items == null ? 0 : items.size(),
                            PacketOpcodes.PlayerOfferingRsp);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingHelper sendOfferingRsp: {}", t.toString());
        }
    }

    private static void sendOfferingRsp(
            Player player,
            int offeringId,
            int retcode,
            java.util.List<emu.grasscutter.data.common.ItemParamData> items) {
        sendOfferingRsp(player, offeringId, retcode, items, null, false);
    }
    private static final ConcurrentHashMap<Integer, LevelRow> LEVEL_ROWS = new ConcurrentHashMap<>();
    private static volatile boolean levelsLoaded = false;

    private static final class LevelRow {
        final int offeringId;
        final int level;
        final int rewardId;
        final int consumeId;
        final int consumeCount;

        LevelRow(int offeringId, int level, int rewardId, int consumeId, int consumeCount) {
            this.offeringId = offeringId;
            this.level = level;
            this.rewardId = rewardId;
            this.consumeId = consumeId;
            this.consumeCount = consumeCount;
        }
    }

    private static void ensureLevelConfigs() {
        if (levelsLoaded) {
            return;
        }
        synchronized (LEVEL_ROWS) {
            if (levelsLoaded) {
                return;
            }
            try {
                // Same as everywhere else: follow the configured resource folder rather than
                // assuming it is called "resources".
                java.nio.file.Path p =
                        emu.grasscutter.utils.FileUtils.getExcelPath(
                                "OfferingLevelUpExcelConfigData.json");
                String json = java.nio.file.Files.readString(p);
                // Lightweight parse that does not depend on Gson field-name differences.
                var arr = new com.google.gson.JsonParser().parse(json).getAsJsonArray();
                for (var el : arr) {
                    var o = el.getAsJsonObject();
                    int oid = o.has("offeringId") ? o.get("offeringId").getAsInt() : 0;
                    int level = o.has("level") ? o.get("level").getAsInt() : 0;
                    int rewardId = o.has("rewardId") ? o.get("rewardId").getAsInt() : 0;
                    if (oid <= 0 || level <= 0) {
                        continue;
                    }
                    int consumeId = 0;
                    int consumeCount = 0;
                    if (o.has("consumeItemConfigVec") && o.get("consumeItemConfigVec").isJsonArray()) {
                        for (var c : o.getAsJsonArray("consumeItemConfigVec")) {
                            if (!c.isJsonObject()) {
                                continue;
                            }
                            var co = c.getAsJsonObject();
                            int id = co.has("id") ? co.get("id").getAsInt() : 0;
                            int cnt = co.has("count") ? co.get("count").getAsInt() : 0;
                            if (id > 0 && cnt > 0) {
                                consumeId = id;
                                consumeCount = cnt;
                                break;
                            }
                        }
                    }
                    LEVEL_ROWS.put(oid * 1000 + level, new LevelRow(oid, level, rewardId, consumeId, consumeCount));
                }
                levelsLoaded = true;
                Grasscutter.getLogger()
                        .debug("OfferingHelper loaded {} level rows", LEVEL_ROWS.size());
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("OfferingHelper load levels failed: {}", t.toString());
            }
        }
    }
}
