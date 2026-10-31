package emu.grasscutter.game.systems;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static emu.grasscutter.game.systems.TestPlayers.server;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.CoopRequest;
import emu.grasscutter.game.systems.TestPlayers.TestPlayer;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.server.game.GameServer;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
public final class MultiplayerSystemTest {
    private GameServer server;
    private MultiplayerSystem system;
    private TestPlayer host, guest, outsider;
    private World party;
    private int oldCapacity;

    @BeforeEach
    void setup() {
        oldCapacity = GAME_OPTIONS.avatarLimits.multiplayerTeam;
        server = server();
        system = server.getMultiplayerSystem();
        host = new TestPlayer(server, 100);
        guest = new TestPlayer(server, 101);
        outsider = new TestPlayer(server, 200);
        party = world(true, host, guest);
        world(false, outsider);
    }

    @AfterEach
    void restore() {
        GAME_OPTIONS.avatarLimits.multiplayerTeam = oldCapacity;
    }

    @Test
    void onlyTheHostKicksAndOnlyFromTheirOwnWorld() {
        var otherHost = new TestPlayer(server, 300);
        var otherWorld = world(true, otherHost, new TestPlayer(server, 301));

        assertFalse(system.kickPlayer(host, otherHost.getUid()));
        assertFalse(system.kickPlayer(host, outsider.getUid()));
        assertFalse(system.kickPlayer(guest, host.getUid()));
        assertSame(otherWorld, otherHost.getWorld());
        assertSame(party, host.getWorld());
        assertEquals(2, otherWorld.getPlayerCount());
    }

    @Test
    void hostKicksOwnGuest() throws Exception {
        var field = MultiplayerSystem.class.getDeclaredField("guestHostUids");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var guestHostUids = (Map<Integer, Integer>) field.get(system);
        guestHostUids.put(guest.getUid(), host.getUid());
        int sceneId = guest.getSceneId();
        var sceneData = JsonUtils.decode("{\"id\": " + sceneId + "}", SceneData.class);
        GameData.getSceneDataMap().put(sceneId, sceneData);
        guest.setScene(new Scene(party, sceneData));

        try {
            assertTrue(system.kickPlayer(host, guest.getUid()));
        } finally {
            GameData.getSceneDataMap().remove(sceneId);
        }

        assertTrue(guestHostUids.isEmpty());
        assertNotSame(party, guest.getWorld());
    }

    @Test
    void applicationsToSelfOwnWorldOrFullWorldAreRefused() {
        system.applyEnterMp(outsider, outsider.getUid());
        assertTrue(outsider.getCoopRequests().isEmpty());

        system.applyEnterMp(guest, host.getUid());
        assertTrue(host.getCoopRequests().isEmpty());

        GAME_OPTIONS.avatarLimits.multiplayerTeam = 2;
        system.applyEnterMp(outsider, host.getUid());
        assertTrue(host.getCoopRequests().isEmpty());
        assertEquals(2, outsider.sent.size());
    }

    @Test
    void applicationToAGuestIsRefusedUpFront() {
        system.applyEnterMp(outsider, guest.getUid());

        assertTrue(guest.getCoopRequests().isEmpty());
        assertEquals(1, outsider.sent.size());
    }

    @Test
    void refusedReplyConsumesTheRequest() {
        GAME_OPTIONS.avatarLimits.multiplayerTeam = 2;
        host.getCoopRequests().put(outsider.getUid(), new CoopRequest(outsider));

        system.applyEnterMpReply(host, outsider.getUid(), true);

        assertTrue(host.getCoopRequests().isEmpty());
        assertFalse(outsider.getWorld().isMultiplayer());
        assertEquals(1, outsider.sent.size());
        assertEquals(2, party.getPlayerCount());
    }

    @Test
    void guestCannotAcceptRequestsIntoTheHostsWorld() {
        guest.getCoopRequests().put(outsider.getUid(), new CoopRequest(outsider));

        system.applyEnterMpReply(guest, outsider.getUid(), true);

        assertTrue(guest.getCoopRequests().isEmpty());
        assertFalse(outsider.getWorld().isMultiplayer());
        assertEquals(2, party.getPlayerCount());
    }

    private static World world(boolean multiplayer, TestPlayer host, TestPlayer... guests) {
        var world = new World(host, multiplayer);
        var members = new ArrayList<TestPlayer>(List.of(guests));
        members.add(0, host);
        for (var member : members) {
            world.getPlayers().add(member);
            member.setWorld(world);
        }
        return world;
    }
}
