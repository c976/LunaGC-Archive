package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ScriptLibKillEntityGroupTest {
    @Test
    void quest39403CanTargetGadgetsOutsideItsOwnNotificationGroup() {
        int notificationGroup = 133007183;
        // Q394 Lua closes the seal gadgets in five distinct groups.
        for (int target : new int[]{
                133007004, 133007076, 133007078, 133007079, 133007001}) {
            assertEquals(target, ScriptLib.killEntityTargetGroup(target, notificationGroup));
        }
    }

    @Test
    void missingExplicitGroupUsesTheCurrentLuaGroup() {
        assertEquals(133007183, ScriptLib.killEntityTargetGroup(0, 133007183));
        assertEquals(133007183, ScriptLib.killEntityTargetGroup(-1, 133007183));
        assertEquals(0, ScriptLib.killEntityTargetGroup(0, 0));
    }
}
