package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.WorldRegions;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MapLayerInfoOuterClass;
import emu.grasscutter.net.proto.PlayerWorldSceneInfoListNotifyOuterClass.PlayerWorldSceneInfoListNotify;
import emu.grasscutter.net.proto.PlayerWorldSceneInfoOuterClass.PlayerWorldSceneInfo;
import java.util.Map;

public class PacketPlayerWorldSceneInfoListNotify extends BasePacket {

    public PacketPlayerWorldSceneInfoListNotify(Player player) {
        super(PacketOpcodes.PlayerWorldSceneInfoListNotify);

        var sceneTags = player.getSceneTags();

        PlayerWorldSceneInfoListNotify.Builder proto =
                PlayerWorldSceneInfoListNotify.newBuilder()
                        .addInfoList(
                                PlayerWorldSceneInfo.newBuilder().setSceneId(1).setIsLocked(false).build());

        for (int scene : GameData.getSceneDataMap().keySet()) {
            var worldInfoBuilder =
                    PlayerWorldSceneInfo.newBuilder()
                            .setSceneId(scene)
                            .setIsLocked(false)
                            .setLimitedRegionInfo(WorldRegions.openRegions(scene));

            if (sceneTags.keySet().contains(scene)) {
                worldInfoBuilder.addAllSceneTagIdList(
                        sceneTags.entrySet().stream()
                                .filter(e -> e.getKey().equals(scene))
                                .map(Map.Entry::getValue)
                                .toList()
                                .get(0));
            }

            if (scene == 3) {
                worldInfoBuilder.setMapLayerInfo(
                        MapLayerInfoOuterClass.MapLayerInfo.newBuilder()
                                .addAllUnlockMapLayerList(GameData.getMapLayerDataMap().keySet())
                                .addAllUnlockMapLayerGroupList(GameData.getMapLayerGroupDataMap().keySet())
                                .build());
            }

            proto.addInfoList(worldInfoBuilder.build());
        }

        proto.addAllUnlockedAreaIdList(WorldRegions.allAreaIds());

        this.setData(proto);
    }
}
