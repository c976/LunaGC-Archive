package emu.grasscutter.data.binout;

import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.scripts.data.SceneGroup;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SceneNpcBornData {
    int sceneId;

    // 7.1 obfuscates this key. Read under its old name only, every file loaded as empty and was
    // skipped, so the server had no NPC spawn data for any scene.
    @SerializedName(value = "bornPosList", alternate = {"GJLIHAJHKEO"})
    List<SceneNpcBornEntry> bornPosList;

    /** Spatial Index For NPC */
    transient RTree<SceneNpcBornEntry, Geometry> index;

    /** npc groups */
    transient Map<Integer, SceneGroup> groups = new ConcurrentHashMap<>();
}
