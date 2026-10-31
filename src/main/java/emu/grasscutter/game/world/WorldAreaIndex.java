package emu.grasscutter.game.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.FileUtils;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Level-1 world area lookup by position, from {@code BinOutput/Scene/WorldArea/scene<N>_worldArea.json}.
 *
 * <p>The client reports its area only through EnterWorldAreaReq, which arrives after the move that
 * crossed the border. Region-gated server state needs the answer for an arbitrary position, so this
 * reads the same outlines the client uses. The file's field names are still obfuscated in 7.1:
 *
 * <pre>
 * OBKNAJCIHEA[]            one entry per level-1 area
 *   LKBLPFHOAFB            the level-1 area
 *     BBLLBIOLMHP          areaID1
 *     JLFEAGHGCLP          its outline
 *       BFLLDPOAJGD[]      polygon points {_x, _z}
 * </pre>
 */
public final class WorldAreaIndex {
    private static final String AREA_LIST = "OBKNAJCIHEA";
    private static final String LEVEL1 = "LKBLPFHOAFB";
    private static final String AREA_ID = "BBLLBIOLMHP";
    private static final String OUTLINE = "JLFEAGHGCLP";
    private static final String POINTS = "BFLLDPOAJGD";

    /** Missing or unreadable scenes cache an empty index so the file is not re-read on every move. */
    private static final Map<Integer, WorldAreaIndex> CACHE = new ConcurrentHashMap<>();

    private final List<Area> areas;

    WorldAreaIndex(List<Area> areas) {
        this.areas = areas;
    }

    public static WorldAreaIndex forScene(int sceneId) {
        return CACHE.computeIfAbsent(sceneId, WorldAreaIndex::load);
    }

    /** Whether outline data was found for this scene at all. */
    public boolean isEmpty() {
        return this.areas.isEmpty();
    }

    /** The level-1 area containing (x, z), or 0 when the point lies in none of them. */
    public int level1AreaAt(float x, float z) {
        for (Area area : this.areas) {
            if (area.contains(x, z)) return area.id;
        }
        return 0;
    }

    private static WorldAreaIndex load(int sceneId) {
        var path = FileUtils.getResourcePath("BinOutput/Scene/WorldArea/scene" + sceneId + "_worldArea.json");
        if (!Files.isRegularFile(path)) {
            Grasscutter.getLogger().debug("[WorldArea] no outline data for scene {} at {}", sceneId, path);
            return new WorldAreaIndex(List.of());
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            var index = parse(JsonParser.parseReader(reader));
            Grasscutter.getLogger()
                    .debug("[WorldArea] loaded {} level-1 areas for scene {}", index.areas.size(), sceneId);
            return index;
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Failed to read world areas for scene {}", sceneId, e);
            return new WorldAreaIndex(List.of());
        }
    }

    static WorldAreaIndex parse(JsonElement root) {
        var areas = new ArrayList<Area>();
        if (root == null || !root.isJsonObject()) return new WorldAreaIndex(areas);
        JsonArray list = asArray(root.getAsJsonObject().get(AREA_LIST));
        if (list == null) return new WorldAreaIndex(areas);

        for (JsonElement entry : list) {
            if (!entry.isJsonObject()) continue;
            JsonObject level1 = asObject(entry.getAsJsonObject().get(LEVEL1));
            if (level1 == null || !level1.has(AREA_ID)) continue;
            JsonObject outline = asObject(level1.get(OUTLINE));
            JsonArray points = outline == null ? null : asArray(outline.get(POINTS));
            if (points == null || points.size() < 3) continue;

            float[] xs = new float[points.size()];
            float[] zs = new float[points.size()];
            int n = 0;
            for (JsonElement point : points) {
                if (!point.isJsonObject()) continue;
                JsonObject p = point.getAsJsonObject();
                if (!p.has("_x") || !p.has("_z")) continue;
                xs[n] = p.get("_x").getAsFloat();
                zs[n] = p.get("_z").getAsFloat();
                n++;
            }
            if (n >= 3) areas.add(new Area(level1.get(AREA_ID).getAsInt(), xs, zs, n));
        }
        return new WorldAreaIndex(areas);
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    static final class Area {
        final int id;
        private final float[] xs;
        private final float[] zs;
        private final int n;
        private final float minX, maxX, minZ, maxZ;

        Area(int id, float[] xs, float[] zs, int n) {
            this.id = id;
            this.xs = xs;
            this.zs = zs;
            this.n = n;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                minX = Math.min(minX, xs[i]);
                maxX = Math.max(maxX, xs[i]);
                minZ = Math.min(minZ, zs[i]);
                maxZ = Math.max(maxZ, zs[i]);
            }
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        /** Even-odd ray cast, after a bounding-box reject that settles almost every area. */
        boolean contains(float x, float z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
            boolean inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                if ((zs[i] > z) != (zs[j] > z)
                        && x < (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                    inside = !inside;
                }
            }
            return inside;
        }
    }
}
