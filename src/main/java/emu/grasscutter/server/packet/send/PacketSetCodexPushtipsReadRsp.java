package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetCodexPushtipsReadRspOuterClass.SetCodexPushtipsReadRsp;

public class PacketSetCodexPushtipsReadRsp extends BasePacket {
    public PacketSetCodexPushtipsReadRsp(int retcode, int typeId, int codexId) {
        super(PacketOpcodes.SetCodexPushtipsReadRsp);

        this.setData(
                SetCodexPushtipsReadRsp.newBuilder()
                        .setRetcode(retcode)
                        .setTypeId(typeId)
                        .setCodexId(codexId)
                        .build());
    }
}
