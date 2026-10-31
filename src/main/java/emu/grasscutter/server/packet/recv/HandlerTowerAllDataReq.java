package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.tower.TowerAbyssFix;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.game.player.EntryNotice;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketTowerAllDataRsp;
import emu.grasscutter.server.packet.send.PacketTowerCurLevelRecordChangeNotify;
import java.util.TreeMap;

@Opcodes(PacketOpcodes.TowerAllDataReq)
public class HandlerTowerAllDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        var towerManager = player.getTowerManager();

        // Sent once, when the abyss screen is opened. Which floors the client will let you into is
        // decided entirely from this reply, so a floor showing locked is either missing its record
        // here or missing the entrance flag - turn this up to see which.
        if (Grasscutter.getLogger().isDebugEnabled()) {
            var stars = new TreeMap<Integer, Integer>();
            towerManager
                    .getRecordMap()
                    .forEach((floorId, record) -> stars.put(floorId, record.getStarCount()));
            Grasscutter.getLogger()
                    .debug(
                            "Tower all-data: entrance cleared={}, floor stars={}",
                            towerManager.canEnterScheduleFloor(),
                            stars);
        }

        // Outside an active chamber challenge, clear the stale floor/chamber and "continue challenge?" state
        // so claiming rewards works
        // after clearing chamber 1, leaving to claim and then finishing, and after clearing a floor, where
        // the next floor used to stay armed.
        if (!TowerAbyssFix.isInTowerDungeon(player) || !towerManager.isInProgress()) {
            session.send(PacketTowerCurLevelRecordChangeNotify.empty());
        }

        EntryNotice.sendOnce(player);

        session.send(
                new PacketTowerAllDataRsp(session.getServer().getTowerSystem(), towerManager));
    }
}
