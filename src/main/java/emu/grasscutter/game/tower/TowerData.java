package emu.grasscutter.game.tower;

import dev.morphia.annotations.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
public class TowerData {
    /** the floor players chose */
    int currentFloorId;

    int currentLevel;
    @Transient int currentLevelId;

    /** floorId - Record */
    Map<Integer, TowerLevelRecord> recordMap;

    /**
     * Last Spiral Abyss schedule id whose progress we are holding. When the live rotation changes,
     * schedule floors (9-12) are wiped; entrance floors stay.
     */
    int lastScheduleId;

    /**
     * This period's skip start floor index (9 / 10 / 11). From 5.1+: full-star floor 12 last period
     * → 11; full-star floor 11 → 10; else 9.
     */
    int skipToFloorIndex = 9;

    /** {@code TowerAllDataRsp._TowerSkipFloorState} ordinal for this period. */
    int skipFloorState;

    /** Item id → count granted as skipped-floor chamber treasure (shown in TowerAllDataRsp). */
    Map<Integer, Integer> skipFloorGrantedRewards;

    @Transient int entryScene;

    /**
     * Abyss teams selected for the current run (avatar guids). Persisted so a forced reconnect can
     * rebuild the temporary team instead of falling back to the overworld party.
     */
    List<List<Long>> abyssTeamGuids = new ArrayList<>();

    /** Which temporary team half was active when the session dropped. */
    int abyssTempTeamIndex = -1;

    /** When true, login should restore {@link #abyssTeamGuids} instead of dumping the player outside. */
    boolean resumeAbyssOnLogin;

    /** Buff cards offered for the current chamber; cleared when the chamber changes. */
    @Transient List<Integer> currentBuffOfferings = new ArrayList<>();

    /** Player's buff pick for the current chamber, or 0 if none yet. */
    @Transient int selectedTowerBuffId;
}
