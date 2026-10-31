package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.BlossomBriefInfoNotifyOuterClass.BlossomBriefInfoNotify;
import emu.grasscutter.net.proto.BlossomBriefInfoOuterClass.BlossomBriefInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PacketBlossomBriefInfoNotifyNativeTest {
    @Test
    void global71MarkerUsesOriginal6743AndRepeatedField10() throws Exception {
        var entry = BlossomBriefInfo.newBuilder()
                .setSceneId(3)
                .setResin(20)
                .setRefreshId(2)
                .build();
        var packet = new PacketBlossomBriefInfoNotify(List.of(entry));

        assertEquals(6743, PacketOpcodes.BlossomBriefInfoNotify);
        assertEquals(6743, packet.getOpcode());
        assertEquals(
                10,
                BlossomBriefInfoNotify.getDescriptor()
                        .findFieldByName("brief_info_list")
                        .getNumber());
        var wire = CodedInputStream.newInstance(packet.getData());
        assertEquals(0x52, wire.readTag());
        assertArrayEquals(entry.toByteArray(), wire.readByteArray());
        assertEquals(0, wire.readTag());
        assertEquals(
                List.of(entry),
                BlossomBriefInfoNotify.parseFrom(packet.getData()).getBriefInfoListList());
    }
}
