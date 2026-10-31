package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.config.ConfigContainer.GameOptions;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarStorage;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketDoSetPlayerBornDataNotify;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;
import emu.grasscutter.server.packet.send.PacketSetPlayerBornDataRsp;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

@ExtendWith(ServerResourceFixture.class)
final class NativeCharacterSelectionTest {
    private GameOptions.NewAccountIntro previousIntro;
    private ServerRunMode previousRunModeOverride;
    private Object previousRegionCache;

    @BeforeEach
    void useNativeSelectionWithoutStartingARegionServer() throws Exception {
        previousIntro = GAME_OPTIONS.newAccountIntro;
        previousRunModeOverride = (ServerRunMode) field(Grasscutter.class, "runModeOverride").get(null);
        previousRegionCache = field(PacketPlayerLoginRsp.class, "regionCache").get(null);
        GAME_OPTIONS.newAccountIntro = new GameOptions.NewAccountIntro();
        Grasscutter.setRunModeOverride(ServerRunMode.GAME_ONLY);
        field(PacketPlayerLoginRsp.class, "regionCache")
                .set(null, QueryCurrRegionHttpRsp.newBuilder()
                        .setRegionInfo(RegionInfo.getDefaultInstance()).build());
    }

    @AfterEach
    void restoreConfiguration() throws Exception {
        GAME_OPTIONS.newAccountIntro = previousIntro;
        Grasscutter.setRunModeOverride(previousRunModeOverride);
        field(PacketPlayerLoginRsp.class, "regionCache").set(null, previousRegionCache);
    }

    @Test
    void defaultConfigurationWaitsForThePlayersOwnTravelerChoice() {
        var intro = new GameOptions.NewAccountIntro();

        assertTrue(intro.enabled);
        assertEquals(0, intro.doSetPlayerBornDataNotify);
        assertEquals(0, intro.setPlayerBornDataRsp);
        assertEquals(0, intro.fallbackSeconds);
    }

    @Test
    void olderConfigurationWithNoSelectionOptionUsesTheNativeDefault() {
        var options = new Gson().fromJson("{}", GameOptions.class);

        assertTrue(options.newAccountIntro.enabled);
        assertEquals(0, options.newAccountIntro.fallbackSeconds);
    }

    @Test
    void explicitlyDisabledConfigurationAndLegacyFieldsArePreserved() {
        var options = new Gson().fromJson(
                "{\"newAccountIntro\":{\"enabled\":false,\"fallbackSeconds\":15}}", GameOptions.class);

        assertFalse(options.newAccountIntro.enabled);
        assertEquals(15, options.newAccountIntro.fallbackSeconds);
    }

    @Test
    void freshAccountReceivesNativeSelectionAndLoginResponseWithoutBeingBorn() throws Exception {
        var session = session(0);

        new HandlerPlayerLoginReq().handle(session, new byte[0], new byte[0]);

        assertEquals(SessionState.PICKING_CHARACTER, session.getState());
        assertEquals(0, session.player.getAvatars().getAvatarCount());
        assertEquals(0, session.player.getMainCharacterId());
        assertEquals(0, session.player.logins);
        assertEquals(0, session.player.avatarAdds);
        assertEquals(List.of(22899, PacketOpcodes.PlayerLoginRsp), opcodes(session));
        assertInstanceOf(PacketDoSetPlayerBornDataNotify.class, session.packets.get(0));
        assertInstanceOf(PacketPlayerLoginRsp.class, session.packets.get(1));
        assertEquals(0, session.closes);
    }

    @Test
    void freshAccountKeepsAnExplicitNotifyCmdIdOverride() throws Exception {
        GAME_OPTIONS.newAccountIntro.doSetPlayerBornDataNotify = 12345;
        var session = session(0);

        new HandlerPlayerLoginReq().handle(session, new byte[0], new byte[0]);

        assertEquals(SessionState.PICKING_CHARACTER, session.getState());
        assertEquals(List.of(12345, PacketOpcodes.PlayerLoginRsp), opcodes(session));
        assertEquals(0, session.player.logins);
        assertEquals(0, session.player.avatarAdds);
    }

