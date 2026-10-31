package emu.grasscutter.game.managers;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CityLevelupData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.StatuePromoteData;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;

/**
 * Statue / city offering rewards with per-nation multipliers, evenly split across reward levels
 * (typically Lv.2-10 = 9 steps). Stamina is unchanged (still from excel). Protected items keep
 * their original per-level amounts: shrine keys, traveler constellation memories and Crown-tier stones.
 */
public final class StatueOfferRewardHelper {
    private StatueOfferRewardHelper() {}

    /** Reminiscence stone. */
    private static final int ITEM_REMINISCE_STONE = 100533;
    /** Acquaint Fate. */
    private static final int ITEM_ACQUAINT = 224;
    /** Adventure EXP and primogems. */
    private static final int ITEM_ADVENTURE_EXP = 102;
    private static final int ITEM_PRIMOGEM = 201;

    /** Traveler constellation memories. */
    private static final IntSet CONSTELLATION_MEMORIES =
            new IntOpenHashSet(new int[] {912, 913, 914});

    /** Shrine keys seen on statues. */
    private static final IntSet SHRINE_KEYS =
            new IntOpenHashSet(new int[] {107008, 107018, 107025, 107027, 107031});

    /** cityId -> level -> itemId -> count */
    // Built once, then published whole: readers never see a map that is still being filled.
    private static volatile Map<Integer, Int2ObjectMap<Int2IntMap>> CACHE = Map.of();

    private static final Object LOCK = new Object();

    /** Rewards granted when the city's SotS reaches {@code level} (after level-up). */
    public static Int2IntMap rewardsForLevel(int cityId, int level) {
        ensureBuilt();
        var byLevel = CACHE.get(cityId);
        if (byLevel == null) return new Int2IntOpenHashMap();
        var row = byLevel.get(level);
        return row != null ? row : new Int2IntOpenHashMap();
    }

    private static void ensureBuilt() {
        if (!CACHE.isEmpty()) return;
        synchronized (LOCK) {
            if (!CACHE.isEmpty()) return;
            Map<Integer, Int2ObjectMap<Int2IntMap>> built = new HashMap<>();
            for (int cityId = 1; cityId <= 8; cityId++) {
                built.put(cityId, buildCity(cityId));
            }
            CACHE = built;
        }
    }

