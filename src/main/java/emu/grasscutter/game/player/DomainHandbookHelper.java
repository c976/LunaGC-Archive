package emu.grasscutter.game.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.packet.send.PacketGetDailyDungeonEntryInfoRsp;
import emu.grasscutter.server.packet.send.PacketGetSceneAreaRsp;
import emu.grasscutter.server.packet.send.PacketSceneAreaUnlockNotify;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import emu.grasscutter.server.packet.send.SceneAreaVisibility;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adventurer Handbook domain list sync (ported from the known-good 6.6 DomainHandbook patch).
 *
 * <p>Synchronizes native handbook entries and already-unlocked map points. A handbook listing
 * never discovers a dungeon entrance or changes the player's permanent unlock state.
 */
public final class DomainHandbookHelper {
    private static final Path CONFIG_PATH =
            Paths.get("resources", "ExcelBinOutput", "DungeonEntryExcelConfigData.json");
    private static final Path SCENE3_POINTS =
            Paths.get("resources", "BinOutput", "Scene", "Point", "scene3_point.json");

    private static volatile boolean loaded;
    private static final Map<Integer, List<Integer>> HANDBOOK_ENTRIES = new HashMap<>();
    private static final Map<Integer, Set<Integer>> HANDBOOK_AREAS = new HashMap<>();
    private static final Set<Integer> DEFERRED_DONE = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Long> LAST_FULL_SYNC_MS = new ConcurrentHashMap<>();
    private static final long FULL_SYNC_COOLDOWN_MS = 30_000L;

    private DomainHandbookHelper() {}

