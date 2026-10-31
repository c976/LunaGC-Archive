package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DungeonWayPointActivateReqOuterClass.DungeonWayPointActivateReq;
import emu.grasscutter.net.proto.HomeSceneJumpReqOuterClass.HomeSceneJumpReq;
import emu.grasscutter.net.proto.SceneEntityDrownReqOuterClass.SceneEntityDrownReq;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Messages mapped from kitkat-multiverse/genshin-protocol 7.1.0 with their 7.1 field numbers. */
final class KitkatMappedProtocolTest {
    @Test
    void opcodesAreMapped() {
        assertEquals(2178, PacketOpcodes.SceneEntityDrownReq);
        assertEquals(28813, PacketOpcodes.SceneEntityDrownRsp);
        assertEquals(5230, PacketOpcodes.DungeonWayPointActivateReq);
        assertEquals(27673, PacketOpcodes.HomeSceneJumpReq);
        assertEquals(6969, PacketOpcodes.SetPlayerHeadImageReq);
    }

    @Test
    void requestsDecodeSevenOneFieldNumbers() throws Exception {
        // entity_id 5 on field 15, way_point_id 7 on field 3, is_enter_room_scene on field 8.
        assertEquals(5, SceneEntityDrownReq.parseFrom(HexFormat.of().parseHex("7805")).getEntityId());
        assertEquals(7, DungeonWayPointActivateReq.parseFrom(HexFormat.of().parseHex("1807")).getWayPointId());
        assertTrue(HomeSceneJumpReq.parseFrom(HexFormat.of().parseHex("4001")).getIsEnterRoomScene());
    }

    @Test
    void emptyMessageNamesFromKitkatAreNotTrusted() {
        // kitkat's names for field-less messages collide with captured CmdIds, so they stay unmapped.
        assertEquals(0, PacketOpcodes.GetWorldMpInfoReq);
        assertEquals(0, PacketOpcodes.GetMapMarkTipsReq);
    }
}
