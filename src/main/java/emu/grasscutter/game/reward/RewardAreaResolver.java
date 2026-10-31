package emu.grasscutter.game.reward;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CityData;
import emu.grasscutter.data.excels.world.WorldAreaData;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/** Resolves a native world-area ID to its parent area and city for all reward rules. */
public final class RewardAreaResolver {
    private RewardAreaResolver() {}

    public record Area(int parentAreaId, int cityId) {
        public void addAttributes(Map<String, Object> attributes) {
            if (parentAreaId > 0) attributes.put("area", parentAreaId);
            if (cityId > 0) attributes.put("city", cityId);
        }
    }

    public static Area resolve(RewardContext context) {
        if (context == null) throw new IllegalArgumentException("Reward context required");
        int areaId = context.areaId();
        return resolve(context.sceneId(), areaId, context.sourceRegion(),
                areaId == 0 ? null : GameData.getWorldAreaDataMap().get(areaId),
                GameData.getCityDataMap().values());
    }

    /** An area ID is a native key, not necessarily a positive integer. */
    static Area resolve(int sceneId, int areaId, String sourceRegion,
                        WorldAreaData worldArea, Collection<CityData> cities) {
        int parent = 0;
        int cityId = 0;
        if (areaId != 0 && worldArea != null
                && worldArea.getId() == areaId && worldArea.getSceneId() == sceneId) {
            parent = worldArea.getParentArea();
            if (parent > 0) {
                for (CityData city : cities) {
                    if (city.getAreaIdVec() == null
                            || !city.getAreaIdVec().contains(parent)) continue;
                    if (cityId != 0 && cityId != city.getCityId()) {
                        // Do not assign either country when native ownership conflicts.
                        return new Area(0, 0);
                    }
                    cityId = city.getCityId();
                }
            }
        }

        // Imported scene objects can supply a source-backed region without area_id.
        // A verified native city always takes precedence over that fallback.
        if (cityId == 0 && sourceRegion != null && !sourceRegion.isBlank()) {
            cityId = cityFromSourceRegion(sourceRegion);
        }
        return new Area(parent, cityId);
    }

    private static int cityFromSourceRegion(String region) {
        return switch (region.toLowerCase(Locale.ROOT)) {
            case "mondstadt" -> 1;
            case "liyue" -> 2;
            case "inazuma" -> 3;
            case "sumeru", "sumeru36" -> 4;
            case "fontaine" -> 5;
            case "natlan" -> 6;
            case "nodkrai", "nod-krai", "nod_krai" -> 7;
            case "snezhnaya" -> 8;
            default -> 0;
        };
    }
}
