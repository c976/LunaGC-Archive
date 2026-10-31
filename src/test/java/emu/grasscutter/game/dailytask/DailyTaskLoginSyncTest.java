package emu.grasscutter.game.dailytask;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.WorldOwnerDailyTaskNotifyOuterClass.WorldOwnerDailyTaskNotify;
import emu.grasscutter.server.game.*;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

/** Stored-board initialization through real Player/GameSession sending, with no socket or DB. */
@ExtendWith(ServerResourceFixture.class)
final class DailyTaskLoginSyncTest {
    private RecordingPlayer player;
    private RecordingManager manager;
    private MemoryTransport transport;

    @BeforeAll
    static void productionOrigin() {
        System.out.println(
                "DailyTaskManager production origin: "
                        + DailyTaskManager.class.getProtectionDomain().getCodeSource().getLocation());
    }

    @BeforeEach
    void setup() throws Exception {
        player = allocate(RecordingPlayer.class);
        player.rank = 12;
        manager =
                new Gson()
                        .fromJson(
                                "{\"ownerUid\":10001,\"cityId\":1,\"lastGenerationDate\":"
                                        + today()
                                        + ",\"scoreRewardTaken\":true,\"dailyTasks\":["
                                        + "{\"taskId\":11001,\"rewardId\":2103,\"finishProgress\":10},"
                                        + "{\"taskId\":10106,\"rewardId\":2103,\"finishProgress\":6},"
                                        + "{\"taskId\":10504,\"rewardId\":2103,\"finishProgress\":1},"
                                        + "{\"taskId\":10200,\"rewardId\":2103,\"finishProgress\":5,\"progress\":5,\"finished\":true}]}",
                                RecordingManager.class);
        attach(manager);
    }

    private void attach(RecordingManager attached) throws Exception {
        manager = attached;
        player.manager = manager;
        manager.setPlayer(player);
        var session = new GameSession(null);
        session.setState(GameSession.SessionState.ACTIVE);
        session.setPlayer(player);
        player.setSession(session);
        transport = new MemoryTransport();
        var tunnel = GameSession.class.getDeclaredField("tunnel");
        tunnel.setAccessible(true);
        tunnel.set(session, transport);
    }

    @Test
    void sameDayLoginDeliversExistingBoardWithoutSaveRerollOrNewCompletion() throws Exception {
        var originalTasks = List.copyOf(manager.getDailyTasks());
        manager.onPlayerLogin();
        assertEquals(List.of(2841, 28004), opcodes());
        assertBoard(1);
        assertEquals(0, manager.saves);
        assertEquals(today(), manager.getLastGenerationDate());
        assertTrue(manager.isScoreRewardTaken());
        for (int i = 0; i < 4; i++) assertSame(originalTasks.get(i), manager.getDailyTasks().get(i));
        assertTrue(manager.getDailyTasks().get(3).isFinished());
        assertEquals(5, manager.getDailyTasks().get(3).getProgress());
    }

