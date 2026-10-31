package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        var intro = GAME_OPTIONS.newAccountIntro;
        boolean freshAccount = player.getAvatars().getAvatarCount() == 0;

        if (freshAccount && intro.enabled) {
            // Native selection keeps the account unborn until the client submits SetPlayerBornDataReq.
            // World/scene initialization therefore remains outside this login request.
            session.setState(SessionState.PICKING_CHARACTER);

            BasePacket notify =
                    intro.doSetPlayerBornDataNotify > 0
                            ? new BasePacket(intro.doSetPlayerBornDataNotify)
                            : new PacketDoSetPlayerBornDataNotify();
            session.send(notify);
            Grasscutter.getLogger()
                    .info(
                            "[intro] new account, waiting for character creation (notify cmdId={}).",
                            notify.getOpcode());

            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        if (freshAccount) {
            createDefaultTraveler(player);

            // Skipping the visible native intro must still use the ordinary world and scene
            // lifecycle. Only fresh quest creation is delayed until the first PostEnterSceneRsp.
            BornIntroGate.armSceneReady(session);
        } else {
            BornDataHelper.ensureMainCharacter(player);
        }

        player.onLogin();

        // Also on a plain login: a client that dropped mid-intro reconnects with its Traveler
        // already chosen and lands here, while its bootstrap is still waiting on the old
        // connection's pause cycles. Without this its EnterSceneReady stays deferred forever and
        // Quest 351 never starts. A no-op for players with no bootstrap.
        BornIntroGate.markWorldLoginComplete(session);

        session.send(new PacketPlayerLoginRsp(session));
    }

    private static void createDefaultTraveler(Player player) {
        int avatarId = 10000007;
        Avatar mainCharacter = new Avatar(avatarId);

        if (!GAME_OPTIONS.questing.enabled) {
            mainCharacter.setSkillDepotData(GameData.getAvatarSkillDepotDataMap().get(704));
        }

        player.addAvatar(mainCharacter, false);
        player.setMainCharacterId(avatarId);
        player.setHeadImage(avatarId);
        var team = player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars();
        team.clear();
        team.add(avatarId);
        player.save();
    }
}
