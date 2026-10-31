package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.entity.gadget.platform.PointArrayRoute;
import emu.grasscutter.net.packet.PacketOpcodes;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

class PlatformPointArrayTest {
    @Test
    void changedArrayNotifiesOnceAndRetiresTheConfigRun() throws Exception {
        var f = new ConfigRouteDeadlineTest.Fixture(200);
        assertTrue(f.gadget.startPlatform());
        assertFalse(f.ids().isEmpty());
        var tasks = f.ids();
        f.scene.packets.clear();
        f.scene.manager.events.clear();

        assertEquals(-1, f.lib.SetPlatformPointArray(174002, 41, new int[] {1, 2}, new LuaTable()));
        var route = assertInstanceOf(PointArrayRoute.class, f.gadget.getRouteConfig());
        assertEquals(41, route.getPointArrayId());
        assertEquals(1, f.scene.packets.size());
        assertEquals(PacketOpcodes.PlatformChangeRouteNotify, f.scene.packets.get(0).getOpcode());
        for (int id : tasks) assertNull(f.scene.scheduler.getTask(id));

        // The old config route's points no longer move the gadget or raise reach events.
        f.scene.now += 100000;
        f.polls(10);
        assertTrue(f.scene.manager.events.isEmpty());

        assertEquals(-1, f.lib.SetPlatformPointArray(174002, 41, new int[] {9}, new LuaTable()));
        assertEquals(1, f.scene.packets.size());
    }
}
