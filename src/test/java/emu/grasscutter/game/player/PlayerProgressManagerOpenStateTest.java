package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.data.excels.OpenStateData;
import org.junit.jupiter.api.Test;

final class PlayerProgressManagerOpenStateTest {
    private static OpenStateData state(String json) {
        return new Gson().fromJson(json, OpenStateData.class);
    }

    @Test
    void loginBulkUnlockIsOptInEvenWhenQuestingIsOff() {
        var defaultConfig = new ConfigContainer.GameOptions();
        assertFalse(defaultConfig.unlockAllOpenStatesOnLogin);
        assertFalse(defaultConfig.questing.enabled);

        var configured = new Gson().fromJson(
                "{\"server\":{\"game\":{\"gameOptions\":{\"questing\":{\"enabled\":false},"
                        + "\"unlockAllOpenStatesOnLogin\":true}}}}",
                ConfigContainer.class);
        assertTrue(configured.server.game.gameOptions.unlockAllOpenStatesOnLogin);
    }

    @Test
    void realSevenOneQuestConditionIsDecoded() {
        var quest = state(
                "{\"id\":31,\"cond\":[{\"cond_type\":\"OPEN_STATE_COND_QUEST\",\"param\":2010001},{}]}");
        assertEquals(OpenStateData.OpenStateCondType.OPEN_STATE_COND_QUEST,
                quest.getCond().get(0).getCondType());
        assertEquals(2010001, quest.getCond().get(0).getParam());
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(quest));
        assertFalse(PlayerProgressManager.isDefaultOpenState(quest));
    }

    @Test
    void legacyConditionFieldNameRemainsSupported() {
        var level = state(
                "{\"id\":30,\"cond\":[{\"condType\":\"OPEN_STATE_COND_PLAYER_LEVEL\",\"param\":2}]}");
        assertEquals(OpenStateData.OpenStateCondType.OPEN_STATE_COND_PLAYER_LEVEL,
                level.getCond().get(0).getCondType());
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(level));
    }

    @Test
    void emptyConditionsCannotOpenNonDefaultFeatures() {
        var absent = state("{\"id\":47,\"cond\":[{},{}]}");
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(absent));
        assertFalse(PlayerProgressManager.isDefaultOpenState(absent));
    }

    @Test
    void actualDefaultsAndPaimonRemainOpen() {
        var defaultState = state("{\"id\":10,\"defaultState\":true,\"cond\":[]}");
        assertTrue(PlayerProgressManager.isDefaultOpenState(defaultState));
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(defaultState));

        var paimon = state("{\"id\":1,\"allowClientOpen\":true,\"cond\":[]}");
        assertTrue(PlayerProgressManager.isDefaultOpenState(paimon));
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(paimon));

        var clientDefault = state(
                "{\"id\":7,\"defaultState\":true,\"allowClientOpen\":true,\"cond\":[]}");
        assertFalse(PlayerProgressManager.isDefaultOpenState(clientDefault));
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(clientDefault));
    }
}
