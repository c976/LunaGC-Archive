package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ExecuteGroupTriggerRspOuterClass.ExecuteGroupTriggerRsp;

public class PacketExecuteGroupTriggerRsp extends BasePacket {
    public PacketExecuteGroupTriggerRsp(String sourceName) {
        super(PacketOpcodes.ExecuteGroupTriggerRsp);

        this.setData(ExecuteGroupTriggerRsp.newBuilder().setSourceName(sourceName));
    }
}