    @ParameterizedTest
    @ValueSource(ints = {10000005, 10000007})
    void existingAccountKeepsItsChosenTravelerAndLogsInNormally(int traveler) throws Exception {
        var session = session(traveler);
        var avatar = session.player.getAvatars().getAvatarById(traveler);

        new HandlerPlayerLoginReq().handle(session, new byte[0], new byte[0]);

        assertEquals(1, session.player.logins);
        assertEquals(SessionState.ACTIVE, session.getState());
        assertEquals(traveler, session.player.getMainCharacterId());
        assertEquals("Existing Traveler", session.player.getNickname());
        assertEquals(1, session.player.getAvatars().getAvatarCount());
        assertSame(avatar, session.player.getAvatars().getAvatarById(traveler));
        assertEquals(0, session.player.avatarAdds);
        assertEquals(List.of(PacketOpcodes.PlayerLoginRsp), opcodes(session));
    }

    @Test
    void bornPacketsUseTheKnown71CmdIdsAndEmptySuccessPayload() {
        assertEquals(26105, PacketOpcodes.SetPlayerBornDataReq);
        assertEquals(22899, PacketOpcodes.DoSetPlayerBornDataNotify);
        assertEquals(4385, PacketOpcodes.SetPlayerBornDataRsp);
        assertEquals(PacketOpcodes.SetPlayerBornDataRsp, PacketSetPlayerBornDataRsp.CMD_ID);

        var notify = new PacketDoSetPlayerBornDataNotify();
        var response = new PacketSetPlayerBornDataRsp();
        assertEquals(PacketOpcodes.DoSetPlayerBornDataNotify, notify.getOpcode());
        assertEquals(PacketOpcodes.SetPlayerBornDataRsp, response.getOpcode());
        assertEquals(12, notify.build().length);
        assertEquals(12, response.build().length);
    }

    private static List<Integer> opcodes(RecordingSession session) {
        return session.packets.stream().map(BasePacket::getOpcode).toList();
    }

    private static RecordingSession session(int traveler) throws Exception {
        var session = allocate(RecordingSession.class);
        var player = allocate(RecordingPlayer.class);
        player.avatars = new AvatarStorage(player);
        player.traveler = traveler;
        player.nickname = "Existing Traveler";
        player.session = session;
        if (traveler != 0) player.avatars.getAvatars().put(traveler, allocate(Avatar.class));
        session.player = player;
        session.account = allocate(Account.class);
        session.packets = new ArrayList<>();
        session.setState(SessionState.WAITING_FOR_LOGIN);
        return session;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        return type.cast(unsafe.allocateInstance(type));
    }

    private static final class RecordingPlayer extends Player {
        private AvatarStorage avatars;
        private int traveler;
        private String nickname;
        private RecordingSession session;
        private int logins;
        private int avatarAdds;

        @Override
        public AvatarStorage getAvatars() {
            return avatars;
        }

        @Override
        public int getUid() {
            return 900001;
        }

        @Override
        public int getMainCharacterId() {
            return traveler;
        }

        @Override
        public String getNickname() {
            return nickname;
        }

        @Override
        public void onLogin() {
            logins++;
            session.setState(SessionState.ACTIVE);
        }

        @Override
        public void addAvatar(Avatar avatar, boolean addToCurrentTeam) {
            avatarAdds++;
            fail("Native selection must not automatically create a Traveler");
        }

        @Override
        public void save() {
            fail("Login must not change a fresh selection or an existing Traveler");
        }
    }

    private static final class RecordingSession extends GameSession {
        private RecordingPlayer player;
        private Account account;
        private List<BasePacket> packets;
        private int closes;

        private RecordingSession() {
            super(null);
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public Account getAccount() {
            return account;
        }

        @Override
        public void send(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public void close() {
            closes++;
        }
    }
}
