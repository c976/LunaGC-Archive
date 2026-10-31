package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PushTipsReadFinishRspOuterClass.PushTipsReadFinishRsp;

public class PacketPushTipsReadFinishRsp extends BasePacket {
    public PacketPushTipsReadFinishRsp(int retcode, int tipId) {
        super(PacketOpcodes.PushTipsReadFinishRsp);

        this.setData(
                PushTipsReadFinishRsp.newBuilder().setRetcode(retcode).setPushTipsId(tipId).build());
    }
}
