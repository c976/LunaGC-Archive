package emu.grasscutter.data.common;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DailyDungeonData;
import emu.grasscutter.game.world.Position;
import it.unimi.dsi.fastutil.ints.*;
import lombok.*;

/** Aligned with remote LunaGC PointData (expose all weekday dungeon rotations). */
public final class PointData {
    private static final int[] ALL_DAYS = {1, 2, 3, 4, 5, 6, 7};

    @Getter @Setter private int id;
    @Getter private int areaId;

    private String $type;
    /** Native point subtype (e.g. TOWER/PORTAL), distinct from $type. */
    @com.google.gson.annotations.SerializedName("type")
    @Getter private String pointType;
    @Getter private String mapVisibility;
    @Getter private Position tranPos;
    @Getter private Position pos;
    @Getter private Position rot;
    @Getter private Position tranRot;
    @Getter private Position size;
    @Getter private boolean forbidSimpleUnlock;
    @Getter private boolean unlocked;
    @Getter private boolean groupLimit;
    @Getter private int gadgetId;

    @SerializedName(
            value = "maxSpringVolume",
            alternate = {"MJNAILKLGHG"})
    @Getter
    private int maxSpringVolume;

    @Getter private boolean isModelHidden;

    /** Goddess tip NPC. Nod-Krai City7 points store this as {@code FLLJPPMDODA}. */
    @SerializedName(
            value = "npcId",
            alternate = {"FLLJPPMDODA"})
    @Getter
    private int npcId;

    /** Older points use {@code damageRatio}; Nod-Krai obfuscated as {@code MEGMIMEDODJ}. */
    @SerializedName(
            value = "damageRatio",
            alternate = {"MEGMIMEDODJ"})
    @Getter
    private String damageRatio;

    @SerializedName(
            value = "cutsceneList",
            alternate = {"FOPHEKONPDL"})
    @Getter
    private int[] cutsceneList;

    @SerializedName(
            value = "dungeonIds",
            alternate = {"JHHFPGJNMIN"})
    @Getter
    private int[] dungeonIds;

    @SerializedName(
            value = "dungeonRandomList",
            alternate = {"GLEKJMEEOMH"})
    @Getter
    private int[] dungeonRandomList;

    @SerializedName(
            value = "groupIDs",
            alternate = {"HFOBOOHKBGF"})
    @Getter
    private int[] groupIDs;

    @SerializedName(
            value = "tranSceneId",
            alternate = {"JHBICGBAPIH"})
    @Getter
    @Setter
    private int tranSceneId;

    public String getType() {
        return $type;
    }

    public void updateDailyDungeon() {
        if (this.dungeonRandomList == null || this.dungeonRandomList.length == 0) {
            return;
        }

        IntSet seen = new IntOpenHashSet();
        IntList newDungeons = new IntArrayList();

        for (int randomId : this.dungeonRandomList) {
            DailyDungeonData data = GameData.getDailyDungeonDataMap().get(randomId);
            if (data == null) {
                continue;
            }
            for (int day : ALL_DAYS) {
                for (int d : data.getDungeonsByDay(day)) {
                    if (seen.add(d)) {
                        newDungeons.add(d);
                    }
                }
            }
        }

        this.dungeonIds = newDungeons.toIntArray();
    }
}