    public static void onPlayerLogin(Player player) {
        if (player == null) {
            return;
        }
        try {
            ensureLoaded();
            DEFERRED_DONE.remove(player.getUid());
            LAST_FULL_SYNC_MS.remove(player.getUid());
            scheduleDeferredSync(player);
            Grasscutter.getLogger()
                    .debug(
                            "DomainHandbook login-prepare uid={} entries={} areas={}",
                            player.getUid(),
                            HANDBOOK_ENTRIES.values().stream().mapToInt(List::size).sum(),
                            HANDBOOK_AREAS.values().stream().mapToInt(Set::size).sum());
            // Investigation chapters - same login window as the domain unlock.
            try {
                InvestigationHandbookHelper.onPlayerLogin(player);
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("InvestigationHandbook from DomainHandbook: {}", t.toString());
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("DomainHandbook login failed: {}", t.toString());
        }
    }

    public static void onEnterScene(Player player, int sceneId) {
        if (player == null || sceneId != 3) {
            return;
        }
        try {
            ensureLoaded();
            syncToClient(player, "enter-scene");
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("DomainHandbook enter-scene failed: {}", t.toString());
        }
    }

    /** Client opens Adventurer Handbook to the domain tab (InteractDailyDungeonInfoNotify). */
    public static boolean onInteractDailyDungeon(Player player) {
        if (player == null || player.getSession() == null) {
            return false;
        }
        try {
            ensureLoaded();
            // Avoid re-sending GetScenePointRsp here (resets handbook scroll).
            // Handler sends both wire layouts after this returns.
            return true;
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("DomainHandbook interact failed: {}", t.toString());
            return false;
        }
    }

    public static void syncToClient(Player player, String reason) {
        if (player == null || player.getSession() == null) {
            return;
        }
        ensureLoaded();
        long now = System.currentTimeMillis();
        Long last = LAST_FULL_SYNC_MS.get(player.getUid());
        if (last != null
                && now - last < FULL_SYNC_COOLDOWN_MS
                && !"deferred".equals(reason)
                && !"enter-scene".equals(reason)) {
            PacketGetDailyDungeonEntryInfoRsp.sendBothLayouts(player, 3);
            return;
        }
        LAST_FULL_SYNC_MS.put(player.getUid(), now);
        int points = 0;
        int areas = 0;
        for (Map.Entry<Integer, List<Integer>> entry : HANDBOOK_ENTRIES.entrySet()) {
            int sceneId = entry.getKey();
            List<Integer> pointIds = entry.getValue();
            Set<Integer> areaIds = HANDBOOK_AREAS.getOrDefault(sceneId, Set.of());
            if (!areaIds.isEmpty()) {
                var unlockedAreas = SceneAreaVisibility.forPlayer(player, sceneId);
                var alreadyAreas = new ArrayList<Integer>();
                for (int aid : areaIds) {
                    if (unlockedAreas.contains(aid)) alreadyAreas.add(aid);
                }
                if (!alreadyAreas.isEmpty()) {
                    player.getSession().send(new PacketSceneAreaUnlockNotify(sceneId, alreadyAreas));
                    areas += alreadyAreas.size();
                }
            }
            if (!pointIds.isEmpty()) {
                // Synchronize actual discoveries only. Handbook listing is not an unlock event.
                var already = alreadyUnlockedEntries(
                        pointIds,
                        player.getUnlockedScenePoints(sceneId),
                        player.getForceLockedScenePoints(sceneId));
                if (!already.isEmpty()) {
                    player.getSession().send(new PacketScenePointUnlockNotify(sceneId, already));
                    points += already.size();
                }
            }
            player.getSession().send(new PacketGetSceneAreaRsp(player, sceneId));
            PacketGetDailyDungeonEntryInfoRsp.sendBothLayouts(player, sceneId);
        }
        Grasscutter.getLogger()
                .debug(
                        "DomainHandbook sync uid={} reason={} points={} areas={}",
                        player.getUid(),
                        reason,
                        points,
                        areas);
    }

    /** Select stored discoveries without changing unlock state or releasing quest locks. */
    static List<Integer> alreadyUnlockedEntries(
            List<Integer> handbookEntries, Set<Integer> unlocked, Set<Integer> forceLocked) {
        var result = new ArrayList<Integer>();
        for (int pointId : handbookEntries) {
            if (unlocked.contains(pointId) && !forceLocked.contains(pointId)) {
                result.add(pointId);
            }
        }
        return result;
    }

    private static void scheduleDeferredSync(Player player) {
        final int uid = player.getUid();
        Thread t =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(4000L);
                                Player online = Grasscutter.getGameServer().getPlayerByUid(uid);
                                if (online == null || online.getSession() == null) {
                                    return;
                                }
                                if (!DEFERRED_DONE.add(uid)) {
                                    return;
                                }
                                syncToClient(online, "deferred");
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            } catch (Throwable e) {
                                Grasscutter.getLogger()
                                        .warn("DomainHandbook deferred: {}", e.toString());
                            }
                        },
                        "DomainHandbook-defer-" + uid);
        t.setDaemon(true);
        t.start();
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        HANDBOOK_ENTRIES.clear();
        HANDBOOK_AREAS.clear();
        Map<Integer, Integer> pointToArea = loadScene3PointAreas();
        try {
            if (Files.exists(CONFIG_PATH)) {
                JsonArray arr =
                        JsonParser.parseReader(Files.newBufferedReader(CONFIG_PATH)).getAsJsonArray();
                Set<String> seen = new HashSet<>();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("isShowInAdvHandbook")
                            || !obj.get("isShowInAdvHandbook").getAsBoolean()) {
                        continue;
                    }
                    if (!obj.has("sceneId") || !obj.has("dungeonEntryId")) {
                        continue;
                    }
                    int sceneId = obj.get("sceneId").getAsInt();
                    int pointId = obj.get("dungeonEntryId").getAsInt();
                    String key = sceneId + ":" + pointId;
                    if (!seen.add(key)) {
                        continue;
                    }
                    HANDBOOK_ENTRIES.computeIfAbsent(sceneId, k -> new ArrayList<>()).add(pointId);
                    Integer areaId = pointToArea.get(pointId);
                    if (areaId != null && areaId > 0) {
                        HANDBOOK_AREAS.computeIfAbsent(sceneId, k -> new HashSet<>()).add(areaId);
                    }
                }
            }
            if (HANDBOOK_ENTRIES.isEmpty()) {
                HANDBOOK_ENTRIES.put(3, List.of(48, 44, 38, 218, 271, 396, 503, 731, 848, 1085));
                HANDBOOK_AREAS.put(3, new HashSet<>(List.of(1, 2, 3, 4, 5, 6, 7)));
            }
            loaded = true;
            Grasscutter.getLogger()
                    .debug(
                            "DomainHandbook loaded entries={} areaGroups={}",
                            HANDBOOK_ENTRIES.values().stream().mapToInt(List::size).sum(),
                            HANDBOOK_AREAS.values().stream().mapToInt(Set::size).sum());
        } catch (Exception e) {
            Grasscutter.getLogger().warn("DomainHandbook load failed: {}", e.toString());
            loaded = true;
        }
    }

    private static Map<Integer, Integer> loadScene3PointAreas() {
        Map<Integer, Integer> map = new HashMap<>();
        if (!Files.exists(SCENE3_POINTS)) {
            return map;
        }
        try {
            JsonObject root =
                    JsonParser.parseReader(Files.newBufferedReader(SCENE3_POINTS)).getAsJsonObject();
            JsonObject points = root.getAsJsonObject("points");
            if (points == null) {
                return map;
            }
            for (Map.Entry<String, JsonElement> e : points.entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject p = e.getValue().getAsJsonObject();
                if (!p.has("$type") || !"DungeonEntry".equals(p.get("$type").getAsString())) {
                    continue;
                }
                if (!p.has("areaId")) {
                    continue;
                }
                try {
                    map.put(Integer.parseInt(e.getKey()), p.get("areaId").getAsInt());
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger().warn("DomainHandbook scene3 area load: {}", e.toString());
        }
        return map;
    }
}
