package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Descriptors.FieldDescriptor;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TakePlayerLevelRewardReqOuterClass.TakePlayerLevelRewardReq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdventureRankRequestSchemaTest {
    @Test
    void canonicalDescriptorUsesCapturedFieldAndUnsignedType() {
        var field = TakePlayerLevelRewardReq.getDescriptor().findFieldByName("level");
        assertEquals(15, field.getNumber());
        assertEquals(FieldDescriptor.Type.UINT32, field.getType());
        assertEquals(7321, PacketOpcodes.TakePlayerLevelRewardReq);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void decodesCapturedKatheryneRequest(int level) throws Exception {
        byte[] captured = {(byte) 0x78, (byte) level};
        assertEquals(level, TakePlayerLevelRewardReq.parseFrom(captured).getLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void writesTheCapturedWireLayout(int level) {
        byte[] captured = {(byte) 0x78, (byte) level};
        assertArrayEquals(
                captured,
                TakePlayerLevelRewardReq.newBuilder().setLevel(level).build().toByteArray());
    }

    @Test
    void staleFieldElevenIsNotTheCanonicalLevel() throws Exception {
        assertEquals(0, TakePlayerLevelRewardReq.parseFrom(new byte[] {0x58, 0x03}).getLevel());
    }
}
