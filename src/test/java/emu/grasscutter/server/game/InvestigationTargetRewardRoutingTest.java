package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TakeInvestigationTargetRewardRspOuterClass.TakeInvestigationTargetRewardRsp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(ServerResourceFixture.class)
final class InvestigationTargetRewardRoutingTest {
    // Captured from a 7.1 client claiming two Experience objectives.
    private static final int CAPTURED_OPCODE = 2703;

    @ParameterizedTest
    @CsvSource({"48e1d403, 60001", "48e2d403, 60002"})
    void capturedObjectiveClaimIsRegisteredAndAnswered(String hex, int objectiveId) throws Exception {
        var router = new GameServerPacketHandler(PacketHandler.class);
        var session = new CaptureSession();

        router.handle(session, CAPTURED_OPCODE, new byte[0], HexFormat.of().parseHex(hex));

        assertEquals(1, session.sent.size(), "The Experience claim must receive a response");
        assertEquals(CAPTURED_OPCODE, PacketOpcodes.TakeInvestigationTargetRewardReq);
        var packet = session.sent.get(0);
        assertEquals(PacketOpcodes.TakeInvestigationTargetRewardRsp, packet.getOpcode());
        var reply = TakeInvestigationTargetRewardRsp.parseFrom(packet.getData());
        assertEquals(objectiveId, reply.getQuestId());
        // No player: verify routing and rejection without modifying inventory or progress.
        assertTrue(reply.getRetcode() != 0);
    }

    private static final class CaptureSession extends GameSession {
        private final List<BasePacket> sent = new ArrayList<>();

        private CaptureSession() {
            super(null);
            setState(SessionState.ACTIVE);
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }
    }
}
