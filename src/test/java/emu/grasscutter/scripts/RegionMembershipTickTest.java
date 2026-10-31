package emu.grasscutter.scripts;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.entity.EntityRegion;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.constants.ScriptRegionShape;
import emu.grasscutter.scripts.data.SceneRegion;
import emu.grasscutter.scripts.data.ScriptArgs;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sun.misc.Unsafe;

/** Compares the optimized helper with the independent pre-optimization region update. */
@ExtendWith(ServerResourceFixture.class)
public final class RegionMembershipTickTest {
    @Test
    void emptyRegionsDoNotReadEntities() throws Exception {
        assertEquivalent(f -> f.tick());
    }

    @Test
    void enteringAvatarPreservesParametersAndAddBeforeEvent() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 10);
            f.entity(1, 1, 0);
            f.tick();
        });
        assertEquals(1, result.events.size());
        var event = result.events.get(0);
        assertEquals(EventType.EVENT_ENTER_REGION, event.type);
        assertEquals("1", event.source);
        assertEquals(101, event.param1);
        assertEquals(42, event.groupId);
        assertEquals(List.of(id(1, 1)), event.members);
        assertTrue(event.entered);
        assertFalse(event.left);
    }

    @Test
    void leavingAvatarPreservesCleanupBeforeEvent() throws Exception {
        var result = assertEquivalent(f -> {
            var region = f.region(1, 0, 10);
            var entity = f.entity(1, 1, 100);
            region.getEntities().add(entity.getId());
            f.tick();
        });
        assertEquals(EventType.EVENT_LEAVE_REGION, result.events.get(0).type);
        assertTrue(result.events.get(0).members.isEmpty());
        assertTrue(result.events.get(0).left);
    }

    @Test
    void unchangedMembershipDoesNotRaiseFlagsOrEvents() throws Exception {
        var result = assertEquivalent(f -> {
            var region = f.region(1, 0, 10);
            var entity = f.entity(1, 1, 0);
            region.getEntities().add(entity.getId());
            f.tick();
        });
        assertTrue(result.events.isEmpty());
        var region = result.regions.get(id(5, 1));
        assertFalse(region.entered);
        assertFalse(region.left);
    }

    @Test
    void disappearedEntityRaisesLeaveAndClearsMembership() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 10).getEntities().add(id(1, 1));
            f.tick();
        });
        assertEquals(1, result.events.size());
        assertEquals(EventType.EVENT_LEAVE_REGION, result.events.get(0).type);
        assertTrue(result.regions.get(id(5, 1)).members.isEmpty());
    }

    @Test
    void gadgetEntityType19UpdatesMembershipWithoutEvents() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 10);
            var entity = f.entity(4, 2, 0);
            f.tick();
            entity.position = new Position(100, 0, 0);
            f.tick();
        });
        assertTrue(result.events.isEmpty());
        var region = result.regions.get(id(5, 1));
        assertTrue(region.entered);
        assertTrue(region.left);
        assertTrue(region.members.isEmpty());
    }

    @Test
    void preservesAllEntityTypesIncludingUnknownTypes() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 10);
            for (var type : new int[] {1, 2, 3, 4, 5, 6, 9, 11, 63}) {
                f.entity(type, type, 0);
            }
            f.tick();
        });
        assertEquals(8, result.events.size());
        assertEquals(9, result.regions.get(id(5, 1)).members.size());
    }

    @Test
    void entitySpawnedByEnterEventIsVisibleToNextRegion() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 5);
            f.region(2, 100, 5);
            f.entity(1, 1, 0);
            f.manager.onEvent = args -> {
                if (args.param1 == 101 && args.type == EventType.EVENT_ENTER_REGION) {
                    f.entity(2, 2, 100);
                }
            };
            f.tick();
        });
        assertEquals(List.of(id(1, 1), id(2, 2)),
                result.events.stream().map(Event::targetId).toList());
    }

    @Test
    void entityRemovedByEnterEventStillLeavesLaterRegion() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 5);
            f.region(2, 0, 5).getEntities().add(id(1, 1));
            f.entity(1, 1, 0);
            f.manager.onEvent = args -> {
                if (args.param1 == 101 && args.type == EventType.EVENT_ENTER_REGION) {
                    f.scene.entities.remove(args.target_eid);
                }
            };
            f.tick();
        });
        assertEquals(List.of(EventType.EVENT_ENTER_REGION, EventType.EVENT_LEAVE_REGION),
                result.events.stream().map(Event::type).toList());
        assertEquals(List.of(101, 102), result.events.stream().map(Event::param1).toList());
    }

    @Test
    void enterCallbackMovementIsRecheckedDuringCleanup() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 5);
            var entity = f.entity(1, 1, 0);
            f.manager.onEvent = args -> entity.position = new Position(100, 0, 0);
            f.tick();
            f.tick();
        });
        assertEquals(1, result.events.size());
        var region = result.regions.get(id(5, 1));
        assertTrue(region.members.isEmpty());
        assertTrue(region.entered);
        assertTrue(region.left);
    }

    @Test
    void multipleEnterEventsAllSeeEveryEntityAlreadyAdded() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 5);
            for (int i = 1; i <= 10; i++) {
                f.entity(1, i, 0);
            }
            f.tick();
        });
        assertEquals(10, result.events.size());
        assertTrue(result.events.stream().allMatch(e -> e.members.size() == 10));
    }

    @Test
    void eventTargetUsesOrderedIdSnapshotEvenIfEntityIdChanges() throws Exception {
        var result = assertEquivalent(f -> {
            f.region(1, 0, 5);
            var entity = f.entity(1, 1, 0);
            entity.renumberAfterFirstRead = true;
            entity.replacementId = id(1, 2);
            f.tick();
        });
        assertEquals(id(1, 1), result.events.get(0).targetId);
    }

    @Test
    void fiftyPlayerWorldsMatchAcrossMovementAndDespawn() throws Exception {
        for (int world = 0; world < 50; world++) {
            final int seed = world;
            assertEquivalent(f -> {
                var random = new Random(seed);
                for (int r = 1; r <= 12; r++) {
                    f.region(r, r * 10, 15);
                }
                for (int e = 1; e <= 80; e++) {
                    f.entity(e <= 4 ? 1 : 2 + e % 3, e, random.nextInt(140));
                }
                for (int tick = 0; tick < 8; tick++) {
                    for (var entity : f.scene.entities.values()) {
                        ((TestEntity) entity).position = new Position(random.nextInt(140), 0, 0);
                    }
                    if (tick == 4) {
                        f.scene.entities.remove(id(1, 1));
                    }
                    f.tick();
                }
            });
        }
    }

    @Test
    void allSupportedShapesMatchAtBoundaries() throws Exception {
        assertEquivalent(f -> {
            for (int r = 1; r <= 4; r++) {
                var meta = f.region(r, 0, 10).getMetaRegion();
                meta.shape = switch (r) {
                    case 1 -> ScriptRegionShape.SPHERE;
                    case 2 -> ScriptRegionShape.CUBIC;
                    case 3 -> ScriptRegionShape.CYLINDER;
                    default -> ScriptRegionShape.POLYGON;
                };
                meta.size = new Position(20, 20, 20);
                meta.height = 20;
                meta.point_array = List.of(new Position(-10, -10, 0), new Position(10, -10, 0),
                        new Position(10, 10, 0), new Position(-10, 10, 0));
            }
            for (int i = 0; i <= 20; i++) {
                f.entity(1, i + 1, i - 10);
            }
            f.tick();
            for (var entity : f.scene.entities.values()) {
                ((TestEntity) entity).position = new Position(10.001f, 0, 0);
            }
            f.tick();
        });
    }

    @Test
    void staticFiftyWorldBenchmarkReportsWithoutTimingAssertion() throws Exception {
        var baseline = benchmarkFixtures(false);
        var optimized = benchmarkFixtures(true);
        for (int i = 0; i < 3; i++) {
            tickAll(baseline);
            tickAll(optimized);
        }
        long[] originalNanos = new long[5];
        long[] helperNanos = new long[5];
        for (int i = 0; i < 5; i++) {
            if (i % 2 == 0) {
                originalNanos[i] = tickAll(baseline);
                helperNanos[i] = tickAll(optimized);
            } else {
                helperNanos[i] = tickAll(optimized);
                originalNanos[i] = tickAll(baseline);
            }
        }
        for (int i = 0; i < baseline.size(); i++) {
            assertEquals(baseline.get(i).snapshot(), optimized.get(i).snapshot());
        }
        Arrays.sort(originalNanos);
        Arrays.sort(helperNanos);
        System.out.printf("REGION_BENCHMARK worlds=50 entitiesPerWorld=800 regionsPerWorld=100 "
                        + "baselineMedianMs=%.3f helperMedianMs=%.3f ratio=%.3f%n",
                originalNanos[2] / 1_000_000.0, helperNanos[2] / 1_000_000.0,
                (double) helperNanos[2] / originalNanos[2]);
    }

    private static List<Fixture> benchmarkFixtures(boolean optimized) throws Exception {
        var fixtures = new ArrayList<Fixture>();
        for (int world = 0; world < 50; world++) {
            var fixture = new Fixture(optimized);
            fixture.manager.recordEvents = false;
            for (int r = 1; r <= 100; r++) {
                fixture.region(r, (r % 20) * 10, 30);
            }
            for (int e = 1; e <= 800; e++) {
                fixture.entity(e <= 4 ? 1 : 2 + e % 3, e, e % 200);
            }
            fixtures.add(fixture);
        }
        return fixtures;
    }

    private static long tickAll(List<Fixture> fixtures) {
        long started = System.nanoTime();
        fixtures.forEach(Fixture::tick);
        return System.nanoTime() - started;
    }

    private static Snapshot assertEquivalent(Consumer<Fixture> scenario) throws Exception {
        var baseline = new Fixture(false);
        scenario.accept(baseline);
        var optimized = new Fixture(true);
        scenario.accept(optimized);
        var expected = baseline.snapshot();
        assertEquals(expected, optimized.snapshot());
        return expected;
    }

    private static void checkRegionsBaseline(
            SceneScriptManager manager, Map<Integer, EntityRegion> regions) {
        if (regions.size() == 0) {
            return;
        }

        for (var region : regions.values()) {
            var entities =
                    manager.getScene().getEntities().values().stream()
                            .filter(e -> region.getMetaRegion().contains(e.getPosition()))
                            .toList();

            var entitiesIds = entities.stream().map(GameEntity::getId).toList();
            var enterEntities =
                    entitiesIds.stream().filter(e -> !region.getEntities().contains(e)).toList();
            var leaveEntities =
                    region.getEntities().stream().filter(e -> !entitiesIds.contains(e)).toList();

            entities.forEach(region::addEntity);

            for (var targetId : enterEntities) {
                if (EntityIdType.toEntityType(targetId >> ENTITY_ID_BIT_SHIFT).getValue() == 19) continue;
                Grasscutter.getLogger()
                        .trace("Call EVENT_ENTER_REGION_{}", region.getMetaRegion().config_id);
                manager.callEvent(
                        new ScriptArgs(region.getGroupId(), EventType.EVENT_ENTER_REGION, region.getConfigId())
                                .setEventSource(EntityIdType.toEntityType(targetId >> ENTITY_ID_BIT_SHIFT).getValue())
                                .setSourceEntityId(region.getId())
                                .setTargetEntityId(targetId));
            }

            for (var entityId : region.getEntities()) {
                var entity = manager.getScene().getEntityById(entityId);
                if (entity == null || !region.getMetaRegion().contains(entity.getPosition())) {
                    region.removeEntity(entityId);
                }
            }

            for (var targetId : leaveEntities) {
                if (EntityIdType.toEntityType(targetId >> ENTITY_ID_BIT_SHIFT).getValue() == 19) continue;
                manager.callEvent(
                        new ScriptArgs(region.getGroupId(), EventType.EVENT_LEAVE_REGION, region.getConfigId())
                                .setEventSource(EntityIdType.toEntityType(targetId >> ENTITY_ID_BIT_SHIFT).getValue())
                                .setSourceEntityId(region.getId())
                                .setTargetEntityId(targetId));
            }
        }
    }

    private static int id(int type, int sequence) {
        return (type << ENTITY_ID_BIT_SHIFT) | sequence;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record RegionState(Set<Integer> members, boolean entered, boolean left) {}

    private record Event(int groupId, int type, int param1, int param2, int param3, String source,
                         int sourceId, int targetId, List<Integer> members, boolean entered, boolean left) {}

    private record Snapshot(List<Event> events, Map<Integer, RegionState> regions, List<Integer> entityIds) {}

    private static final class Fixture {
        private final boolean optimized;
        private final RecordingScene scene;
        private final RecordingManager manager;
        private final Map<Integer, EntityRegion> regions = new LinkedHashMap<>();

        private Fixture(boolean optimized) throws Exception {
            this.optimized = optimized;
            this.scene = allocate(RecordingScene.class);
            this.scene.entities = new LinkedHashMap<>();
            this.manager = allocate(RecordingManager.class);
            set(manager, SceneScriptManager.class, "scene", scene);
            set(manager, SceneScriptManager.class, "regions", regions);
            manager.regions = regions;
            manager.events = new ArrayList<>();
            manager.onEvent = args -> {};
            manager.recordEvents = true;
        }

        private EntityRegion region(int sequence, float x, int radius) {
            try {
                var region = allocate(EntityRegion.class);
                var meta = new SceneRegion();
                meta.config_id = 100 + sequence;
                meta.pos = new Position(x, 0, 0);
                meta.shape = ScriptRegionShape.SPHERE;
                meta.radius = radius;
                region.setId(id(5, sequence));
                region.setGroupId(42);
                region.setConfigId(meta.config_id);
                set(region, EntityRegion.class, "metaRegion", meta);
                set(region, EntityRegion.class, "position", meta.pos);
                set(region, EntityRegion.class, "entities", ConcurrentHashMap.newKeySet());
                regions.put(region.getId(), region);
                return region;
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        private TestEntity entity(int type, int sequence, float x) {
            try {
                var entity = allocate(TestEntity.class);
                entity.setId(id(type, sequence));
                entity.position = new Position(x, 0, 0);
                scene.entities.put(entity.getId(), entity);
                return entity;
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        private void tick() {
            if (optimized) {
                RegionMembershipTick.check(manager, regions);
            } else {
                checkRegionsBaseline(manager, regions);
            }
        }

        private Snapshot snapshot() {
            var states = new LinkedHashMap<Integer, RegionState>();
            regions.forEach((id, region) -> states.put(id,
                    new RegionState(Set.copyOf(region.getEntities()), region.isEntityEnter(), region.isEntityLeave())));
            return new Snapshot(List.copyOf(manager.events), states, List.copyOf(scene.entities.keySet()));
        }
    }

    private static final class RecordingManager extends SceneScriptManager {
        private Map<Integer, EntityRegion> regions;
        private List<Event> events;
        private Consumer<ScriptArgs> onEvent;
        private boolean recordEvents;

        private RecordingManager() {
            super(null);
        }

        @Override
        public Future<?> callEvent(ScriptArgs args) {
            if (recordEvents) {
                var region = regions.get(args.source_eid);
                events.add(new Event(args.group_id, args.type, args.param1, args.param2, args.param3,
                        args.source, args.source_eid, args.target_eid, List.copyOf(region.getEntities()),
                        region.isEntityEnter(), region.isEntityLeave()));
            }
            onEvent.accept(args);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingScene extends Scene {
        private Map<Integer, GameEntity> entities;

        private RecordingScene() {
            super(null, null);
        }

        @Override
        public Map<Integer, GameEntity> getEntities() {
            return entities;
        }

        @Override
        public GameEntity getEntityById(int id) {
            return entities.get(id);
        }
    }

    private static final class TestEntity extends GameEntity {
        private Position position;
        private boolean renumberAfterFirstRead;
        private int idReads;
        private int replacementId;

        private TestEntity() {
            super(null);
        }

        @Override
        public void initAbilities() {}

        @Override
        public int getEntityTypeId() {
            return 0;
        }

        @Override
        public int getId() {
            if (renumberAfterFirstRead && ++idReads > 1) {
                return replacementId;
            }
            return super.getId();
        }

        @Override
        public Int2FloatMap getFightProperties() {
            return null;
        }

        @Override
        public Position getPosition() {
            return position;
        }

        @Override
        public Position getRotation() {
            return new Position();
        }

        @Override
        public SceneEntityInfo toProto() {
            return SceneEntityInfo.getDefaultInstance();
        }
    }
}
