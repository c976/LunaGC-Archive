package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ChooseCurAvatarTeamRspOuterClass.ChooseCurAvatarTeamRsp;
import emu.grasscutter.server.packet.send.PacketChooseCurAvatarTeamRsp;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The first acknowledgement reaches the real send boundary without sockets or player state. */
@ExtendWith(ServerResourceFixture.class)
final class TeamSwitchResponseProtocolTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void firstDeploymentAcknowledgementCarriesTheSelectedTeam(int teamId) throws Exception {
        var session = new GameSession(null);
        var transport = new RecordingTransport();
        var tunnel = GameSession.class.getDeclaredField("tunnel");
        tunnel.setAccessible(true);
        tunnel.set(session, transport);
        var acknowledgement = new PacketChooseCurAvatarTeamRsp(teamId);
        acknowledgement.shouldEncrypt = false;

        session.send(acknowledgement);

        assertEquals(1, transport.writes.size(), "The first acknowledgement must not be dropped");
        assertEquals(29488, acknowledgement.getOpcode());
        assertEquals(29488, PacketOpcodes.ChooseCurAvatarTeamRsp);
        assertArrayEquals(new byte[] {0x28, (byte) teamId}, acknowledgement.getData());
        assertEquals(teamId, ChooseCurAvatarTeamRsp.parseFrom(acknowledgement.getData()).getCurTeamId());
        assertEquals(5, ChooseCurAvatarTeamRsp.getDescriptor().findFieldByName("cur_team_id").getNumber());
    }

    @Test
    void errorRetcodeUsesSignedInt32Field12() {
        var error = ChooseCurAvatarTeamRsp.newBuilder().setRetcode(-1).build();
        assertArrayEquals(HexFormat.of().parseHex("60ffffffffffffffffff01"), error.toByteArray());
        assertEquals(12, ChooseCurAvatarTeamRsp.getDescriptor().findFieldByName("retcode").getNumber());
    }

    private static final class RecordingTransport implements GameSessionManager.KcpTunnel {
        final List<byte[]> writes = new ArrayList<>();
        @Override public InetSocketAddress getAddress() { return new InetSocketAddress("127.0.0.1", 1); }
        @Override public void writeData(byte[] data) { writes.add(data); }
        @Override public void close() {}
        @Override public int getSrtt() { return 0; }
    }
}
