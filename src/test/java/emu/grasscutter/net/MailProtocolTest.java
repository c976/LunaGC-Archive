package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DelMailReqOuterClass.DelMailReq;
import emu.grasscutter.net.proto.GetAllMailNotifyOuterClass.GetAllMailNotify;
import emu.grasscutter.net.proto.GetMailItemReqOuterClass.GetMailItemReq;
import emu.grasscutter.net.proto.ReadMailNotifyOuterClass.ReadMailNotify;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** The 7.1 mailbox requests, without which the mailbox never finishes loading. */
final class MailProtocolTest {
    @Test
    void mailboxRequestsAreMapped() {
        assertEquals(28960, PacketOpcodes.GetAllMailNotify);
        assertEquals(8592, PacketOpcodes.ReadMailNotify);
    }

    @Test
    void requestFieldsMatchSevenOne() throws Exception {
        // is_collected = true on field 2; mail ids 3 and 5 packed on field 14.
        assertTrue(GetAllMailNotify.parseFrom(HexFormat.of().parseHex("1001")).getIsCollected());
        var read = ReadMailNotify.parseFrom(HexFormat.of().parseHex("72020305"));
        assertEquals(java.util.List.of(3, 5), read.getMailIdListList());
    }

    @Test
    void claimAndDeleteReadTheirMailIds() throws Exception {
        assertEquals(3042, PacketOpcodes.GetMailItemReq);
        assertEquals(3404, PacketOpcodes.DelMailReq);
        // Claiming attachments: mail ids 3 and 5 packed on field 12.
        var claim = GetMailItemReq.parseFrom(HexFormat.of().parseHex("62020305"));
        assertEquals(java.util.List.of(3, 5), claim.getMailIdListList());
        // Deleting: mail ids 3 and 5 packed on field 15.
        var delete = DelMailReq.parseFrom(HexFormat.of().parseHex("7a020305"));
        assertEquals(java.util.List.of(3, 5), delete.getMailIdListList());
    }
}
