package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.tower.TowerAbyssFix;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.TowerSurrenderReqOuterClass.TowerSurrenderReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTowerCurLevelRecordChangeNotify;
import emu.grasscutter.server.packet.send.PacketTowerMiddleLevelChangeTeamNotify;
import emu.grasscutter.server.packet.send.PacketTowerSurrenderRsp;

@Opcodes(PacketOpcodes.TowerSurrenderReq)
public class HandlerTowerSurrenderReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        TowerSurrenderReq req =
                TowerSurrenderReq.parseFrom(payload == null ? new byte[0] : payload);
        int clientSeq = 0;
        if (header != null && header.length > 0) {
            try {
                clientSeq = PacketHead.parseFrom(header).getClientSequenceId();
            } catch (Exception ignored) {
                // Best effort — still answer the dialog.
            }
        }

        var player = session.getPlayer();
        if (player == null) {
            session.send(new PacketTowerSurrenderRsp(-1, clientSeq));
            return;
        }

        boolean reconfigure = req.getIsReconfigureParty();
        boolean inTower = TowerAbyssFix.isInTowerDungeon(player);

        if (inTower && reconfigure) {
            // Video 13.38.57: confirm succeeds (Rsp logged) but team UI never opens — client stays
            // at the start key. Official empty MidLevel notify (7.0 CmdId 21707) is what opens the
            // challenge team-config page; CurLevelRecord alone is not enough.
            var tower = player.getTowerManager();
            tower.beginAwaitingTeamReconfigure();
            tower.restartCurrentChamber();
            session.send(new PacketTowerSurrenderRsp(0, clientSeq));
            TowerAbyssFix.endChallengeUi(player, true);
            // Clear "run in progress" banner first, then push floor+teams for the picker prefill.
            player.sendPacket(PacketTowerCurLevelRecordChangeNotify.empty());
            tower.notifyCurLevelRecordChange();
            player.sendPacket(new PacketTowerMiddleLevelChangeTeamNotify());
            var scene = player.getScene();
            if (scene != null) {
                scene.setDontDestroyWhenEmpty(false);
            }
            Grasscutter.getLogger()
                    .info(
                            "Tower surrender uid={} reconfigure=true seq={} chamber={} (restart current chamber)",
                            player.getUid(),
                            clientSeq,
                            tower.getCurrentLevel());
            return;
        }

        if (inTower) {
            player.getTowerManager().clearAwaitingTeamReconfigure();
            TowerAbyssFix.endChallengeUi(player);
            player.getTowerManager().onEnd();
            var scene = player.getScene();
            if (scene != null) {
                scene.setDontDestroyWhenEmpty(false);
            }
            Grasscutter.getLogger()
                    .info("Tower surrender uid={} reconfigure=false seq={}", player.getUid(), clientSeq);
        }

        session.send(new PacketTowerSurrenderRsp(0, clientSeq));
    }
}
