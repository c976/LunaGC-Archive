package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors.FieldDescriptor;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeAchievementGoalRewardRspOuterClass.TakeAchievementGoalRewardRsp;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PacketTakeAchievementGoalRewardRspTest {
    @Test
    void mapsTheResponseAndRequest() {
        assertEquals(22054, new PacketTakeAchievementGoalRewardRsp().getOpcode());
        assertEquals(28295, PacketOpcodes.TakeAchievementGoalRewardReq);
    }

    @Test
    void generatedDescriptorMatchesNativeReader() {
        var descriptor = TakeAchievementGoalRewardRsp.getDescriptor();
        assertEquals(3, descriptor.getFields().size());
        var retcode = descriptor.findFieldByName("retcode");
        assertEquals(7, retcode.getNumber());
        assertEquals(FieldDescriptor.Type.INT32, retcode.getType());
        assertFalse(retcode.isRepeated());
        var ids = descriptor.findFieldByName("id_list");
        assertEquals(8, ids.getNumber());
        assertEquals(FieldDescriptor.Type.UINT32, ids.getType());
        assertTrue(ids.isRepeated());
        assertTrue(ids.isPacked());
        var items = descriptor.findFieldByName("item_list");
        assertEquals(9, items.getNumber());
        assertEquals(FieldDescriptor.Type.MESSAGE, items.getType());
        assertTrue(items.isRepeated());
        assertEquals(ItemParam.getDescriptor(), items.getMessageType());
        assertEquals(1, items.getMessageType().findFieldByName("item_id").getNumber());
        assertEquals(FieldDescriptor.Type.UINT32,
                items.getMessageType().findFieldByName("item_id").getType());
        assertEquals(2, items.getMessageType().findFieldByName("count").getNumber());
        assertEquals(FieldDescriptor.Type.UINT32,
                items.getMessageType().findFieldByName("count").getType());
    }

    @Test
    void successPacketUsesPackedGoalIdsAndNestedItems() throws Exception {
        // Constructed from native tags, not a captured successful claim: goal 1, item 201 x5.
        var fixture = HexFormat.of().parseHex("4201014a0508c9011005");
        var item = ItemParam.newBuilder().setItemId(201).setCount(5).build();
        var packet = new PacketTakeAchievementGoalRewardRsp(List.of(1), List.of(item));
        assertArrayEquals(fixture, packet.getData());
        var decoded = TakeAchievementGoalRewardRsp.parseFrom(fixture);
        assertEquals(0, decoded.getRetcode());
        assertEquals(List.of(1), decoded.getIdListList());
        assertEquals(List.of(item), decoded.getItemListList());
        assertEquals(
                List.of(1),
                TakeAchievementGoalRewardRsp.parseFrom(HexFormat.of().parseHex("4001"))
                        .getIdListList());
    }

    @Test
    void defaultErrorRetainsAlreadyTakenSemanticsOnFieldSeven() throws Exception {
        // RET_REWARD_HAS_TAKEN = 629 -> ordinary varint f5 04, preceded by tag 38.
        var fixture = HexFormat.of().parseHex("38f504");
        assertEquals(629, Retcode.RET_REWARD_HAS_TAKEN_VALUE);
        assertArrayEquals(fixture, new PacketTakeAchievementGoalRewardRsp().getData());
        var decoded = TakeAchievementGoalRewardRsp.parseFrom(fixture);
        assertEquals(Retcode.RET_REWARD_HAS_TAKEN_VALUE, decoded.getRetcode());
        assertTrue(decoded.getIdListList().isEmpty());
        assertTrue(decoded.getItemListList().isEmpty());
        // Signed int32 uses a ten-byte negative varint, not ZigZag.
        assertArrayEquals(
                HexFormat.of().parseHex("38ffffffffffffffffff01"),
                TakeAchievementGoalRewardRsp.newBuilder().setRetcode(-1).build().toByteArray());
    }
}
