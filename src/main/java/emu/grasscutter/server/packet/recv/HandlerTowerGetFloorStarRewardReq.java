package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerGetFloorStarRewardReqOuterClass.TowerGetFloorStarRewardReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTowerAllDataRsp;
import emu.grasscutter.server.packet.send.PacketTowerCurLevelRecordChangeNotify;
import emu.grasscutter.server.packet.send.PacketTowerFloorRecordChangeNotify;
import emu.grasscutter.server.packet.send.PacketTowerGetFloorStarRewardRsp;

@Opcodes(PacketOpcodes.TowerGetFloorStarRewardReq)
public class HandlerTowerGetFloorStarRewardReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        TowerGetFloorStarRewardReq req =
                TowerGetFloorStarRewardReq.parseFrom(payload == null ? new byte[0] : payload);
        int floorId = req.getFloorId();
        var player = session.getPlayer();
        var tower = player.getTowerManager();

        // Always clear the "continue challenge?" prompt so reward claiming works even if a prior floor-clear left a stale
        // CurLevelRecord (e.g. client still thinks the next floor is active).
        session.send(PacketTowerCurLevelRecordChangeNotify.empty());

        boolean ok = tower.claimFloorStarReward(floorId);
        session.send(new PacketTowerGetFloorStarRewardRsp(floorId, ok ? 0 : -1));

        var record = tower.getRecordMap().get(floorId);
        // Always refresh floor + full abyss data so reward claiming and checkmarks match the claim cursor.
        session.send(
                new PacketTowerFloorRecordChangeNotify(
                        floorId,
                        record != null ? record.getStarCount() : 0,
                        tower.canEnterScheduleFloor(),
                        record));
        session.send(new PacketTowerAllDataRsp(session.getServer().getTowerSystem(), tower));
    }
}
