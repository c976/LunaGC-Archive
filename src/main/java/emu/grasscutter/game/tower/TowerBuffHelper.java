package emu.grasscutter.game.tower;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tower.TowerLevelData;
import java.util.*;

/** Rolls chamber buff offerings from {@code towerBuffConfigStrList} excel pools. */
public final class TowerBuffHelper {
    private static final List<Integer> FALLBACK_BUFFS = List.of(4, 28, 18);

    private TowerBuffHelper() {}

    public static List<Integer> rollBuffOfferings(TowerLevelData levelData, int scheduleId) {
        if (levelData == null
                || levelData.getTowerBuffConfigStrList() == null
                || levelData.getTowerBuffConfigStrList().isEmpty()) {
            return new ArrayList<>(FALLBACK_BUFFS);
        }

        var rng = new Random(Objects.hash(scheduleId, levelData.getId()));
        var offerings = new ArrayList<Integer>(3);
        for (String pool : levelData.getTowerBuffConfigStrList()) {
            if (offerings.size() >= 3) break;
            int picked = pickWeighted(pool, rng);
            if (picked > 0) {
                offerings.add(picked);
            }
        }

        while (offerings.size() < 3) {
            offerings.add(FALLBACK_BUFFS.get(offerings.size()));
        }
        return offerings;
    }

    private static int pickWeighted(String pool, Random rng) {
        if (pool == null || pool.isBlank()) return 0;

        var entries = new ArrayList<int[]>();
        int total = 0;
        for (String part : pool.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(':');
            if (colon <= 0) continue;
            try {
                int buffId = Integer.parseInt(trimmed.substring(0, colon));
                int weight = Integer.parseInt(trimmed.substring(colon + 1));
                if (buffId <= 0 || weight <= 0) continue;
                entries.add(new int[] {buffId, weight});
                total += weight;
            } catch (NumberFormatException ignored) {
                // Skip malformed tokens.
            }
        }
        if (entries.isEmpty() || total <= 0) return 0;

        int roll = rng.nextInt(total);
        for (int[] entry : entries) {
            roll -= entry[1];
            if (roll < 0) return entry[0];
        }
        return entries.get(entries.size() - 1)[0];
    }

    public static boolean isValidOffering(int towerBuffId, List<Integer> offerings) {
        return towerBuffId > 0 && offerings != null && offerings.contains(towerBuffId);
    }

    public static int getAbilityBuffId(int towerBuffId) {
        var data = GameData.getTowerBuffDataMap().get(towerBuffId);
        return data != null ? data.getBuffId() : 0;
    }
}
