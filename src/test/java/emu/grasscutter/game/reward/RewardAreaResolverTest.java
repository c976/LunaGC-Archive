package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import emu.grasscutter.data.excels.CityData;
import emu.grasscutter.data.excels.world.WorldAreaData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RewardAreaResolverTest {
    private static CityData city(int id, int... areas) {
        var result = new CityData();
        result.setCityId(id);
        result.setAreaIdVec(java.util.Arrays.stream(areas).boxed().toList());
        return result;
    }

    private static WorldAreaData area(int parent, int child, int scene) {
        return new Gson().fromJson(
                "{\"areaID1\":" + parent + ",\"areaID2\":" + child
                        + ",\"sceneID\":" + scene + "}", WorldAreaData.class);
    }

    private static RewardAreaResolver.Area resolve(
            int scene, WorldAreaData area, List<CityData> cities) {
        return RewardAreaResolver.resolve(scene, area.getId(), null, area, cities);
    }

    @Test
    void nativeParentAreaAndCityAreSharedWithCssAndSigils() {
        var attrs = new HashMap<String, Object>();
        var parent = area(10, 0, 3);
        var location = resolve(3, parent, List.of(city(1, 1, 10), city(2, 5, 8)));
        location.addAttributes(attrs);
        assertEquals(Map.of("area", 10, "city", 1), attrs);
        assertEquals(305, RegionalSigilResolver.fromCityId(location.cityId()));

        attrs.clear();
        resolve(3, area(8, 0, 3), List.of(city(1, 1), city(2, 8)))
                .addAttributes(attrs);
        assertEquals(Map.of("area", 8, "city", 2), attrs);
    }

    @Test
    void compositeSubareaUsesNativeParentIdToFindCountry() {
        var child = area(77, 7701, 3);
        assertEquals(504692813, child.getId());
        var location = resolve(3, child, List.of(city(1, 1, 77), city(2, 8)));
        assertEquals(new RewardAreaResolver.Area(77, 1), location);
        var attributes = new HashMap<String, Object>();
        location.addAttributes(attributes);
        assertEquals(Map.of("area", 77, "city", 1), attributes);
        assertEquals(305, RegionalSigilResolver.fromCityId(location.cityId()));
    }

    @Test
    void signedCompositeAreaIdsMustNotBeDiscarded() {
        var child = area(600, 60001, 11);
        assertTrue(child.getId() < 0);
        var location = resolve(11, child, List.of(city(5, 600)));
        assertEquals(new RewardAreaResolver.Area(600, 5), location);
        assertEquals(302, RegionalSigilResolver.fromCityId(location.cityId()));
    }

    @Test
    void unsupportedAndAmbiguousNativeMetadataCannotClaimAnArea() {
        var child = area(10, 0, 3);
        assertEquals(new RewardAreaResolver.Area(0, 0),
                RewardAreaResolver.resolve(7, child.getId(), null, child,
                        List.of(city(1, 10))));
        assertEquals(new RewardAreaResolver.Area(0, 0),
                RewardAreaResolver.resolve(3, child.getId(), null, child,
                        List.of(city(1, 10), city(2, 10))));
        assertEquals(new RewardAreaResolver.Area(0, 0),
                RewardAreaResolver.resolve(3, 10000, null, child,
                        List.of(city(1, 10))));
    }

    @Test
    void sourceRegionFillsMissingCityForBothCssAndSigils() {
        var location = RewardAreaResolver.resolve(3, 0, "mondstadt", null, List.of());
        var attrs = new HashMap<String, Object>();
        location.addAttributes(attrs);
        assertEquals(Map.of("city", 1), attrs);
        assertEquals(305, RegionalSigilResolver.fromCityId(location.cityId()));

        assertEquals(new RewardAreaResolver.Area(0, 4),
                RewardAreaResolver.resolve(3, 0, "sumeru36", null, List.of()));
        assertEquals(new RewardAreaResolver.Area(0, 0),
                RewardAreaResolver.resolve(3, 0, "unknown", null, List.of()));
    }

    @Test
    void importedRegionControlsBothCssPrimogemsAndRegionalSigils() {
        var names = new emu.grasscutter.data.ItemIdRegistry(
                Map.of(201, "Primogem", 305, "Anemo Sigil", 302, "Hydro Sigil"));
        var loot = emu.grasscutter.game.drop.YamlLootTables.parse("""
                seals:
                  - {reward: genshin.currency.sigil}
                """, names::resolve);
        var css = CssRewards.parse("""
                chest {
                    astaps.loot.seals: 1;
                    genshin.currency.primogem: 2;
                }
                chest[city=1] { genshin.currency.primogem: 0; }
                """, names, loot);
        var context = new RewardContext(3, 0, "mondstadt");
        var attributes = new HashMap<String, Object>();
        attributes.put("kind", "common");
        RewardAreaResolver.resolve(context).addAttributes(attributes);
        assertEquals(Map.of(305, 1),
                RewardRoller.rollIds(css.resolveIds("chest", attributes),
                        loot, new java.util.Random(1), context));
        var fontaine = new RewardContext(3, 0, "fontaine");
        attributes.clear();
        attributes.put("kind", "common");
        RewardAreaResolver.resolve(fontaine).addAttributes(attributes);
        assertEquals(Map.of(201, 2, 302, 1),
                RewardRoller.rollIds(css.resolveIds("chest", attributes),
                        loot, new java.util.Random(1), fontaine));
    }

    @Test
    void nativeAreaTakesPriorityOverConflictingImportedRegion() {
        var data = area(19, 0, 3);
        assertEquals(new RewardAreaResolver.Area(19, 2),
                RewardAreaResolver.resolve(3, 19, "fontaine", data,
                        List.of(city(2, 19))));
    }
}
