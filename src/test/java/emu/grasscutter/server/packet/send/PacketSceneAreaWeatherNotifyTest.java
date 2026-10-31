package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.world.WeatherData;
import emu.grasscutter.game.props.ClimateType;
import org.junit.jupiter.api.Test;

/** Regression for quest 35901 storm leakage after a cross-scene transfer. */
final class PacketSceneAreaWeatherNotifyTest {
    private static final Gson JSON = new Gson();

    private static WeatherData area(int id, int scene, int gadget) {
        return JSON.fromJson(
                "{\"areaID\":" + id + ",\"sceneID\":" + scene
                        + ",\"gadgetID\":" + gadget + "}", WeatherData.class);
    }

    @Test
    void questStormIsSentWithItsRealWeatherGadgetInMondstadt() {
        var data = area(3, 3, 70020003);
        var proto = PacketSceneAreaWeatherNotify.forScene(
                3, 3, ClimateType.CLIMATE_SUNNY, data);
        assertEquals(3, proto.getWeatherAreaId());
        assertEquals(70020003, proto.getWeatherGadgetId());
        assertEquals(ClimateType.CLIMATE_SUNNY.getValue(), proto.getClimateType());
    }

    @Test
    void stormGadgetDoesNotLeakIntoTheNextDungeonScene() {
        var data = area(3, 3, 70020003);
        var proto = PacketSceneAreaWeatherNotify.forScene(
                1004, 3, ClimateType.CLIMATE_CLOUDY, data);
        assertEquals(0, proto.getWeatherAreaId());
        assertEquals(0, proto.getWeatherGadgetId());
        assertEquals(ClimateType.CLIMATE_SUNNY.getValue(), proto.getClimateType());
        // The player's saved selection is not overwritten; scene 3 can reuse it.
        assertEquals(3, PacketSceneAreaWeatherNotify.forScene(
                3, 3, ClimateType.CLIMATE_SUNNY, data).getWeatherAreaId());
    }

    @Test
    void weatherAreaIdsAreNotSceneIds() {
        var data = area(2150, 4, 70020001);
        assertEquals(2150, PacketSceneAreaWeatherNotify.forScene(
                4, 2150, ClimateType.CLIMATE_SUNNY, data).getWeatherAreaId());
        assertEquals(0, PacketSceneAreaWeatherNotify.forScene(
                2150, 2150, ClimateType.CLIMATE_SUNNY, data).getWeatherAreaId());
    }

    @Test
    void directClimateChangesWithNoAreaStayAvailable() {
        var proto = PacketSceneAreaWeatherNotify.forScene(
                3, 0, ClimateType.CLIMATE_CLOUDY, null);
        assertEquals(0, proto.getWeatherAreaId());
        assertEquals(0, proto.getWeatherGadgetId());
        assertEquals(ClimateType.CLIMATE_CLOUDY.getValue(), proto.getClimateType());
    }
}
