package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GetSceneAreaRspOuterClass.GetSceneAreaRsp;
import emu.grasscutter.net.proto.GetScenePointRspOuterClass.GetScenePointRsp;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("mapFogEnabled")
final class PacketGetSceneAreaRspTest {
    private boolean previousFogEnabled;

    @BeforeEach
    void enableFog() {
        previousFogEnabled = GAME_OPTIONS.mapFogEnabled;
        GAME_OPTIONS.mapFogEnabled = true;
    }

    @AfterEach
    void restoreFog() {
        GAME_OPTIONS.mapFogEnabled = previousFogEnabled;
    }

    @Test
    void fogIsEnabledByDefaultAndMissingSettingKeepsThatDefault() {
        assertTrue(new ConfigContainer.GameOptions().mapFogEnabled);
        var missingSetting = new Gson().fromJson(
                "{\"server\":{\"game\":{\"gameOptions\":{}}}}",
                ConfigContainer.class);
        assertTrue(missingSetting.server.game.gameOptions.mapFogEnabled);
        var disabled = new Gson().fromJson(
                "{\"server\":{\"game\":{\"gameOptions\":{\"mapFogEnabled\":false}}}}",
                ConfigContainer.class);
        assertFalse(disabled.server.game.gameOptions.mapFogEnabled);
    }

    @Test
    void freshPlayerDoesNotSeeStarterStatueAreaBeforeActivation() throws Exception {
        var player = new Player();
        var response = GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(player, 3).getData());
        var points = GetScenePointRsp.parseFrom(new PacketGetScenePointRsp(player, 3).getData());

        assertEquals(3, response.getSceneId());
        assertTrue(response.getAreaIdListList().isEmpty());
        assertTrue(points.getUnlockAreaListList().isEmpty());
        assertTrue(player.getUnlockedSceneAreas(3).isEmpty());
        assertFalse(player.getUnlockedScenePoints(3).contains(7));
    }

    @Test
    void regionOneIsGatedByStatueSevenWithoutDestroyingLegacyAreaFlags() throws Exception {
        var player = new Player();
        player.getUnlockedSceneAreas(3).addAll(Set.of(1, 3, 7));
        player.getUnlockedSceneAreas(4).add(25);

        var mondstadt = GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(player, 3).getData());
        var otherScene = GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(player, 4).getData());

        assertEquals(Set.of(3, 7), Set.copyOf(mondstadt.getAreaIdListList()));
        assertEquals(Set.of(25), Set.copyOf(otherScene.getAreaIdListList()));
        assertEquals(Set.of(1, 3, 7), player.getUnlockedSceneAreas(3));
        assertEquals(Set.of(25), player.getUnlockedSceneAreas(4));

        player.getUnlockedScenePoints(3).add(7);
        assertEquals(Set.of(1, 3, 7), Set.copyOf(
                GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(player, 3).getData())
                        .getAreaIdListList()));

        player.getForceLockedScenePoints(3).add(7);
        assertEquals(Set.of(3, 7), Set.copyOf(
                GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(player, 3).getData())
                        .getAreaIdListList()));
    }

    @Test
    void activatedStatueRevealsAreaOneEvenIfAnOldSaveLacksItsAreaFlag() throws Exception {
        var player = new Player();
        player.getUnlockedScenePoints(3).add(7);
        var areas = GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(player, 3).getData());
        var points = GetScenePointRsp.parseFrom(new PacketGetScenePointRsp(player, 3).getData());

        assertEquals(Set.of(1), Set.copyOf(areas.getAreaIdListList()));
        assertEquals(Set.of(1), Set.copyOf(points.getUnlockAreaListList()));
        assertTrue(player.getUnlockedSceneAreas(3).isEmpty());
    }

    @Test
    void disablingFogStillShowsAllAreasWithStatueLocked() throws Exception {
        var player = new Player();
        player.getUnlockedSceneAreas(3).add(1);
        GAME_OPTIONS.mapFogEnabled = false;
        var areas = GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(player, 3).getData());
        assertEquals(1000, areas.getAreaIdListCount());
        assertFalse(player.getUnlockedScenePoints(3).contains(7));
        assertEquals(Set.of(1), player.getUnlockedSceneAreas(3));
    }

    @Test
    void fogCanBeSwitchedRepeatedlyWithoutChangingProgress() throws Exception {
        var player = new Player();
        player.getUnlockedSceneAreas(3).addAll(Set.of(1, 3));
        player.getUnlockedScenePoints(3).add(7);

        for (boolean enabled : new boolean[] {true, false, true, false, true}) {
            GAME_OPTIONS.mapFogEnabled = enabled;

            var areas = GetSceneAreaRsp.parseFrom(
                    new PacketGetSceneAreaRsp(player, 3).getData());
            var points = GetScenePointRsp.parseFrom(
                    new PacketGetScenePointRsp(player, 3).getData());

            if (enabled) {
                assertEquals(Set.of(1, 3), Set.copyOf(areas.getAreaIdListList()));
                assertEquals(Set.of(1, 3), Set.copyOf(points.getUnlockAreaListList()));
            } else {
                assertEquals(1000, areas.getAreaIdListCount());
                assertEquals(1000, points.getUnlockAreaListCount());
                assertEquals(1, areas.getAreaIdList(0));
                assertEquals(1000, areas.getAreaIdList(999));
            }
            assertEquals(Set.of(1, 3), player.getUnlockedSceneAreas(3));
        }
    }
}
