package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceType.LoadPriority;
import it.unimi.dsi.fastutil.ints.*;

/**
 * One friendship reward of an avatar. Most avatars have only the level 10 name card, but some
 * have more rows (Zibai and Linnea at level 1, Aino at level 6), so rows are keyed by avatar and
 * level rather than by avatar alone.
 */
@ResourceType(name = "FetterCharacterCardExcelConfigData.json", loadPriority = LoadPriority.HIGHEST)
public class FetterCharacterCardData extends GameResource {
    private int avatarId;
    private int fetterLevel;
    private int rewardId;

    @Override
    public int getId() {
        return avatarId * 100 + fetterLevel;
    }

    public int getAvatarId() {
        return avatarId;
    }

    public int getFetterLevel() {
        return fetterLevel;
    }

    public int getRewardId() {
        return rewardId;
    }

    /** Fetter level to reward id for one avatar. */
    public static Int2IntMap rewardsOf(int avatarId) {
        var rewards = new Int2IntOpenHashMap();
        for (var card : GameData.getFetterCharacterCardDataMap().values()) {
            if (card.avatarId == avatarId) rewards.put(card.fetterLevel, card.rewardId);
        }
        return rewards;
    }

    @Override
    public void onLoad() {}
}
