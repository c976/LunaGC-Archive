package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.managers.mapmark.MapMark;
import emu.grasscutter.game.managers.mapmark.MapMarksManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.MapMarkFromTypeOuterClass.MapMarkFromType;
import emu.grasscutter.net.proto.MapMarkPointTypeOuterClass.MapMarkPointType;
import emu.grasscutter.server.packet.send.PacketMarkMapRsp;
import emu.grasscutter.server.packet.send.PacketOneoffGatherPointDetectorDataNotify;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Substitute for the inventory "go gather" action.
 * Official material-icon pins are drawn by the client from Scene gather data (not MapMarks).
 * PS builds often lack that data for Natlan, so the button does nothing.
 * We place named MapMarks + optional detector hints from specialty_materials_points.json.
 */
public final class SpecialtyMaterialTrackHelper {
    private static final Path MATERIALS_FILE = Path.of("data", "specialty_materials_points.json");
    private static final String MARK_PREFIX = "Specialty:";
    private static final String LEGACY_PREFIX = "mat:";
    private static volatile Map<Integer, List<Position>> byItemId;

    private SpecialtyMaterialTrackHelper() {}

    private static void ensureLoaded() {
        if (byItemId != null) return;
        synchronized (SpecialtyMaterialTrackHelper.class) {
            if (byItemId != null) return;
            Map<Integer, List<Position>> map = new HashMap<>();
            try {
                if (Files.isRegularFile(MATERIALS_FILE)) {
                    String raw = Files.readString(MATERIALS_FILE, StandardCharsets.UTF_8);
                    JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject o = arr.get(i).getAsJsonObject();
                        if (!o.has("itemId")) continue;
                        int itemId = o.get("itemId").getAsInt();
                        float x = o.get("x").getAsFloat();
                        float y = o.has("y") ? o.get("y").getAsFloat() : 0f;
                        float z = o.get("z").getAsFloat();
                        map.computeIfAbsent(itemId, k -> new ArrayList<>()).add(new Position(x, y, z));
                    }
                }
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("SpecialtyMaterialTrackHelper load fail: {}", t.toString());
            }
            byItemId = map;
            Grasscutter.getLogger()
                    .info(
                            "SpecialtyMaterialTrackHelper loaded {} item types, {} points",
                            map.size(),
                            map.values().stream().mapToInt(List::size).sum());
        }
    }

    private static String displayName(int itemId) {
        switch (itemId) {
            case 101253:
                return "Purpurbloom";
            case 101255:
                return "Dracolite";
            case 101254:
                return "Skysplit Gem";
            case 101235:
                return "Lumidouce Bell";
            case 101268:
                return "Icegrass";
            case 101269:
                return "Pine Amber";
            case 101231:
                return "Clearwater Grass";
            case 101232:
                return "Pale Crystal Shell";
            default:
                return "Material " + itemId;
        }
    }

    private static boolean isSpecialtyMark(String name) {
        return name != null
                && (name.startsWith(MARK_PREFIX)
                        || name.startsWith(LEGACY_PREFIX)
                        || name.startsWith("Purpurbloom")
                        || name.startsWith("Dracolite")
                        || name.startsWith("Lumidouce")
                        || name.startsWith("Icegrass")
                        || name.startsWith("Pine")
                        || name.startsWith("Skysplit")
                        || name.startsWith("Clearwater")
                        || name.startsWith("Pale")
                        || name.startsWith("Material")
                        // Legacy marks created before the rename still carry the old Chinese names.
                        // Kept as escapes so /trackmat clear can still remove them.
                        || name.startsWith("\u67af\u53f6")
                        || name.startsWith("\u7409\u9cde")
                        || name.startsWith("\u67d4\u706f")
                        || name.startsWith("\u51ac\u65e5")
                        || name.startsWith("\u677e\u8102")
                        || name.startsWith("\u88c2\u7a7a")
                        || name.startsWith("\u8309\u6d01")
                        || name.startsWith("\u82cd\u6676")
                        || name.startsWith("\u6750\u6599"));
    }

    public static int resolveItemId(String token) {
        if (token == null || token.isBlank()) return 0;
        token = token.trim();
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
        }
        String t = token.toLowerCase(Locale.ROOT);
        Map<String, Integer> aliases = new HashMap<>();
        aliases.put("purpurbloom", 101253);
        aliases.put("withering", 101253);
        aliases.put("dracolite", 101255);
        aliases.put("skysplit", 101254);
        aliases.put("lumidouce", 101235);
        aliases.put("icelea", 101268);
        aliases.put("pineamber", 101269);
        aliases.put("pine", 101269);
        Integer id = aliases.get(t);
        if (id != null) return id;
        for (Map.Entry<String, Integer> e : aliases.entrySet()) {
            if (e.getKey().contains(t) || t.contains(e.getKey())) return e.getValue();
        }
        return 0;
    }

    public static int track(Player player, int itemId) {
        if (player == null || itemId <= 0) return 0;
        ensureLoaded();
        List<Position> points = byItemId.get(itemId);
        if (points == null || points.isEmpty()) return 0;

        MapMarksManager mgr = player.getMapMarksManager();
        Map<String, MapMark> marks = mgr.getMapMarks();

        Iterator<Map.Entry<String, MapMark>> it = marks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MapMark> e = it.next();
            if (e.getValue() != null && isSpecialtyMark(e.getValue().getName())) {
                it.remove();
            }
        }

        String baseName = displayName(itemId);
        int added = 0;
        for (Position pos : points) {
            if (marks.size() >= MapMarksManager.mapMarkMaxCount) break;
            String name = baseName + "#" + (added + 1);
            MapMark mark =
                    new MapMark(
                            3,
                            name,
                            pos,
                            MapMarkPointType.MapMarkPointType_COLLECTION,
                            MapMarkFromType.MapMarkFromType_NOE,
                            0,
                            0);
            marks.put(mgr.getMapMarkKey(pos), mark);
            added++;
        }

        try {
            mgr.save();
        } catch (Throwable ignored) {
        }
        player.getSession().send(new PacketMarkMapRsp(marks));
        // Detector notify carries materialId - client may show material-icon style hints.
        try {
            player.getSession().send(new PacketOneoffGatherPointDetectorDataNotify(itemId, points));
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("detector notify failed: {}", t.toString());
        }
        Grasscutter.getLogger()
                .info(
                        "SpecialtyMaterialTrackHelper uid={} itemId={} name={} marked={}",
                        player.getUid(),
                        itemId,
                        baseName,
                        added);
        return added;
    }

    public static int clear(Player player) {
        if (player == null) return 0;
        MapMarksManager mgr = player.getMapMarksManager();
        Map<String, MapMark> marks = mgr.getMapMarks();
        int removed = 0;
        Iterator<Map.Entry<String, MapMark>> it = marks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MapMark> e = it.next();
            if (e.getValue() != null && isSpecialtyMark(e.getValue().getName())) {
                it.remove();
                removed++;
            }
        }
        try {
            mgr.save();
        } catch (Throwable ignored) {
        }
        player.getSession().send(new PacketMarkMapRsp(marks));
        try {
            player.getSession().send(new PacketOneoffGatherPointDetectorDataNotify(0, List.of()));
        } catch (Throwable ignored) {
        }
        return removed;
    }
}
