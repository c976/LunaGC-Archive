package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SetReliquaryStarStateRsp._SetReliquaryStarStateRsp;

/** SetReliquaryStarStateRsp. */
public class PacketSetReliquaryStarStateRsp extends BasePacket {
    public PacketSetReliquaryStarStateRsp(long guid, boolean starred) {
        super(PacketOpcodes.SetReliquaryStarStateRsp);
        this.setData(
                _SetReliquaryStarStateRsp.newBuilder()
                        .setTargetReliquaryGuid(guid)
                        .setIsRelicStarred(starred)
                        .build());
    }
}
