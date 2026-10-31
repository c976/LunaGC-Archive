package emu.grasscutter.game.systems;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import java.lang.reflect.Field;
import java.util.*;
import sun.misc.Unsafe;

/** Test fixtures: a bare roster-only server and players that record their packets. */
public final class TestPlayers {
    private TestPlayers() {}

    public static final class TestPlayer extends Player {
        public final List<BasePacket> sent = new ArrayList<>();
        private final TeamManager teamManager;

        public TestPlayer(GameServer server, int uid) {
            setUid(uid);
            setSession(new GameSession(server));
            setSceneLoadState(SceneLoadState.LOADED);
            teamManager = new TeamManager(this);
            server.getPlayers().put(uid, this);
        }

        @Override
        public TeamManager getTeamManager() {
            return teamManager;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            sent.add(packet);
        }

        @Override
        public String getNickname() {
            return "player" + getUid();
        }

        @Override
        public boolean isOnline() {
            return true;
        }
    }

    /**
     * GameServer's constructor binds sockets and starts systems; tests only need its roster. Also
     * installs an empty combat config, which every World reads.
     */
    public static GameServer server() {
        if (GameData.getConfigGlobalCombat() == null) {
            GameData.setConfigGlobalCombat(new ConfigGlobalCombat());
        }
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var server = (GameServer) ((Unsafe) unsafeField.get(null)).allocateInstance(GameServer.class);
            for (var entry : Map.of("players", new HashMap<>(), "worlds", new HashSet<>()).entrySet()) {
                Field field = GameServer.class.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                field.set(server, entry.getValue());
            }
            Field system = GameServer.class.getDeclaredField("multiplayerSystem");
            system.setAccessible(true);
            system.set(server, new MultiplayerSystem(server));
            return server;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
