package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class GameSessionCloseTest {
    @Test
    void closeLogsOutOnceAndAlwaysNotifiesAndDisconnects() {
        var player = new FailingLogoutPlayer();
        var session = new RecordingSession(player);
        session.onConnected(new FakeTunnel());

        assertThrows(IllegalStateException.class, session::handleClose);
        assertEquals(1, session.sent);
        assertFalse(session.isConnected());

        // The transport's own close callback arrives after shutdown already closed the session.
        session.handleClose();
        assertEquals(1, player.logouts);
        assertEquals(1, session.sent);
    }

    private static final class FailingLogoutPlayer extends Player {
        int logouts;

        @Override
        public void onLogout() {
            logouts++;
            throw new IllegalStateException("save rejected");
        }
    }

    private static final class RecordingSession extends GameSession {
        private final Player player;
        int sent;

        RecordingSession(Player player) {
            super(null);
            this.player = player;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void send(BasePacket packet) {
            sent++;
        }
    }

    private static final class FakeTunnel implements GameSessionManager.KcpTunnel {
        @Override
        public InetSocketAddress getAddress() {
            return new InetSocketAddress("127.0.0.1", 22102);
        }

        @Override
        public void writeData(byte[] bytes) {}

        @Override
        public void close() {}

        @Override
        public int getSrtt() {
            return 0;
        }
    }
}
