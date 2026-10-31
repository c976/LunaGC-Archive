package emu.grasscutter.data.excels;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import org.junit.jupiter.api.*;

/** Rows from the 7.1 FetterCharacterCardExcelConfigData: Zibai has a level 1 reward too. */
final class FetterCharacterCardDataTest {
    private static final int AVATAR = 9_900_126;

    @AfterEach
    void cleanup() {
        GameData.getFetterCharacterCardDataMap()
                .values()
                .removeIf(card -> card.getAvatarId() == AVATAR);
    }

    @Test
    void keepsEveryRewardOfOneAvatar() {
        put("{\"rewardId\":241109,\"fetterLevel\":10,\"avatarId\":" + AVATAR + "}");
        put("{\"rewardId\":240103,\"fetterLevel\":1,\"avatarId\":" + AVATAR + "}");

        var rewards = FetterCharacterCardData.rewardsOf(AVATAR);

        assertEquals(2, rewards.size());
        assertEquals(241109, rewards.get(10), "the name card stays the level 10 reward");
        assertEquals(240103, rewards.get(1));
        assertEquals(0, rewards.get(6), "no reward at other levels");
    }

    private static void put(String json) {
        var card = new Gson().fromJson(json, FetterCharacterCardData.class);
        GameData.getFetterCharacterCardDataMap().put(card.getId(), card);
    }
}
