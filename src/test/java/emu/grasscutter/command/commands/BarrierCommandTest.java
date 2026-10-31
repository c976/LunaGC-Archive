package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.net.proto.OpenStateChangeNotifyOuterClass.OpenStateChangeNotify;
import java.util.Map;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class BarrierCommandTest {
    @Test
    void requiresExplicitStateWithoutAnOnlinePlayerForParsing() {
        var command = new BarrierCommand();
        assertEquals(Command.TargetRequirement.NONE,
                BarrierCommand.class.getAnnotation(Command.class).targetRequirement());
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs());
        for (String state : new String[] {"on", "off", "1", "0", "ON", "OFF"}) {
            assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs(state));
        }
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("toggle"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("on", "off"));
    }

    @Test
    void togglesStarterRegionRestrictionWithOpenState47() throws Exception {
        var off = OpenStateChangeNotify.parseFrom(BarrierCommand.barrierStatePacket(false).getData());
        var on = OpenStateChangeNotify.parseFrom(BarrierCommand.barrierStatePacket(true).getData());

        assertEquals(Map.of(47, 1), off.getOpenStateMapMap());
        assertEquals(Map.of(47, 0), on.getOpenStateMapMap());
    }

    @Test
    void retainsPermissionsAndAliases() {
        var annotation = BarrierCommand.class.getAnnotation(Command.class);
        assertEquals("player.setprop", annotation.permission());
        assertEquals("player.setprop.others", annotation.permissionTargeted());
        assertArrayEquals(new String[] {"br"}, annotation.aliases());
    }
}
