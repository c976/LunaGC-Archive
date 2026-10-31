/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.player.TeamInfo
 *  emu.grasscutter.game.player.TeamManager
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.server.packet.send.PacketAvatarDataNotify
 *  emu.grasscutter.server.packet.send.PacketAvatarTeamAllDataNotify
 *  emu.grasscutter.server.packet.send.PacketChooseCurAvatarTeamRsp
 *  emu.grasscutter.server.packet.send.PacketSetUpAvatarTeamRsp
 */
package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarTeamAllDataNotify;
import emu.grasscutter.server.packet.send.PacketChooseCurAvatarTeamRsp;
import emu.grasscutter.server.packet.send.PacketSetUpAvatarTeamRsp;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class TeamPartyFixHelper {
    private static final int TEAM_ID_XOR = 63709;
    private static final int TEAM_ID_ADD = 19529;
    private static Method setCurrentTeamIdMethod;

    private TeamPartyFixHelper() {
    }

    public static int decodeTeamId(int n) {
        return (n ^ 0xF8DD) - 19529;
    }

    private static boolean isValidTeamSlot(TeamManager teamManager, int n) {
        return n >= 1 && n <= 4;
    }

    public static int resolveTeamId(TeamManager teamManager, int n) {
        int n2 = TeamPartyFixHelper.decodeTeamId(n);
        if (TeamPartyFixHelper.isValidTeamSlot(teamManager, n2)) {
            return n2;
        }
        if (TeamPartyFixHelper.isValidTeamSlot(teamManager, n)) {
            return n;
        }
        int n3 = n ^ 0xF8DD;
        if (TeamPartyFixHelper.isValidTeamSlot(teamManager, n3)) {
            return n3;
        }
        int n4 = teamManager.getCurrentTeamId();
        if (TeamPartyFixHelper.isValidTeamSlot(teamManager, n4)) {
            Grasscutter.getLogger().warn("resolveTeamId fallback current uid={} raw={} decoded={} -> {}", new Object[]{teamManager.getPlayer().getUid(), n, n2, n4});
            return n4;
        }
        Grasscutter.getLogger().warn("resolveTeamId fallback 1 uid={} raw={} decoded={}", new Object[]{teamManager.getPlayer().getUid(), n, n2});
        return 1;
    }

    private static void replySetup(TeamManager teamManager, int n) {
        Player player = teamManager.getPlayer();
        TeamInfo teamInfo = (TeamInfo)teamManager.getTeams().get(n);
        if (teamInfo == null) {
            teamInfo = teamManager.getCurrentTeamInfo();
        }
        if (teamInfo == null) {
            teamInfo = new TeamInfo();
        }
        player.sendPacket((BasePacket)new PacketSetUpAvatarTeamRsp(player, n, teamInfo));
        player.sendPacket((BasePacket)new PacketAvatarTeamAllDataNotify(player));
    }

    public static void setupAvatarTeam(TeamManager teamManager, int n, List<Long> list) {
        TeamInfo teamInfo;
        int n2;
        if (!TeamPartyFixHelper.isValidTeamSlot(teamManager, n)) {
            n2 = TeamPartyFixHelper.resolveTeamId(teamManager, n);
            Grasscutter.getLogger().warn("setupAvatarTeam clamp teamId {} -> {} uid={}", new Object[]{n, n2, teamManager.getPlayer().getUid()});
            n = n2;
        }
        if (list == null || list.isEmpty() || teamManager.getPlayer().isInMultiplayer()) {
            Grasscutter.getLogger().warn("setupAvatarTeam rejected uid={} teamId={} size={}", new Object[]{teamManager.getPlayer().getUid(), n, list == null ? -1 : list.size()});
            TeamPartyFixHelper.replySetup(teamManager, n);
            return;
        }
        n2 = Math.max(1, teamManager.getMaxTeamSize());
        List<Long> list2 = list;
        if (list2.size() > n2) {
            Grasscutter.getLogger().warn("setupAvatarTeam truncate uid={} teamId={} size={} -> {}", new Object[]{teamManager.getPlayer().getUid(), n, list2.size(), n2});
            list2 = new ArrayList<Long>(list2.subList(0, n2));
        }
        if ((teamInfo = (TeamInfo)teamManager.getTeams().get(n)) == null) {
            teamInfo = new TeamInfo();
            teamManager.getTeams().put(n, teamInfo);
        }
        LinkedHashSet<Avatar> linkedHashSet = new LinkedHashSet<Avatar>();
        for (Long l : list2) {
            long l2;
            if (l == null || (l2 = l.longValue()) <= 0L) continue;
            Avatar avatar = teamManager.getPlayer().getAvatars().getAvatarByGuid(l2);
            if (avatar == null) {
                Grasscutter.getLogger().warn("setupAvatarTeam skip missing guid uid={} teamId={} guid={}", new Object[]{teamManager.getPlayer().getUid(), n, l2});
                continue;
            }
            if (linkedHashSet.size() >= n2) break;
            linkedHashSet.add(avatar);
        }
        if (linkedHashSet.isEmpty()) {
            Grasscutter.getLogger().warn("setupAvatarTeam rejected empty uid={} teamId={}", (Object)teamManager.getPlayer().getUid(), (Object)n);
            TeamPartyFixHelper.replySetup(teamManager, n);
            return;
        }
        teamInfo.getAvatars().clear();
        teamManager.addAvatarsToTeam(teamInfo, linkedHashSet);
        Player player = teamManager.getPlayer();
        if (n == teamManager.getCurrentTeamId()) {
            if (teamManager.getCurrentCharacterIndex() >= linkedHashSet.size()) {
                teamManager.setCurrentCharacterIndex(0);
            }
            teamManager.updateTeamEntities(null);
        }
        TeamPartyFixHelper.replySetup(teamManager, n);
        player.sendPacket((BasePacket)new PacketAvatarDataNotify(player));
        player.save();
        Grasscutter.getLogger().info("setupAvatarTeam ok uid={} teamId={} avatars={}", new Object[]{player.getUid(), n, linkedHashSet.size()});
    }

    public static void setCurrentTeam(TeamManager teamManager, int n) {
        if (teamManager.getPlayer().isInMultiplayer()) {
            return;
        }
        TeamInfo teamInfo = (TeamInfo)teamManager.getTeams().get(n);
        if (teamInfo == null || teamInfo.getAvatars().isEmpty()) {
            Grasscutter.getLogger().warn("setCurrentTeam rejected uid={} teamId={}", (Object)teamManager.getPlayer().getUid(), (Object)n);
            return;
        }
        TeamPartyFixHelper.invokeSetCurrentTeamId(teamManager, n);
        teamManager.updateTeamEntities((BasePacket)new PacketChooseCurAvatarTeamRsp(n));
        Player player = teamManager.getPlayer();
        player.sendPacket((BasePacket)new PacketAvatarTeamAllDataNotify(player));
        player.sendPacket((BasePacket)new PacketAvatarDataNotify(player));
        player.save();
        Grasscutter.getLogger().info("setCurrentTeam ok uid={} teamId={}", (Object)player.getUid(), (Object)n);
    }

    private static void invokeSetCurrentTeamId(TeamManager teamManager, int n) {
        try {
            if (setCurrentTeamIdMethod == null) {
                Method method = TeamManager.class.getDeclaredMethod("setCurrentTeamId", Integer.TYPE);
                method.setAccessible(true);
                setCurrentTeamIdMethod = method;
            }
            setCurrentTeamIdMethod.invoke((Object)teamManager, n);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException("setCurrentTeamId reflect failed", reflectiveOperationException);
        }
    }
}

