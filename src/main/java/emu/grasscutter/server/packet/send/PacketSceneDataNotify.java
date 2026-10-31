package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneDataNotifyOuterClass.SceneDataNotify;

public class PacketSceneDataNotify extends BasePacket {
    public PacketSceneDataNotify(Player player) {
        super(PacketOpcodes.SceneDataNotify);
        int sceneId = DomainDungeonHelper.notifySceneId(player);
        var host = player.getWorld().getHost();
        var owner = host != null ? host : player;
        // The native handler replaces this scene's active tags. An empty list here clears
        // the tags already supplied by PlayerWorldSceneInfoListNotify during scene init.
        this.setData(SceneDataNotify.newBuilder()
                .setSceneId(sceneId)
                .addAllSceneTagIdList(owner.getSceneTags().getOrDefault(sceneId, java.util.Set.of()))
                .build());
    }
}
