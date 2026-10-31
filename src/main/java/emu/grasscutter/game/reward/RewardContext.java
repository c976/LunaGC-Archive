package emu.grasscutter.game.reward;

/** Source metadata for resolving regional items at reward grant time. */
public record RewardContext(int sceneId, int areaId, String sourceRegion) {
    public static final RewardContext NONE = new RewardContext(0, 0, null);
}
