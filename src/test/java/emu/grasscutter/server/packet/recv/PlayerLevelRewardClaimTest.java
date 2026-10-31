package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.PlayerLevelData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TakePlayerLevelRewardRspOuterClass.TakePlayerLevelRewardRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameServerPacketHandler;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

/** Captured 7.1 clicks through the real router, with isolated resources and inventory. */
@ExtendWith(ServerResourceFixture.class)
final class PlayerLevelRewardClaimTest {
    // User confirmed Katheryne's rank 2/3/4 clicks on 2026-10-10; independent of constants.
    private static final int CAPTURED_OPCODE = 7321;
    private RecordingPlayer player;
    private CaptureSession session;
    private GameServerPacketHandler router;
    private final Map<Integer, PlayerLevelData> oldLevels = new HashMap<>();
    private RewardData oldReward;

    @BeforeEach
    void setup() throws Exception {
        for (int level : List.of(2, 3, 4, 5, 6)) {
            oldLevels.put(level, GameData.getPlayerLevelDataMap().get(level));
            GameData.getPlayerLevelDataMap()
                    .put(
                            level,
                            new Gson()
                                    .fromJson(
                                            "{\"level\":" + level + ",\"rewardId\":991001}",
                                            PlayerLevelData.class));
        }
        oldReward = GameData.getRewardDataMap().get(991001);
        var reward = new RewardData();
        reward.rewardId = 991001;
        reward.rewardItemList = List.of(new ItemParamData(201, 100));
        GameData.getRewardDataMap().put(reward.rewardId, reward);
        player = allocate(RecordingPlayer.class);
        player.inventory = allocate(RecordingInventory.class);
        player.inventory.grants = new ArrayList<>();
        player.setRewardedLevels(new HashSet<>());
        session = new CaptureSession();
        session.player = player;
        player.setSession(session);
        router = new GameServerPacketHandler(PacketHandler.class);
    }

    @AfterEach
    void restore() {
        oldLevels.forEach(
                (id, row) -> {
                    if (row == null) GameData.getPlayerLevelDataMap().remove((int) id);
                    else GameData.getPlayerLevelDataMap().put((int) id, row);
                });
        if (oldReward == null) GameData.getRewardDataMap().remove(991001);
        else GameData.getRewardDataMap().put(991001, oldReward);
    }

    @Test
    void earnedRewardPaysOnceAndRetryIsAnswered() throws Exception {
        claim(5);
        assertEquals(0, reply().getRetcode());
        assertEquals(991001, reply().getRewardId());
        assertEquals(1, player.inventory.grants.size());
        assertTrue(player.getRewardedLevels().contains(5));
        assertEquals(1, player.saves);
        session.sent.clear();
        claim(5);
        assertEquals(0, reply().getRetcode(), "A duplicate must be answered without paying again");
        assertEquals(1, player.inventory.grants.size());
        assertEquals(1, player.saves);
    }

    @Test
    void futureRankIsRejectedWithoutPaying() throws Exception {
        claim(6);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
        assertTrue(player.getRewardedLevels().isEmpty());
    }

    @Test
    void unknownRankIsAnsweredWithoutThrowing() throws Exception {
        claim(9999);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
    }

    @Test
    void missingRewardDataDoesNotConsumeTheClaim() throws Exception {
        GameData.getRewardDataMap().remove(991001);
        claim(5);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.getRewardedLevels().isEmpty());
    }

    @Test
    void nullPlayerIsRejectedAndAnswered() throws Exception {
        session.player = null;
        claim(5);
        assertNotEquals(0, reply().getRetcode());
    }

    @ParameterizedTest
    @CsvSource({"7802, 2", "7803, 3", "7804, 4"})
    void capturedKatheryneClicksRouteDecodeAndPayOnce(String hex, int level) throws Exception {
        route(hex);
        assertEquals(CAPTURED_OPCODE, PacketOpcodes.TakePlayerLevelRewardReq);
        assertEquals(0, reply().getRetcode());
        assertEquals(level, reply().getLevel());
        assertEquals(991001, reply().getRewardId());
        assertEquals(1, player.inventory.grants.size());
        assertEquals(Set.of(level), player.getRewardedLevels());
        session.sent.clear();
        route(hex);
        assertEquals(0, reply().getRetcode());
        assertEquals(1, player.inventory.grants.size());
        assertEquals(1, player.saves);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "5805", "7a0105", "780580", "7c"})
    void missingWrongWireTypeOrMalformedRankIsAnsweredWithoutPaying(String hex) throws Exception {
        route(hex);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
        assertTrue(player.getRewardedLevels().isEmpty());
    }

    @Test
    void unknownFieldsAreSkippedAndLastRankWins() throws Exception {
        route("08017805");
        assertEquals(0, reply().getRetcode());
        assertEquals(5, reply().getLevel());
        session.sent.clear();
        route("78057806");
        assertNotEquals(0, reply().getRetcode());
        assertEquals(1, player.inventory.grants.size(), "A trailing future rank must not pay");
    }

    private void claim(int level) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        output.writeUInt32(15, level);
        output.flush();
        router.handle(session, CAPTURED_OPCODE, new byte[0], bytes.toByteArray());
    }

    private void route(String hex) {
        router.handle(session, CAPTURED_OPCODE, new byte[0], HexFormat.of().parseHex(hex));
    }

    private TakePlayerLevelRewardRsp reply() throws Exception {
        assertEquals(1, session.sent.size(), "Every claim must receive a response");
        assertEquals(PacketOpcodes.TakePlayerLevelRewardRsp, session.sent.get(0).getOpcode());
        return TakePlayerLevelRewardRsp.parseFrom(session.sent.get(0).getData());
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingPlayer extends Player {
        RecordingInventory inventory;
        int saves;

        @Override
        public int getLevel() {
            return 5;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class RecordingInventory extends Inventory {
        List<List<ItemParamData>> grants;

        private RecordingInventory() {
            super(null);
        }

        @Override
        public void addItemParamDatas(Collection<ItemParamData> items, ActionReason reason) {
            assertEquals(ActionReason.PlayerUpgradeReward, reason);
            grants.add(List.copyOf(items));
        }
    }

    private static final class CaptureSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();
        Player player;

        private CaptureSession() {
            super(null);
            setState(SessionState.ACTIVE);
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }
    }
}
