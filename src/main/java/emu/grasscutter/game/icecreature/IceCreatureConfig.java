package emu.grasscutter.game.icecreature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.FileUtils;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.io.Reader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Ice Creature (Icemaking) tables, read on first use.
 *
 * <p>Field names are still obfuscated in 7.1:
 *
 * <pre>
 * IceCreatureExcelConfigData      id = creature type, EIJNPJAALDL = build material cost, gadgetIdList
 * IceCreatureSkinExcelConfigData  gadgetId, AFLDPGPHHJN = the creature type it is a skin of
 * IceCreatureAreaExcelConfigData  HEENPDGJJFI = level-1 area, AFLDPGPHHJN = type, maxNum
 * IceCreatureMiscExcelConfigData  sceneIdList = scenes that allow building
 * ConstValueExcelConfigData       CONST_VALUE_SNEZHNAYA_ICE_CREATURE_MATERIAL_LIMIT
 * </pre>
 */
public final class IceCreatureConfig {
    private static final String TYPE = "AFLDPGPHHJN";
    private static final String AREA = "HEENPDGJJFI";
    private static final String COST = "EIJNPJAALDL";

    /** Used when ConstValueExcelConfigData has no material limit (its 7.1 value). */
    static final float DEFAULT_MATERIAL_LIMIT = 400f;

    private static volatile IceCreatureConfig instance;

    private final Int2IntOpenHashMap typeByGadget = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap costByType = new Int2IntOpenHashMap();
    private final Map<Long, Integer> maxByAreaAndType = new HashMap<>();
    private final Set<Integer> buildScenes = new IntOpenHashSet();
    /** Player-buildable types (rows with an {@code order}) and their gadgets, by type id. */
    private final java.util.SortedMap<Integer, java.util.List<Integer>> buildableGadgetsByType =
            new java.util.TreeMap<>();
    private float materialLimit = DEFAULT_MATERIAL_LIMIT;

    IceCreatureConfig() {}

    public static IceCreatureConfig get() {
        var config = instance;
        if (config == null) {
            synchronized (IceCreatureConfig.class) {
                config = instance;
                if (config == null) instance = config = load();
            }
        }
        return config;
    }

    /** The creature type a gadget builds, or 0 when it is no Ice Creature. */
    public int typeOf(int gadgetId) {
        return this.typeByGadget.getOrDefault(gadgetId, 0);
    }

    /** Build material a type costs. */
    public int costOf(int type) {
        return this.costByType.getOrDefault(type, 0);
    }

    /** How many of a type one player may keep in a level-1 area; 0 when the area allows none. */
    public int maxInArea(int areaId, int type) {
        return this.maxByAreaAndType.getOrDefault(key(areaId, type), 0);
    }

    /** Whether any limit row exists for this area, i.e. whether building is allowed there at all. */
    public boolean isBuildArea(int areaId) {
        return this.maxByAreaAndType.keySet().stream().anyMatch(k -> (int) (k >> 32) == areaId);
    }

    /** Types the build wheel offers, each with its gadgets (the first is the default look). */
    public java.util.SortedMap<Integer, java.util.List<Integer>> getBuildableGadgetsByType() {
        return this.buildableGadgetsByType;
    }

    public boolean isBuildScene(int sceneId) {
        return this.buildScenes.isEmpty() ? sceneId == 3 : this.buildScenes.contains(sceneId);
    }

    public float getMaterialLimit() {
        return this.materialLimit;
    }

    private static long key(int areaId, int type) {
        return ((long) areaId << 32) | (type & 0xFFFFFFFFL);
    }

    private static IceCreatureConfig load() {
        var config = new IceCreatureConfig();
        try {
            config.parseTypes(read("IceCreatureExcelConfigData.json"));
            config.parseSkins(read("IceCreatureSkinExcelConfigData.json"));
            config.parseAreas(read("IceCreatureAreaExcelConfigData.json"));
            config.parseMisc(read("IceCreatureMiscExcelConfigData.json"));
            config.parseConstValues(read("ConstValueExcelConfigData.json"));
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Failed to read the Ice Creature tables", e);
        }
        Grasscutter.getLogger()
                .debug(
                        "[IceCreature] loaded {} gadgets, {} types, {} area limits, material limit {}",
                        config.typeByGadget.size(),
                        config.costByType.size(),
                        config.maxByAreaAndType.size(),
                        config.materialLimit);
        return config;
    }

    private static JsonElement read(String file) throws Exception {
        var path = FileUtils.getExcelPath(file);
        if (!Files.isRegularFile(path)) {
            Grasscutter.getLogger().debug("[IceCreature] {} missing at {}", file, path);
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader);
        }
    }

    void parseTypes(JsonElement root) {
        for (JsonObject row : rows(root)) {
            int type = intOf(row, "id");
            if (type == 0) continue;
            this.costByType.put(type, intOf(row, COST));
            var gadgets = row.get("gadgetIdList");
            var list = new java.util.ArrayList<Integer>();
            if (gadgets != null && gadgets.isJsonArray()) {
                for (JsonElement gadget : gadgets.getAsJsonArray()) {
                    this.typeByGadget.put(gadget.getAsInt(), type);
                    list.add(gadget.getAsInt());
                }
            }
            // Only the wheel's types carry an order; the rest are placed by quests and activities.
            if (row.has("order") && !list.isEmpty()) this.buildableGadgetsByType.put(type, list);
        }
    }

    void parseSkins(JsonElement root) {
        for (JsonObject row : rows(root)) {
            int gadget = intOf(row, "gadgetId");
            int type = intOf(row, TYPE);
            if (gadget != 0 && type != 0) this.typeByGadget.put(gadget, type);
        }
    }

    void parseAreas(JsonElement root) {
        for (JsonObject row : rows(root)) {
            int area = intOf(row, AREA);
            int type = intOf(row, TYPE);
            if (area != 0 && type != 0) this.maxByAreaAndType.put(key(area, type), intOf(row, "maxNum"));
        }
    }

    void parseMisc(JsonElement root) {
        for (JsonObject row : rows(root)) {
            var scenes = row.get("sceneIdList");
            if (scenes == null || !scenes.isJsonArray()) continue;
            for (JsonElement scene : scenes.getAsJsonArray()) this.buildScenes.add(scene.getAsInt());
        }
    }

    void parseConstValues(JsonElement root) {
        for (JsonObject row : rows(root)) {
            if (!row.has("name")
                    || !"CONST_VALUE_SNEZHNAYA_ICE_CREATURE_MATERIAL_LIMIT".equals(row.get("name").getAsString())) {
                continue;
            }
            var values = row.get("value");
            if (values != null && values.isJsonArray() && !values.getAsJsonArray().isEmpty()) {
                try {
                    this.materialLimit = Float.parseFloat(values.getAsJsonArray().get(0).getAsString());
                } catch (NumberFormatException ignored) {
                    // keep the default
                }
            }
        }
    }

    private static Iterable<JsonObject> rows(JsonElement root) {
        var out = new java.util.ArrayList<JsonObject>();
        if (root == null || !root.isJsonArray()) return out;
        for (JsonElement row : (JsonArray) root) if (row.isJsonObject()) out.add(row.getAsJsonObject());
        return out;
    }

    private static int intOf(JsonObject row, String key) {
        var value = row.get(key);
        return value == null || !value.isJsonPrimitive() ? 0 : value.getAsInt();
    }
}
