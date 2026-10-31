package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.InvestigationHandbookHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerProgress;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.TakeInvestigationRewardRspOuterClass.TakeInvestigationRewardRsp;
import emu.grasscutter.server.game.GameSession;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

/**
 * Whole-payload validation at the existing handlers, without accounts or external resources.
 * Chapter claims carry the chapter id on field 6 (7.1 TakeInvestigationRewardReq, CmdId 8841).
 */
@ExtendWith(ServerResourceFixture.class)
final class InvestigationRewardPayloadValidationTest {
    private static final int CHAPTER = 10001, TARGET = 60001, REWARD = 991002;
    private RecordingPlayer player;
    private CaptureSession session;
    private Map<Integer, Object> chapters;
    private Map<Integer, List<Object>> targets;
    private Map<Integer, Object> targetsById;
    private Map<Integer, Object> savedTargetsById;
    private Map<Integer, Object> savedChapters;
    private Map<Integer, List<Object>> savedTargets;
    private boolean oldLoaded;
    private RewardData oldReward;

    private static Field helperField(String name) throws Exception {
        var field = InvestigationHandbookHelper.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        chapters = (Map<Integer, Object>) helperField("CHAPTERS_BY_ID").get(null);
        targets = (Map<Integer, List<Object>>) helperField("TARGETS_BY_CHAPTER").get(null);
        savedChapters = new HashMap<>(chapters);
        savedTargets = new HashMap<>(targets);
        targetsById = (Map<Integer, Object>) helperField("TARGETS_BY_ID").get(null);
        savedTargetsById = new HashMap<>(targetsById);
        // Reconciliation visits every loaded target, not just this test's target.
        // Keep unrelated cached resource definitions away from the minimal player mock.
        chapters.clear();
        targets.clear();
        targetsById.clear();
        oldLoaded = helperField("loaded").getBoolean(null);
        helperField("loaded").setBoolean(null, true);
        setChapter(1);
        var targetClass = Class.forName(InvestigationHandbookHelper.class.getName() + "$TargetDef");
        var target =
                new Gson()
                        .fromJson(
                                "{\"id\":60001,\"investigationId\":10001,\"progress\":1,\"rewardId\":991002}",
                                targetClass);
        targets.put(CHAPTER, List.of(target));
        targetsById.put(TARGET, target);
        oldReward = GameData.getRewardDataMap().get(REWARD);
        var reward = new RewardData();
        reward.rewardId = REWARD;
        reward.rewardItemList = List.of(new ItemParamData(201, 20));
        GameData.getRewardDataMap().put(REWARD, reward);
        player = allocate(RecordingPlayer.class);
        player.progress = new PlayerProgress();
        player.inventory = allocate(RecordingInventory.class);
        player.inventory.grants = new ArrayList<>();
        player.inventory.expectedReason = ActionReason.InvestigationReward;
        session = new CaptureSession();
        session.player = player;
        player.setSession(session);
    }

    private void setChapter(int unlockLevel) throws Exception {
        var type = Class.forName(InvestigationHandbookHelper.class.getName() + "$ChapterDef");
        chapters.put(
                CHAPTER,
                new Gson()
                        .fromJson(
                                "{\"id\":10001,\"rewardId\":991002,\"unlockLevel\":"
                                        + unlockLevel
                                        + "}",
                                type));
    }

    @AfterEach
    void restore() throws Exception {
        chapters.clear();
        chapters.putAll(savedChapters);
        targets.clear();
        targets.putAll(savedTargets);
        targetsById.clear();
        targetsById.putAll(savedTargetsById);
        helperField("loaded").setBoolean(null, oldLoaded);
        if (oldReward == null) GameData.getRewardDataMap().remove(REWARD);
        else GameData.getRewardDataMap().put(REWARD, oldReward);
    }

    private static Stream<Arguments> malformedPayloads() {
        // {id} is a valid selected field. {tag} starts that field with varint wire type.
        var cases =
                List.of(
                        "00{id}",
                        "0c{id}",
                        "0b0c00{id}",
                        "0b14{id}",
                        "{id}00",
                        "{id}8080",
                        "{id}0a02ff",
                        "{id}0b",
                        "{id}0b14",
                        "{id}0c",
                        "{id}{end}",
                        "{id}{tag}80",
                        "{id}{wrong}",
                        "{wrong}{id}",
                        "{id}{fixed64}",
                        "{id}{fixed32}",
                        "{id}{group}",
                        "{id}{invalid}");
        return Stream.of(false, true)
                .flatMap(
                        target ->
                                cases.stream()
                                        .map(hex -> Arguments.of(target, payload(target, hex))));
    }

    private static Stream<Arguments> validPayloads() {
        var cases =
                List.of(
                        "{id}",
                        "0802{id}1003",
                        "0a020001{id}1501020304",
                        "0b10030c{id}190102030405060708",
                        "{zero}{id}",
                        "{id}{zero}{id}");
        return Stream.of(false, true)
                .flatMap(
                        target ->
                                cases.stream()
                                        .map(hex -> Arguments.of(target, payload(target, hex))));
    }

