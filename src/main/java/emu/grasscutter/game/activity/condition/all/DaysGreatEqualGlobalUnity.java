package emu.grasscutter.game.activity.condition.all;

import static emu.grasscutter.game.activity.condition.ActivityConditions.NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL_GLOBAL_UNITY;

import emu.grasscutter.game.activity.condition.ActivityCondition;

/**
 * Days counted from a server-wide open time rather than a per-player one. This server opens every
 * activity at the same moment for everyone, so it is the same count as {@link DaysGreatEqual}.
 */
@ActivityCondition(NEW_ACTIVITY_COND_DAYS_GREAT_EQUAL_GLOBAL_UNITY)
public class DaysGreatEqualGlobalUnity extends DaysGreatEqual {}
