package emu.grasscutter.data.excels.reliquary;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins the artifact weights filled in for 7.x tables, which no longer carry them. */
public final class RelicWeightDefaultsTest {
    private static ReliquaryMainPropData mainProp(String json) {
        var data = JsonUtils.decode(json, ReliquaryMainPropData.class);
        data.onLoad();
        return data;
    }

    private static ReliquaryAffixData affix(String json) {
        var data = JsonUtils.decode(json, ReliquaryAffixData.class);
        data.onLoad();
        return data;
    }

    @Test
    @DisplayName("a 7.1 sands row with no weight gets the sands drop rate")
    public void sandsMainProp() {
        assertEquals(
                1000,
                mainProp("{\"id\":10007,\"propDepotId\":1000,\"propType\":\"FIGHT_PROP_CHARGE_EFFICIENCY\"}")
                        .getWeight());
        // Sands rows for stats the slot never rolls stay out of the pool.
        assertEquals(
                0,
                mainProp("{\"id\":10009,\"propDepotId\":1000,\"propType\":\"FIGHT_PROP_FIRE_SUB_HURT\"}")
                        .getWeight());
    }

    @Test
    @DisplayName("a single-stat depot row still gets a weight")
    public void fixedDepotMainProp() {
        assertEquals(
                1,
                mainProp("{\"id\":30096,\"propDepotId\":3096,\"propType\":\"FIGHT_PROP_CRITICAL\"}")
                        .getWeight());
    }

    @Test
    @DisplayName("a weight the table does carry is kept")
    public void explicitWeightKept() {
        assertEquals(
                42,
                mainProp("{\"id\":10001,\"propDepotId\":1000,\"propType\":\"FIGHT_PROP_HP_PERCENT\",\"weight\":42}")
                        .getWeight());
    }

    @Test
    @DisplayName("substats with no weight roll 6 : 4 : 3 with even upgrade odds")
    public void affixWeights() {
        var flat = affix("{\"id\":501021,\"depotId\":501,\"propType\":\"FIGHT_PROP_HP\",\"propValue\":209.13}");
        var percent = affix("{\"id\":501031,\"depotId\":501,\"propType\":\"FIGHT_PROP_HP_PERCENT\",\"propValue\":0.04}");
        var crit = affix("{\"id\":501201,\"depotId\":501,\"propType\":\"FIGHT_PROP_CRITICAL\",\"propValue\":0.027}");
        assertEquals(150, flat.getWeight());
        assertEquals(100, percent.getWeight());
        assertEquals(75, crit.getWeight());
        assertEquals(2500, crit.getUpgradeWeight());
    }

    @Test
    @DisplayName("every slot's main stat weights add up to the whole")
    public void slotsAddUp() {
        String[] goblet = {
            "HP_PERCENT", "ATTACK_PERCENT", "DEFENSE_PERCENT", "FIRE_ADD_HURT", "ELEC_ADD_HURT",
            "ICE_ADD_HURT", "WATER_ADD_HURT", "WIND_ADD_HURT", "ROCK_ADD_HURT", "GRASS_ADD_HURT",
            "PHYSICAL_ADD_HURT", "ELEMENT_MASTERY"
        };
        int sum = 0;
        for (var p : goblet) {
            sum += mainProp("{\"propDepotId\":5000,\"propType\":\"FIGHT_PROP_" + p + "\"}").getWeight();
        }
        assertEquals(10000, sum);
    }
}
