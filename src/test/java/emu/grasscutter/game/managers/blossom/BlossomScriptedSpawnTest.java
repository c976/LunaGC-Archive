package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.SpawnDataEntry.GridBlockId;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.utils.JsonUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BlossomScriptedSpawnTest {
    @Test
    void originalBlossomCampIdsAndLinksAreSourceBacked() {
        var mondstadtWealth = JsonUtils.decode(
                "{\"id\":101001001,\"cityId\":1,\"limit_level\":12,"
                        + "\"sectionId\":1001,\"refreshTypeVec\":[1,3],"
                        + "\"newGroupVec\":[133001374],\"nextCampIdVec\":[101001002]}",
                emu.grasscutter.data.excels.BlossomCampData.class);
        var mondstadtRevelation = JsonUtils.decode(
                "{\"id\":101002003,\"cityId\":1,\"limit_level\":8,"
                        + "\"sectionId\":1002,\"refreshTypeVec\":[1,3],"
                        + "\"newGroupVec\":[133002369],\"nextCampIdVec\":[101002004]}",
                emu.grasscutter.data.excels.BlossomCampData.class);
        assertEquals(133001374, mondstadtWealth.getNewGroupVec().get(0));
        assertEquals(101001002, mondstadtWealth.getNextCampIdVec().get(0));
        assertEquals(133002369, mondstadtRevelation.getNewGroupVec().get(0));
        assertEquals(101002004, mondstadtRevelation.getNextCampIdVec().get(0));
        assertEquals(101001001, BlossomNativeCamps.initialId(
                1, BlossomType.GOLD, List.of(mondstadtWealth, mondstadtRevelation)));
        assertEquals(101002003, BlossomNativeCamps.initialId(
                1, BlossomType.BLUE, List.of(mondstadtWealth, mondstadtRevelation)));
        assertEquals(101001001, BlossomNativeCamps.initialId(
                2, BlossomType.GOLD, List.of(mondstadtWealth, mondstadtRevelation)));
        assertEquals(101002003, BlossomNativeCamps.initialId(
                2, BlossomType.BLUE, List.of(mondstadtWealth, mondstadtRevelation)));
    }

    @Test
    void encounterCompletesFromKillScoreEvenWithLingeringMonsterReferences() {
        assertFalse(BlossomActivity.encounterComplete(8, 4, 0, 4));
        assertFalse(BlossomActivity.encounterComplete(8, 8, 1, 7));
        assertTrue(BlossomActivity.encounterComplete(8, 8, 2, 8));
        assertTrue(BlossomActivity.encounterComplete(8, 8, 0, 7));
    }

    @Test
    void global71OriginalClient20And40ResinPacketsSelectSeparateAmounts() throws Exception {
        // Captured GadgetInteractReq FINISH packets from the Global 7.1 client.
        var req20 = emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                .parseFrom(java.util.HexFormat.of().parseHex("20bea4bd21380158a98980047001"));
        var req40 = emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                .parseFrom(java.util.HexFormat.of().parseHex("20bea4bd21380158f88880047002"));
        assertEquals(1, BlossomManager.normalClaimCount(req20));
        assertEquals(2, BlossomManager.normalClaimCount(req40));
        assertEquals(1, req20.getResinCostTypeValue());
        assertEquals(1, req40.getResinCostTypeValue());
    }

    @Test
    void global71ResinModesUseTheirOwnDebitsAndRewardMultipliers() {
        var normal = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_NORMAL, 0, 1);
        assertEquals(1, normal.rolls());
        assertEquals(20, normal.amount());
        assertEquals(106, normal.itemId());

        var doubleClaim = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_NORMAL, 0, 2);
        assertEquals(2, doubleClaim.rolls());
        assertEquals(40, doubleClaim.amount());
        assertEquals(106, doubleClaim.itemId());
        assertNull(BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_NORMAL, 0, 0));
        assertNull(BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_NORMAL, 0, 3));

        var condensed = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_CONDENSE, 0, 0);
        assertEquals(3, condensed.rolls());
        assertEquals(220007, condensed.itemId());
        assertEquals(1, condensed.amount());

        var fragile = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_FRAGILE, 0, 0);
        assertEquals(3, fragile.rolls());
        assertEquals(107009, fragile.itemId());

        var transientResin = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_TIMING, 0, 0);
        assertEquals(3, transientResin.rolls());
        assertEquals(107012, transientResin.itemId());

        var primogems = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_HCOIN, 0, 0);
        assertEquals(3, primogems.rolls());
        assertEquals(201, primogems.itemId());
        assertEquals(50, primogems.amount());
        assertNull(BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_HCOIN,
                emu.grasscutter.game.managers.ResinManager.MAX_RESIN_BUYING_COUNT, 0));
    }

    @Test
    void nativeBlossomChestTypesUseSeparateRewardGadgets() {
        // BlossomChestExcelConfigData: SCOIN uses 70210109 and EXP uses 70210110.
        assertEquals(70210109, BlossomType.GOLD.getRewardChestGadgetId());
        assertEquals(70210110, BlossomType.BLUE.getRewardChestGadgetId());
        assertEquals(1, BlossomType.GOLD.getBlossomChestId());
        assertEquals(2, BlossomType.BLUE.getBlossomChestId());
    }

    @Test
    void scriptedSpawnsSelectOnlyAuthoredLeyLineOutcropsInTheCurrentScene() {
        var mondstadt = group(3, 133000001,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":2,\"pos\":{\"x\":4,\"y\":5,\"z\":6}}",
                "{\"gadgetId\":70210109,\"configId\":3,\"pos\":{\"x\":7,\"y\":8,\"z\":9}}");
        var elsewhere = group(4, 144000001,
                "{\"gadgetId\":70360056,\"configId\":4,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}");
        var sources = new HashMap<GridBlockId, ArrayList<SpawnDataEntry>>();
        sources.put(new GridBlockId(3, 0, 0, 0), new ArrayList<>(mondstadt.getSpawns()));
        sources.put(new GridBlockId(4, 0, 0, 0), new ArrayList<>(elsewhere.getSpawns()));

        var selected = BlossomManager.scriptedCandidates(sources, 3);
        assertEquals(List.of(70360057, 70360056),
                selected.stream().map(SpawnDataEntry::getGadgetId).toList());
        assertTrue(BlossomManager.scriptedCandidates(sources, 5).isEmpty());
    }

    @Test
    void progressionUnlocksEachTypeAtItsExpectedAdventureRank() {
        assertFalse(BlossomManager.unlockedAtRank(BlossomType.BLUE, 7));
        assertTrue(BlossomManager.unlockedAtRank(BlossomType.BLUE, 8));
        assertFalse(BlossomManager.unlockedAtRank(BlossomType.GOLD, 11));
        assertTrue(BlossomManager.unlockedAtRank(BlossomType.GOLD, 12));
    }

    @Test
    void identicalLocationSourcesSelectOneInteractiveOutcrop() {
        var group = group(3, 133000001,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":2,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":3,\"pos\":{\"x\":50,\"y\":2,\"z\":3}}");
        var lists = new HashMap<GridBlockId, ArrayList<SpawnDataEntry>>();
        lists.put(new GridBlockId(3, 0, 0, 0), new ArrayList<>(group.getSpawns()));
        var selected = BlossomManager.scriptedCandidates(lists, 3);
        // Both types remain candidates; selection chooses one per type and city.
        assertEquals(3, selected.size());
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 2));
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 3));
    }


    @Test
    void nativeRefreshCountAndAreaOwnershipSelectOneOfEachTypePerCity() {
        var sources = group(3, 133000001,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":2,\"pos\":{\"x\":11,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":3,\"pos\":{\"x\":22,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":4,\"pos\":{\"x\":33,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":5,\"pos\":{\"x\":44,\"y\":2,\"z\":3}}");
        var mondstadt = JsonUtils.decode(
                "{\"cityId\":1,\"areaIdVec\":[1]}", emu.grasscutter.data.excels.CityData.class);
        var liyue = JsonUtils.decode(
                "{\"cityId\":2,\"areaIdVec\":[5]}", emu.grasscutter.data.excels.CityData.class);
        var gold = JsonUtils.decode(
                "{\"id\":1,\"cityId\":1,\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\","
                        + "\"refreshCount\":1,\"refreshCondVec\":[{\"type\":"
                        + "\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[12]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var blue = JsonUtils.decode(
                "{\"id\":2,\"cityId\":1,\"refreshType\":\"BLOSSOM_REFRESH_EXP\","
                        + "\"refreshCount\":1,\"refreshCondVec\":[{\"type\":"
                        + "\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[8]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var candidates = BlossomManager.scriptedCandidates(
                java.util.Map.of(new GridBlockId(3, 0, 0, 0),
                        new ArrayList<>(sources.getSpawns())), 3);
        var cfg = List.of(gold, blue);
        var cities = List.of(mondstadt, liyue);
        var onlyGold = BlossomManager.selectActiveSpawns(candidates, 12, List.of(1),
                cities, cfg, spawn -> 1);
        assertEquals(2, onlyGold.size());
        assertEquals(1, onlyGold.stream().filter(
                entry -> entry.getGadgetId() == BlossomType.GOLD.getGadgetId()).count());
        assertEquals(1, onlyGold.stream().filter(
                entry -> entry.getGadgetId() == BlossomType.BLUE.getGadgetId()).count());
        assertEquals(1, BlossomManager.selectActiveSpawns(candidates, 8, List.of(1),
                cities, cfg, spawn -> 1).size());
        assertTrue(BlossomManager.selectActiveSpawns(candidates, 7, List.of(1),
                cities, cfg, spawn -> 1).isEmpty());
        assertTrue(BlossomManager.selectActiveSpawns(candidates, 12, List.of(1),
                cities, cfg, spawn -> 0).isEmpty());
    }

    @Test
    void selectedPositionsDoNotOverlapAcrossTypesAndConsumedCanRotate() {
        var sources = group(3, 133000001,
                "{\"gadgetId\":70360057,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":2,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":3,\"pos\":{\"x\":50,\"y\":2,\"z\":3}}");
        var city = JsonUtils.decode("{\"cityId\":1,\"areaIdVec\":[1]}",
                emu.grasscutter.data.excels.CityData.class);
        var gold = JsonUtils.decode("{\"id\":1,\"cityId\":1,"
                        + "\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\",\"refreshCount\":1,"
                        + "\"refreshCondVec\":[{\"type\":"
                        + "\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[12]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var blue = JsonUtils.decode("{\"id\":2,\"cityId\":1,"
                        + "\"refreshType\":\"BLOSSOM_REFRESH_EXP\",\"refreshCount\":1,"
                        + "\"refreshCondVec\":[{\"type\":"
                        + "\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[8]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var pool = BlossomManager.scriptedCandidates(java.util.Map.of(
                new GridBlockId(3, 0, 0, 0), new ArrayList<>(sources.getSpawns())), 3);
        var selected = BlossomManager.selectActiveSpawns(pool, 12, List.of(1),
                List.of(city), List.of(gold, blue), entry -> 1);
        assertEquals(List.of(1, 3),
                selected.stream().map(SpawnDataEntry::getConfigId).toList());
        var afterClaim = BlossomManager.selectActiveSpawns(
                pool.stream().filter(entry -> entry.getConfigId() != 1).toList(),
                12, List.of(1), List.of(city), List.of(gold, blue), entry -> 1);
        assertEquals(List.of(2),
                afterClaim.stream().map(SpawnDataEntry::getConfigId).toList());
    }

    private static SpawnGroupEntry group(int sceneId, int groupId, String... spawns) {
        var group = JsonUtils.decode(
                "{\"sceneId\":" + sceneId + ",\"groupId\":" + groupId
                        + ",\"spawns\":[" + String.join(",", spawns) + "]}", SpawnGroupEntry.class);
        group.getSpawns().forEach(spawn -> spawn.setGroup(group));
        return group;
    }
}