    private static String payload(boolean target, String template) {
        return template.replace("{id}", target ? "48e1d403" : "30914e")
                .replace("{tag}", target ? "48" : "30")
                .replace("{zero}", target ? "4800" : "3000")
                // A length-delimited ID is deliberately followed by an otherwise valid varint.
                .replace("{wrong}", target ? "4ae1d403" : "32914e")
                .replace("{fixed64}", (target ? "49" : "31") + "0000000000000000")
                .replace("{fixed32}", (target ? "4d" : "35") + "00000000")
                .replace("{group}", target ? "4b4c" : "3334")
                .replace("{invalid}", target ? "4e" : "36")
                .replace("{end}", target ? "4c" : "34");
    }

    @ParameterizedTest(name = "target={0}, malformed={1}")
    @MethodSource("malformedPayloads")
    void malformedPayloadCannotConsumeAnEarnedReward(boolean target, String hex) throws Exception {
        player.progress.setInvestigationTargetProgress(TARGET, 1);
        var reply = claim(target, hex);
        assertNotEquals(0, reply[1], hex);
        assertEquals(0, reply[0], hex);
        assertTrue(player.inventory.grants.isEmpty(), hex);
        assertEquals(0, player.progress.getInvestigationChapterState(CHAPTER), hex);
        assertEquals(0, player.progress.getInvestigationTargetState(TARGET), hex);
        assertEquals(0, player.saves, hex);
    }

    @ParameterizedTest(name = "target={0}, valid={1}")
    @MethodSource("validPayloads")
    void validUnknownFieldsAndLastScalarValueRemainSupported(boolean target, String hex)
            throws Exception {
        player.progress.setInvestigationTargetProgress(TARGET, 1);
        var reply = claim(target, hex);
        assertEquals(target ? TARGET : CHAPTER, reply[0]);
        assertEquals(0, reply[1]);
        assertEquals(1, player.inventory.grants.size());
        assertEquals(60, player.inventory.grants.get(0).get(0).getCount());
        assertEquals(
                3,
                target
                        ? player.progress.getInvestigationTargetState(TARGET)
                        : player.progress.getInvestigationChapterState(CHAPTER));
        assertEquals(1, player.saves);
    }

    @ParameterizedTest
    @MethodSource("claimKinds")
    void lastScalarZeroDoesNotClaimEarlierEarnedId(boolean target) throws Exception {
        player.progress.setInvestigationTargetProgress(TARGET, 1);
        var reply = claim(target, payload(target, "{id}{zero}"));
        assertEquals(0, reply[0]);
        assertNotEquals(0, reply[1]);
        assertTrue(player.inventory.grants.isEmpty());
        assertEquals(0, player.saves);
    }

    private static Stream<Boolean> claimKinds() {
        return Stream.of(false, true);
    }

    private int[] claim(boolean target, String hex) throws Exception {
        player.inventory.expectedReason =
                target ? ActionReason.InvestigationTargetReward : ActionReason.InvestigationReward;
        if (target) {
            new HandlerTakeInvestigationTargetRewardReq()
                    .handle(session, new byte[0], HexFormat.of().parseHex(hex));
        } else {
            new HandlerTakeInvestigationRewardReq()
                    .handle(session, new byte[0], HexFormat.of().parseHex(hex));
        }
        int opcode =
                target
                        ? emu.grasscutter.net.packet.PacketOpcodes.TakeInvestigationTargetRewardRsp
                        : emu.grasscutter.net.packet.PacketOpcodes.TakeInvestigationRewardRsp;
        var replies = session.sent.stream().filter(packet -> packet.getOpcode() == opcode).toList();
        assertEquals(1, replies.size());
        if (target) {
            var reply =
                    emu.grasscutter.net.proto.TakeInvestigationTargetRewardRspOuterClass
                            .TakeInvestigationTargetRewardRsp.parseFrom(replies.get(0).getData());
            return new int[] {reply.getQuestId(), reply.getRetcode()};
        }
        var reply = TakeInvestigationRewardRsp.parseFrom(replies.get(0).getData());
        return new int[] {reply.getId(), reply.getRetcode()};
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingPlayer extends Player {
        RecordingInventory inventory;
        PlayerProgress progress;
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
        public PlayerProgress getPlayerProgress() {
            return progress;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class RecordingInventory extends Inventory {
        List<List<ItemParamData>> grants;
        ActionReason expectedReason;

        private RecordingInventory() {
            super(null);
        }

        @Override
        public void addItemParamDatas(Collection<ItemParamData> items, ActionReason reason) {
            assertEquals(expectedReason, reason);
            grants.add(List.copyOf(items));
        }
    }

    private static final class CaptureSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();
        Player player;

        private CaptureSession() {
            super(null);
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
