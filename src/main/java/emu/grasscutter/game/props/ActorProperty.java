package emu.grasscutter.game.props;

import java.util.Map;

public final class ActorProperty {

    private static final Map<String, FightProperty> FIGHT_PROPS =
            Map.ofEntries(
                    Map.entry("Actor_AttackRatio", FightProperty.FIGHT_PROP_ATTACK_PERCENT),
                    Map.entry("Actor_AttackDelta", FightProperty.FIGHT_PROP_ATTACK),
                    Map.entry("Actor_AttackExtraDelta", FightProperty.FIGHT_PROP_ATTACK),
                    Map.entry("Actor_DefenceRatio", FightProperty.FIGHT_PROP_DEFENSE_PERCENT),
                    Map.entry("Actor_DefenceDelta", FightProperty.FIGHT_PROP_DEFENSE),
                    Map.entry("Actor_MaxHPRatio", FightProperty.FIGHT_PROP_HP_PERCENT),
                    Map.entry("Actor_ElemMasteryDelta", FightProperty.FIGHT_PROP_ELEMENT_MASTERY),
                    Map.entry("Actor_ElemMasteryExtraDelta", FightProperty.FIGHT_PROP_ELEMENT_MASTERY),
                    Map.entry("Actor_CriticalDelta", FightProperty.FIGHT_PROP_CRITICAL),
                    Map.entry("Actor_CriticalHurtDelta", FightProperty.FIGHT_PROP_CRITICAL_HURT),
                    Map.entry("Actor_AntiCriticalDelta", FightProperty.FIGHT_PROP_ANTI_CRITICAL),
                    Map.entry("Actor_ChargeEfficiencyDelta", FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY),
                    Map.entry("Actor_HealAddDelta", FightProperty.FIGHT_PROP_HEAL_ADD),
                    Map.entry("Actor_HealedAddDelta", FightProperty.FIGHT_PROP_HEALED_ADD),
                    Map.entry("Actor_AddHurtDelta", FightProperty.FIGHT_PROP_ADD_HURT),
                    Map.entry("Actor_SubHurtDelta", FightProperty.FIGHT_PROP_SUB_HURT),
                    Map.entry("Actor_SkillCDMinusRatio", FightProperty.FIGHT_PROP_SKILL_CD_MINUS_RATIO),
                    Map.entry("Actor_ShieldCostMinusRatio", FightProperty.FIGHT_PROP_SHIELD_COST_MINUS_RATIO),
                    Map.entry("Actor_HitHeadDmgRatio", FightProperty.FIGHT_PROP_HIT_HEAD_ADD_HURT),
                    Map.entry("Actor_DefenceIgnoreRatio", FightProperty.FIGHT_PROP_DEFENCE_IGNORE_RATIO),
                    Map.entry("Actor_DefenceIgnoreDelta", FightProperty.FIGHT_PROP_DEFENCE_IGNORE_DELTA),
                    Map.entry("Actor_PhysicalAddHurtDelta", FightProperty.FIGHT_PROP_PHYSICAL_ADD_HURT),
                    Map.entry("Actor_PhysicalSubHurtDelta", FightProperty.FIGHT_PROP_PHYSICAL_SUB_HURT),
                    Map.entry("Actor_FireAddHurtDelta", FightProperty.FIGHT_PROP_FIRE_ADD_HURT),
                    Map.entry("Actor_ElecAddHurtDelta", FightProperty.FIGHT_PROP_ELEC_ADD_HURT),
                    Map.entry("Actor_WaterAddHurtDelta", FightProperty.FIGHT_PROP_WATER_ADD_HURT),
                    Map.entry("Actor_GrassAddHurtDelta", FightProperty.FIGHT_PROP_GRASS_ADD_HURT),
                    Map.entry("Actor_WindAddHurtDelta", FightProperty.FIGHT_PROP_WIND_ADD_HURT),
                    Map.entry("Actor_RockAddHurtDelta", FightProperty.FIGHT_PROP_ROCK_ADD_HURT),
                    Map.entry("Actor_IceAddHurtDelta", FightProperty.FIGHT_PROP_ICE_ADD_HURT),
                    Map.entry("Actor_FireSubHurtDelta", FightProperty.FIGHT_PROP_FIRE_SUB_HURT),
                    Map.entry("Actor_ElecSubHurtDelta", FightProperty.FIGHT_PROP_ELEC_SUB_HURT),
                    Map.entry("Actor_WaterSubHurtDelta", FightProperty.FIGHT_PROP_WATER_SUB_HURT),
                    Map.entry("Actor_GrassSubHurtDelta", FightProperty.FIGHT_PROP_GRASS_SUB_HURT),
                    Map.entry("Actor_WindSubHurtDelta", FightProperty.FIGHT_PROP_WIND_SUB_HURT),
                    Map.entry("Actor_RockSubHurtDelta", FightProperty.FIGHT_PROP_ROCK_SUB_HURT),
                    Map.entry("Actor_IceSubHurtDelta", FightProperty.FIGHT_PROP_ICE_SUB_HURT));

    public static FightProperty getFightProperty(String key) {
        return FIGHT_PROPS.get(key);
    }

    private ActorProperty() {}
}
