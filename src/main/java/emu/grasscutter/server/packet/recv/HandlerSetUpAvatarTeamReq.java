package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.TeamSetupHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetUpAvatarTeamReqOuterClass.SetUpAvatarTeamReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.SetUpAvatarTeamReq)
public class HandlerSetUpAvatarTeamReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetUpAvatarTeamReq req = SetUpAvatarTeamReq.parseFrom(payload);

        int teamId = (req.getTeamId() ^ 63709) - 19529;

        // Bypass TeamManager.setupAvatarTeam (remote may still have get(team.size()) bug).
        TeamSetupHelper.setupAvatarTeam(
                session.getPlayer(), teamId, req.getAvatarTeamGuidListList());
    }
}
