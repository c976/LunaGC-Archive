package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterScenePeerNotifyOuterClass.EnterScenePeerNotify;

public class PacketEnterScenePeerNotify extends BasePacket {

    public PacketEnterScenePeerNotify(Player player) {
        super(PacketOpcodes.EnterScenePeerNotify);

        EnterScenePeerNotify proto =
                EnterScenePeerNotify.newBuilder()
                        .setDestSceneId(DomainDungeonHelper.notifySceneId(player))
                        .setPeerId(player.getPeerId())
                        .setHostPeerId(player.getWorld().getHost().getPeerId())
                        .setEnterSceneToken(player.getEnterSceneToken())
                        .build();

        this.setData(proto);
    }
}