    @Test
    void repeatedLoginReannouncesBoardInsteadOfDependingOnGeneration() throws Exception {
        manager.onPlayerLogin();
        transport.writes.clear();
        manager.onPlayerLogin();
        assertEquals(List.of(2841, 28004), opcodes());
        assertBoard(1);
        assertEquals(0, manager.saves);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void restoredPreferenceAndQuotaAreSentExactlyWithoutMigration(int city) throws Exception {
        var gson = new Gson();
        var json = gson.toJsonTree(manager, DailyTaskManager.class).getAsJsonObject();
        json.addProperty("cityId", city);
        var restored = gson.fromJson(json, RecordingManager.class);
        attach(restored);
        manager.onPlayerLogin();
        assertEquals(List.of(2841, 28004), opcodes());
        assertBoard(city);
        assertEquals(city, manager.getCityId());
        assertEquals(0, manager.saves);
        assertTrue(manager.isScoreRewardTaken());
        assertEquals(today(), manager.getLastGenerationDate());
    }

    @Test
    void underRankPlayerIsNotAdvertisedABoardEvenWithInheritedTasks() {
        player.rank = 11;
        manager.onPlayerLogin();
        assertEquals(List.of(2841), opcodes());
        assertEquals(0, manager.saves);
        assertEquals(4, manager.getDailyTasks().size());
    }

    @Test
    void emptyCurrentQuotaDoesNotAdvertiseAnInventedBoard() {
        manager.getDailyTasks().clear();
        manager.onPlayerLogin();
        assertEquals(List.of(2841), opcodes());
        assertEquals(0, manager.saves);
        assertTrue(manager.getDailyTasks().isEmpty());
    }

    @Test
    void incompleteCurrentQuotaIsPreservedRatherThanRerolledOrAdvertisedAsFour() {
        manager.getDailyTasks().remove(3);
        manager.onPlayerLogin();
        assertEquals(List.of(2841), opcodes());
        assertEquals(3, manager.getDailyTasks().size());
        assertEquals(0, manager.saves);
    }

    @Test
    void staleBoardIsNotAdvertisedWhenTodaysGenerationCannotRun() {
        var gson = new Gson();
        var json = gson.toJsonTree(manager, DailyTaskManager.class).getAsJsonObject();
        json.addProperty("lastGenerationDate", today() - 1);
        try {
            attach(gson.fromJson(json, RecordingManager.class));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        manager.onPlayerLogin();
        if (manager.getLastGenerationDate() == today()) return; // Resources allowed a fresh set.
        assertEquals(List.of(2841), opcodes());
        assertEquals(today() - 1, manager.getLastGenerationDate());
    }

    private void assertBoard(int city) throws Exception {
        var frame = transport.writes.get(1);
        var buffer = ByteBuffer.wrap(frame);
        int header = buffer.getShort(4) & 0xffff;
        int size = buffer.getInt(6);
        assertEquals(12 + header + size, frame.length);
        var board =
                WorldOwnerDailyTaskNotify.parseFrom(
                        Arrays.copyOfRange(frame, 10 + header, 10 + header + size));
        assertEquals(city, board.getFilterCityId());
        assertEquals(4, board.getTaskListCount());
        assertEquals(
                List.of(11001, 10106, 10504, 10200),
                board.getTaskListList().stream().map(task -> task.getDailyTaskId()).toList());
        assertEquals(1, board.getFinishedDailyTaskNum());
    }

    private List<Integer> opcodes() {
        return transport.writes.stream()
                .map(bytes -> ByteBuffer.wrap(bytes).getShort(2) & 0xffff)
                .toList();
    }

    private static int today() {
        var date = java.time.LocalDate.now(java.time.ZoneId.systemDefault());
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((Unsafe) f.get(null)).allocateInstance(type));
    }

    private static final class RecordingManager extends DailyTaskManager {
        int saves;

        RecordingManager(Player player) {
            super(player);
        }

        @Override
        public void save() {
            saves++;
        }

        @Override
        public int getScoreRewardId() {
            return 0;
        }
    }

    private static final class RecordingPlayer extends Player {
        int rank;
        DailyTaskManager manager;

        @Override
        public int getUid() {
            return 10001;
        }

        @Override
        public int getLevel() {
            return rank;
        }

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        public Scene getScene() {
            return null;
        }

        @Override
        public DailyTaskManager getDailyTaskManager() {
            return manager;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packet.shouldEncrypt = false; // Test transport only; production framing/send remains real.
            super.sendPacket(packet);
        }
    }

    private static final class MemoryTransport implements GameSessionManager.KcpTunnel {
        final List<byte[]> writes = new ArrayList<>();

        @Override
        public InetSocketAddress getAddress() {
            return new InetSocketAddress("127.0.0.1", 1);
        }

        @Override
        public void writeData(byte[] data) {
            writes.add(data.clone());
        }

        @Override
        public void close() {}

        @Override
        public int getSrtt() {
            return 0;
        }
    }
}
