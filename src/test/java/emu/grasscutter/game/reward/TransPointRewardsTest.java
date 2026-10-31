package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.ResourceRegistry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.drop.YamlLootTables;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class TransPointRewardsTest {
    private static final Gson GSON = new Gson();

    private static String read(String resource) throws Exception {
        try (var stream = TransPointRewardsTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<Integer, Integer> roll(
            CssRewards css, YamlLootTables loot, String type, int scene, int point) {
        return RewardRoller.resolveKeys(RewardRoller.roll(
                css.resolve("transpoint", Map.of(
                        "type", type, "scene", scene, "point", point)),
                loot, new Random(1)), loot, RewardContext.NONE);
    }

    @Test
    void bundledPointRulesPreserveAllFormerTomlPayouts() throws Exception {
        var registry = new ResourceRegistry(Map.of(
                "genshin.experience.adventure", 102,
                "genshin.currency.primogem", 201,
                "genshin.currency.sigil.light_realm", 101692));
        // This stylesheet does not reference loot tables. Test its complete
        // resource-key settlement without loading unrelated chest artifacts.
        var loot = YamlLootTables.parse("{}", registry::resolve);
        var css = CssRewards.parse(RewardConfiguration.readBundled("transpoints.lcss"),
                registry, loot);
        var standard = Map.of(102, 50, 201, 5);
        assertEquals(standard, roll(css, loot, "waypoint", 3, 8));
        assertEquals(standard, roll(css, loot, "statue", 3, 3));
        assertEquals(standard, roll(css, loot, "domain", 3, 48));
        assertEquals(Map.of(102, 10), roll(css, loot, "waypoint", 3, 6));
        assertEquals(Map.of(201, 5, 101692, 20), roll(css, loot, "waypoint", 7, 2));
        var gateway = css.resolve("transpoint",
                Map.of("type", "waypoint", "scene", 7, "point", 2));
        assertTrue(gateway.loot().isEmpty());
        assertTrue(gateway.rangedLoot().isEmpty());
        assertEquals(20, gateway.items().get("genshin.currency.sigil.light_realm"));
        assertEquals(emu.grasscutter.data.ResourceId.of(
                "genshin", "currency", "sigil", "light_realm"),
                emu.grasscutter.data.ResourceId.parse("genshin.currency.sigil.light_realm"));
        assertEquals(Map.of(), roll(css, loot, "unknown", 3, 8));
        assertTrue(css.resolve("transpoint", Map.of(
                "kind", "waypoint", "scene", 3, "point", 8)).items().isEmpty());
    }

    @Test
    void eligibilityStillExcludesUnrelatedAndAlreadyUnlockedPoints() {
        var point = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"gadgetId\":70110002}", PointData.class);
        var statue = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"maxSpringVolume\":5000}", PointData.class);
        var hidden = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"mapVisibility\":\"Never\"}", PointData.class);
        var portal = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"type\":\"PORTAL\"}", PointData.class);
        var domain = GSON.fromJson("{\"$type\":\"DungeonEntry\"}", PointData.class);
        assertEquals(TransPointRewardEligibility.Kind.WAYPOINT,
                TransPointRewardEligibility.kindOf(point, false));
        assertEquals(TransPointRewardEligibility.Kind.STATUE,
                TransPointRewardEligibility.kindOf(statue, true));
        assertEquals(TransPointRewardEligibility.Kind.DOMAIN,
                TransPointRewardEligibility.kindOf(domain, false));
        assertNull(TransPointRewardEligibility.kindOf(hidden, false));
        assertNull(TransPointRewardEligibility.kindOf(portal, false));
        assertNull(TransPointRewardEligibility.kindOf(GSON.fromJson(
                "{\"$type\":\"DungeonEntry\",\"unlocked\":true}", PointData.class), false));
    }

    @Test
    void aStoryDomainWithoutNativeRewardIdRemainsEligible() {
        var dungeon = GSON.fromJson(
                "{\"id\":1003,\"sceneId\":3,\"dungeonEntryId\":20}",
                DungeonEntryData.class);
        var point = GSON.fromJson("{\"$type\":\"DungeonEntry\"}", PointData.class);
        assertEquals(0, dungeon.getRewardDataId());
        assertEquals(TransPointRewardEligibility.Kind.DOMAIN,
                TransPointRewardEligibility.kindOf(point, false));
    }
}
