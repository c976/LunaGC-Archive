package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Real registration + real normal-visibility selection lambda; entity load/unload are recording
 * seams.
 */
class SceneGroupReplacementTest {
    static final int OLD = 133001034, NEW = 133001306;
    Map<Integer, GroupReplacementData> saved;
    Shell scene;
    SceneGroup old, replacement;
    GroupReplacementData edge;

    @BeforeEach
    void setup() throws Exception {
        saved = new HashMap<>(GameData.getGroupReplacements());
        GameData.getGroupReplacements().clear();
        edge = new GroupReplacementData(NEW, new ArrayList<>(List.of(OLD)));
        GameData.getGroupReplacements().put(NEW, edge);
        old = group(OLD, false);
        replacement = group(NEW, true);
        scene = shell();
    }

    @AfterEach
    void restore() {
        GameData.getGroupReplacements().clear();
        GameData.getGroupReplacements().putAll(saved);
    }

    static SceneGroup group(int id, boolean dynamic) {
        var group = SceneGroup.of(id);
        group.dynamic_load = dynamic;
        group.block_id = 3001;
        group.is_replaceable = new SceneReplaceable();
        group.is_replaceable.value = true;
        group.is_replaceable.version = 0;
        group.is_replaceable.new_bin_only = true;
        return group;
    }

    Shell shell() throws Exception {
        var s = allocate(Shell.class);
        s.groups = new LinkedHashSet<>();
        s.unloads = new ArrayList<>();
        var scripts = allocate(Scripts.class);
        var block = new SceneBlock();
        block.id = 3001;
        block.groups.put(OLD, old);
        block.groups.put(NEW, replacement);
        scripts.blocks = new LinkedHashMap<>(Map.of(3001, block));
        field(s, Scene.class, "loadedGroups", s.groups);
        field(s, Scene.class, "scriptManager", scripts);
        return s;
    }

    SceneGroup visibleSelection(Shell s, int id) throws Exception {
        var candidates =
                Arrays.stream(Scene.class.getDeclaredMethods())
                        .filter(
                                m ->
                                        m.getName().startsWith("lambda$checkGroups$")
                                                && m.getReturnType() == SceneGroup.class
                                                && Arrays.equals(m.getParameterTypes(), new Class<?>[] {Integer.class}))
                        .toList();
        assertEquals(1, candidates.size(), "Exact real toLoad.map selection boundary");
        var method = candidates.get(0);
        method.setAccessible(true);
        return (SceneGroup) method.invoke(s, Integer.valueOf(id));
    }

    @Test
    void initialReplacementRetainsAuthoredEdge() {
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        assertEquals(List.of(OLD), edge.replace_groups);
        assertEquals(List.of(OLD), scene.unloads);
        assertEquals(Set.of(replacement), scene.groups);
    }

    @Test
    void chainedReplacementSkipsNodeAlreadyUnloadedByLaterReplacement() {
        int middle = NEW, last = NEW + 1;
        var middleGroup = replacement;
        var lastGroup = group(last, true);
        GameData.getGroupReplacements().put(last, new GroupReplacementData(last, new ArrayList<>(List.of(middle))));
        scene.groups.addAll(List.of(middleGroup, lastGroup));
        assertDoesNotThrow(scene::onRegisterGroups);
        assertEquals(List.of(middle), scene.unloads);
        assertEquals(Set.of(lastGroup), scene.groups);
        assertEquals(List.of(OLD), edge.replace_groups);
    }

    @Test
    void immutableAuthoredListWorks() {
        edge.replace_groups = List.of(OLD);
        scene.groups.addAll(List.of(old, replacement));
        assertDoesNotThrow(scene::onRegisterGroups);
        assertEquals(List.of(OLD), edge.replace_groups);
    }

    @Test
    void reintroducedOldIsReplacedAgain() {
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        scene.groups.add(old);
        scene.onRegisterGroups();
        assertEquals(List.of(OLD, OLD), scene.unloads);
        assertFalse(scene.groups.contains(old));
        assertEquals(List.of(OLD), edge.replace_groups);
    }

    @Test
    void repeatedRegistrationWithoutOldDoesNotUnloadAgain() {
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        for (int i = 0; i < 20; i++) scene.onRegisterGroups();
        assertEquals(List.of(OLD), scene.unloads);
    }

    @Test
    void activeReplacementBlocksOrdinaryLoadBeforeSpawn() throws Exception {
        scene.groups.add(replacement);
        for (int i = 0; i < 20; i++) assertNull(visibleSelection(scene, OLD));
        assertTrue(scene.unloads.isEmpty());
        assertEquals(Set.of(replacement), scene.groups);
    }

