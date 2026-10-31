package emu.grasscutter.game.managers;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/** Identifies Statue-of-the-Seven points and their goddess NPC placements. */
public final class StatueTalkQuests {
    private StatueTalkQuests() {}

    private static final Int2IntMap AREA_TO_NPC = new Int2IntOpenHashMap();
    private static final IntSet GODDESS_NPC_IDS = new IntOpenHashSet();
    private static volatile boolean goddessNpcIdsLoaded;
    /**
     * SotS pillar gadgets that sometimes omit {@code maxSpringVolume} in scene3_point (Nod-Krai
     * City 7 points 1515–1517 use type {@code KDEHKECBDBO}).
     */
    private static final IntSet STATUE_GADGETS =
            new IntOpenHashSet(new int[] {70130009, 70130010, 70130011, 73176017});
    // scene3_point.json maxSpringVolume>0 npcId (starter 1201 has no npcId on point 7)
    private static final IntSet KNOWN_GODDESS_NPCS =
            new IntOpenHashSet(
                    new int[] {
                        1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1209, 1268, 1272, 1273,
                        1274, 1275, 1277, 1278, 1279, 1281, 1282, 1283, 1284, 1285, 1286, 1287,
                        1288, 1289, 1290, 4900, 4901, 4902, 4903, 4905, 4906, 5900, 5901, 5902,
                        5903, 5904, 5905, 5906, 5907, 6900, 6901, 6902, 6903, 6906, 6907, 6909,
                        6910, 6911, 7900, 7901, 7902, 7906, 7907, 7908, 7909, 7910, 7911
                    });

    static {
        // NPC placements absent from some scene-point rows in the 7.1 resource.
        AREA_TO_NPC.put(51, 6906);
        AREA_TO_NPC.put(52, 6907);
        AREA_TO_NPC.put(53, 6910);
        AREA_TO_NPC.put(70, 7900);
        AREA_TO_NPC.put(71, 7901);
        AREA_TO_NPC.put(72, 7902);
    }

    /** @return goddess npcId for area tip, or 0 if unknown */
    public static int npcForArea(int areaId) {
        if (!AREA_TO_NPC.containsKey(areaId)) {
            return 0;
        }
        return AREA_TO_NPC.get(areaId);
    }

    /**
     * True for Statue of the Seven scene points — including Nod-Krai City 7 pillars that have no
     * {@code maxSpringVolume} in point json (client still EnterTrans on them).
     */
    public static boolean isStatuePoint(PointData pd) {
        if (pd == null) return false;
        if (pd.getMaxSpringVolume() > 0) return true;
        if (STATUE_GADGETS.contains(pd.getGadgetId())) return true;
        String type = pd.getType();
        return type != null && type.contains("KDEHKECBDBO");
    }

    /**
     * SceneNpcBorn {@code configId} for a Statue of the Seven goddess NPC. Hardcoded because
     * PointData.npcId is easy to miss in Gson and an empty set silently dropped Fontaine F.
     */
    public static boolean isGoddessNpc(int configId) {
        if (configId <= 0) return false;
        if (KNOWN_GODDESS_NPCS.contains(configId)) return true;
        ensureGoddessNpcIds();
        return GODDESS_NPC_IDS.contains(configId);
    }

    private static void ensureGoddessNpcIds() {
        if (goddessNpcIdsLoaded) return;
        synchronized (GODDESS_NPC_IDS) {
            if (goddessNpcIdsLoaded) return;
            for (var entry : GameData.getScenePointEntryMap().values()) {
                if (entry == null || entry.getPointData() == null) continue;
                var pd = entry.getPointData();
                if (isStatuePoint(pd) && pd.getNpcId() > 0) {
                    GODDESS_NPC_IDS.add(pd.getNpcId());
                }
            }
            GODDESS_NPC_IDS.addAll(AREA_TO_NPC.values());
            goddessNpcIdsLoaded = true;
        }
    }
}
