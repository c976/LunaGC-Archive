package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.config.ConfigContainer.GameOptions;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarStatePersist;
import emu.grasscutter.game.avatar.AvatarStorage;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketPlayerNicknameNotify;
import emu.grasscutter.server.packet.send.PacketSetPlayerBornDataRsp;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

@ExtendWith(ServerResourceFixture.class)
@ResourceLock("GameData")
final class NativeCharacterBirthTest {
    private static final AtomicInteger NEXT_UID = new AtomicInteger(910000);
    private final Map<Map<?, ?>, Map<?, ?>> originalResources = new IdentityHashMap<>();
    private final List<RecordingSession> sessions = new ArrayList<>();
    private GameOptions.NewAccountIntro previousIntro;
    private boolean previousQuesting;

    @BeforeEach
    void useMinimalTravelerResources() throws Exception {
        previousIntro = GAME_OPTIONS.newAccountIntro;
        previousQuesting = GAME_OPTIONS.questing.enabled;
        GAME_OPTIONS.newAccountIntro = new GameOptions.NewAccountIntro();
        GAME_OPTIONS.questing.enabled = true;
        isolate(GameData.getAvatarDataMap());
        isolate(GameData.getAvatarSkillDepotDataMap());
        addTraveler(10000005, 501, "PlayerBoy");
        addTraveler(10000007, 701, "PlayerGirl");
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void restoreResourcesAndRemoveOnlyTestBootstraps() throws Exception {
        for (var session : sessions) {
            bootstraps().remove(session.player.getUid());
            for (var avatar : session.player.avatars) AvatarStatePersist.afterRecalc(avatar);
        }
        for (var backup : originalResources.entrySet()) {
            Map resources = backup.getKey();
            resources.clear();
            resources.putAll(backup.getValue());
        }
        GAME_OPTIONS.newAccountIntro = previousIntro;
        GAME_OPTIONS.questing.enabled = previousQuesting;
    }

    @ParameterizedTest
    @ValueSource(ints = {10000005, 10000007})
    void chosenTravelerIsSavedAndNativeIntroIsArmedBeforeWorldLogin(int traveler) throws Exception {
        var session = session();
        String nickname = traveler == 10000005 ? "Aether" : "Lumine";

        handle(session, traveler, nickname);

        var player = session.player;
        assertEquals(1, player.avatars.getAvatarCount());
        assertEquals(traveler, player.avatars.getAvatarById(traveler).getAvatarId());
        assertEquals(traveler, player.getMainCharacterId());
        assertEquals(traveler, player.getHeadImage());
        assertEquals(nickname, player.getNickname());
        assertEquals(List.of(traveler), player.teams.getCurrentSinglePlayerTeamInfo().getAvatars());
        assertEquals(traveler, player.savedTraveler);
        assertEquals(traveler, player.savedHeadImage);
        assertEquals(List.of(traveler), player.savedTeam);
        assertEquals(1, player.saves);
        assertEquals(1, player.avatarAdds);
        assertFalse(player.addedToCurrentTeam);
        assertEquals(1, player.mails.size());
        assertEquals(0, player.logins);
        assertNull(player.getWorld());
        assertEquals(SessionState.ACTIVE, session.getState());
        assertTrue(BornIntroGate.isFreshPlayerBootstrap(session));
        assertEquals(2, session.packets.size());
        var response = assertInstanceOf(PacketSetPlayerBornDataRsp.class, session.packets.get(0));
        assertEquals(4385, response.getOpcode());
        assertEquals(12, response.build().length);
        assertInstanceOf(PacketPlayerNicknameNotify.class, session.packets.get(1));
        assertEquals(0, session.closes);
    }

    @Test
    void nonTravelerAvatarIsRejectedWithoutBirthOrNativeIntro() throws Exception {
        var session = session();

        handle(session, 10000002, "Invalid");

        assertErrorResponse(session);
        assertEquals(0, session.player.avatars.getAvatarCount());
        assertEquals(0, session.player.avatarAdds);
        assertEquals(0, session.player.saves);
        assertEquals(0, session.player.logins);
        assertEquals("Unborn", session.player.getNickname());
        assertTrue(session.player.mails.isEmpty());
        assertFalse(BornIntroGate.isFreshPlayerBootstrap(session));
        assertEquals(SessionState.PICKING_CHARACTER, session.getState());
        assertEquals(0, session.closes);
    }

    @Test
    void absentTravelerResourcesRejectBirthAndCloseTheSession() throws Exception {
        GameData.getAvatarDataMap().remove(10000005);
        var session = session();

        handle(session, 10000005, "Aether");

        assertErrorResponse(session);
        assertEquals(0, session.player.avatars.getAvatarCount());
        assertEquals(0, session.player.saves);
        assertFalse(BornIntroGate.isFreshPlayerBootstrap(session));
        assertEquals(1, session.closes);
    }

    @Test
    void repeatedBornRequestDoesNotReplaceTheChosenTravelerOrRestartTheIntro() throws Exception {
        var session = session();
        handle(session, 10000005, "Aether");
        var avatar = session.player.avatars.getAvatarById(10000005);
        var bootstrap = bootstraps().get(session.player.getUid());

        // The router already rejects born requests in ACTIVE; this also checks the handler directly.
        handle(session, 10000007, "Repeated");

        assertEquals(1, session.player.avatars.getAvatarCount());
        assertSame(avatar, session.player.avatars.getAvatarById(10000005));
        assertNull(session.player.avatars.getAvatarById(10000007));
        assertEquals(10000005, session.player.getMainCharacterId());
        assertEquals(10000005, session.player.getHeadImage());
        assertEquals(List.of(10000005), session.player.teams.getCurrentSinglePlayerTeamInfo().getAvatars());
        assertEquals(1, session.player.avatarAdds);
        assertEquals(1, session.player.saves);
        assertEquals(1, session.player.mails.size());
        assertEquals(0, session.player.logins);
        assertEquals(2, session.packets.size());
        assertSame(bootstrap, bootstraps().get(session.player.getUid()));
        assertEquals(SessionState.ACTIVE, session.getState());
    }

    @Test
    void successfulBirthPreservesAnExplicitResponseCmdIdOverride() throws Exception {
        GAME_OPTIONS.newAccountIntro.setPlayerBornDataRsp = 12345;
        var session = session();

        handle(session, 10000007, "Lumine");

        assertEquals(12345, session.packets.get(0).getOpcode());
        assertEquals(12, session.packets.get(0).build().length);
        assertTrue(BornIntroGate.isFreshPlayerBootstrap(session));
        assertEquals(0, session.player.logins);
    }

    private static void handle(RecordingSession session, int avatarId, String nickname) throws Exception {
        var payload = SetPlayerBornDataReq.newBuilder()
                .setAvatarId(avatarId).setNickName(nickname).build().toByteArray();
        new HandlerSetPlayerBornDataReq().handle(session, new byte[0], payload);
    }

    private static void assertErrorResponse(RecordingSession session) throws Exception {
        assertEquals(1, session.packets.size());
        var response = assertInstanceOf(PacketSetPlayerBornDataRsp.class, session.packets.get(0));
        assertEquals(4385, response.getOpcode());
        var data = CodedInputStream.newInstance(response.getData());
        assertEquals(7 << 3, data.readTag());
        assertEquals(-1, data.readInt32());
        assertEquals(0, data.readTag());
    }

    private RecordingSession session() throws Exception {
        var session = allocate(RecordingSession.class);
        var player = allocate(RecordingPlayer.class);
        player.uid = NEXT_UID.incrementAndGet();
        player.nickname = "Unborn";
        player.avatars = new AvatarStorage(player);
        player.teams = new TeamManager(player);
        player.teams.getCurrentSinglePlayerTeamInfo().getAvatars().add(10000002);
        player.mails = new ArrayList<>();
        session.player = player;
        session.packets = new ArrayList<>();
        session.setState(SessionState.PICKING_CHARACTER);
        sessions.add(session);
        return session;
    }

    private static void addTraveler(int avatarId, int depotId, String name) throws Exception {
        var depot = new AvatarSkillDepotData();
        set(depot, "id", depotId);
        set(depot, "skills", List.of());
        set(depot, "talents", List.of());
        set(depot, "elementType", ElementType.None);
        set(depot, "inherentProudSkillOpens", List.of());
        set(depot, "questProudSkillGroupIds", new IntArrayList());
        GameData.getAvatarSkillDepotDataMap().put(depotId, depot);

        var data = new AvatarData();
        set(data, "id", avatarId);
        set(data, "name", name);
        set(data, "fetters", List.of());
        GameData.getAvatarDataMap().put(avatarId, data);
    }

    private void isolate(Map<?, ?> resources) {
        originalResources.put(resources, new HashMap<>(resources));
        resources.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, ?> bootstraps() throws Exception {
        return (Map<Integer, ?>) field(BornIntroGate.class, "FRESH_PLAYER_BOOTSTRAPS").get(null);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
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
        private int uid;
        private AvatarStorage avatars;
        private TeamManager teams;
        private String nickname;
        private int traveler;
        private int headImage;
        private int logins;
        private int saves;
        private int avatarAdds;
        private boolean addedToCurrentTeam;
        private int savedTraveler;
        private int savedHeadImage;
        private List<Integer> savedTeam;
        private List<Mail> mails;

        @Override public int getUid() { return uid; }
        @Override public AvatarStorage getAvatars() { return avatars; }
        @Override public TeamManager getTeamManager() { return teams; }
        @Override public String getNickname() { return nickname; }
        @Override public void setNickname(String nickname) { this.nickname = nickname; }
        @Override public int getMainCharacterId() { return traveler; }
        @Override public void setMainCharacterId(int traveler) { this.traveler = traveler; }
        @Override public int getHeadImage() { return headImage; }
        @Override public void setHeadImage(int headImage) { this.headImage = headImage; }
        @Override public void onLogin() { logins++; }
        @Override public void sendMail(Mail mail) { mails.add(mail); }

        @Override
        public void addAvatar(Avatar avatar, boolean addToCurrentTeam) {
            avatarAdds++;
            addedToCurrentTeam = addToCurrentTeam;
            avatars.getAvatars().put(avatar.getAvatarId(), avatar);
        }

        @Override
        public void save() {
            saves++;
            savedTraveler = traveler;
            savedHeadImage = headImage;
            savedTeam = List.copyOf(teams.getCurrentSinglePlayerTeamInfo().getAvatars());
        }
    }

    private static final class RecordingSession extends GameSession {
        private RecordingPlayer player;
        private List<BasePacket> packets;
        private int closes;

        private RecordingSession() { super(null); }
        @Override public Player getPlayer() { return player; }
        @Override public void send(BasePacket packet) { packets.add(packet); }
        @Override public void close() { closes++; }
    }
}
