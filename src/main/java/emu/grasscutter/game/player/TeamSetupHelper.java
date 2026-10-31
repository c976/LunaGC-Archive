package emu.grasscutter.game.player;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.server.packet.send.PacketAvatarTeamUpdateNotify;
import emu.grasscutter.server.packet.send.PacketSetUpAvatarTeamRsp;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Correct team setup by teamId. Prefer this over {@link TeamManager#setupAvatarTeam} when that
 * method still uses the old get(team.size()) bug (always writes team 1).
 *
 * <p>Kept as a separate class so remote jars can ship the fix without rewriting TeamManager.class
 * (which previously caused white-screen regressions).
 */
public final class TeamSetupHelper {
    private TeamSetupHelper() {}

    public static void setupAvatarTeam(Player player, int teamId, List<Long> list) {
        if (player == null) {
            return;
        }
        TeamManager tm = player.getTeamManager();
        TeamInfo teamInfo = tm.getTeams().get(teamId);

        if (list == null
                || list.isEmpty()
                || list.size() > tm.getMaxTeamSize()
                || player.isInMultiplayer()
                || teamInfo == null) {
            if (teamInfo != null) {
                player.sendPacket(new PacketSetUpAvatarTeamRsp(player, teamId, teamInfo));
            } else {
                player.sendPacket(new PacketAvatarTeamUpdateNotify(player));
            }
            return;
        }

        LinkedHashSet<Avatar> newTeam = new LinkedHashSet<>();
        for (Long guid : list) {
            Avatar avatar = player.getAvatars().getAvatarByGuid(guid.longValue());
            if (avatar == null || newTeam.contains(avatar)) {
                player.sendPacket(new PacketSetUpAvatarTeamRsp(player, teamId, teamInfo));
                return;
            }
            newTeam.add(avatar);
        }

        teamInfo.getAvatars().clear();
        tm.addAvatarsToTeam(teamInfo, newTeam);

        try {
            player.save();
        } catch (Throwable ignored) {
        }
    }
}