    private static Int2ObjectMap<Int2IntMap> buildCity(int cityId) {
        // Original per-level maps for reward-bearing levels (sorted).
        List<LevelSnap> snaps = loadOriginalSnaps(cityId);
        if (snaps.isEmpty()) {
            return new Int2ObjectOpenHashMap<>();
        }

        Int2IntMap originalsTotal = new Int2IntOpenHashMap();
        for (var snap : snaps) {
            for (var e : snap.items.int2IntEntrySet()) {
                originalsTotal.put(
                        e.getIntKey(), originalsTotal.getOrDefault(e.getIntKey(), 0) + e.getIntValue());
            }
        }

        // Snezhnaya: RewardExcel 210382-210390 is missing, so a fallback table with the same shape as the
        // other new regions is used, with the cryo sigil 306.
        if (cityId == 8 && originalsTotal.isEmpty()) {
            originalsTotal.put(ITEM_ADVENTURE_EXP, 2160);
            originalsTotal.put(ITEM_PRIMOGEM, 900);
            originalsTotal.put(306, 90); // cryo sigil - do NOT use 308, the lunar sigil
            for (var snap : snaps) {
                snap.items.clear();
            }
        }

        Int2IntOpenHashMap multipliers = multipliersForCity(cityId);
        int n = snaps.size();

        // Even split of boosted totals.
        Int2ObjectMap<int[]> splits = new Int2ObjectOpenHashMap<>();
        for (var e : originalsTotal.int2IntEntrySet()) {
            int itemId = e.getIntKey();
            int total = e.getIntValue();
            if (isProtected(itemId)) continue;
            int mult = multipliers.getOrDefault(itemId, 0);
            if (mult <= 0) continue; // not boosted - keep original per-level below
            splits.put(itemId, evenSplit(total * mult, n));
        }

        Int2ObjectMap<Int2IntMap> byLevel = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < n; i++) {
            LevelSnap snap = snaps.get(i);
            Int2IntMap out = new Int2IntOpenHashMap();

            // Protected / non-boosted: keep original amount on this level.
            for (var e : snap.items.int2IntEntrySet()) {
                int itemId = e.getIntKey();
                if (isProtected(itemId) || !splits.containsKey(itemId)) {
                    if (e.getIntValue() > 0) {
                        out.put(itemId, e.getIntValue());
                    }
                }
            }

            // Boosted: even share for this level index.
            for (var e : splits.int2ObjectEntrySet()) {
                int amt = e.getValue()[i];
                if (amt > 0) out.put(e.getIntKey(), amt);
            }

            byLevel.put(snap.level, out);
        }
        return byLevel;
    }

    private static boolean isProtected(int itemId) {
        return itemId == ITEM_REMINISCE_STONE
                || CONSTELLATION_MEMORIES.contains(itemId)
                || SHRINE_KEYS.contains(itemId);
    }

    /**
     * Per-item multipliers for boostable rewards. Missing entry = leave original (unless protected).
     */
    private static Int2IntOpenHashMap multipliersForCity(int cityId) {
        Int2IntOpenHashMap m = new Int2IntOpenHashMap();
        switch (cityId) {
            case 1 -> { // Mondstadt
                m.put(ITEM_PRIMOGEM, 55);
                m.put(305, 10); // anemo sigil
                m.put(ITEM_ADVENTURE_EXP, 3);
            }
            case 2 -> { // Liyue
                m.put(ITEM_PRIMOGEM, 55);
                m.put(307, 10); // geo sigil
                m.put(ITEM_ADVENTURE_EXP, 3);
            }
            case 3 -> { // Inazuma
                m.put(ITEM_PRIMOGEM, 25);
                m.put(304, 10); // electro sigil
                m.put(ITEM_ADVENTURE_EXP, 3);
            }
            case 4 -> { // Sumeru
                m.put(ITEM_PRIMOGEM, 25);
                m.put(303, 10); // dendro sigil
                m.put(ITEM_ADVENTURE_EXP, 3);
            }
            case 5 -> { // Fontaine
                m.put(ITEM_PRIMOGEM, 30);
                m.put(302, 20); // hydro sigil
                m.put(ITEM_ADVENTURE_EXP, 5);
            }
            case 6 -> { // Natlan
                m.put(ITEM_PRIMOGEM, 25);
                m.put(301, 20); // pyro sigil
                m.put(ITEM_ACQUAINT, 5);
                m.put(ITEM_ADVENTURE_EXP, 5);
            }
            case 7 -> { // Nod-Krai
                m.put(ITEM_PRIMOGEM, 25);
                m.put(308, 20); // lunar sigil
                m.put(ITEM_ACQUAINT, 10);
                m.put(ITEM_ADVENTURE_EXP, 5);
            }
            case 8 -> { // Snezhnaya
                m.put(ITEM_PRIMOGEM, 25);
                m.put(306, 20); // cryo sigil
                m.put(ITEM_ADVENTURE_EXP, 5);
            }
            default -> {}
        }
        return m;
    }

    private static int[] evenSplit(int total, int n) {
        int[] out = new int[n];
        if (n <= 0) return out;
        int base = total / n;
        int rem = total % n;
        for (int i = 0; i < n; i++) {
            out[i] = base + (i < rem ? 1 : 0);
        }
        return out;
    }

    private static List<LevelSnap> loadOriginalSnaps(int cityId) {
        List<LevelSnap> snaps = new ArrayList<>();

        // Cities 1-4: StatuePromote wins over CityLevelup for the same levels.
        boolean usePromote = false;
        for (var data : GameData.getStatuePromoteDataMap().values()) {
            if (data.getCityId() == cityId) {
                usePromote = true;
                break;
            }
        }

        if (usePromote) {
            for (int level = 2; level <= 20; level++) {
                StatuePromoteData data = GameData.getStatuePromoteData(cityId, level);
                if (data == null) continue;
                LevelSnap snap = new LevelSnap(level);
                if (data.getRewardIdList() != null) {
                    for (int rewardId : data.getRewardIdList()) {
                        addRewardItems(snap.items, rewardId);
                    }
                }
                snaps.add(snap);
            }
        } else {
            for (int level = 2; level <= 20; level++) {
                CityLevelupData data = GameData.getCityLevelupData(cityId, level);
                if (data == null) continue;
                LevelSnap snap = new LevelSnap(level);
                if (data.getRewardId() > 0) {
                    addRewardItems(snap.items, data.getRewardId());
                }
                snaps.add(snap);
            }
        }

        snaps.sort(Comparator.comparingInt(s -> s.level));
        return snaps;
    }

    private static void addRewardItems(Int2IntMap dest, int rewardId) {
        RewardData reward = GameData.getRewardDataMap().get(rewardId);
        if (reward == null || reward.getRewardItemList() == null) return;
        for (var param : reward.getRewardItemList()) {
            if (param == null || param.getId() <= 0 || param.getCount() <= 0) continue;
            dest.put(param.getId(), dest.getOrDefault(param.getId(), 0) + param.getCount());
        }
    }

    private static final class LevelSnap {
        final int level;
        final Int2IntMap items = new Int2IntOpenHashMap();

        LevelSnap(int level) {
            this.level = level;
        }
    }
}
