package emu.grasscutter.game.tower;

import dev.morphia.annotations.Entity;
import java.util.*;

@Entity
public class TowerLevelRecord {
    /** floorId in config */
    private int floorId;
    /** LevelId - Stars */
    private Map<Integer, Integer> passedLevelMap;

    private int floorStarRewardProgress;

    public TowerLevelRecord() {}

    public TowerLevelRecord(int floorId) {
        this.floorId = floorId;
        this.passedLevelMap = new HashMap<>();
        this.floorStarRewardProgress = 0;
    }

    public TowerLevelRecord setLevelStars(int levelId, int stars) {
        if (passedLevelMap == null) {
            passedLevelMap = new HashMap<>();
        }
        // Drop string-key duplicates Morphia may have hydrated beside the int key.
        passedLevelMap.entrySet().removeIf(e -> e.getKey() != null && e.getKey() != levelId
                && String.valueOf(levelId).equals(String.valueOf(e.getKey())));
        passedLevelMap.put(levelId, stars);
        return this;
    }

    public int getLevelStars(int levelId) {
        if (passedLevelMap == null || passedLevelMap.isEmpty()) {
            return 0;
        }
        Integer direct = passedLevelMap.get(levelId);
        if (direct != null) {
            return direct;
        }
        for (var entry : passedLevelMap.entrySet()) {
            if (entry.getKey() != null
                    && String.valueOf(levelId).equals(String.valueOf(entry.getKey()))
                    && entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return 0;
    }

    public int getStarCount() {
        return passedLevelMap.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getFloorId() {
        return floorId;
    }

    public void setFloorId(int floorId) {
        this.floorId = floorId;
    }

    public Map<Integer, Integer> getPassedLevelMap() {
        return passedLevelMap;
    }

    public void setPassedLevelMap(Map<Integer, Integer> passedLevelMap) {
        this.passedLevelMap = passedLevelMap;
    }

    public int getFloorStarRewardProgress() {
        return floorStarRewardProgress;
    }

    public void setFloorStarRewardProgress(int floorStarRewardProgress) {
        this.floorStarRewardProgress = floorStarRewardProgress;
    }
}
