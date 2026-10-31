package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Passive cooldown lookup must never enter the side-effecting group loader. */
class OpenWorldRefreshLookupTest {
    private Shell scene;
    private Scripts scripts;

    @BeforeEach
    void setup() throws Exception {
        scene = allocate(Shell.class);
        scripts = allocate(Scripts.class);
        scripts.active = new HashMap<>();
        scripts.cached = new HashMap<>();
        scripts.blocks = new HashMap<>();
        scene.scripts = scripts;
    }

    private int resolve(Scene input, int group) throws Exception {
        var method =
                OpenWorldSpawnHelper.class.getDeclaredMethod("resolveRefreshId", Scene.class, int.class);
        method.setAccessible(true);
        int result = (Integer) method.invoke(null, input, group);
        assertEquals(0, scripts.loadingLookups, "Passive lookup must not call getGroupById");
        return result;
    }

    private static SceneGroup group(int id, int refresh) {
        var group = SceneGroup.of(id);
        group.refresh_id = refresh;
        return group;
    }

    private static SceneGroupInstance instance(SceneGroup group) {
        var instance = new SceneGroupInstance();
        instance.setLuaGroup(group);
        return instance;
    }

    @Test
    void activeGroupKeepsItsAuthoredRefreshId() throws Exception {
        scripts.active.put(7, instance(group(7, 1001)));
        assertEquals(1001, resolve(scene, 7));
    }

    @Test
    void unloadedCachedGroupDoesNotNeedActivation() throws Exception {
        scripts.cached.put(7, instance(group(7, 9001)));
        assertEquals(9001, resolve(scene, 7));
    }

    @Test
    void parsedOrdinaryMetadataKeepsRefreshIdWithoutLoading() throws Exception {
        var block = new SceneBlock();
        block.groups.put(7, group(7, 1002));
        scripts.blocks.put(3, block);
        assertEquals(1002, resolve(scene, 7));
    }

    @Test
    void zeroRefreshFallsThroughToKnownMetadataWithoutLoading() throws Exception {
        scripts.active.put(7, instance(group(7, 0)));
        scripts.cached.put(7, instance(group(7, 0)));
        var block = new SceneBlock();
        block.groups.put(7, group(7, 1003));
        scripts.blocks.put(3, block);
        assertEquals(1003, resolve(scene, 7));
    }

    @Test
    void missingMetadataUsesExistingDefaultWithoutLoading() throws Exception {
        assertEquals(OpenWorldSpawnHelper.DEFAULT_REFRESH_ID, resolve(scene, 7));
    }

    @Test
    void missingBlockGroupTableDoesNotRequireLoad() throws Exception {
        var block = new SceneBlock();
        block.groups = null;
        scripts.blocks.put(3, block);
        assertEquals(OpenWorldSpawnHelper.DEFAULT_REFRESH_ID, resolve(scene, 7));
    }

    @Test
    void nullScenePreservesDefault() throws Exception {
        assertEquals(OpenWorldSpawnHelper.DEFAULT_REFRESH_ID, resolve(null, 7));
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    static final class Shell extends Scene {
        Scripts scripts;

        Shell() {
            super(null, null);
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts;
        }
    }

    static final class Scripts extends SceneScriptManager {
        Map<Integer, SceneGroupInstance> active, cached;
        Map<Integer, SceneBlock> blocks;
        int loadingLookups;

        Scripts() {
            super(null);
        }

        @Override
        public SceneGroupInstance getGroupInstanceById(int id) {
            return active.get(id);
        }

        @Override
        public Map<Integer, SceneGroupInstance> getCachedGroupInstances() {
            return cached;
        }

        @Override
        public Map<Integer, SceneBlock> getBlocks() {
            return blocks;
        }

        @Override
        public SceneGroup getGroupById(int id) {
            loadingLookups++;
            throw new AssertionError("Loading lookup is forbidden");
        }
    }
}
