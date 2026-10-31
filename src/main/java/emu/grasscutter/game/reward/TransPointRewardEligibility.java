package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.PointData;

/** Identifies eligible first-time scene-point activation events. */
public final class TransPointRewardEligibility {
    public enum Kind { WAYPOINT, STATUE, DOMAIN }

    private TransPointRewardEligibility() {}

    /** Point subtype and pre-unlock flags determine eligibility, not payout. */
    public static Kind kindOf(PointData point, boolean isStatue) {
        if (point == null || point.isUnlocked()) return null;
        // Recent statue resources may use an obfuscated $type rather than SceneTransPoint.
        if (isStatue && emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(point)) {
            return Kind.STATUE;
        }
        // Native DungeonEntry points are eligible on actual client activation.
        // rewardDataId in DungeonEntryExcel is not an eligibility switch: story
        // domains can have no native reward ID, while the operator owns the reward.
        if ("DungeonEntry".equals(point.getType())) {
            return "Never".equals(point.getMapVisibility()) ? null : Kind.DOMAIN;
        }
        if (!"SceneTransPoint".equals(point.getType())) return null;
        // PORTAL and unidentified TOWER subtypes are not ordinary waypoints.
        if (point.getPointType() != null) return null;
        if ("Never".equals(point.getMapVisibility())) return null;
        return Kind.WAYPOINT;
    }
}
