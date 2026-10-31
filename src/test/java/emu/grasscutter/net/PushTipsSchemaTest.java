package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetPushTipsRewardReqOuterClass.GetPushTipsRewardReq;
import emu.grasscutter.net.proto.GetPushTipsRewardRspOuterClass.GetPushTipsRewardRsp;
import emu.grasscutter.net.proto.PushTipsAllDataNotifyOuterClass.PushTipsAllDataNotify;
import emu.grasscutter.net.proto.PushTipsChangeNotifyOuterClass.PushTipsChangeNotify;
import emu.grasscutter.net.proto.PushTipsDataOuterClass.PushTipsData;
import emu.grasscutter.net.proto.PushTipsReadFinishReqOuterClass.PushTipsReadFinishReq;
import emu.grasscutter.net.proto.PushTipsReadFinishRspOuterClass.PushTipsReadFinishRsp;
import emu.grasscutter.net.proto.SetCodexPushtipsReadReqOuterClass.SetCodexPushtipsReadReq;
import emu.grasscutter.net.proto.SetCodexPushtipsReadRspOuterClass.SetCodexPushtipsReadRsp;
import org.junit.jupiter.api.Test;

/** kitkat-multiverse/genshin-protocol 7.1.0 field numbers and CmdIds of the push tips messages. */
final class PushTipsSchemaTest {
    @Test
    void commandIds() {
        assertEquals(21552, PacketOpcodes.PushTipsAllDataNotify);
        assertEquals(4737, PacketOpcodes.PushTipsChangeNotify);
        assertEquals(22209, PacketOpcodes.PushTipsReadFinishReq);
        assertEquals(7034, PacketOpcodes.PushTipsReadFinishRsp);
        assertEquals(8298, PacketOpcodes.GetPushTipsRewardReq);
        assertEquals(21994, PacketOpcodes.GetPushTipsRewardRsp);
        assertEquals(3090, PacketOpcodes.SetCodexPushtipsReadReq);
        assertEquals(22326, PacketOpcodes.SetCodexPushtipsReadRsp);
    }

    @Test
    void tipDataAndAllDataNotify() {
        assertEquals(12, PushTipsData.STATE_FIELD_NUMBER);
        assertEquals(7, PushTipsData.PUSH_TIPS_ID_FIELD_NUMBER);
        assertEquals(2, PushTipsAllDataNotify.PUSH_TIPS_LIST_FIELD_NUMBER);
    }

    @Test
    void changeNotify() {
        assertEquals(7, PushTipsChangeNotify.PUSH_TIPS_LIST_FIELD_NUMBER);
        var descriptor = PushTipsChangeNotify.getDescriptor();
        assertEquals(2, descriptor.findFieldByName("_repeated_push_tips_id_list").getNumber());
    }

    @Test
    void readFinish() {
        assertEquals(14, PushTipsReadFinishReq.PUSH_TIPS_ID_FIELD_NUMBER);
        assertEquals(7, PushTipsReadFinishRsp.RETCODE_FIELD_NUMBER);
        assertEquals(14, PushTipsReadFinishRsp.PUSH_TIPS_ID_FIELD_NUMBER);
    }

    @Test
    void reward() {
        assertEquals(3, GetPushTipsRewardReq.PUSH_TIPS_ID_LIST_FIELD_NUMBER);
        assertEquals(6, GetPushTipsRewardRsp.PUSH_TIPS_ID_LIST_FIELD_NUMBER);
        assertEquals(5, GetPushTipsRewardRsp.RETCODE_FIELD_NUMBER);
    }

    @Test
    void codexRead() {
        assertEquals(7, SetCodexPushtipsReadReq.CODEX_ID_FIELD_NUMBER);
        assertEquals(10, SetCodexPushtipsReadReq.TYPE_ID_FIELD_NUMBER);
        assertEquals(15, SetCodexPushtipsReadRsp.TYPE_ID_FIELD_NUMBER);
        assertEquals(8, SetCodexPushtipsReadRsp.CODEX_ID_FIELD_NUMBER);
        assertEquals(6, SetCodexPushtipsReadRsp.RETCODE_FIELD_NUMBER);
    }
}
