package emu.grasscutter.game.friends;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.game.systems.TestPlayers.TestPlayer;
import emu.grasscutter.net.proto.DealAddFriendResultTypeOuterClass.DealAddFriendResultType;
import emu.grasscutter.net.proto.DealAddFriendRspOuterClass.DealAddFriendRsp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class FriendsListTest {
    private static final DealAddFriendResultType ACCEPT =
            DealAddFriendResultType.DealAddFriendResultType_DEAL_ADD_FRIEND_ACCEPT;
    private static final DealAddFriendResultType DECLINE =
            DealAddFriendResultType.DealAddFriendResultType_DEAL_ADD_FRIEND_REJECT;

    private TestPlayer asker, recipient;
    private Record mine, theirs;

    @BeforeEach
    void setup() {
        var server = TestPlayers.server();
        asker = new TestPlayer(server, 100);
        recipient = new TestPlayer(server, 200);
        mine = new Record(recipient, asker, asker);
        theirs = new Record(asker, recipient, asker);
        recipient.getFriendsList().addPendingFriend(mine);
        asker.getFriendsList().addPendingFriend(theirs);
    }

    @Test
    void acceptClearsBothPendingListsAndMakesFriends() {
        recipient.getFriendsList().handleFriendRequest(asker.getUid(), ACCEPT);

        assertTrue(recipient.getFriendsList().getPendingFriends().isEmpty());
        assertTrue(asker.getFriendsList().getPendingFriends().isEmpty());
        assertSame(mine, recipient.getFriendsList().getFriends().get(asker.getUid()));
        assertSame(theirs, asker.getFriendsList().getFriends().get(recipient.getUid()));
        assertEquals(0, mine.deletes + theirs.deletes);
    }

    @Test
    void declineClearsBothPendingLists() {
        recipient.getFriendsList().handleFriendRequest(asker.getUid(), DECLINE);

        assertTrue(recipient.getFriendsList().getPendingFriends().isEmpty());
        assertTrue(asker.getFriendsList().getPendingFriends().isEmpty());
        assertEquals(1, mine.deletes);
        assertEquals(1, theirs.deletes);
    }

    @Test
    void declineAfterTheAskerAlreadyBefriendedReportsAccept() throws Exception {
        asker.getFriendsList().getPendingFriends().remove(recipient.getUid());
        theirs.setIsFriend(true);
        asker.getFriendsList().addFriend(theirs);

        recipient.getFriendsList().handleFriendRequest(asker.getUid(), DECLINE);

        var rsp = DealAddFriendRsp.parseFrom(recipient.sent.get(recipient.sent.size() - 1).getData());
        assertEquals(ACCEPT, rsp.getDealAddFriendResult());
        assertTrue(mine.isFriend());
        assertEquals(0, mine.deletes + theirs.deletes);
    }

    private static final class Record extends Friendship {
        int deletes;

        Record(Player owner, Player friend, Player asker) {
            super(owner, friend, asker);
        }

        @Override
        public void save() {}

        @Override
        public void delete() {
            deletes++;
        }
    }
}
