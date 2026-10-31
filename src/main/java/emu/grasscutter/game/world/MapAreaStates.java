package emu.grasscutter.game.world;

import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.FileUtils;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Map area states (Inazuma mist, Sumeru sandstorms, Natlan erosion zones and the like) from
 * MapAreaConfigData. A quest switches one with QUEST_EXEC_CHANGE_MAP_AREA_STATE; any area no quest
 * has touched stays open, as before.
 */
public final class MapAreaStates {
    private static volatile List<Integer> ids;

    private MapAreaStates() {}

    /** The configured map area ids, read once from MapAreaConfigData. */
    public static List<Integer> ids() {
        var loaded = ids;
        if (loaded == null) {
            synchronized (MapAreaStates.class) {
                loaded = ids;
                if (loaded == null) ids = loaded = load();
            }
        }
        return loaded;
    }

    /** Each configured area with the player's state, open unless a quest closed it. */
    public static Map<Integer, Boolean> forPlayer(Player player) {
        var states = new TreeMap<Integer, Boolean>();
        var overrides = player == null ? Map.<Integer, Boolean>of() : player.getMapAreaStates();
        for (int id : ids()) states.put(id, overrides.getOrDefault(id, true));
        // A quest may name an area the table does not list; keep what it set.
        overrides.forEach(states::putIfAbsent);
        return states;
    }

    private static List<Integer> load() {
        var out = new ArrayList<Integer>();
        try {
            var path = FileUtils.getExcelPath("MapAreaConfigData.json");
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    for (var row : JsonParser.parseReader(reader).getAsJsonArray()) {
                        var id = row.getAsJsonObject().get("id");
                        if (id != null) out.add(id.getAsInt());
                    }
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Failed to read MapAreaConfigData", e);
        }
        Collections.sort(out);
        Grasscutter.getLogger().debug("[MapArea] loaded {} map areas", out.size());
        return List.copyOf(out);
    }
}
