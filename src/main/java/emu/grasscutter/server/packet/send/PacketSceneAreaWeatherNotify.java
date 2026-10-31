package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.world.WeatherData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneAreaWeatherNotifyOuterClass.SceneAreaWeatherNotify;

public class PacketSceneAreaWeatherNotify extends BasePacket {

    /**
     * Player.weatherId holds the last selected weather area. It persists across
     * scene transfers, but weather areas and their gadgets belong to a specific
     * scene. Do not send Mondstadt's storm gadget into a dungeon or another map.
     *
     * Keep the stored area unchanged so returning to the original scene can
     * restore its weather after SceneInitFinishReq.
     */
    static SceneAreaWeatherNotify forScene(
            int sceneId, int weatherId, ClimateType climate, WeatherData weatherData) {
        boolean foreignArea = weatherData != null
                && weatherData.getSceneID() > 0
                && weatherData.getSceneID() != sceneId;
        int effectiveWeatherId = foreignArea ? 0 : weatherId;
        ClimateType effectiveClimate =
                foreignArea ? ClimateType.CLIMATE_SUNNY : climate;

        var builder = SceneAreaWeatherNotify.newBuilder()
                .setWeatherAreaId(effectiveWeatherId)
                .setClimateType(effectiveClimate.getValue());
        if (!foreignArea && weatherData != null && weatherData.getGadgetID() > 0) {
            builder.setWeatherGadgetId(weatherData.getGadgetID());
        }
        return builder.build();
    }

    public PacketSceneAreaWeatherNotify(Player player) {
        super(PacketOpcodes.SceneAreaWeatherNotify);
        var weatherData = GameData.getWeatherDataMap().get(player.getWeatherId());
        this.setData(forScene(
                player.getSceneId(), player.getWeatherId(),
                player.getClimate(), weatherData));
    }
}
