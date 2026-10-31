package emu.grasscutter.game.world;

import dev.morphia.annotations.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.scripts.data.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.*;
import org.bson.types.ObjectId;

@Entity(value = "group_instances", useDiscriminator = false)
public final class SceneGroupInstance {
    @Id private ObjectId id;

    @Indexed private int ownerUid; // This group is owned by the host player
    @Getter private int groupId;

    @Getter private transient SceneGroup luaGroup;
    @Getter @Setter private int targetSuiteId;
    @Getter @Setter private int activeSuiteId;
    @Getter private Set<Integer> deadEntities; // Config_ids
    // Explicit Lua gadget removal must survive later group loads. Ordinary
    // combat kills retain their pre-existing oneoff/persistent spawn policy.
    private Set<Integer> scriptDestroyedGadgets = new HashSet<>();
    private boolean isCached;

    @Getter private Map<Integer, Integer> cachedGadgetStates;
    @Getter private Map<String, Integer> cachedVariables;

    @Getter @Setter private int lastTimeRefreshed;

    /** Versioned commission-owned encounter state; ordinary groups never read or write it. */
    @Getter @Setter private String dailyTaskSnapshot;

    public SceneGroupInstance(SceneGroup group, Player owner) {
        this.luaGroup = group;
        this.groupId = group.id;
        this.targetSuiteId = 0;
        this.activeSuiteId = 0;
        this.lastTimeRefreshed = 0;
        this.ownerUid = owner.getUid();
        this.deadEntities = new HashSet<>();
        this.cachedGadgetStates = new ConcurrentHashMap<>();
        this.cachedVariables = new ConcurrentHashMap<>();

        this.isCached =
                false; // This is true when the group is not loaded on scene but caches suite data
    }

    @Deprecated // Morphia only!
    SceneGroupInstance() {
        this.cachedVariables = new ConcurrentHashMap<>();
        this.deadEntities = new HashSet<>();
        this.cachedGadgetStates = new ConcurrentHashMap<>();
    }

    public void setLuaGroup(SceneGroup group) {
        this.luaGroup = group;
        this.groupId = group.id;
    }

    public boolean isCached() {
        return this.isCached;
    }

    public void setCached(boolean value) {
        this.isCached = value;
        save(); // Save each time a group is registered or unregistered
    }

    /**
     * Make sure {@link #cachedVariables} is non-null.
     *
     * <p>Documents written before the field existed come back from Morphia with it unset - the
     * no-arg constructor Morphia uses does initialise it, but a field absent from the document then
     * overwrites that with null. Scripts write group variables through this map, so a null one turns
     * every {@code SetGroupVariableValue} on an old group into an NPE.
     */
    public void ensureCachedVariables() {
        if (this.cachedVariables == null) {
            this.cachedVariables = new ConcurrentHashMap<>();
        }
    }

    public void cacheGadgetState(SceneGadget g, int state) {
        if (g.persistent) // Only cache when is persistent
        cachedGadgetStates.put(g.config_id, state);
    }

    public int getCachedGadgetState(SceneGadget g) {
        Integer state = cachedGadgetStates.getOrDefault(g.config_id, null);
        return (state == null) ? g.state : state;
    }

    public synchronized boolean isScriptGadgetDestroyed(int configId) {
        return scriptDestroyedGadgets != null && scriptDestroyedGadgets.contains(configId);
    }

    /** Mark even unloaded gadgets so the next group spawn cannot revive them. */
    public synchronized boolean markScriptGadgetDestroyed(int configId) {
        if (configId <= 0) return false;
        if (scriptDestroyedGadgets == null) scriptDestroyedGadgets = new HashSet<>();
        return scriptDestroyedGadgets.add(configId);
    }

    /** Consumption is distinct from combat death: opened chests must never respawn. */
    public void markGadgetConsumed(int configId) {
        if (markScriptGadgetDestroyed(configId)) save();
    }

    public void save() {
        DatabaseHelper.saveGroupInstance(this);
    }
}