    @Test
    void normalCampAllowedAfterReplacementLeaves() throws Exception {
        scene.groups.add(replacement);
        assertNull(visibleSelection(scene, OLD));
        scene.groups.remove(replacement);
        assertSame(old, visibleSelection(scene, OLD));
        scene.groups.add(old);
        scene.onRegisterGroups();
        assertTrue(scene.unloads.isEmpty());
        assertTrue(scene.groups.contains(old));
    }

    @Test
    void replacementReentryReappliesWithoutStickySuppression() throws Exception {
        scene.groups.add(replacement);
        assertNull(visibleSelection(scene, OLD));
        scene.groups.clear();
        assertSame(old, visibleSelection(scene, OLD));
        scene.groups.add(replacement);
        assertNull(visibleSelection(scene, OLD));
    }

    @Test
    void loadedReplacementDoesNotSuppressAnotherScene() throws Exception {
        scene.groups.add(replacement);
        var other = shell();
        assertNull(visibleSelection(scene, OLD));
        assertSame(old, visibleSelection(other, OLD));
        other.groups.addAll(List.of(old, replacement));
        other.onRegisterGroups();
        assertFalse(other.groups.contains(old));
        assertEquals(List.of(OLD), edge.replace_groups);
    }

    @Test
    void higherOldVersionIsNotReplacedWithoutOverride() throws Exception {
        old.is_replaceable.version = 2;
        old.is_replaceable.new_bin_only = false;
        replacement.is_replaceable.version = 1;
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        assertTrue(scene.unloads.isEmpty());
        assertSame(old, visibleSelection(scene, OLD));
    }

    @Test
    void equalVersionRetainsExistingReplacementRule() throws Exception {
        old.is_replaceable.version = 2;
        old.is_replaceable.new_bin_only = false;
        replacement.is_replaceable.version = 2;
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        assertEquals(List.of(OLD), scene.unloads);
        assertNull(visibleSelection(scene, OLD));
    }

    @Test
    void oldValueFalseWithoutOverrideIsNotReplaced() throws Exception {
        old.is_replaceable.value = false;
        old.is_replaceable.new_bin_only = false;
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        assertTrue(scene.unloads.isEmpty());
        assertSame(old, visibleSelection(scene, OLD));
    }

    @Test
    void newBinOverrideRetainsExistingPrecedence() throws Exception {
        old.is_replaceable.value = false;
        old.is_replaceable.version = 9;
        replacement.is_replaceable.version = 0;
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        assertEquals(List.of(OLD), scene.unloads);
        assertNull(visibleSelection(scene, OLD));
    }

    @Test
    void nullOldReplaceabilityIsNotSuppressed() throws Exception {
        old.is_replaceable = null;
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        assertTrue(scene.unloads.isEmpty());
        assertSame(old, visibleSelection(scene, OLD));
    }

    @Test
    void nullReplacementReplaceabilityIsNotSuppressed() throws Exception {
        replacement.is_replaceable = null;
        scene.groups.addAll(List.of(old, replacement));
        scene.onRegisterGroups();
        assertTrue(scene.unloads.isEmpty());
        assertSame(old, visibleSelection(scene, OLD));
    }

    @Test
    void NonDynamicGroupCannotOwnSuppression() throws Exception {
        replacement.dynamic_load = false;
        scene.groups.add(replacement);
        assertSame(old, visibleSelection(scene, OLD));
    }

    @Test
    void unrelatedAuthoredEdgeDoesNotSuppressOrdinaryCamp() throws Exception {
        edge.replace_groups = List.of(999);
        scene.groups.add(replacement);
        assertSame(old, visibleSelection(scene, OLD));
    }

    @Test
    void dynamicCandidateIsNeverLoadedByOrdinarySelection() throws Exception {
        assertNull(visibleSelection(scene, NEW));
    }

    static <T> T allocate(Class<T> type) throws Exception {
        var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }

    static void field(Object object, Class<?> type, String name, Object value) throws Exception {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        f.set(object, value);
    }

    static final class Shell extends Scene {
        Set<SceneGroup> groups;
        List<Integer> unloads;

        Shell() {
            super(null, null);
        }

        @Override
        public boolean loadBlock(SceneBlock block) {
            return true;
        }

        @Override
        public void unloadGroup(SceneBlock block, int id) {
            unloads.add(id);
            groups.removeIf(g -> g.id == id);
        }
    }

    static final class Scripts extends SceneScriptManager {
        Map<Integer, SceneBlock> blocks;

        Scripts() {
            super(null);
        }

        @Override
        public Map<Integer, SceneBlock> getBlocks() {
            return blocks;
        }
    }
}
