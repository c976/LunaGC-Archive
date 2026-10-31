package emu.grasscutter.data.excels.reliquary;

import emu.grasscutter.game.props.FightProperty;
import java.util.EnumMap;
import java.util.Map;

/**
 * Artifact roll weights for resource packs that do not carry them.
 *
 * <p>Current release client resources do not expose the server-side reliquary roll weights. These
 * defaults preserve known server-side weight ratios, normalized where convenient, and are used only
 * when a resource row has no weight of its own.
 */
final class RelicWeightDefaults {
    /**
     * Every value tier of a substat is equally likely to be the one an upgrade adds. The known
     * server-side weights are equal per tier; 2500 simply normalizes the four tiers to 10000.
     */
    static final int UPGRADE_WEIGHT = 2500;

    /** Substats roll flat HP/ATK/DEF : percent stats, ER, EM : crit at 6 : 4 : 3. */
    private static final Map<FightProperty, Integer> AFFIX = new EnumMap<>(FightProperty.class);

    /** Main stats per slot, out of 10000. Slots are keyed by their main stat depot. */
    private static final Map<Integer, Map<FightProperty, Integer>> MAIN_PROP = Map.of(
            4000, Map.of(FightProperty.FIGHT_PROP_HP, 10000), // flower
            2000, Map.of(FightProperty.FIGHT_PROP_ATTACK, 10000), // plume
            1000, Map.of( // sands
                    FightProperty.FIGHT_PROP_HP_PERCENT, 2668,
                    FightProperty.FIGHT_PROP_ATTACK_PERCENT, 2666,
                    FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 2666,
                    FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 1000,
                    FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 1000),
            5000, Map.ofEntries( // goblet
                    Map.entry(FightProperty.FIGHT_PROP_HP_PERCENT, 1925),
                    Map.entry(FightProperty.FIGHT_PROP_ATTACK_PERCENT, 1925),
                    Map.entry(FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 1900),
                    Map.entry(FightProperty.FIGHT_PROP_FIRE_ADD_HURT, 500),
                    Map.entry(FightProperty.FIGHT_PROP_ELEC_ADD_HURT, 500),
                    Map.entry(FightProperty.FIGHT_PROP_ICE_ADD_HURT, 500),
                    Map.entry(FightProperty.FIGHT_PROP_WATER_ADD_HURT, 500),
                    Map.entry(FightProperty.FIGHT_PROP_WIND_ADD_HURT, 500),
                    Map.entry(FightProperty.FIGHT_PROP_ROCK_ADD_HURT, 500),
                    Map.entry(FightProperty.FIGHT_PROP_GRASS_ADD_HURT, 500),
                    Map.entry(FightProperty.FIGHT_PROP_PHYSICAL_ADD_HURT, 500),
                    Map.entry(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 250)),
            3000, Map.of( // circlet
                    FightProperty.FIGHT_PROP_HP_PERCENT, 2200,
                    FightProperty.FIGHT_PROP_ATTACK_PERCENT, 2200,
                    FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 2200,
                    FightProperty.FIGHT_PROP_CRITICAL, 1000,
                    FightProperty.FIGHT_PROP_CRITICAL_HURT, 1000,
                    FightProperty.FIGHT_PROP_HEAL_ADD, 1000,
                    FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 400));

    static {
        AFFIX.put(FightProperty.FIGHT_PROP_HP, 150);
        AFFIX.put(FightProperty.FIGHT_PROP_ATTACK, 150);
        AFFIX.put(FightProperty.FIGHT_PROP_DEFENSE, 150);
        AFFIX.put(FightProperty.FIGHT_PROP_HP_PERCENT, 100);
        AFFIX.put(FightProperty.FIGHT_PROP_ATTACK_PERCENT, 100);
        AFFIX.put(FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 100);
        AFFIX.put(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 100);
        AFFIX.put(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 100);
        AFFIX.put(FightProperty.FIGHT_PROP_CRITICAL, 75);
        AFFIX.put(FightProperty.FIGHT_PROP_CRITICAL_HURT, 75);
    }

    private RelicWeightDefaults() {}

    /**
     * The main stat weight for a row. The five slot depots use the drop rates above, and a stat a
     * slot cannot roll gets zero. Any other depot pins a single stat, so its row just needs a weight.
     */
    static int mainProp(int depotId, FightProperty prop) {
        var slot = MAIN_PROP.get(depotId);
        if (slot == null) return 1;
        return prop == null ? 0 : slot.getOrDefault(prop, 0);
    }

    /** The substat weight for a row. Stats outside the normal ten only show up in fixed depots. */
    static int affix(FightProperty prop) {
        return prop == null ? 1 : AFFIX.getOrDefault(prop, 1);
    }
}
