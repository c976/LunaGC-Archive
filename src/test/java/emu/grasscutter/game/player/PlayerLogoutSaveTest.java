package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.friends.FriendsList;
import org.junit.jupiter.api.Test;

class PlayerLogoutSaveTest {
    @Test
    void logoutStillSavesWhenCleanupAndPlayerSaveFail() {
        var player = new FailingPlayer();

        player.onLogout();

        assertEquals(1, player.saves);
        assertEquals(1, player.friends.saves);
        assertEquals(1, player.removals);
    }

    private static final class FailingPlayer extends Player {
        final RecordingFriends friends = new RecordingFriends(this);
        int saves;
        int removals;

        // There is no session, so the cleanup before the saves throws.
        @Override
        public void save() {
            saves++;
            throw new IllegalStateException("save rejected");
        }

        @Override
        public FriendsList getFriendsList() {
            return friends;
        }

        @Override
        public void removeFromServer() {
            removals++;
        }
    }

    private static final class RecordingFriends extends FriendsList {
        int saves;

        RecordingFriends(Player player) {
            super(player);
        }

        @Override
        public void save() {
            saves++;
        }
    }
}
