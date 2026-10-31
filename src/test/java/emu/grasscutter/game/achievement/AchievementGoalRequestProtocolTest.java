package emu.grasscutter.game.achievement;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TakeAchievementGoalRewardReqOuterClass.TakeAchievementGoalRewardReq;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.HandlerTakeAchievementGoalRewardReq;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

/**
 * Synthetic category requests exercise real dispatch and full framing before the reward boundary.
 */
@ExtendWith(ServerResourceFixture.class)
final class AchievementGoalRequestProtocolTest {
    private RecordingAchievements achievements;
    private CaptureSession session;

    @BeforeEach
    void setup() throws Exception {
        achievements = new RecordingAchievements();
        var player = allocate(RecordingPlayer.class);
        player.achievements = achievements;
        session = new CaptureSession(player);
    }

    @Test
    void canonicalCategoryGiftReachesGoalBoundaryThroughDispatch() {
        new GameServerPacketHandler(PacketHandler.class)
                .handle(session, 28295, new byte[0], HexFormat.of().parseHex("72010a"));
        assertEquals(28295, PacketOpcodes.TakeAchievementGoalRewardReq);
        assertEquals(1, achievements.calls);
        assertEquals(List.of(10), achievements.ids);
    }

    @Test
    void canonicalDescriptorAndSerializationMatchSyntheticFixture() throws Exception {
        var descriptor = TakeAchievementGoalRewardReq.getDescriptor();
        assertEquals(1, descriptor.getFields().size());
        var field = descriptor.findFieldByName("id_list");
        assertEquals(14, field.getNumber());
        assertTrue(field.isRepeated());
        assertTrue(field.isPacked());
        assertEquals("UINT32", field.getType().name());
        assertEquals(
                List.of(10),
                TakeAchievementGoalRewardReq.parseFrom(HexFormat.of().parseHex("72010a")).getIdListList());
        assertArrayEquals(
                HexFormat.of().parseHex("72010a"),
                TakeAchievementGoalRewardReq.newBuilder().addIdList(10).build().toByteArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"72010a", "700a", "080172010a", "72010a1801", "72010a220178", "72010a2d01000000", "72010a08ffffffffffffffffff01", "72010a290100000000000000"})
    void packedUnpackedAndUnknownFieldsAreAccepted(String hex) throws Exception {
        handle(HexFormat.of().parseHex(hex));
        assertEquals(1, achievements.calls);
        assertEquals(List.of(10), achievements.ids);
        assertTrue(session.sent.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "52010a",
                "72010a00",
                "72010a8080",
                "72010a1c",
                "72020a",
                "750a000000",
                "74",
                "720100",
                "72020a0a",
                "72010a700a",
                "70ffffffff0f",
                "708a80808010",
                "708a808080808080808002",
                "728080808008",
                "7281808080100a",
                "0a808080801072010a",
                "0a8080808010",
                "0b72010a0c",
                "72010a0b0c",
                "f0808080100a",
                "f280808010010a",
                "00",
                "72010a0880808080808080808002",
                "72010a08ffffffffffffffffff7f"
            })
    void malformedMissingDuplicateAndOutOfRangeIdsNeverReachRewards(String hex) throws Exception {
        handle(HexFormat.of().parseHex(hex));
        rejected();
    }

    @Test
    void nullOversizedAndOverlongBatchesNeverReachRewards() throws Exception {
        for (byte[] bytes : new byte[][] {null, new byte[4097], batch(65)}) {
            session.sent.clear();
            handle(bytes);
            rejected();
        }
    }

    @Test
    void maximumBoundedBatchReachesBoundaryOnce() throws Exception {
        handle(batch(64));
        assertEquals(1, achievements.calls);
        assertEquals(64, achievements.ids.size());
    }

    @Test
    void missingPlayerOrAchievementsFailsClosed() throws Exception {
        session.player = null;
        handle(HexFormat.of().parseHex("72010a"));
        rejected();
        session.sent.clear();
        session.player = allocate(RecordingPlayer.class);
        handle(HexFormat.of().parseHex("72010a"));
        rejected();
    }

    private void handle(byte[] payload) throws Exception {
        new HandlerTakeAchievementGoalRewardReq().handle(session, new byte[0], payload);
    }

    private void rejected() {
        assertEquals(0, achievements.calls);
        assertEquals(1, session.sent.size());
        // The request fix does not change the response builder or claim/delivery semantics.
        assertEquals(PacketOpcodes.TakeAchievementGoalRewardRsp, session.sent.get(0).getOpcode());
    }

    private static byte[] batch(int size) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        for (int i = 1; i <= size; i++) output.writeUInt32(14, i);
        output.flush();
        return bytes.toByteArray();
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingAchievements extends Achievements {
        int calls;
        List<Integer> ids;

        RecordingAchievements() {
            super(null, 10001, null, new HashMap<>(), 0, new ArrayList<>());
        }

        @Override
        public synchronized void takeGoalReward(List<Integer> ids) {
            calls++;
            this.ids = List.copyOf(ids);
        }
    }

    private static final class RecordingPlayer extends Player {
        Achievements achievements;

        @Override
        public Achievements getAchievements() {
            return achievements;
        }
    }

    private static final class CaptureSession extends GameSession {
        Player player;
        final List<BasePacket> sent = new ArrayList<>();

        CaptureSession(Player player) {
            super(null);
            this.player = player;
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
