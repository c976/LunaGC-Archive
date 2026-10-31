package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.tower.TowerAbyssFix;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerEnterLevelReqOuterClass.TowerEnterLevelReq;
import emu.grasscutter.server.game.GameSession;
import java.lang.reflect.Field;
import java.util.List;

@Opcodes(PacketOpcodes.TowerEnterLevelReq)
public class HandlerTowerEnterLevelReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Player player = session.getPlayer();
        TowerEnterLevelReq req = TowerEnterLevelReq.parseFrom(payload == null ? new byte[0] : payload);
        int enterPointId = req.getEnterPointId() > 0 ? req.getEnterPointId() : 45;
        TeamManager teamManager = player.getTeamManager();

        // Team selection and start are separate client packets. Wait briefly for the selected
        // temporary team so the abyss never silently falls back to the overworld party.
        for (int attempt = 0; attempt < 40 && !hasTemporaryTeam(teamManager); attempt++) {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!hasTemporaryTeam(teamManager)) {
            Grasscutter.getLogger().warn(
                    "TowerEnterLevel aborted uid={}: temporaryTeam not ready", player.getUid());
            return;
        }

        TowerAbyssFix.rememberEnterPoint(player, enterPointId);
        // Live 7.0 TowerEnterLevelReq only has enter_point_id — getIsRestartFloor may be absent.
        // Never call it directly: NoSuchMethodError kills the Netty defaultEventLoop → whitescreen.
        boolean restartFloor = isRestartFloor(req);
        if (restartFloor) {
            // Client restart-after-reconfigure: always chamber 1 of the current floor.
            player.getTowerManager().restartFloorFromChamberOne();
        }
        boolean firstChamber = TowerAbyssFix.isFirstChamber(player) || restartFloor;
        if (!player.getTowerManager().enterLevel(enterPointId)) {
            return;
        }
        try {
            teamManager.useTemporaryTeam(0);
            if (firstChamber) {
                TowerAbyssFix.prepareFirstChamber(player);
            }
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn(
                    "TowerEnterLevel post-handoff failed uid={}: {}",
                    player.getUid(),
                    throwable.toString());
        }
        // Lua already installs option 175/176. Do not reinject option 177 after a delay.
    }

    private static boolean hasTemporaryTeam(TeamManager teamManager) {
        try {
            Field field = TeamManager.class.getDeclaredField("temporaryTeam");
            field.setAccessible(true);
            Object value = field.get(teamManager);
            return value instanceof List<?> list && !list.isEmpty();
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isRestartFloor(TowerEnterLevelReq req) {
        try {
            Object value = req.getClass().getMethod("getIsRestartFloor").invoke(req);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
