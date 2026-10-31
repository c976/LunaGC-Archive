package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class DomainDropSafetyTest {
    private static DungeonDropEntry entry(List<Integer> counts, List<Integer> items) {
        var entry = new DungeonDropEntry();
        entry.setCounts(counts);
        entry.setItems(items);
        return entry;
    }

    @Test
    void sourceSelectionRequiresVerifiedNativeOrVerifiedProxy() {
        assertEquals(DomainDropSafety.RewardSource.NATIVE_ROOT,
                DomainDropSafety.chooseRewardSource(true, true));
        assertEquals(DomainDropSafety.RewardSource.VALIDATED_PROXY,
                DomainDropSafety.chooseRewardSource(false, true));
        assertEquals(DomainDropSafety.RewardSource.NONE,
                DomainDropSafety.chooseRewardSource(false, false));
    }

    @Test
    void nativeGraphDoesNotTrustLegacyArtifactsFor4706() {
        var graph = Map.of(85315500, List.of(85315501),
                85315501, List.of(202, 71513, 72523));
        var nativeItems = DomainDropSafety.nativeLeafIds(85315500, graph::get);
        var proxyItems = Set.of(202, 47513, 47523, 48513, 48523);
        assertFalse(DomainDropSafety.matchesNativeFamilies(nativeItems, proxyItems,
                DungeonSubType.DUNGEON_SUB_RELIQUARY, id -> id >= 20000));
        assertEquals(DomainDropSafety.RewardSource.VALIDATED_PROXY,
                DomainDropSafety.chooseRewardSource(false, true));
        assertTrue(DomainDropSafety.matchesNativeFamilies(
                Set.of(202, 47513, 48513), proxyItems,
                DungeonSubType.DUNGEON_SUB_RELIQUARY, id -> id >= 20000));
    }

    @Test
    void brokenAndCyclicNativeGraphsFailClosed() {
        assertTrue(DomainDropSafety.nativeLeafIds(1,
                Map.of(1, List.<Integer>of())::get).isEmpty());
        assertTrue(DomainDropSafety.nativeLeafIds(1,
                Map.of(1, List.of(2), 2, List.of(1))::get).isEmpty());
        assertTrue(DomainDropSafety.nativeLeafIds(1,
                Map.of(1, List.of(0))::get).isEmpty());
    }

    @Test
    void mixedCyclicNativeGraphIsRejectedEvenWithValidLeaves() {
        var graph = Map.of(
                1, List.of(2, 3),
                2, List.of(1),
                3, List.of(114049));
        assertTrue(DomainDropSafety.nativeLeafIds(1, graph::get).isEmpty());
    }

    @Test
    void sharedNativeSubtableIsNotMistakenForACycle() {
        var graph = Map.of(
                1, List.of(2, 3),
                2, List.of(4),
                3, List.of(4),
                4, List.of(114049));
        assertEquals(Set.of(114049), DomainDropSafety.nativeLeafIds(1, graph::get));
    }

    @Test
    void fontaineWeaponDomainsHaveValidatedPayableCompatibilityPools() throws IOException {
        var source = Files.readString(Path.of("data", "DungeonDrop.json"));
        DungeonDrop[] drops = new Gson().fromJson(source, DungeonDrop[].class);
        var pools = new HashMap<Integer, List<DungeonDropEntry>>();
        for (var drop : drops) {
            assertTrue(pools.putIfAbsent(drop.getDungeonId(), drop.getDrops()) == null,
                    "Duplicate dungeon pool: " + drop.getDungeonId());
        }
        int[] bases = {4470, 4500, 4504};
        int[] materials = {114049, 114053, 114057};
        int[] mora = {1125, 1550, 1850, 2200};
        int[] friendship = {10, 15, 15, 20};
        for (int family = 0; family < bases.length; family++) {
            for (int tier = 0; tier < 4; tier++) {
                int dungeonId = bases[family] + tier;
                final int firstMaterial = materials[family];
                final int lastMaterial = firstMaterial + tier;
                var pool = pools.get(dungeonId);
                assertTrue(pool != null && !pool.isEmpty(), "Missing rewards: " + dungeonId);
                DomainDropSafety.validatePool(
                        dungeonId, pool, DungeonSubType.DUNGEON_SUB_WEAPON);
                var expectedIds = new HashSet<>(Set.of(102, 202, 105));
                for (int grade = 0; grade <= tier; grade++) {
                    expectedIds.add(firstMaterial + grade);
                }
                var actualIds = new HashSet<Integer>();
                pool.forEach(entry -> actualIds.addAll(entry.getItems()));
                assertEquals(expectedIds, actualIds, "Wrong reward family: " + dungeonId);
                for (int rolls : new int[] {1, 2, 3}) {
                    var rewards = DomainDropRoller.roll(
                            dungeonId, DungeonSubType.DUNGEON_SUB_WEAPON,
                            pool, rolls, false);
                    assertFalse(rewards.isEmpty(), "Empty rewards: " + dungeonId);
                    assertTrue(rewards.stream().allMatch(item ->
                            item.getCount() > 0 && expectedIds.contains(item.getId())));
                    assertEquals(100 * rolls, rewards.stream()
                            .filter(item -> item.getId() == 102)
                            .mapToInt(item -> item.getCount()).sum());
                    assertEquals(mora[tier] * rolls, rewards.stream()
                            .filter(item -> item.getId() == 202)
                            .mapToInt(item -> item.getCount()).sum());
                    assertEquals(friendship[tier] * rolls, rewards.stream()
                            .filter(item -> item.getId() == 105)
                            .mapToInt(item -> item.getCount()).sum());
                    assertTrue(rewards.stream().anyMatch(
                            item -> item.getId() >= firstMaterial
                                    && item.getId() <= lastMaterial),
                            "Missing weapon material: " + dungeonId);
                }
            }
        }
    }

    @Test
    void actual4706CompatibilityPoolUsesOnlyExpectedArtifactFamilies() throws IOException {
        var source = Files.readString(Path.of("data", "DungeonDrop.json"));
        DungeonDrop[] drops = new Gson().fromJson(source, DungeonDrop[].class);
        var pool = Arrays.stream(drops).filter(drop -> drop.getDungeonId() == 4706)
                .findFirst().orElseThrow();
        DomainDropSafety.validatePool(4706, pool.getDrops(),
                DungeonSubType.DUNGEON_SUB_RELIQUARY);
        var ids = new HashSet<Integer>();
        pool.getDrops().forEach(entry -> ids.addAll(entry.getItems()));
        ids.removeIf(id -> id < 20000);
        assertTrue(DomainDropSafety.isVerifiedCompatibilityPool(4706, ids));
        assertFalse(DomainDropSafety.isVerifiedCompatibilityPool(
                4706, Set.of(71513, 72523)));
    }

    @Test
    void acceptsUniformAndWeightedValidPools() {
        var first = entry(List.of(1, 3), List.of(104301, 104302));
        first.setProbabilities(List.of(1, 2, 1));
        first.setItemProbabilities(List.of(0, 10));
        var second = entry(List.of(2700), List.of(202));
        DomainDropSafety.validatePool(500, List.of(first, second));
    }

    @Test
    void rejectsEmptyItemPoolAndBrokenCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(5), List.of()))));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(), List.of(202)))));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(3, 2), List.of(202)))));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(0, 10001), List.of(202)))));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(1), List.of(-1)))));
    }

    @Test
    void rejectsBadWeightsBeforeRandomSelection() {
        var wrongLength = entry(List.of(1, 3), List.of(202));
        wrongLength.setProbabilities(List.of(50, 50));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(wrongLength)));

        var zeroMass = entry(List.of(1), List.of(202, 203));
        zeroMass.setItemProbabilities(List.of(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(zeroMass)));

        var negative = entry(List.of(1), List.of(202, 203));
        negative.setItemProbabilities(List.of(-1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(negative)));

        var overflow = entry(List.of(1), List.of(202, 203));
        overflow.setItemProbabilities(List.of(Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(overflow)));
    }

    @ParameterizedTest
    @ValueSource(ints = {5000, 5001, 5002, 5008, 5050, 5100, 5101})
    void nativeRootIsPreferredOverExistingServerProxy(int dungeonId) throws IOException {
        // Upstream still includes these proxies; do not require deleting them.
        var source = Files.readString(Path.of("data", "DungeonDrop.json"));
        DungeonDrop[] drops = new Gson().fromJson(source, DungeonDrop[].class);
        assertTrue(Arrays.stream(drops).anyMatch(drop -> drop.getDungeonId() == dungeonId));
        assertEquals(DomainDropSafety.RewardSource.NATIVE_ROOT,
                DomainDropSafety.chooseRewardSource(true, true));
    }

    @ParameterizedTest
    @ValueSource(ints = {4480, 4484, 4665, 4683, 4687, 5018, 5022, 5060, 5064})
    void weaponMaterialsInReliquaryProxyAreRejected(int dungeonId) throws IOException {
        var source = Files.readString(Path.of("data", "DungeonDrop.json"));
        DungeonDrop[] drops = new Gson().fromJson(source, DungeonDrop[].class);
        Map<Integer, List<DungeonDropEntry>> byId = new HashMap<>();
        for (DungeonDrop drop : drops) {
            byId.put(drop.getDungeonId(), drop.getDrops());
        }
        assertTrue(byId.containsKey(dungeonId));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(
                        dungeonId, byId.get(dungeonId), DungeonSubType.DUNGEON_SUB_RELIQUARY));
    }

    @Test
    void rejectNewMalformedProxiesWithoutRequiringAnUnrelatedDataCleanup() throws IOException {
        var source = Files.readString(Path.of("data", "DungeonDrop.json"));
        DungeonDrop[] drops = new Gson().fromJson(source, DungeonDrop[].class);
        // The upstream drop table still has six known entries with invalid item pools.
        // This PR does not rewrite the data. Any *new* invalid pool must fail CI.
        var existingMalformed = Set.of(5000, 5001, 5002, 5008, 5100, 5101);
        var invalid = new HashSet<Integer>();
        for (DungeonDrop drop : drops) {
            try {
                DomainDropSafety.validatePool(drop.getDungeonId(), drop.getDrops());
            } catch (IllegalArgumentException e) {
                invalid.add(drop.getDungeonId());
            }
        }
        assertTrue(existingMalformed.containsAll(invalid),
                "New invalid DungeonDrop.json pool(s): " + invalid);
    }

    @Test
    void emptyRewardsNeverInvokeGrant() {
        var attempts = new AtomicInteger();
        var rewarded = new HashSet<Integer>();
        boolean success = DomainDropSafety.commitOnce(new Object(), rewarded, 101,
                List.of(), (items, confirmed) -> { attempts.incrementAndGet(); return true; });
        assertFalse(success);
        assertEquals(0, attempts.get());
        assertTrue(rewarded.isEmpty());
    }

    @Test
    void declinedGrantLeavesClaimAvailable() {
        var rewarded = new HashSet<Integer>();
        var attempts = new AtomicInteger();
        assertFalse(DomainDropSafety.commitOnce(new Object(), rewarded, 101,
                List.of(202), (items, confirmed) -> {
                    attempts.incrementAndGet();
                    return false;
                }));
        assertTrue(rewarded.isEmpty());
        assertTrue(DomainDropSafety.commitOnce(new Object(), rewarded, 101,
                List.of(202), (items, confirmed) -> {
                    attempts.incrementAndGet();
                    confirmed.run();
                    return true;
                }));
        assertEquals(2, attempts.get());
        assertTrue(rewarded.contains(101));
    }

    @Test
    void successfulClaimGrantsOnceAndRejectsDuplicatesBeforeCharging() {
        Object lock = new Object();
        var rewarded = new HashSet<Integer>();
        var payments = new AtomicInteger();
        var grants = new AtomicInteger();
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean success = DomainDropSafety.commitOnce(lock, rewarded, 101,
                    List.of(202), (items, confirmed) -> {
                        assertEquals(List.of(202), items);
                        payments.incrementAndGet();
                        confirmed.run();
                        grants.incrementAndGet();
                        return true;
                    });
            assertEquals(attempt == 0, success);
        }
        assertEquals(1, payments.get());
        assertEquals(1, grants.get());
        assertTrue(rewarded.contains(101));
    }

    @Test
    void throwingGrantDoesNotPermitSecondCharge() {
        Object lock = new Object();
        var rewarded = new HashSet<Integer>();
        var charged = new AtomicInteger();
        assertThrows(IllegalStateException.class,
                () -> DomainDropSafety.commitOnce(lock, rewarded, 101,
                        List.of(202), (items, confirmed) -> {
                            charged.incrementAndGet();
                            confirmed.run();
                            throw new IllegalStateException("inventory failure");
                        }));
        assertTrue(rewarded.contains(101));
        assertFalse(DomainDropSafety.commitOnce(lock, rewarded, 101,
                List.of(202), (items, confirmed) -> {
                    charged.incrementAndGet();
                    return true;
                }));
        assertEquals(1, charged.get());
    }
    @Test
    void concurrentClaimAndResetShareMonitorAndPreventDuplicateCharge() throws Exception {
        Object monitor = new Object();
        var rewarded = new HashSet<Integer>();
        var run = new DomainClaimRunIdentity();
        String firstRunId = run.operationId(4471);
        var paid = new AtomicInteger();
        var granted = new AtomicInteger();
        var inClaim = new java.util.concurrent.CountDownLatch(1);
        var mayFinish = new java.util.concurrent.CountDownLatch(1);
        var resetDone = new java.util.concurrent.CountDownLatch(1);

        var claiming = new Thread(() -> DomainDropSafety.commitOnce(
                monitor, rewarded, 70632, List.of(47513), (items, confirmed) -> {
                    paid.incrementAndGet();
                    confirmed.run();
                    inClaim.countDown();
                    try {
                        if (!mayFinish.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new IllegalStateException("claim blocked");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    granted.incrementAndGet();
                    return true;
                }));
        var restarting = new Thread(() -> {
            synchronized (monitor) {
                rewarded.clear();
                run.restart();
            }
            resetDone.countDown();
        });
        claiming.start();
        try {
            assertTrue(inClaim.await(3, java.util.concurrent.TimeUnit.SECONDS));
            restarting.start();
            assertFalse(resetDone.await(150, java.util.concurrent.TimeUnit.MILLISECONDS),
                    "Reset must wait while paid claim is completing");
        } finally {
            mayFinish.countDown();
            claiming.join(3000);
            restarting.join(3000);
        }
        assertFalse(claiming.isAlive());
        assertFalse(restarting.isAlive());
        assertEquals(1, paid.get());
        assertEquals(1, granted.get());
        assertEquals(0L, resetDone.getCount());
        assertFalse(firstRunId.equals(run.operationId(4471)));

        // A delayed request from the previous run cannot authorize another charge.
        assertFalse(DomainDropSafety.commitOnce(
                monitor, rewarded, 70632, List.of(47513), (items, confirmed) -> {
                    if (!firstRunId.equals(run.operationId(4471))) return false;
                    paid.incrementAndGet();
                    confirmed.run();
                    return true;
                }));
        assertEquals(1, paid.get());
        assertTrue(DomainDropSafety.commitOnce(
                monitor, rewarded, 70632, List.of(47513), (items, confirmed) -> {
                    paid.incrementAndGet();
                    confirmed.run();
                    granted.incrementAndGet();
                    return true;
                }));
        assertEquals(2, paid.get());
        assertEquals(2, granted.get());
    }

    @Test
    void dungeonManagerResetUsesSynchronizedMethod() throws Exception {
        var reset = DungeonManager.class.getDeclaredMethod("restartDungeon");
        assertTrue(java.lang.reflect.Modifier.isSynchronized(reset.getModifiers()));
    }

}
