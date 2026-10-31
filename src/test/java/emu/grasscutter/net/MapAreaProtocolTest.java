package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.packet.send.PacketMapAreaChangeNotify;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

final class MapAreaProtocolTest {
    @Test
    void changeNotifyUsesSevenOneLayout() {
        var packet = new PacketMapAreaChangeNotify(56, true);
        assertEquals(PacketOpcodes.MapAreaChangeNotify, packet.getOpcode());
        // map_area_info_list on field 12 holding {map_area_id 56, is_open true}.
        assertEquals("620408381001", HexFormat.of().formatHex(packet.getData()));
    }
}
