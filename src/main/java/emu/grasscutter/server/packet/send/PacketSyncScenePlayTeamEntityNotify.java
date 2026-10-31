package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SyncScenePlayTeamEntityNotifyOuterClass.SyncScenePlayTeamEntityNotify;

public class PacketSyncScenePlayTeamEntityNotify extends BasePacket {

    public PacketSyncScenePlayTeamEntityNotify(Player player) {
        super(PacketOpcodes.SyncScenePlayTeamEntityNotify);

        SyncScenePlayTeamEntityNotify proto =
                SyncScenePlayTeamEntityNotify.newBuilder()
                        .setSceneId(DomainDungeonHelper.notifySceneId(player))
                        .build();

        this.setData(proto);
    }
}
