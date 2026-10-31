package emu.grasscutter.game.managers.blossom;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BlossomCampData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;

/**
 * Authoritative 7.1 ley-line camp locations and next-camp edges.
 * BlossomGroups.newGroupVec refers to the original Lua scene group, which has
 * separate gold/blue worktops at the same authored location.
 */
final class BlossomNativeCamps {
    record Key(int cityId, BlossomType kind) {}
    private record Source(int campId, int refreshId, SpawnDataEntry entry) {}

    private static final Gson GSON = new Gson();
    private final Map<Key, Integer> activeIds = new HashMap<>();
    private final Map<String, Source> resolved = new HashMap<>();
    private final Map<SpawnDataEntry, Source> byEntry = new IdentityHashMap<>();
    private final Set<Integer> unresolvedGroups = new HashSet<>();

    static int initialId(int cityId, BlossomType type, List<BlossomCampData> eligible) {
        if (cityId == 1) {
            // Both IDs exist in the native BlossomGroups graph. Preserve our initial
            // city-1 sources when replacing the incomplete legacy spawn candidate pool.
            return type.getCircleCampId();
        }
        if (eligible.isEmpty()) return 0;
        // No native starting-node selection has yet been recovered for other cities.
        // Choose distinct authored groups deterministically until that rule is proven.
        return eligible.get(type == BlossomType.BLUE && eligible.size() > 1 ? 1 : 0).getId();
    }

    List<SpawnDataEntry> select(Scene scene, int rank, Collection<Integer> unlockedAreas) {
        var rows = GameData.getBlossomCampDataMap();
        if (rows.isEmpty()) return List.of();
        var cities = GameData.getCityDataMap().values();
        var refreshes = new ArrayList<>(GameData.getBlossomRefreshExcelConfigDataMap().values());
        refreshes.sort(Comparator.comparingInt(BlossomRefreshExcelConfigData::getId));
        var selected = new ArrayList<SpawnDataEntry>();

        for (var refresh : refreshes) {
            BlossomType kind = switch (refresh.getRefreshType()) {
                case "BLOSSOM_REFRESH_SCOIN" -> BlossomType.GOLD;
                case "BLOSSOM_REFRESH_EXP" -> BlossomType.BLUE;
                default -> null;
            };
            if (kind == null || refresh.getRefreshCount() == 0
                    || !BlossomManager.refreshEligible(refresh, rank, unlockedAreas, cities)) {
                continue;
            }
            int city = refresh.getCityId();
            var cityData = GameData.getCityDataMap().get(city);
            if (cityData == null || cityData.getSceneId() != scene.getId()) continue;
            var eligible = rows.values().stream()
                    .filter(camp -> camp.getCityId() == city
                            && camp.getNewGroupVec() != null && !camp.getNewGroupVec().isEmpty()
                            && camp.getRefreshTypeVec() != null
                            && (camp.getRefreshTypeVec().contains(1) || camp.getRefreshTypeVec().contains(3))
                            && camp.getLimitLevel() <= rank)
                    .sorted(Comparator.comparingInt(BlossomCampData::getId))
                    .toList();
            if (eligible.isEmpty()) continue;

            Key key = new Key(city, kind);
            int initial = initialId(city, kind, eligible);
            int campId = activeIds.computeIfAbsent(key, ignored -> initial);
            BlossomCampData camp = rows.get(campId);
            if (camp == null || camp.getCityId() != city || camp.getLimitLevel() > rank) {
                campId = initial;
                activeIds.put(key, initial);
                camp = rows.get(campId);
            }
            if (camp == null) continue;
            var source = resolve(scene, camp, kind, refresh.getId());
            if (source == null) continue;
            if (selected.stream().anyMatch(existing ->
                    existing.getPos().computeDistance(source.entry().getPos()) < 2.5f)) {
                // A city can have one of each type, but never stack two interactive
                // gadgets at the same physical coordinates.
                continue;
            }
            selected.add(source.entry());
        }
        return selected;
    }

    private Source resolve(Scene scene, BlossomCampData camp, BlossomType kind, int refreshId) {
        String key = camp.getId() + ":" + kind.name();
        Source existing = resolved.get(key);
        if (existing != null) return existing;
        for (int groupId : camp.getNewGroupVec()) {
            if (unresolvedGroups.contains(groupId)) continue;
            var group = SceneGroup.of(groupId).load(scene.getId());
            if (group.gadgets == null) {
                if (unresolvedGroups.add(groupId)) {
                    Grasscutter.getLogger().warn("Ley-line original Lua group missing scene={} group={}",
                            scene.getId(), groupId);
                }
                continue;
            }
            var gadget = group.gadgets.values().stream()
                    .filter(item -> item.gadget_id == kind.getGadgetId())
                    .min(Comparator.comparingInt(item -> item.config_id))
                    .orElse(null);
            if (gadget == null) continue;
            String json = GSON.toJson(Map.of(
                    "sceneId", scene.getId(),
                    "groupId", groupId,
                    "spawns", List.of(Map.of(
                            "gadgetId", gadget.gadget_id,
                            "configId", gadget.config_id,
                            "pos", gadget.pos,
                            "rot", gadget.rot))));
            SpawnGroupEntry entry = JsonUtils.decode(json, SpawnGroupEntry.class);
            var source = entry.getSpawns().get(0);
            source.setGroup(entry);
            Source result = new Source(camp.getId(), refreshId, source);
            resolved.put(key, result);
            byEntry.put(source, result);
            return result;
        }
        return null;
    }

    boolean owns(SpawnDataEntry entry) {
        return byEntry.containsKey(entry);
    }

    int campId(SpawnDataEntry entry) {
        Source source = byEntry.get(entry);
        return source == null ? 0 : source.campId();
    }

    int refreshId(SpawnDataEntry entry) {
        Source source = byEntry.get(entry);
        return source == null ? 0 : source.refreshId();
    }

    boolean advance(SpawnDataEntry entry, int rank) {
        Source source = byEntry.get(entry);
        if (source == null) return false;
        var camp = GameData.getBlossomCampDataMap().get(source.campId());
        var next = camp.getNextCampIdVec();
        int nextId = 0;
        if (next != null) {
            for (int candidate : next) {
                var row = GameData.getBlossomCampDataMap().get(candidate);
                if (row != null && row.getCityId() == camp.getCityId()
                        && row.getLimitLevel() <= rank
                        && row.getNewGroupVec() != null && !row.getNewGroupVec().isEmpty()) {
                    nextId = candidate;
                    break;
                }
            }
        }
        if (nextId == 0) {
            var eligible = GameData.getBlossomCampDataMap().values().stream()
                    .filter(row -> row.getCityId() == camp.getCityId()
                            && row.getLimitLevel() <= rank
                            && row.getSectionId() == camp.getSectionId())
                    .sorted(Comparator.comparingInt(BlossomCampData::getId))
                    .toList();
            if (eligible.isEmpty()) return false;
            nextId = eligible.get(0).getId();
        }
        var kind = BlossomType.valueOf(entry.getGadgetId());
        activeIds.put(new Key(camp.getCityId(), kind), nextId);
        Grasscutter.getLogger().info("Ley-line original camp advanced city={} kind={} {} -> {}",
                camp.getCityId(), kind, source.campId(), nextId);
        return true;
    }
}
