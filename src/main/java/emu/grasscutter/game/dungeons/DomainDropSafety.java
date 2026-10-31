package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.BiFunction;

/** Validation and one-claim commit guard for configured dungeon rewards. */
final class DomainDropSafety {
    private static final int MAX_COUNT_CHOICES = 10_000;
    private static final int MAX_ITEMS_PER_ROLL = 10_000;

    private DomainDropSafety() {}

    enum RewardSource { NATIVE_ROOT, VALIDATED_PROXY, NONE }

    /**
     * Presence of positive weights is not proof that a legacy native root belongs to
     * this dungeon. Compare every reachable reward-family leaf before selecting it.
     * Preview data is corroborating metadata, never a payable reward source.
     */
    static RewardSource chooseRewardSource(boolean verifiedNative, boolean verifiedProxy) {
        if (verifiedNative) return RewardSource.NATIVE_ROOT;
        return verifiedProxy ? RewardSource.VALIDATED_PROXY : RewardSource.NONE;
    }

    /** Resolve every positive-weight terminal; reject broken or cyclic graphs, including mixed cycles. */
    static Set<Integer> nativeLeafIds(int root, IntFunction<List<Integer>> children) {
        if (root <= 0) return Set.of();
        var pending = new ArrayDeque<NativeVisit>();
        var active = new HashSet<Integer>();
        var finished = new HashSet<Integer>();
        var leaves = new HashSet<Integer>();
        pending.push(new NativeVisit(root, false));
        while (!pending.isEmpty()) {
            var visit = pending.pop();
            int id = visit.id();
            if (visit.exiting()) {
                active.remove(id);
                finished.add(id);
                continue;
            }
            if (id <= 0 || active.contains(id)) return Set.of();
            if (finished.contains(id)) continue; // Shared descendant in an acyclic DAG.
            if (finished.size() + active.size() >= 10_000) return Set.of();
            var targets = children.apply(id);
            if (targets == null) {
                leaves.add(id);
                finished.add(id);
                continue;
            }
            if (targets.isEmpty() || targets.stream().anyMatch(x -> x == null || x <= 0)) {
                return Set.of();
            }
            active.add(id);
            pending.push(new NativeVisit(id, true));
            for (int i = targets.size() - 1; i >= 0; i--) {
                pending.push(new NativeVisit(targets.get(i), false));
            }
        }
        return leaves;
    }

    private record NativeVisit(int id, boolean exiting) {}

    /** Artifact IDs use a three-digit set family; talent/weapon tiers use a five-digit family. */
    static int rewardFamily(int itemId, DungeonSubType subType) {
        return subType == DungeonSubType.DUNGEON_SUB_RELIQUARY
                ? itemId / 100 : itemId / 10;
    }

    static boolean matchesNativeFamilies(
            Set<Integer> nativeItems, Set<Integer> expectedItems, DungeonSubType type,
            java.util.function.IntPredicate isCategoryReward) {
        if (nativeItems == null || nativeItems.isEmpty()
                || expectedItems == null || expectedItems.isEmpty()) return false;
        var expected = new HashSet<Integer>();
        for (int id : expectedItems) {
            if (isCategoryReward.test(id)) expected.add(rewardFamily(id, type));
        }
        if (expected.isEmpty()) return false;
        var actual = new HashSet<Integer>();
        for (int id : nativeItems) {
            if (isCategoryReward.test(id)) actual.add(rewardFamily(id, type));
        }
        return !actual.isEmpty() && expected.containsAll(actual);
    }

    /** Confirm the known 4706 compatibility pool points to the 475/485 artifact sets. */
    static boolean isVerifiedCompatibilityPool(int dungeonId, Set<Integer> artifactIds) {
        if (dungeonId != 4706) return true;
        if (artifactIds == null || artifactIds.isEmpty()) return false;
        var families = new HashSet<Integer>();
        for (int id : artifactIds) families.add(id / 100);
        return families.equals(Set.of(475, 485));
    }

    static void validatePool(int dungeonId, List<DungeonDropEntry> entries) {
        validatePool(dungeonId, entries, DungeonSubType.DUNGEON_SUB_NONE);
    }

    static void validatePool(
            int dungeonId, List<DungeonDropEntry> entries, DungeonSubType subType) {
        if (entries == null || entries.isEmpty()) {
            throw invalid(dungeonId, "no reward entries");
        }
        for (int index = 0; index < entries.size(); index++) {
            DungeonDropEntry entry = entries.get(index);
            if (entry == null || entry.getCounts() == null || entry.getCounts().isEmpty()) {
                throw invalid(dungeonId, "entry " + index + " has no counts");
            }
            List<Integer> counts = entry.getCounts();
            Integer start = counts.get(0);
            Integer end = counts.get(counts.size() - 1);
            if (start == null || end == null || start < 0 || end < start
                    || end > MAX_ITEMS_PER_ROLL) {
                throw invalid(dungeonId, "entry " + index + " has invalid count bounds");
            }
            long choices = (long) end - start + 1;
            if (choices > MAX_COUNT_CHOICES) {
                throw invalid(dungeonId, "entry " + index + " has an excessive count range");
            }
            validateWeights(dungeonId, index, "count", entry.getProbabilities(), (int) choices);

            List<Integer> items = entry.getItems();
            if (items == null || items.isEmpty() || items.stream().anyMatch(id -> id == null || id <= 0)) {
                throw invalid(dungeonId, "entry " + index + " has no valid item pool");
            }
            validateWeights(dungeonId, index, "item", entry.getItemProbabilities(), items.size());
            if (subType == DungeonSubType.DUNGEON_SUB_RELIQUARY
                    && items.stream().anyMatch(id -> id >= 114000 && id < 115000)) {
                throw invalid(dungeonId, "reliquary pool contains weapon ascension materials");
            }
        }
    }

    private static void validateWeights(
            int dungeonId, int index, String kind, List<Integer> weights, int choices) {
        if (weights == null || weights.isEmpty()) {
            return; // No explicit weights means uniform selection.
        }
        if (weights.size() != choices) {
            throw invalid(dungeonId, "entry " + index + " has mismatched " + kind + " weights");
        }
        long total = 0;
        for (Integer weight : weights) {
            if (weight == null || weight < 0) {
                throw invalid(dungeonId, "entry " + index + " has negative/null " + kind + " weight");
            }
            total += weight;
        }
        if (total <= 0 || total >= Integer.MAX_VALUE) {
            throw invalid(dungeonId, "entry " + index + " has invalid " + kind + " weight sum");
        }
    }

    private static IllegalArgumentException invalid(int dungeonId, String explanation) {
        return new IllegalArgumentException(
                "DungeonDrop.json dungeon " + dungeonId + ": " + explanation);
    }

    /**
     * Serialize a single claim for this dungeon session. The grant implementation is responsible
     * for validating the complete item batch and applying any inventory constraints.
     *
     * <p>The grant receives a confirmation callback. It must invoke that callback only after
     * a payment succeeds, and before adding any items. This prevents a retry from charging a
     * second time if the subsequent grant throws. It is not a database transaction.
     */
    static <T> boolean commitOnce(
            Object lock,
            Set<Integer> rewarded,
            int uid,
            List<T> rewards,
            BiFunction<List<T>, Runnable, Boolean> attemptGrant) {
        synchronized (lock) {
            if (rewarded == null || rewarded.contains(uid) || rewards == null
                    || rewards.isEmpty()) {
                return false;
            }
            return attemptGrant.apply(rewards, () -> rewarded.add(uid));
        }
    }
}
