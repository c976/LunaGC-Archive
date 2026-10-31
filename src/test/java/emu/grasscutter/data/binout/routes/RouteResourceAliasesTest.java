package emu.grasscutter.data.binout.routes;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.Test;

class RouteResourceAliasesTest {
    private static final String ROUTE =
            """
            [{"_localId":300300282,"_name":"Seelie","_type":"Loop",
              "_arriveRange":2,"_rotType":"ROT_ROUND","_rotAngleType":"ROT_ANGLE_Y",
              "_points":[{"_pos":{"_x":2453.382,"_y":296.623,"_z":-1613.664},
                          "_speedLevel":1,"_waitTime":0.5,"_targetVelocity":4.5,
                          "_rotRoundLeaveDir":{"_x":0,"_y":90,"_z":0},
                          "_hasReachEvent":true}]}]
            """;

    private static SceneRoutes decode(String json) {
        return JsonUtils.decode(JsonParser.parseString(json), SceneRoutes.class);
    }

    private static void check(SceneRoutes scene) {
        assertEquals(3, scene.getSceneId());
        assertEquals(1, scene.getRoutes().length);
        Route route = scene.getRoutes()[0];
        assertEquals(300300282, route.getLocalId());
        assertEquals("Seelie", route.getName());
        assertEquals(RouteType.Loop, route.getType());
        assertEquals(2f, route.getArriveRange());
        assertEquals(RotType.ROT_ROUND, route.getRotType());
        assertEquals(RotAngleType.ROT_ANGLE_Y, route.getRotAngleType());
        RoutePoint point = route.getPoints()[0];
        assertEquals(2453.382f, point.getPos().getX());
        assertEquals(296.623f, point.getPos().getY());
        assertEquals(-1613.664f, point.getPos().getZ());
        assertEquals(1, point.getSpeedLevel());
        assertEquals(0.5f, point.getWaitTime());
        assertEquals(4.5f, point.getTargetVelocity());
        assertTrue(point.isHasReachEvent());
    }

    @Test
    void firstObservedRootFamilyLoadsUnderscoreFields() {
        check(decode("{\"AEJNKFGDCEK\":3,\"LOLKGILMKJN\":" + ROUTE + "}"));
    }

    @Test
    void secondObservedRootFamilyLoadsUnderscoreFields() {
        check(decode("{\"BHMDDFNMFAB\":3,\"CNOMPALHFAC\":" + ROUTE + "}"));
    }

    @Test
    void canonicalFieldsRemainReadableAndWritable() {
        String canonicalRoute = ROUTE.replace("\"_", "\"");
        SceneRoutes scene = decode("{\"sceneId\":3,\"routes\":" + canonicalRoute + "}");
        check(scene);
        var encoded = JsonParser.parseString(JsonUtils.encode(scene)).getAsJsonObject();
        assertTrue(encoded.has("sceneId"));
        assertTrue(encoded.has("routes"));
        assertFalse(encoded.has("AEJNKFGDCEK"));
        var route = encoded.getAsJsonArray("routes").get(0).getAsJsonObject();
        assertTrue(route.has("localId"));
        assertFalse(route.has("_localId"));
    }

    @Test
    void missingOptionalTypeRetainsExistingUnknownDefault() {
        SceneRoutes scene = decode("{\"sceneId\":3,\"routes\":[{\"localId\":1,\"points\":[]}]}");
        assertEquals(RouteType.Unknown, scene.getRoutes()[0].getType());
    }
}
