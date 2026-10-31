package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.ProfilePictureHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetOnlinePlayerListRspOuterClass.GetOnlinePlayerListRsp;
import emu.grasscutter.net.proto.MpSettingTypeOuterClass.MpSettingType;
import emu.grasscutter.net.proto.OnlinePlayerInfoOuterClass.OnlinePlayerInfo;
import java.util.List;

/**
 * 自写版在线玩家列表。
 *
 * <p>官方那版是"一把梭"：任何一个人的昵称/签名/名片/头像有一项为空或抛异常，整份列表就发不出去，
 * 客户端表现为"有时候谁都不显示"。这里改成：每个玩家单独 try/catch（坏人就跳过），
 * 每个字段单独兜底（空就填默认值），保证列表一定能发出去。
 */
public class PacketGetOnlinePlayerListRsp extends BasePacket {
    public PacketGetOnlinePlayerListRsp(Player session) {
        super(PacketOpcodes.GetOnlinePlayerListRsp);

        GetOnlinePlayerListRsp.Builder proto = GetOnlinePlayerListRsp.newBuilder();
        int added = 0;

        try {
            List<Player> players =
                    Grasscutter.getGameServer().getPlayers().values().stream().limit(50).toList();
            for (Player p : players) {
                if (p == null || session == null || p.getUid() == session.getUid()) {
                    continue;
                }
                try {
                    proto.addPlayerInfoList(buildInfo(p));
                    added++;
                } catch (Throwable t) {
                }
            }
        } catch (Throwable t) {
        }

        if (session != null) {
        }
        this.setData(proto);
    }

    /** 单玩家信息构造：逐字段兜底，绝不因为一项缺失而整体失败。 */
    private static OnlinePlayerInfo buildInfo(Player p) {
        OnlinePlayerInfo.Builder b = OnlinePlayerInfo.newBuilder();
        b.setUid(p.getUid());

        String nick = null;
        try {
            nick = p.getNickname();
        } catch (Throwable ignored) {
        }
        b.setNickname(nick == null || nick.isEmpty() ? ("Player" + p.getUid()) : nick);

        try {
            b.setPlayerLevel(p.getLevel());
        } catch (Throwable ignored) {
        }
        try {
            b.setWorldLevel(p.getWorldLevel());
        } catch (Throwable ignored) {
        }
        try {
            MpSettingType ms = p.getMpSetting();
            if (ms != null) {
                b.setMpSettingTypeValue(ms.getNumber());
            }
        } catch (Throwable ignored) {
        }
        try {
            b.setNameCardId(p.getNameCardId());
        } catch (Throwable ignored) {
        }
        try {
            String sg = p.getSignature();
            if (sg != null) {
                b.setSignature(sg);
            }
        } catch (Throwable ignored) {
        }
        try {
            b.setProfilePicture(ProfilePictureHelper.toProto(p.getHeadImage()));
        } catch (Throwable ignored) {
        }
        try {
            b.setCurPlayerNumInWorld(
                    p.getWorld() != null ? p.getWorld().getPlayerCount() : 1);
        } catch (Throwable ignored) {
            b.setCurPlayerNumInWorld(1);
        }
        return b.build();
    }
}
